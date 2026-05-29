package de.thomasugh.compoundv.ability.vone;

import de.thomasugh.compoundv.CompoundV;
import de.thomasugh.compoundv.ability.Ability;
import de.thomasugh.compoundv.server.SchedulerAdapter;
import de.thomasugh.compoundv.server.TaskHandle;
import de.thomasugh.compoundv.util.AttributeUtil;
import de.thomasugh.compoundv.util.MessageUtil;
import de.thomasugh.compoundv.util.AbilityKillTracker;
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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

public class StormstrikeAbility implements Ability {

    private final CompoundV plugin;
    private final Set<UUID> launching = new HashSet<>();
    private final Map<UUID, Long> launchCooldown = new HashMap<>();
    private final Map<UUID, Long> lightningCooldown = new HashMap<>();
    private final Map<UUID, UUID> activeLightningTokens = new HashMap<>();
    private final Map<UUID, TaskHandle> lightningStrikeTasks = new HashMap<>();
    private final Map<UUID, Long> fallImpactCooldown = new HashMap<>();
    private final Map<UUID, Long> lastHandledAt = new HashMap<>();
    private final Map<UUID, Boolean> beamActive = new HashMap<>();
    private final Map<UUID, Integer> beamTicks = new HashMap<>();
    private final Map<UUID, Integer> beamDamageCounter = new HashMap<>();
    private final Map<UUID, Long> beamCooldownUntil = new HashMap<>();
    private final Map<UUID, Integer> meleeCounter = new HashMap<>();
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
        activeLightningTokens.remove(uuid);
        TaskHandle lightningTask = lightningStrikeTasks.remove(uuid);
        if (lightningTask != null) lightningTask.cancel();
        fallImpactCooldown.remove(uuid);
        lastHandledAt.remove(uuid);
        beamActive.remove(uuid);
        beamTicks.remove(uuid);
        beamDamageCounter.remove(uuid);
        beamCooldownUntil.remove(uuid);
        meleeCounter.remove(uuid);
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
        player.getWorld().playSound(player.getLocation(), Sound.ENTITY_LIGHTNING_BOLT_THUNDER, 0.45f, 1.85f);
        player.getWorld().playSound(player.getLocation(), Sound.BLOCK_BEACON_POWER_SELECT, 0.45f, 1.7f);
        MessageUtil.sendActionBar(player, plugin.getLocaleManager().msg("stormstrike.beam_on"));
    }

    @Override
    public void onTick(Player player) {
        renderFlightParticles(player);

        if (!beamActive.getOrDefault(player.getUniqueId(), false)) return;

        int ticks = beamTicks.merge(player.getUniqueId(), 1, Integer::sum);
        int maxTicks = plugin.getConfig().getInt("abilities.stormstrike.beam_max_ticks", 120);
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
        if (overheated) {
            long cooldownMs = plugin.getConfig().getLong("abilities.stormstrike.beam_cooldown_ms", 5000L);
            beamCooldownUntil.put(uuid, System.currentTimeMillis() + Math.max(0L, cooldownMs));
        }
        MessageUtil.sendActionBar(player, plugin.getLocaleManager().msg(overheated ? "stormstrike.beam_overheated" : "stormstrike.beam_off"));
        player.playSound(player.getLocation(), Sound.BLOCK_FIRE_EXTINGUISH, 0.35f, 1.85f);
    }

    private void fireStormBeam(Player player) {
        double range = plugin.getConfig().getDouble("abilities.stormstrike.beam_range", 35.0);
        double damage = plugin.getConfig().getDouble("abilities.stormstrike.beam_damage_hearts", 0.95625) * 2.0;
        int damageIntervalTicks = Math.max(1, plugin.getConfig().getInt("abilities.stormstrike.beam_damage_interval_ticks", 2));
        double hitRadius = plugin.getConfig().getDouble("abilities.stormstrike.beam_hit_radius", 6.5);
        int slownessTicks = plugin.getConfig().getInt("abilities.stormstrike.beam_slowness_ticks", 60);
        int slownessAmplifier = plugin.getConfig().getInt("abilities.stormstrike.beam_slowness_amplifier", 1);

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
            if (!isNearBeamTarget(eye, dir, effectiveRange, hitRadius, target)) continue;
            target.setNoDamageTicks(0);
            AbilityKillTracker.damage(plugin, target, player, Math.max(0.0, damage), "death_messages.stormstrike", false);
            target.addPotionEffect(new PotionEffect(PotionEffects.SLOWNESS,
                    Math.max(20, slownessTicks), Math.max(0, slownessAmplifier), false, true, true));
            world.spawnParticle(Particle.ELECTRIC_SPARK, target.getLocation().add(0, 1, 0), 24, 0.28, 0.40, 0.28, 0.10);
            world.playSound(target.getLocation(), Sound.ENTITY_LIGHTNING_BOLT_IMPACT, 0.35f, 1.75f);
        }
    }

    private void renderStormBeam(World world, Location origin, Vector direction, double distance) {
        Particle.DustOptions whiteCore = new Particle.DustOptions(Color.fromRGB(252, 252, 255), 0.42f);
        Particle.DustOptions purpleGlow = new Particle.DustOptions(Color.fromRGB(184, 155, 255), 0.34f);
        Vector side = direction.clone().crossProduct(new Vector(0, 1, 0));
        if (side.lengthSquared() < 0.001) side = new Vector(1, 0, 0);
        side.normalize();
        Vector up = side.clone().crossProduct(direction).normalize();

        double segment = plugin.getConfig().getDouble("abilities.stormstrike.beam_segment_length", 0.75);
        double jitter = plugin.getConfig().getDouble("abilities.stormstrike.beam_zigzag_strength", 0.46);
        ThreadLocalRandom random = ThreadLocalRandom.current();

        Location previous = origin.clone().add(direction.clone().multiply(0.45));
        Vector previousOffset = new Vector(0, 0, 0);
        for (double d = 0.85; d <= distance; d += segment) {
            double fade = Math.min(1.0, d / Math.max(1.0, distance));
            Vector offset = side.clone().multiply(random.nextDouble(-jitter, jitter) * (0.65 + fade))
                    .add(up.clone().multiply(random.nextDouble(-jitter, jitter) * 0.75));
            Vector smoothed = previousOffset.multiply(0.25).add(offset.multiply(0.75));
            Location current = origin.clone().add(direction.clone().multiply(d)).add(smoothed);
            drawLightningSegment(world, previous, current, whiteCore, purpleGlow);

            if (random.nextDouble() < 0.22) {
                double branchLength = random.nextDouble(0.55, 1.45);
                Vector branchDir = side.clone().multiply(random.nextBoolean() ? 1 : -1)
                        .add(up.clone().multiply(random.nextDouble(-0.45, 0.95)))
                        .add(direction.clone().multiply(random.nextDouble(0.05, 0.35)))
                        .normalize();
                Location branchEnd = current.clone().add(branchDir.multiply(branchLength));
                drawLightningSegment(world, current, branchEnd, whiteCore, purpleGlow);
            }

            previous = current;
            previousOffset = smoothed;
        }
    }

    private void drawLightningSegment(World world, Location from, Location to,
                                      Particle.DustOptions core, Particle.DustOptions glow) {
        Vector delta = to.toVector().subtract(from.toVector());
        double length = delta.length();
        if (length <= 0.001) return;
        Vector step = delta.normalize().multiply(0.16);
        int steps = Math.max(1, (int) Math.ceil(length / 0.16));
        Location point = from.clone();
        for (int i = 0; i <= steps; i++) {
            world.spawnParticle(Particle.DUST, point, 1, 0.012, 0.012, 0.012, 0, core);
            if (i % 2 == 0) world.spawnParticle(Particle.DUST, point, 1, 0.012, 0.012, 0.012, 0, glow);
            if (i % 4 == 0) world.spawnParticle(Particle.ELECTRIC_SPARK, point, 1, 0.010, 0.010, 0.010, 0.008);
            if (i % 7 == 0) world.spawnParticle(Particle.END_ROD, point, 1, 0.006, 0.006, 0.006, 0.002);
            point.add(step);
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
        world.playSound(location, Sound.ENTITY_LIGHTNING_BOLT_THUNDER, 0.55f, 1.85f);
        world.playSound(location, Sound.ENTITY_FIREWORK_ROCKET_LAUNCH, 0.65f, 1.45f);
        world.spawnParticle(Particle.ELECTRIC_SPARK, location.clone().add(0, 0.45, 0), 82, 0.95, 0.35, 0.95, 0.28);
        world.spawnParticle(Particle.CLOUD, location.clone().add(0, 0.18, 0), 18, 0.85, 0.08, 0.85, 0.10);
        world.spawnParticle(Particle.END_ROD, location.clone().add(0, 0.7, 0), 38, 0.75, 0.55, 0.75, 0.05);
        renderLaunchElectricArcs(world, location.clone().add(0, 0.55, 0));

        player.setFlying(false);
        player.setAllowFlight(false);

        double velocity = plugin.getConfig().getDouble("abilities.stormstrike.launch_velocity", 2.75);
        Vector look = player.getLocation().getDirection();
        player.setVelocity(new Vector(look.getX() * 0.20, velocity, look.getZ() * 0.20));

        int peakTicks = plugin.getConfig().getInt("abilities.stormstrike.launch_peak_ticks", 22);
        SchedulerAdapter.runLater(plugin, player, () -> {
            launching.remove(uuid);
            if (player.isOnline()) {
                player.setAllowFlight(true);
                player.setFlying(true);
                player.setFlySpeed((float) flySpeed());
            }
        }, peakTicks);
    }

    private void renderFlightParticles(Player player) {
        if (!player.isFlying()) return;
        if (player.getTicksLived() % 3 != 0) return;

        Location trail = player.getLocation().add(0, 0.55, 0);
        World world = player.getWorld();
        world.spawnParticle(Particle.ELECTRIC_SPARK, trail, 5, 0.32, 0.28, 0.32, 0.035);
        if (player.getTicksLived() % 9 == 0) {
            world.spawnParticle(Particle.END_ROD, trail, 2, 0.22, 0.18, 0.22, 0.012);
        }
    }

    private void renderLaunchElectricArcs(World world, Location center) {
        Particle.DustOptions white = new Particle.DustOptions(Color.fromRGB(255, 255, 255), 0.82f);
        Particle.DustOptions yellow = new Particle.DustOptions(Color.fromRGB(255, 246, 170), 0.65f);
        ThreadLocalRandom random = ThreadLocalRandom.current();
        for (int i = 0; i < 8; i++) {
            double angle = (Math.PI * 2.0 / 8.0) * i + random.nextDouble(-0.18, 0.18);
            Location from = center.clone().add(Math.cos(angle) * 0.35, random.nextDouble(0.0, 0.75), Math.sin(angle) * 0.35);
            Location to = center.clone().add(Math.cos(angle) * random.nextDouble(1.0, 1.75), random.nextDouble(0.15, 1.45), Math.sin(angle) * random.nextDouble(1.0, 1.75));
            drawLightningSegment(world, from, to, white, yellow);
        }
    }

    public void strikeLightning(Player player) {
        UUID uuid = player.getUniqueId();
        long handledNow = System.currentTimeMillis();
        long lastHandled = lastHandledAt.getOrDefault(uuid, 0L);
        if (handledNow - lastHandled < 250L) return;
        lastHandledAt.put(uuid, handledNow);

        if (activeLightningTokens.containsKey(uuid)) return;

        long cooldownMs = plugin.getConfig().getLong("abilities.stormstrike.lightning_cooldown_ms", 7000L);
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
        int maxBolts = plugin.getConfig().getInt("abilities.stormstrike.lightning_max_bolts", 2);
        if (maxBolts < minBolts) maxBolts = minBolts;
        int bolts = minBolts + ThreadLocalRandom.current().nextInt(Math.max(1, maxBolts - minBolts + 1));
        double spread = plugin.getConfig().getDouble("abilities.stormstrike.lightning_spread", 1.8);

        List<Location> strikeCenters = new ArrayList<>();
        for (int i = 0; i < bolts; i++) {
            strikeCenters.add(target.clone().add(
                    ThreadLocalRandom.current().nextDouble(-spread, spread),
                    0.15,
                    ThreadLocalRandom.current().nextDouble(-spread, spread)));
        }

        UUID token = UUID.randomUUID();
        activeLightningTokens.put(uuid, token);
        player.getWorld().playSound(player.getLocation(), Sound.ENTITY_LIGHTNING_BOLT_THUNDER, 0.55f, 1.65f);
        player.getWorld().playSound(target, Sound.BLOCK_BEACON_POWER_SELECT, 0.55f, 1.85f);
        MessageUtil.sendActionBar(player, plugin.getLocaleManager().msg("stormstrike.released"));

        int durationTicks = Math.max(2, plugin.getConfig().getInt("abilities.stormstrike.lightning_duration_ticks", 30));
        int damageInterval = Math.max(1, plugin.getConfig().getInt("abilities.stormstrike.lightning_damage_interval_ticks", 2));
        final TaskHandle[] task = new TaskHandle[1];
        task[0] = SchedulerAdapter.runTimer(plugin, player, new Runnable() {
            int age = 0;

            @Override public void run() {
                if (!player.isOnline() || !token.equals(activeLightningTokens.get(uuid))) {
                    if (task[0] != null) task[0].cancel();
                    return;
                }

                Set<UUID> damagedThisTick = new HashSet<>();
                for (Location center : strikeCenters) {
                    if (age == 0 && center.getWorld() != null) center.getWorld().strikeLightningEffect(center);
                    renderLightningStrikeEffect(center, age);
                    if (age % damageInterval == 0) {
                        applyStrikeHitRange(player, center, damagedThisTick);
                    }
                }

                age++;
                if (age >= durationTicks) {
                    activeLightningTokens.remove(uuid);
                    lightningStrikeTasks.remove(uuid);
                    if (task[0] != null) task[0].cancel();
                }
            }
        }, 0L, 1L);
        lightningStrikeTasks.put(uuid, task[0]);
    }

    private void renderLightningStrikeEffect(Location base, int age) {
        World world = base.getWorld();
        if (world == null) return;

        Particle.DustOptions white = new Particle.DustOptions(Color.fromRGB(250, 250, 255), 0.72f);
        Particle.DustOptions purple = new Particle.DustOptions(Color.fromRGB(176, 128, 255), 0.62f);
        ThreadLocalRandom random = ThreadLocalRandom.current();

        Location from = base.clone().add(random.nextDouble(-0.22, 0.22), 2.8 + random.nextDouble(0.0, 0.8), random.nextDouble(-0.22, 0.22));
        Location previous = from;
        int joints = 5 + random.nextInt(3);
        for (int i = 1; i <= joints; i++) {
            double progress = i / (double) joints;
            Location current = base.clone().add(
                    random.nextDouble(-0.65, 0.65) * (1.0 - progress * 0.45),
                    2.8 * (1.0 - progress) + 0.22,
                    random.nextDouble(-0.65, 0.65) * (1.0 - progress * 0.45));
            drawLightningSegment(world, previous, current, white, purple);
            if (random.nextDouble() < 0.42) {
                Location branch = current.clone().add(random.nextDouble(-1.2, 1.2), random.nextDouble(-0.25, 0.65), random.nextDouble(-1.2, 1.2));
                drawLightningSegment(world, current, branch, white, purple);
            }
            previous = current;
        }

        if (age % 3 == 0) {
            world.spawnParticle(Particle.ELECTRIC_SPARK, base.clone().add(0, 0.75, 0), 32, 0.85, 0.55, 0.85, 0.20);
            world.spawnParticle(Particle.END_ROD, base.clone().add(0, 0.95, 0), 12, 0.70, 0.40, 0.70, 0.035);
            world.playSound(base, Sound.ENTITY_LIGHTNING_BOLT_IMPACT, 0.12f, 1.9f);
        }
    }

    public void handleMeleeHit(Player attacker, LivingEntity target) {
        // Stormstrike melee no longer spawns lightning or spark hits.
        // Lightning is intentionally limited to Sneak + Left-Click and the F beam.
    }

    public void triggerFallImpact(Player player, double fallenBlocks) {
        double minFall = plugin.getConfig().getDouble("abilities.stormstrike.fall_impact_min_blocks", 10.0);
        if (fallenBlocks < minFall) return;

        UUID uuid = player.getUniqueId();
        long now = System.currentTimeMillis();
        long readyAt = fallImpactCooldown.getOrDefault(uuid, 0L);
        if (readyAt > now) {
            long seconds = Math.max(1L, (long) Math.ceil((readyAt - now) / 1000.0));
            MessageUtil.sendActionBar(player, plugin.getLocaleManager().msg("stormstrike.fall_cooldown", "seconds", Long.toString(seconds)));
            return;
        }
        fallImpactCooldown.put(uuid, now + plugin.getConfig().getLong("abilities.stormstrike.fall_impact_cooldown_ms", 100000L));

        Location center = player.getLocation();
        World world = player.getWorld();
        double radius = plugin.getConfig().getDouble("abilities.stormstrike.fall_impact_radius", 10.0);
        int bolts = plugin.getConfig().getInt("abilities.stormstrike.fall_impact_bolts", 6);
        float power = (float) plugin.getConfig().getDouble("abilities.stormstrike.fall_impact_power", 1.1);
        boolean blockDamage = plugin.getConfig().getBoolean("abilities.stormstrike.fall_impact_block_damage", true);
        double damage = plugin.getConfig().getDouble("abilities.stormstrike.fall_impact_damage_hearts", 3.0) * 2.0;
        double knockback = plugin.getConfig().getDouble("abilities.stormstrike.fall_impact_knockback", 1.65);

        if (power > 0) world.createExplosion(center, power, false, blockDamage, player);
        world.spawnParticle(Particle.EXPLOSION, center.clone().add(0, 0.18, 0), 1, 0.12, 0.04, 0.12, 0);
        world.spawnParticle(Particle.ELECTRIC_SPARK, center.clone().add(0, 0.7, 0), 120, radius * 0.35, 0.7, radius * 0.35, 0.18);
        world.playSound(center, Sound.ENTITY_LIGHTNING_BOLT_THUNDER, 0.85f, 1.35f);

        ThreadLocalRandom random = ThreadLocalRandom.current();
        for (int i = 0; i < bolts; i++) {
            double angle = Math.PI * 2.0 * i / Math.max(1, bolts) + random.nextDouble(-0.22, 0.22);
            double dist = random.nextDouble(radius * 0.35, radius);
            Location bolt = center.clone().add(Math.cos(angle) * dist, 0, Math.sin(angle) * dist);
            world.strikeLightning(bolt);
        }

        for (Entity entity : world.getNearbyEntities(center, radius, radius, radius)) {
            if (!(entity instanceof LivingEntity target) || target.equals(player)) continue;
            double distance = Math.max(0.35, target.getLocation().distance(center));
            if (distance > radius) continue;
            double factor = Math.max(0.2, 1.0 - (distance / radius));
            AbilityKillTracker.damage(plugin, target, player, damage * factor, "death_messages.stormstrike", false);
            target.addPotionEffect(new PotionEffect(PotionEffects.SLOWNESS, 80, 1, false, true, true));
            Vector push = target.getLocation().toVector().subtract(center.toVector());
            if (push.lengthSquared() < 0.0001) push = player.getLocation().getDirection().clone();
            target.setVelocity(target.getVelocity().add(push.normalize().multiply(knockback * factor).setY(0.35 + factor * 0.35)));
        }

        MessageUtil.sendActionBar(player, plugin.getLocaleManager().msg("stormstrike.fall_impact"));
    }

    private void applyStrikeHitRange(Player player, Location bolt, Set<UUID> damagedThisTick) {
        double radius = plugin.getConfig().getDouble("abilities.stormstrike.lightning_hit_radius", 7.0);
        double damage = plugin.getConfig().getDouble("abilities.stormstrike.lightning_tick_damage_hearts", 0.54) * 2.0;
        int slownessTicks = plugin.getConfig().getInt("abilities.stormstrike.lightning_slowness_ticks", 80);
        int slownessAmplifier = plugin.getConfig().getInt("abilities.stormstrike.lightning_slowness_amplifier", 1);

        for (Entity entity : bolt.getWorld().getNearbyEntities(bolt, radius, radius, radius)) {
            if (!(entity instanceof LivingEntity target) || target.equals(player)) continue;
            if (target instanceof Player targetPlayer
                    && plugin.getAbilityManager().getAbility(targetPlayer) instanceof StormstrikeAbility) continue;
            if (target.getLocation().distanceSquared(bolt) > radius * radius) continue;
            if (!damagedThisTick.add(target.getUniqueId())) continue;
            target.setNoDamageTicks(0);
            target.setNoDamageTicks(0);
            AbilityKillTracker.damage(plugin, target, player, Math.max(0.0, damage), "death_messages.stormstrike", false);
            target.addPotionEffect(new PotionEffect(PotionEffects.SLOWNESS,
                    Math.max(20, slownessTicks), Math.max(0, slownessAmplifier), false, true, true));
            target.getWorld().spawnParticle(Particle.ELECTRIC_SPARK,
                    target.getLocation().add(0, Math.min(1.4, Math.max(0.7, target.getEyeHeight())), 0),
                    32, 0.42, 0.48, 0.42, 0.16);
            target.getWorld().spawnParticle(Particle.END_ROD,
                    target.getLocation().add(0, Math.min(1.4, Math.max(0.7, target.getEyeHeight())), 0),
                    8, 0.30, 0.34, 0.30, 0.04);
        }
    }

    private Location findStrikeTarget(Player player) {
        double range = plugin.getConfig().getDouble("abilities.stormstrike.lightning_range", 55.0);
        double traceRadius = plugin.getConfig().getDouble("abilities.stormstrike.lightning_trace_radius", 1.1);
        Location eye = player.getEyeLocation();
        Vector direction = eye.getDirection().normalize();
        RayTraceResult result = player.getWorld().rayTrace(eye, direction, range,
                FluidCollisionMode.NEVER, true, Math.max(0.2, traceRadius),
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
