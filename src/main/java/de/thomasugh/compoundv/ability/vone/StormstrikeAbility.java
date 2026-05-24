package de.thomasugh.compoundv.ability.vone;

import de.thomasugh.compoundv.CompoundV;
import de.thomasugh.compoundv.ability.Ability;
import de.thomasugh.compoundv.server.SchedulerAdapter;
import de.thomasugh.compoundv.util.AttributeUtil;
import de.thomasugh.compoundv.util.MessageUtil;
import de.thomasugh.compoundv.util.PotionEffects;
import org.bukkit.Color;
import org.bukkit.FluidCollisionMode;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
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
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

public class StormstrikeAbility implements Ability {

    private final CompoundV plugin;
    private final Set<UUID> launching = new HashSet<>();
    private final Map<UUID, Long> launchCooldown = new HashMap<>();
    private final Map<UUID, Long> lightningCooldown = new HashMap<>();
    private final Map<UUID, Long> lastHandledAt = new HashMap<>();
    private final Map<UUID, Boolean> beamActive = new HashMap<>();
    private final Map<UUID, Integer> beamTicks = new HashMap<>();
    private final Map<UUID, Integer> beamDamageCounter = new HashMap<>();
    private final Map<UUID, Long> beamCooldownUntil = new HashMap<>();
    private final NamespacedKey healthKey;

    public StormstrikeAbility(CompoundV plugin) {
        this.plugin = plugin;
        this.healthKey = new NamespacedKey(plugin, "stormstrike_hearts");
    }

    @Override public String getId() { return "stormstrike"; }
    @Override public String getDisplayName() { return "Stormstrike"; }
    @Override public int getColor() { return 0xF8F3B0; }
    @Override public boolean hasToggle() { return true; }
    @Override public boolean needsTick() { return true; }

    @Override
    public void apply(Player player) {
        player.setAllowFlight(true);
        player.setFlySpeed((float) flySpeed());
        int strength = plugin.getConfig().getInt("abilities.stormstrike.strength_level", 3);
        int resistance = plugin.getConfig().getInt("abilities.stormstrike.resistance_level", 3);
        player.addPotionEffect(new PotionEffect(PotionEffects.STRENGTH,
                Integer.MAX_VALUE, Math.max(0, strength - 1), false, false, true));
        player.addPotionEffect(new PotionEffect(PotionEffects.RESISTANCE,
                Integer.MAX_VALUE, Math.max(0, resistance - 1), false, false, true));
        double extraHearts = plugin.getConfig().getDouble("abilities.stormstrike.extra_hearts", 10.0);
        AttributeUtil.setMaxHealthBonus(player, healthKey, extraHearts * 2.0);
    }

    @Override
    public void remove(Player player) {
        UUID uuid = player.getUniqueId();
        launching.remove(uuid);
        launchCooldown.remove(uuid);
        lightningCooldown.remove(uuid);
        lastHandledAt.remove(uuid);
        beamActive.remove(uuid);
        beamTicks.remove(uuid);
        beamDamageCounter.remove(uuid);
        beamCooldownUntil.remove(uuid);
        AttributeUtil.setMaxHealthBonus(player, healthKey, 0.0);
        player.setFlySpeed(0.1f);
        player.removePotionEffect(PotionEffects.STRENGTH);
        player.removePotionEffect(PotionEffects.RESISTANCE);
        if (player.getGameMode() == GameMode.CREATIVE || player.getGameMode() == GameMode.SPECTATOR) return;
        player.setFlying(false);
        player.setAllowFlight(false);
    }

    @Override
    public void onToggle(Player player) {
        UUID uuid = player.getUniqueId();
        boolean active = beamActive.getOrDefault(uuid, false);
        if (active) {
            stopBeam(player, false);
            return;
        }

        long now = System.currentTimeMillis();
        long readyAt = beamCooldownUntil.getOrDefault(uuid, 0L);
        if (readyAt > now) {
            long seconds = Math.max(1L, (long) Math.ceil((readyAt - now) / 1000.0));
            MessageUtil.sendActionBar(player, plugin.getLocaleManager().msg("stormstrike.beam_cooldown",
                    "seconds", Long.toString(seconds)));
            player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 0.45f, 0.85f);
            return;
        }

