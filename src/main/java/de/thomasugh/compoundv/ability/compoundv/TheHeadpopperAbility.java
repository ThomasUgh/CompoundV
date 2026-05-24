package de.thomasugh.compoundv.ability.compoundv;

import de.thomasugh.compoundv.CompoundV;
import de.thomasugh.compoundv.ability.Ability;
import de.thomasugh.compoundv.server.SchedulerAdapter;
import de.thomasugh.compoundv.util.MessageUtil;
import de.thomasugh.compoundv.util.PotionEffects;
import org.bukkit.Color;
import org.bukkit.FluidCollisionMode;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.entity.Animals;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Monster;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.util.RayTraceResult;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class TheHeadpopperAbility implements Ability {

    private final CompoundV plugin;
    private final Map<UUID, Long> cooldownUntil = new HashMap<>();
    private final Map<UUID, Long> lastHandledAt = new HashMap<>();

    public TheHeadpopperAbility(CompoundV plugin) {
        this.plugin = plugin;
    }

    @Override public String getId() { return "the_headpopper"; }
    @Override public String getDisplayName() { return "The Headpopper"; }
    @Override public int getColor() { return 0xB00020; }

    @Override
    public void apply(Player player) {
        int strength = plugin.getConfig().getInt("abilities.the_headpopper.strength_level", 2);
        int resistance = plugin.getConfig().getInt("abilities.the_headpopper.resistance_level", 2);
        int regeneration = plugin.getConfig().getInt("abilities.the_headpopper.regeneration_level", 1);
        player.addPotionEffect(new PotionEffect(PotionEffects.STRENGTH, Integer.MAX_VALUE, Math.max(0, strength - 1), false, false, true));
        player.addPotionEffect(new PotionEffect(PotionEffects.RESISTANCE, Integer.MAX_VALUE, Math.max(0, resistance - 1), false, false, true));
        player.addPotionEffect(new PotionEffect(PotionEffects.REGENERATION, Integer.MAX_VALUE, Math.max(0, regeneration - 1), false, false, true));
    }

    @Override
    public void remove(Player player) {
        cooldownUntil.remove(player.getUniqueId());
        lastHandledAt.remove(player.getUniqueId());
        player.removePotionEffect(PotionEffects.STRENGTH);
        player.removePotionEffect(PotionEffects.RESISTANCE);
        player.removePotionEffect(PotionEffects.REGENERATION);
    }

    public void markTarget(Player player) {
        UUID uuid = player.getUniqueId();
        long handledNow = System.currentTimeMillis();
        long lastHandled = lastHandledAt.getOrDefault(uuid, 0L);
        if (handledNow - lastHandled < 250L) return;
        lastHandledAt.put(uuid, handledNow);

        long now = System.currentTimeMillis();
        long readyAt = cooldownUntil.getOrDefault(uuid, 0L);
        if (readyAt > now) {
            long seconds = Math.max(1L, (long) Math.ceil((readyAt - now) / 1000.0));
            MessageUtil.sendActionBar(player, plugin.getLocaleManager().msg("the_headpopper.cooldown", "seconds", Long.toString(seconds)));
            return;
        }

        double range = plugin.getConfig().getDouble("abilities.the_headpopper.range", 30.0);
        RayTraceResult result = player.getWorld().rayTrace(player.getEyeLocation(), player.getEyeLocation().getDirection().normalize(),
                range, FluidCollisionMode.NEVER, true, 0.45,
                entity -> entity != player && entity instanceof LivingEntity);
        if (result == null || !(result.getHitEntity() instanceof LivingEntity target)) {
            MessageUtil.sendActionBar(player, plugin.getLocaleManager().msg("the_headpopper.no_target"));
            return;
        }

        cooldownUntil.put(uuid, now + plugin.getConfig().getLong("abilities.the_headpopper.cooldown_ms", 30000L));
        startCountdown(player, target);
    }

    private void startCountdown(Player player, LivingEntity target) {
        int seconds = plugin.getConfig().getInt("abilities.the_headpopper.countdown_seconds", 3);
        int slownessAmplifier = plugin.getConfig().getInt("abilities.the_headpopper.slowness_amplifier", 2);
        int totalTicks = Math.max(1, seconds * 20 + 10);
        target.addPotionEffect(new PotionEffect(PotionEffects.GLOWING, totalTicks, 0, false, false, false));
        target.addPotionEffect(new PotionEffect(PotionEffects.SLOWNESS, totalTicks, Math.max(0, slownessAmplifier), false, true, true));
        player.getWorld().playSound(target.getLocation(), Sound.BLOCK_BEACON_ACTIVATE, 0.55f, 0.65f);

        for (int i = 0; i < seconds; i++) {
            final int remaining = seconds - i;
            SchedulerAdapter.runLater(plugin, () -> {
                if (!isValidMarkedTarget(player, target)) return;
                renderMark(target);
                MessageUtil.sendActionBar(player, plugin.getLocaleManager().msg("the_headpopper.countdown", "seconds", Integer.toString(remaining)));
            }, i * 20L);
        }

        SchedulerAdapter.runLater(plugin, () -> pop(player, target), seconds * 20L);
    }

    private void pop(Player player, LivingEntity target) {
        if (!isValidMarkedTarget(player, target)) return;
        double damage = plugin.getConfig().getDouble("abilities.the_headpopper.damage_hearts", 12.5) * 2.0;
        double mobMultiplier = plugin.getConfig().getDouble("abilities.the_headpopper.mob_damage_multiplier", 2.5);
        if (!(target instanceof Player)) damage *= Math.max(0.0, mobMultiplier);
        Location loc = target.getLocation().add(0, Math.min(1.4, Math.max(0.8, target.getEyeHeight())), 0);
        World world = target.getWorld();
        world.spawnParticle(Particle.DUST, loc, 46, 0.35, 0.35, 0.35, 0,
                new Particle.DustOptions(Color.fromRGB(180, 0, 32), 1.35f));
        world.spawnParticle(Particle.DAMAGE_INDICATOR, loc, 18, 0.24, 0.22, 0.24, 0.08);
        world.playSound(loc, Sound.ENTITY_GENERIC_EXPLODE, 0.72f, 1.65f);
        target.damage(Math.max(0.0, damage), player);
        target.removePotionEffect(PotionEffects.GLOWING);
        target.removePotionEffect(PotionEffects.SLOWNESS);
        MessageUtil.sendActionBar(player, plugin.getLocaleManager().msg("the_headpopper.released"));
    }

    private boolean isValidMarkedTarget(Player player, LivingEntity target) {
        if (!player.isOnline() || target.isDead() || !target.isValid()) return false;
        if (!player.getWorld().equals(target.getWorld())) return false;
        double maxDistance = plugin.getConfig().getDouble("abilities.the_headpopper.range", 30.0) + 4.0;
        return player.getLocation().distanceSquared(target.getLocation()) <= maxDistance * maxDistance;
    }

    private void renderMark(LivingEntity target) {
        Location loc = target.getLocation().add(0, Math.min(1.5, Math.max(0.9, target.getEyeHeight())), 0);
        target.getWorld().spawnParticle(Particle.DUST, loc, 26, 0.28, 0.32, 0.28, 0,
                new Particle.DustOptions(Color.fromRGB(255, 20, 35), 1.0f));
        target.getWorld().spawnParticle(Particle.CRIT, loc, 8, 0.22, 0.22, 0.22, 0.04);
        target.getWorld().playSound(loc, Sound.BLOCK_NOTE_BLOCK_PLING, 0.45f, 0.6f);
    }
}
