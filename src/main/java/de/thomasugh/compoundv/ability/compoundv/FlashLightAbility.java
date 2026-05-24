package de.thomasugh.compoundv.ability.compoundv;

import de.thomasugh.compoundv.CompoundV;
import de.thomasugh.compoundv.ability.Ability;
import de.thomasugh.compoundv.util.MessageUtil;
import de.thomasugh.compoundv.util.PotionEffects;
import org.bukkit.Color;
import org.bukkit.FluidCollisionMode;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Vector;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class FlashLightAbility implements Ability {

    private final CompoundV plugin;
    private final Map<UUID, Long> flashCooldown = new HashMap<>();
    private final Map<UUID, Long> beamCooldown = new HashMap<>();
    private final Map<UUID, Long> lastHandledAt = new HashMap<>();

    public FlashLightAbility(CompoundV plugin) {
        this.plugin = plugin;
    }

    @Override public String getId() { return "flash_light"; }
    @Override public String getDisplayName() { return "Flash Light"; }
    @Override public int getColor() { return 0xFFE066; }
    @Override public boolean hasToggle() { return true; }

    @Override
    public void apply(Player player) {
        int strength = plugin.getConfig().getInt("abilities.flash_light.strength_level", 2);
        int resistance = plugin.getConfig().getInt("abilities.flash_light.resistance_level", 1);
        player.addPotionEffect(new PotionEffect(PotionEffects.STRENGTH,
                Integer.MAX_VALUE, Math.max(0, strength - 1), false, false, true));
        player.addPotionEffect(new PotionEffect(PotionEffects.RESISTANCE,
                Integer.MAX_VALUE, Math.max(0, resistance - 1), false, false, true));
    }

    @Override
    public void remove(Player player) {
        UUID uuid = player.getUniqueId();
        flashCooldown.remove(uuid);
        beamCooldown.remove(uuid);
        lastHandledAt.remove(uuid);
        player.removePotionEffect(PotionEffects.STRENGTH);
        player.removePotionEffect(PotionEffects.RESISTANCE);
    }

    @Override
    public void onToggle(Player player) {
        UUID uuid = player.getUniqueId();
        long now = System.currentTimeMillis();
        long readyAt = flashCooldown.getOrDefault(uuid, 0L);
        if (readyAt > now) {
            long seconds = Math.max(1L, (long) Math.ceil((readyAt - now) / 1000.0));
            MessageUtil.sendActionBar(player, plugin.getLocaleManager().msg("flash_light.flash_cooldown",
                    "seconds", Long.toString(seconds)));
            player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 0.45f, 0.9f);
            return;
        }

        double radius = plugin.getConfig().getDouble("abilities.flash_light.flash_radius", 15.0);
        int durationTicks = plugin.getConfig().getInt("abilities.flash_light.blindness_ticks", 400);
        int amplifier = plugin.getConfig().getInt("abilities.flash_light.blindness_amplifier", 1);
        int affected = 0;

        for (Entity entity : player.getNearbyEntities(radius, radius, radius)) {
            if (!(entity instanceof LivingEntity living) || entity == player) continue;
            living.addPotionEffect(new PotionEffect(PotionEffects.BLINDNESS,
                    durationTicks, Math.max(0, amplifier), false, true, true));
            if (living instanceof Mob mob) {
                mob.setTarget(null);
            }
            affected++;
        }

        flashCooldown.put(uuid, now + plugin.getConfig().getLong("abilities.flash_light.flash_cooldown_ms", 120000L));
        World world = player.getWorld();
        Location center = player.getEyeLocation();
        world.spawnParticle(Particle.FLASH, center, 1, 0, 0, 0, 0);
        world.spawnParticle(Particle.END_ROD, center, 90, 2.2, 1.0, 2.2, 0.08);
        world.spawnParticle(Particle.DUST, center, 70, 1.8, 0.9, 1.8, 0,
                new Particle.DustOptions(Color.fromRGB(255, 238, 120), 1.35f));
        world.playSound(center, Sound.BLOCK_BEACON_ACTIVATE, 0.85f, 1.85f);
        world.playSound(center, Sound.ENTITY_FIREWORK_ROCKET_BLAST, 0.65f, 1.7f);
        MessageUtil.sendActionBar(player, plugin.getLocaleManager().msg("flash_light.flash_released",
                "targets", Integer.toString(affected)));
    }

    public void fireLightBeam(Player player) {
        UUID uuid = player.getUniqueId();
        long handledNow = System.currentTimeMillis();
        long lastHandled = lastHandledAt.getOrDefault(uuid, 0L);
        if (handledNow - lastHandled < 250L) return;
        lastHandledAt.put(uuid, handledNow);

        long now = System.currentTimeMillis();
        long readyAt = beamCooldown.getOrDefault(uuid, 0L);
        if (readyAt > now) return;
        beamCooldown.put(uuid, now + plugin.getConfig().getLong("abilities.flash_light.beam_cooldown_ms", 750L));

        double range = plugin.getConfig().getDouble("abilities.flash_light.beam_range", 15.0);
        double damage = plugin.getConfig().getDouble("abilities.flash_light.beam_damage_hearts", 2.0) * 2.0;
        double knockback = plugin.getConfig().getDouble("abilities.flash_light.beam_knockback", 0.75);
        double vertical = plugin.getConfig().getDouble("abilities.flash_light.beam_vertical_knockback", 0.18);

        Location eye = player.getEyeLocation();
        Vector direction = eye.getDirection().normalize();
        World world = player.getWorld();
        RayTraceResult hit = world.rayTrace(eye, direction, range, FluidCollisionMode.NEVER, true, 0.35,
                entity -> entity != player && entity instanceof LivingEntity);
        double distance = hit != null && hit.getHitPosition() != null
                ? hit.getHitPosition().distance(eye.toVector())
                : range;

        renderBeam(world, eye, direction, distance);
        world.playSound(player.getLocation(), Sound.ENTITY_FIREWORK_ROCKET_SHOOT, 0.55f, 1.85f);
        world.playSound(player.getLocation(), Sound.BLOCK_AMETHYST_BLOCK_CHIME, 0.45f, 1.9f);

        if (hit != null && hit.getHitEntity() instanceof LivingEntity target) {
            target.damage(Math.max(0.0, damage), player);
            int fireTicks = plugin.getConfig().getInt("abilities.flash_light.beam_fire_ticks", 2);
            if (fireTicks > 0) target.setFireTicks(Math.max(target.getFireTicks(), fireTicks));
            Vector push = target.getLocation().toVector().subtract(player.getLocation().toVector());
            if (push.lengthSquared() < 0.0001) push = direction.clone();
            push.normalize().multiply(knockback).setY(vertical);
            target.setVelocity(target.getVelocity().add(push));
            world.spawnParticle(Particle.FLASH, target.getLocation().add(0, 1, 0), 1, 0, 0, 0, 0);
        }
    }

    private void renderBeam(World world, Location start, Vector direction, double distance) {
        Particle.DustOptions dust = new Particle.DustOptions(Color.fromRGB(255, 245, 135), 1.05f);
        double step = 0.28;
        for (double d = 0.4; d <= distance; d += step) {
            Location point = start.clone().add(direction.clone().multiply(d));
            world.spawnParticle(Particle.DUST, point, 2, 0.035, 0.035, 0.035, 0, dust);
            if (((int) (d * 10)) % 6 == 0) {
                world.spawnParticle(Particle.END_ROD, point, 1, 0.025, 0.025, 0.025, 0.01);
            }
        }
    }
}