        beamActive.put(uuid, true);
        beamTicks.put(uuid, 0);
        beamDamageCounter.remove(uuid);
        player.getWorld().playSound(player.getLocation(), Sound.ENTITY_LIGHTNING_BOLT_THUNDER, 0.55f, 1.75f);
        player.getWorld().playSound(player.getLocation(), Sound.BLOCK_BEACON_POWER_SELECT, 0.55f, 1.65f);
        MessageUtil.sendActionBar(player, plugin.getLocaleManager().msg("stormstrike.beam_on"));
    }

    @Override
    public void onTick(Player player) {
        if (!beamActive.getOrDefault(player.getUniqueId(), false)) return;

        int ticks = beamTicks.merge(player.getUniqueId(), 1, Integer::sum);
        int maxTicks = plugin.getConfig().getInt("abilities.stormstrike.beam_max_ticks", 100);
        if (maxTicks > 0 && ticks >= maxTicks) {
            stopBeam(player, true);
            return;
        }

        fireStormBeam(player);
    }

    private void stopBeam(Player player, boolean overheated) {
        UUID uuid = player.getUniqueId();
        beamActive.put(uuid, false);
        beamTicks.remove(uuid);
        beamDamageCounter.remove(uuid);
        long cooldownMs = plugin.getConfig().getLong("abilities.stormstrike.beam_cooldown_ms", 5000L);
        beamCooldownUntil.put(uuid, System.currentTimeMillis() + Math.max(0L, cooldownMs));
        MessageUtil.sendActionBar(player, plugin.getLocaleManager().msg(overheated ? "stormstrike.beam_overheated" : "stormstrike.beam_off"));
        player.playSound(player.getLocation(), Sound.BLOCK_FIRE_EXTINGUISH, 0.4f, 1.75f);
    }

    private void fireStormBeam(Player player) {
        double range = plugin.getConfig().getDouble("abilities.stormstrike.beam_range", 35.0);
        double damage = plugin.getConfig().getDouble("abilities.stormstrike.beam_damage_hearts", 3.0) * 2.0;
        int damageIntervalTicks = Math.max(1, plugin.getConfig().getInt("abilities.stormstrike.beam_damage_interval_ticks", 10));
        double hitRadius = plugin.getConfig().getDouble("abilities.stormstrike.beam_hit_radius", 0.55);

        Location eye = player.getEyeLocation();
        Vector dir = eye.getDirection().normalize();
        World world = player.getWorld();

        RayTraceResult blockHit = world.rayTraceBlocks(eye, dir, range, FluidCollisionMode.NEVER, true);
        double effectiveRange = range;
        if (blockHit != null && blockHit.getHitPosition() != null) {
            effectiveRange = Math.min(range, blockHit.getHitPosition().distance(eye.toVector()));
        }

        renderStormBeam(world, eye, dir, effectiveRange);

        int counter = beamDamageCounter.merge(player.getUniqueId(), 1, Integer::sum);
        if (counter % damageIntervalTicks != 0) return;

        Location center = eye.clone().add(dir.clone().multiply(effectiveRange * 0.5));
        for (Entity entity : world.getNearbyEntities(center,
                effectiveRange * 0.5 + hitRadius, effectiveRange * 0.5 + hitRadius, effectiveRange * 0.5 + hitRadius)) {
            if (!(entity instanceof LivingEntity target) || entity.equals(player)) continue;
            if (!isNearBeam(eye, dir, effectiveRange, hitRadius, target.getEyeLocation())) continue;
            target.damage(Math.max(0.0, damage), player);
            world.spawnParticle(Particle.ELECTRIC_SPARK, target.getLocation().add(0, 1, 0), 16, 0.22, 0.35, 0.22, 0.08);
            world.playSound(target.getLocation(), Sound.ENTITY_LIGHTNING_BOLT_IMPACT, 0.35f, 1.65f);
        }
    }

    private void renderStormBeam(World world, Location origin, Vector direction, double distance) {
        Particle.DustOptions white = new Particle.DustOptions(Color.fromRGB(255, 255, 245), 0.85f);
        Particle.DustOptions paleYellow = new Particle.DustOptions(Color.fromRGB(255, 245, 165), 0.75f);
        Vector side = direction.clone().crossProduct(new Vector(0, 1, 0));
        if (side.lengthSquared() < 0.001) side = new Vector(1, 0, 0);
        side.normalize();
        Vector up = side.clone().crossProduct(direction).normalize();

        double step = plugin.getConfig().getDouble("abilities.stormstrike.beam_particle_step", 0.32);
        double jitter = plugin.getConfig().getDouble("abilities.stormstrike.beam_zigzag_strength", 0.28);
        for (double d = 0.6; d <= distance; d += step) {
            double phase = d * 5.8 + (System.currentTimeMillis() % 700L) / 45.0;
            double sideOffset = Math.sin(phase) * jitter + ThreadLocalRandom.current().nextDouble(-0.06, 0.06);
            double upOffset = Math.cos(phase * 0.74) * jitter * 0.55 + ThreadLocalRandom.current().nextDouble(-0.04, 0.04);
            Location point = origin.clone()
                    .add(direction.clone().multiply(d))
                    .add(side.clone().multiply(sideOffset))
                    .add(up.clone().multiply(upOffset));

            world.spawnParticle(Particle.DUST, point, 2, 0.018, 0.018, 0.018, 0, white);
            if (((int) (d * 10)) % 5 == 0) {
                world.spawnParticle(Particle.DUST, point, 1, 0.025, 0.025, 0.025, 0, paleYellow);
                world.spawnParticle(Particle.ELECTRIC_SPARK, point, 1, 0.035, 0.035, 0.035, 0.02);
            }
            if (((int) (d * 10)) % 8 == 0) {
                world.spawnParticle(Particle.END_ROD, point, 1, 0.02, 0.02, 0.02, 0.01);
            }
        }
    }

    private boolean isNearBeam(Location origin, Vector dir, double range, double radius, Location target) {
        Vector rel = target.toVector().subtract(origin.toVector());
        double along = rel.dot(dir);
        if (along < 0 || along > range) return false;
        Vector closest = origin.toVector().add(dir.clone().multiply(along));
        return closest.distanceSquared(target.toVector()) <= radius * radius;
    }

    public boolean isLaunching(Player player) {
        return launching.contains(player.getUniqueId());
    }

    public void tryLaunch(Player player) {
        UUID uuid = player.getUniqueId();
        if (launching.contains(uuid) || player.isFlying()) return;

        long cooldownMs = plugin.getConfig().getLong("abilities.stormstrike.launch_cooldown_ms", 10000L);
        long now = System.currentTimeMillis();
        long last = launchCooldown.getOrDefault(uuid, 0L);
        if (now - last < cooldownMs) {
            long seconds = (cooldownMs - (now - last)) / 1000 + 1;
            MessageUtil.sendActionBar(player, plugin.getLocaleManager().msg(
                    "launch_cooldown", "seconds", Long.toString(seconds)));
            return;
        }

        launching.add(uuid);
        launchCooldown.put(uuid, now);

        Location location = player.getLocation();
        World world = player.getWorld();
        world.playSound(location, Sound.ENTITY_LIGHTNING_BOLT_THUNDER, 0.9f, 1.65f);
        world.playSound(location, Sound.ENTITY_FIREWORK_ROCKET_LAUNCH, 0.75f, 1.35f);
        world.spawnParticle(Particle.ELECTRIC_SPARK, location.clone().add(0, 0.4, 0), 68, 0.95, 0.35, 0.95, 0.24);
        world.spawnParticle(Particle.CLOUD, location.clone().add(0, 0.25, 0), 30, 0.9, 0.12, 0.9, 0.16);
        world.spawnParticle(Particle.END_ROD, location.clone().add(0, 0.7, 0), 34, 0.75, 0.55, 0.75, 0.05);
        for (int i = 0; i < 3; i++) {
            Location spark = location.clone().add(
                    ThreadLocalRandom.current().nextDouble(-1.2, 1.2),
                    0.0,
                    ThreadLocalRandom.current().nextDouble(-1.2, 1.2));
            world.strikeLightningEffect(spark);
        }

        player.setFlying(false);
        player.setAllowFlight(false);

        double velocity = plugin.getConfig().getDouble("abilities.stormstrike.launch_velocity", 2.75);
        Vector look = player.getLocation().getDirection();
        player.setVelocity(new Vector(look.getX() * 0.20, velocity, look.getZ() * 0.20));

        int peakTicks = plugin.getConfig().getInt("abilities.stormstrike.launch_peak_ticks", 22);
        SchedulerAdapter.runLater(plugin, () -> {
            launching.remove(uuid);
            if (player.isOnline()) {
                player.setAllowFlight(true);
                player.setFlying(true);
                player.setFlySpeed((float) flySpeed());
            }
        }, peakTicks);
    }

    public void strikeLightning(Player player) {
        UUID uuid = player.getUniqueId();
        long handledNow = System.currentTimeMillis();
        long lastHandled = lastHandledAt.getOrDefault(uuid, 0L);
        if (handledNow - lastHandled < 250L) return;
        lastHandledAt.put(uuid, handledNow);

        long cooldownMs = plugin.getConfig().getLong("abilities.stormstrike.lightning_cooldown_ms", 3000L);
        long now = System.currentTimeMillis();
        long readyAt = lightningCooldown.getOrDefault(uuid, 0L);
        if (readyAt > now) {
            long seconds = Math.max(1L, (long) Math.ceil((readyAt - now) / 1000.0));
            MessageUtil.sendActionBar(player, plugin.getLocaleManager().msg("stormstrike.cooldown",
                    "seconds", Long.toString(seconds)));
            player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 0.45f, 0.8f);
            return;
        }

        Location target = findStrikeTarget(player);
        if (target == null) {
            player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 0.45f, 0.8f);
            return;
        }

        lightningCooldown.put(uuid, now + Math.max(0L, cooldownMs));
        int minBolts = plugin.getConfig().getInt("abilities.stormstrike.lightning_min_bolts", 2);
        int maxBolts = plugin.getConfig().getInt("abilities.stormstrike.lightning_max_bolts", 3);
        if (maxBolts < minBolts) maxBolts = minBolts;
        int bolts = minBolts + ThreadLocalRandom.current().nextInt(Math.max(1, maxBolts - minBolts + 1));
        double spread = plugin.getConfig().getDouble("abilities.stormstrike.lightning_spread", 1.4);

        player.getWorld().playSound(player.getLocation(), Sound.ENTITY_LIGHTNING_BOLT_THUNDER, 0.7f, 1.55f);
        for (int i = 0; i < bolts; i++) {
            Location bolt = target.clone().add(
                    ThreadLocalRandom.current().nextDouble(-spread, spread),
                    0,
                    ThreadLocalRandom.current().nextDouble(-spread, spread));
            player.getWorld().strikeLightning(bolt);
            player.getWorld().spawnParticle(Particle.ELECTRIC_SPARK, bolt.clone().add(0, 1, 0), 28, 0.35, 0.55, 0.35, 0.12);
        }
        MessageUtil.sendActionBar(player, plugin.getLocaleManager().msg("stormstrike.released"));
    }

    private Location findStrikeTarget(Player player) {
        double range = plugin.getConfig().getDouble("abilities.stormstrike.lightning_range", 36.0);
        Location eye = player.getEyeLocation();
        Vector direction = eye.getDirection().normalize();
        RayTraceResult result = player.getWorld().rayTrace(eye, direction, range,
                FluidCollisionMode.NEVER, true, 0.35,
                entity -> entity != player && entity instanceof LivingEntity);
        if (result != null) {
            if (result.getHitEntity() != null) return result.getHitEntity().getLocation();
            if (result.getHitBlock() != null) return result.getHitPosition().toLocation(player.getWorld());
            if (result.getHitPosition() != null) return result.getHitPosition().toLocation(player.getWorld());
        }
        return eye.clone().add(direction.multiply(range));
    }

    private double flySpeed() {
        return plugin.getConfig().getDouble("abilities.stormstrike.fly_speed", 0.275);
    }
}
