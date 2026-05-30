package de.thomasugh.compoundv.ability.compoundv;

import de.thomasugh.compoundv.CompoundV;
import de.thomasugh.compoundv.ability.Ability;
import de.thomasugh.compoundv.data.CompoundPotion;
import de.thomasugh.compoundv.data.PlayerAbilityData;
import de.thomasugh.compoundv.server.SchedulerAdapter;
import de.thomasugh.compoundv.util.MessageUtil;
import de.thomasugh.compoundv.util.AbilityKillTracker;
import de.thomasugh.compoundv.util.PotionEffects;
import org.bukkit.Color;
import org.bukkit.FluidCollisionMode;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.util.RayTraceResult;

import java.util.HashMap;
import java.util.Map;
import java.util.Locale;
import java.util.UUID;

public class TheHeadpopperAbility implements Ability {

    private final CompoundV plugin;
    private final Map<UUID, Long> cooldownUntil = new HashMap<>();
    private final Map<UUID, Long> areaCooldownUntil = new HashMap<>();
    private final Map<UUID, Long> lastHandledAt = new HashMap<>();
    private final Map<UUID, UUID> activeMarkedTargets = new HashMap<>();

    public TheHeadpopperAbility(CompoundV plugin) {
        this.plugin = plugin;
    }

    @Override public String getId() { return "the_headpopper"; }
    @Override public String getDisplayName() { return "The Headpopper"; }
    @Override public int getColor() { return 0xB00020; }
    @Override public boolean hasToggle() { return true; }

    @Override
    public void onToggle(Player player) {
        releaseAreaPulse(player);
    }

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
        areaCooldownUntil.remove(player.getUniqueId());
        activeMarkedTargets.remove(player.getUniqueId());
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

        if (activeMarkedTargets.containsKey(uuid)) {
            MessageUtil.sendActionBar(player, plugin.getLocaleManager().msg("the_headpopper.countdown_active"));
            return;
        }

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

