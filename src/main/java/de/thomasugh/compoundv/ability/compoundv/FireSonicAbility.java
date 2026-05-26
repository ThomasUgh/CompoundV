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
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Vector;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class FireSonicAbility implements Ability {

    private final CompoundV plugin;
    private final Map<UUID, Boolean> beamActive = new HashMap<>();
    private final Map<UUID, Integer> activeTicks = new HashMap<>();
    private final Map<UUID, Integer> damageCounter = new HashMap<>();
    private final Map<UUID, Long> cooldownUntil = new HashMap<>();

    public FireSonicAbility(CompoundV plugin) {
        this.plugin = plugin;
    }

    @Override public String getId() { return "fire_sonic"; }
    @Override public String getDisplayName() { return "FireSonic"; }
    @Override public int getColor() { return 0xFF6A1A; }
    @Override public boolean hasToggle() { return true; }
    @Override public boolean needsTick() { return true; }

    @Override
    public void apply(Player player) {
        int strength = plugin.getConfig().getInt("abilities.fire_sonic.strength_level", 1);
        int resistance = plugin.getConfig().getInt("abilities.fire_sonic.resistance_level", 1);
        player.addPotionEffect(new PotionEffect(PotionEffects.FIRE_RESISTANCE,
                Integer.MAX_VALUE, 0, false, false, true));
        player.addPotionEffect(new PotionEffect(PotionEffects.STRENGTH,
                Integer.MAX_VALUE, Math.max(0, strength - 1), false, false, true));
        player.addPotionEffect(new PotionEffect(PotionEffects.RESISTANCE,
                Integer.MAX_VALUE, Math.max(0, resistance - 1), false, false, true));
    }

    @Override
    public void remove(Player player) {
        UUID uuid = player.getUniqueId();
        beamActive.remove(uuid);
        activeTicks.remove(uuid);
        damageCounter.remove(uuid);
        cooldownUntil.remove(uuid);
        player.removePotionEffect(PotionEffects.FIRE_RESISTANCE);
        player.removePotionEffect(PotionEffects.STRENGTH);
        player.removePotionEffect(PotionEffects.RESISTANCE);
    }

    @Override
    public void onToggle(Player player) {
        UUID uuid = player.getUniqueId();
        if (beamActive.getOrDefault(uuid, false)) {
            stopBeam(player, false);
            return;
        }

        long now = System.currentTimeMillis();
        long readyAt = cooldownUntil.getOrDefault(uuid, 0L);
        if (readyAt > now) {
            long seconds = Math.max(1L, (long) Math.ceil((readyAt - now) / 1000.0));
            MessageUtil.sendActionBar(player, plugin.getLocaleManager().msg("fire_sonic.cooldown", "seconds", Long.toString(seconds)));
            player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 0.45f, 0.75f);
            return;
        }

        beamActive.put(uuid, true);
        activeTicks.put(uuid, 0);
        damageCounter.remove(uuid);
        player.getWorld().playSound(player.getLocation(), Sound.ITEM_FIRECHARGE_USE, 0.8f, 1.45f);
        MessageUtil.sendActionBar(player, plugin.getLocaleManager().msg("fire_sonic.beam_on"));
    }

    @Override
    public void onTick(Player player) {
        UUID uuid = player.getUniqueId();
        if (!beamActive.getOrDefault(uuid, false)) return;

        int ticks = activeTicks.merge(uuid, 1, Integer::sum);
        int maxTicks = plugin.getConfig().getInt("abilities.fire_sonic.beam_max_ticks", 40);
        if (maxTicks > 0 && ticks >= maxTicks) {
            stopBeam(player, true);
            return;
        }
        fireBeam(player);
    }

    public void handleMeleeHit(Player attacker, LivingEntity target) {
        int fireTicks = plugin.getConfig().getInt("abilities.fire_sonic.melee_fire_ticks", 160);
        target.setFireTicks(Math.max(target.getFireTicks(), fireTicks));
        target.getWorld().spawnParticle(Particle.FLAME, target.getLocation().add(0, 1, 0), 12, 0.25, 0.35, 0.25, 0.03);
    }

    private void stopBeam(Player player, boolean overheated) {
        UUID uuid = player.getUniqueId();
        beamActive.put(uuid, false);
        activeTicks.remove(uuid);
        damageCounter.remove(uuid);
        if (overheated) {
            long cooldownMs = plugin.getConfig().getLong("abilities.fire_sonic.beam_cooldown_ms", 5000L);
            cooldownUntil.put(uuid, System.currentTimeMillis() + Math.max(0L, cooldownMs));
        }
        player.playSound(player.getLocation(), Sound.BLOCK_FIRE_EXTINGUISH, 0.5f, 1.3f);
        MessageUtil.sendActionBar(player, plugin.getLocaleManager().msg(overheated ? "fire_sonic.beam_overheated" : "fire_sonic.beam_off"));
    }

    private void fireBeam(Player player) {
        double range = plugin.getConfig().getDouble("abilities.fire_sonic.beam_range", 24.0);
        double damage = plugin.getConfig().getDouble("abilities.fire_sonic.beam_damage_hearts", 1.5) * 2.0;
        int fireTicks = plugin.getConfig().getInt("abilities.fire_sonic.beam_fire_ticks", 140);
        int damageInterval = Math.max(1, plugin.getConfig().getInt("abilities.fire_sonic.beam_damage_interval_ticks", 2));
        double radius = plugin.getConfig().getDouble("abilities.fire_sonic.beam_hit_radius", 1.20);

        Location eye = player.getEyeLocation();
        Vector dir = eye.getDirection().normalize();
        World world = player.getWorld();
        RayTraceResult blockHit = world.rayTraceBlocks(eye, dir, range, FluidCollisionMode.NEVER, true);
        double effectiveRange = blockHit != null && blockHit.getHitPosition() != null
                ? Math.min(range, blockHit.getHitPosition().distance(eye.toVector()))
                : range;

        renderFireBeam(world, eye, dir, effectiveRange);
        int counter = damageCounter.merge(player.getUniqueId(), 1, Integer::sum);
        if (counter % damageInterval != 0) return;

        Location center = eye.clone().add(dir.clone().multiply(effectiveRange * 0.5));
        for (Entity entity : world.getNearbyEntities(center,
                effectiveRange * 0.5 + radius, effectiveRange * 0.5 + radius, effectiveRange * 0.5 + radius)) {
            if (!(entity instanceof LivingEntity target) || entity.equals(player)) continue;
            if (!isNearBeamTarget(eye, dir, effectiveRange, radius, target)) continue;
            target.setNoDamageTicks(0);
            target.damage(Math.max(0.0, damage), player);
            target.setFireTicks(Math.max(target.getFireTicks(), fireTicks));
            world.spawnParticle(Particle.FLAME, target.getLocation().add(0, 1, 0), 18, 0.20, 0.30, 0.20, 0.04);
        }
    }

    private void renderFireBeam(World world, Location origin, Vector direction, double distance) {
        Particle.DustOptions orange = new Particle.DustOptions(Color.fromRGB(255, 115, 20), 0.85f);
        for (double d = 0.5; d <= distance; d += 0.30) {
            Location point = origin.clone().add(direction.clone().multiply(d));
            world.spawnParticle(Particle.DUST, point, 2, 0.025, 0.025, 0.025, 0, orange);
            if (((int) (d * 10)) % 5 == 0) {
                world.spawnParticle(Particle.FLAME, point, 1, 0.025, 0.025, 0.025, 0.012);
            }
        }
    }

    private boolean isNearBeamTarget(Location origin, Vector dir, double range, double radius, LivingEntity target) {
        Location feet = target.getLocation();
        Location middle = feet.clone().add(0, Math.max(0.35, target.getEyeHeight() * 0.52), 0);
        return isNearBeam(origin, dir, range, radius, target.getEyeLocation())
                || isNearBeam(origin, dir, range, radius, middle)
                || isNearBeam(origin, dir, range, radius * 0.85, feet.clone().add(0, 0.25, 0));
    }

    private boolean isNearBeam(Location origin, Vector dir, double range, double radius, Location target) {
        Vector rel = target.toVector().subtract(origin.toVector());
        double along = rel.dot(dir);
        if (along < 0 || along > range) return false;
        Vector closest = origin.toVector().add(dir.clone().multiply(along));
        return closest.distanceSquared(target.toVector()) <= radius * radius;
    }
}