        startCountdown(player, target);
    }

    private void releaseAreaPulse(Player player) {
        UUID uuid = player.getUniqueId();
        long now = System.currentTimeMillis();
        long readyAt = areaCooldownUntil.getOrDefault(uuid, 0L);
        if (readyAt > now) {
            long seconds = Math.max(1L, (long) Math.ceil((readyAt - now) / 1000.0));
            MessageUtil.sendActionBar(player, plugin.getLocaleManager().msg("the_headpopper.area_cooldown", "seconds", Long.toString(seconds)));
            player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 0.45f, 0.55f);
            return;
        }

        long cooldownMs = plugin.getConfig().getLong("abilities.the_headpopper.area_cooldown_ms", 120000L);
        areaCooldownUntil.put(uuid, now + Math.max(0L, cooldownMs));

        double radius = plugin.getConfig().getDouble("abilities.the_headpopper.area_radius", 10.0);
        double ratio = plugin.getConfig().getDouble("abilities.the_headpopper.area_damage_health_percent", 0.25);
        Location center = player.getLocation();
        World world = player.getWorld();
        int affected = 0;

        world.playSound(center, Sound.ENTITY_WITHER_AMBIENT, 0.75f, 0.45f);
        world.playSound(center, Sound.ENTITY_WARDEN_HEARTBEAT, 0.85f, 0.72f);
        world.spawnParticle(Particle.DUST, center.clone().add(0, 1.15, 0), 90, radius * 0.34, 0.65, radius * 0.34, 0,
                new Particle.DustOptions(Color.fromRGB(145, 0, 24), 1.25f));
        world.spawnParticle(Particle.DAMAGE_INDICATOR, center.clone().add(0, 1.0, 0), 42, radius * 0.24, 0.45, radius * 0.24, 0.08);

        for (Entity entity : world.getNearbyEntities(center, radius, radius, radius)) {
            if (!(entity instanceof LivingEntity target) || target.equals(player)) continue;
            if (target.getLocation().distanceSquared(center) > radius * radius) continue;
            double damage = Math.max(0.0, target.getHealth() * Math.max(0.0, ratio));
            damage *= targetDamageMultiplier(target);
            Location loc = target.getLocation().add(0, Math.min(1.4, Math.max(0.8, target.getEyeHeight())), 0);
            AbilityKillTracker.damage(plugin, target, player, damage, "death_messages.the_headpopper", false);
            world.spawnParticle(Particle.DUST, loc, 22, 0.25, 0.28, 0.25, 0,
                    new Particle.DustOptions(Color.fromRGB(190, 0, 32), 1.05f));
            world.spawnParticle(Particle.DAMAGE_INDICATOR, loc, 8, 0.18, 0.20, 0.18, 0.08);
            affected++;
        }

        MessageUtil.sendActionBar(player, plugin.getLocaleManager().msg("the_headpopper.area_released", "targets", Integer.toString(affected)));
    }

    private void startCountdown(Player player, LivingEntity target) {
        UUID playerId = player.getUniqueId();
        activeMarkedTargets.put(playerId, target.getUniqueId());

        int seconds = plugin.getConfig().getInt("abilities.the_headpopper.countdown_seconds", 3);
        int slownessAmplifier = plugin.getConfig().getInt("abilities.the_headpopper.slowness_amplifier", 2);
        int totalTicks = Math.max(1, seconds * 20 + 10);
        target.addPotionEffect(new PotionEffect(PotionEffects.GLOWING, totalTicks, 0, false, false, false));
        target.addPotionEffect(new PotionEffect(PotionEffects.SLOWNESS, totalTicks, Math.max(0, slownessAmplifier), false, true, true));
        player.getWorld().playSound(target.getLocation(), Sound.ENTITY_WITHER_AMBIENT, 0.46f, 0.48f);
        player.getWorld().playSound(target.getLocation(), Sound.ENTITY_WARDEN_HEARTBEAT, 0.65f, 0.65f);

        for (int i = 0; i < seconds; i++) {
            final int remaining = seconds - i;
            SchedulerAdapter.runLater(plugin, player, () -> {
                if (!isValidMarkedTarget(player, target)) {
                    cancelCountdown(player, target);
                    return;
                }
                renderMark(target);
                MessageUtil.sendActionBar(player, plugin.getLocaleManager().msg("the_headpopper.countdown", "seconds", Integer.toString(remaining)));
            }, i * 20L);
        }

        SchedulerAdapter.runLater(plugin, player, () -> pop(player, target), seconds * 20L);
    }

    private void pop(Player player, LivingEntity target) {
        if (!isValidMarkedTarget(player, target)) {
            cancelCountdown(player, target);
            return;
        }
        activeMarkedTargets.remove(player.getUniqueId());
        cooldownUntil.put(player.getUniqueId(), System.currentTimeMillis()
                + plugin.getConfig().getLong("abilities.the_headpopper.cooldown_ms", 40000L));
        double damage = plugin.getConfig().getDouble("abilities.the_headpopper.damage_hearts", 20.0) * 2.0;
        double mobMultiplier = plugin.getConfig().getDouble("abilities.the_headpopper.mob_damage_multiplier", 2.5);
        if (!(target instanceof Player)) damage *= Math.max(0.0, mobMultiplier);
        damage *= targetDamageMultiplier(target);
        Location loc = target.getLocation().add(0, Math.min(1.4, Math.max(0.8, target.getEyeHeight())), 0);
        World world = target.getWorld();
        world.spawnParticle(Particle.DUST, loc, 46, 0.35, 0.35, 0.35, 0,
                new Particle.DustOptions(Color.fromRGB(180, 0, 32), 1.35f));
        world.spawnParticle(Particle.DAMAGE_INDICATOR, loc, 18, 0.24, 0.22, 0.24, 0.08);
        world.playSound(loc, Sound.ENTITY_GENERIC_EXPLODE, 0.72f, 1.65f);
        AbilityKillTracker.damage(plugin, target, player, Math.max(0.0, damage), "death_messages.the_headpopper", false);
        target.removePotionEffect(PotionEffects.GLOWING);
        target.removePotionEffect(PotionEffects.SLOWNESS);
        MessageUtil.sendActionBar(player, plugin.getLocaleManager().msg("the_headpopper.released"));
    }

    private boolean isValidMarkedTarget(Player player, LivingEntity target) {
        if (!player.isOnline() || !player.isSneaking() || target.isDead() || !target.isValid()) return false;
        UUID markedTarget = activeMarkedTargets.get(player.getUniqueId());
        if (markedTarget == null || !markedTarget.equals(target.getUniqueId())) return false;
        if (!player.getWorld().equals(target.getWorld())) return false;
        double maxDistance = plugin.getConfig().getDouble("abilities.the_headpopper.range", 30.0) + 4.0;
        return player.getLocation().distanceSquared(target.getLocation()) <= maxDistance * maxDistance;
    }

    private void cancelCountdown(Player player, LivingEntity target) {
        UUID playerId = player.getUniqueId();
        UUID markedTarget = activeMarkedTargets.get(playerId);
        if (markedTarget == null || !markedTarget.equals(target.getUniqueId())) return;
        activeMarkedTargets.remove(playerId);
        target.removePotionEffect(PotionEffects.GLOWING);
        target.removePotionEffect(PotionEffects.SLOWNESS);
        MessageUtil.sendActionBar(player, plugin.getLocaleManager().msg("the_headpopper.cancelled"));
        player.playSound(player.getLocation(), Sound.BLOCK_RESPAWN_ANCHOR_DEPLETE, 0.38f, 0.65f);
    }

    private double targetDamageMultiplier(LivingEntity target) {
        if (!isVOneTarget(target)) return 1.0;
        return Math.max(0.0, plugin.getConfig().getDouble(
                "abilities.the_headpopper.v_one_target_damage_multiplier", 0.5));
    }

    private boolean isVOneTarget(LivingEntity target) {
        if (!(target instanceof Player player)) return false;
        PlayerAbilityData data = plugin.getAbilityManager().getData(player);
        if (data == null) return false;
        if (data.potionType() == CompoundPotion.V_ONE) return true;
        return data.abilityId() != null && data.abilityId().toLowerCase(Locale.ROOT).endsWith("_v_one");
    }

    private void renderMark(LivingEntity target) {
        Location loc = target.getLocation().add(0, Math.min(1.5, Math.max(0.9, target.getEyeHeight())), 0);
        target.getWorld().spawnParticle(Particle.DUST, loc, 26, 0.28, 0.32, 0.28, 0,
                new Particle.DustOptions(Color.fromRGB(255, 20, 35), 1.0f));
        target.getWorld().spawnParticle(Particle.CRIT, loc, 8, 0.22, 0.22, 0.22, 0.04);
        target.getWorld().playSound(loc, Sound.ENTITY_WARDEN_HEARTBEAT, 0.45f, 0.55f);
    }
}
