package de.thomasugh.compoundv.ability.vone;

import de.thomasugh.compoundv.CompoundV;
import de.thomasugh.compoundv.ability.Ability;
import de.thomasugh.compoundv.server.SchedulerAdapter;
import de.thomasugh.compoundv.util.AttributeUtil;
import de.thomasugh.compoundv.util.MessageUtil;
import de.thomasugh.compoundv.util.PotionEffects;
import org.bukkit.Color;
import org.bukkit.ChatColor;
import org.bukkit.GameMode;
import org.bukkit.FluidCollisionMode;
import org.bukkit.NamespacedKey;
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
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public class SonicBoomAbility implements Ability {

    private final CompoundV plugin;
    private final Set<UUID> launching = new HashSet<>();
    private final Map<UUID, Long> launchCooldown = new HashMap<>();
    private final Map<UUID, Long> fallImpactCooldown = new HashMap<>();
    private final Map<UUID, Long> sonicBeamCooldown = new HashMap<>();
    private final Map<UUID, Long> sonicRingCooldown = new HashMap<>();
    private final Map<UUID, Long> fallExplosionReductionUntil = new HashMap<>();
    private final Map<UUID, Integer> meleeHitCounter = new HashMap<>();
    private final NamespacedKey healthKey;

    public SonicBoomAbility(CompoundV plugin) {
        this.plugin = plugin;
        this.healthKey = new NamespacedKey(plugin, "sonic_boom_hearts");
    }

    @Override public String getId() { return "sonic_boom"; }
    @Override public String getDisplayName() { return "Sonic Boom"; }
    @Override public int getColor() { return 0x5CE1FF; }

    @Override
    public void apply(Player player) {
        player.setAllowFlight(true);
        int strength = plugin.getConfig().getInt("abilities.sonic_boom.strength_level", 4);
        int resistance = plugin.getConfig().getInt("abilities.sonic_boom.resistance_level", 4);

        player.addPotionEffect(new PotionEffect(PotionEffects.STRENGTH,
                Integer.MAX_VALUE, Math.max(0, strength - 1), false, false, true));
        player.addPotionEffect(new PotionEffect(PotionEffects.RESISTANCE,
                Integer.MAX_VALUE, Math.max(0, resistance - 1), false, false, true));
        double extraHearts = plugin.getConfig().getDouble("abilities.sonic_boom.extra_hearts", 10.0);
        AttributeUtil.setMaxHealthBonus(player, healthKey, extraHearts * 2.0);
    }

    @Override
    public void remove(Player player) {
        launching.remove(player.getUniqueId());
        launchCooldown.remove(player.getUniqueId());
        fallImpactCooldown.remove(player.getUniqueId());
        sonicBeamCooldown.remove(player.getUniqueId());
        sonicRingCooldown.remove(player.getUniqueId());
        fallExplosionReductionUntil.remove(player.getUniqueId());
        meleeHitCounter.remove(player.getUniqueId());
        AttributeUtil.setMaxHealthBonus(player, healthKey, 0);
        player.setFlySpeed(0.1f);
        if (player.getGameMode() != GameMode.CREATIVE && player.getGameMode() != GameMode.SPECTATOR) {
            player.setFlying(false);
            player.setAllowFlight(false);
        }
        player.removePotionEffect(PotionEffects.STRENGTH);
        player.removePotionEffect(PotionEffects.RESISTANCE);
    }

    public boolean isLaunching(Player player) {
        return launching.contains(player.getUniqueId());
    }

    public void tryLaunch(Player player) {
        UUID uuid = player.getUniqueId();
        if (launching.contains(uuid) || player.isFlying()) return;

        long cooldownMs = plugin.getConfig().getLong("abilities.sonic_boom.launch_cooldown_ms",
                plugin.getConfig().getLong("abilities.the_patriot.shared.launch_cooldown_ms", 10000L));
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
        world.playSound(location, Sound.ENTITY_GENERIC_EXPLODE, 3.0f, 0.48f);
        world.playSound(location, Sound.ENTITY_TNT_PRIMED, 1.35f, 0.8f);
        world.playSound(location, Sound.ITEM_FIRECHARGE_USE, 2.25f, 0.6f);
        world.spawnParticle(Particle.CLOUD, location.clone().add(0, 0.2, 0), 75, 1.35, 0.18, 1.35, 0.28);
        world.spawnParticle(Particle.POOF, location.clone().add(0, 0.4, 0), 42, 1.1, 0.18, 1.1, 0);
        world.spawnParticle(Particle.EXPLOSION, location.clone().add(0, 0.5, 0), 8, 0.9, 0.08, 0.9, 0);
        world.spawnParticle(Particle.LARGE_SMOKE, location.clone().add(0, 0.3, 0), 38, 1.1, 0.12, 1.1, 0.06);
        world.spawnParticle(Particle.DUST, location.clone().add(0, 0.8, 0), 40, 1.2, 0.35, 1.2, 0,
                new Particle.DustOptions(Color.fromRGB(80, 220, 255), 1.05f));

        if (plugin.getConfig().getBoolean("abilities.sonic_boom.launch_block_damage", true)) {
            float power = (float) plugin.getConfig().getDouble("abilities.sonic_boom.launch_block_damage_power", 1.15);
            world.createExplosion(location, power, false, true, player);
        }
        damageLaunchEntities(player, location);

        player.setFlying(false);
        player.setAllowFlight(false);

        double velocity = plugin.getConfig().getDouble("abilities.sonic_boom.launch_velocity", 3.05);
        Vector look = player.getLocation().getDirection();
        player.setVelocity(new Vector(look.getX() * 0.25, velocity, look.getZ() * 0.25));

        int peakTicks = plugin.getConfig().getInt("abilities.sonic_boom.launch_peak_ticks", 24);
        double flySpeed = plugin.getConfig().getDouble("abilities.sonic_boom.launch_fly_speed", 0.30);
        SchedulerAdapter.runLater(plugin, () -> {
            launching.remove(uuid);
            if (player.isOnline()) {
                player.setAllowFlight(true);
                player.setFlying(true);
                player.setFlySpeed((float) flySpeed);
            }
        }, peakTicks);
    }


    private void damageLaunchEntities(Player player, Location center) {
        double radius = plugin.getConfig().getDouble("abilities.sonic_boom.launch_entity_radius", 3.5);
        double maxDamage = plugin.getConfig().getDouble("abilities.sonic_boom.launch_entity_damage_hearts", 4.0) * 2.0;
        double knockback = plugin.getConfig().getDouble("abilities.sonic_boom.launch_entity_knockback", 1.4);
        for (Entity entity : center.getWorld().getNearbyEntities(center, radius, radius, radius)) {
            if (!(entity instanceof LivingEntity target) || entity.equals(player)) continue;
            double distance = Math.max(0.25, target.getLocation().distance(center));
            if (distance > radius) continue;
            double factor = Math.max(0.35, 1.0 - (distance / radius));
            target.damage(maxDamage * factor, player);
            Vector direction = target.getLocation().toVector().subtract(center.toVector());
            if (direction.lengthSquared() < 0.0001) direction = player.getLocation().getDirection().clone();
            target.setVelocity(target.getVelocity().add(direction.normalize().multiply(knockback * factor).setY(0.45 + factor * 0.25)));
        }
    }

    public void triggerFallImpact(Player player, double fallenBlocks) {
        Location location = player.getLocation();
        World world = player.getWorld();
        double blockHeight = plugin.getConfig().getDouble("abilities.sonic_boom.fall_impact_block_height", 35.0);

        if (fallenBlocks < blockHeight) {
            triggerSoftFallImpact(player, location, world);
            return;
        }

        if (isFallImpactCoolingDown(player)) return;
        startFallImpactCooldown(player);

        float power = (float) plugin.getConfig().getDouble("abilities.sonic_boom.fall_impact_power", 10.0);
        boolean blockDamage = plugin.getConfig().getBoolean("abilities.sonic_boom.fall_impact_block_damage", true);
        fallExplosionReductionUntil.put(player.getUniqueId(), System.currentTimeMillis() + 1200L);
        world.createExplosion(location, power, false, blockDamage, player);
        damageNearbyEntities(player, location);

        world.playSound(location, Sound.ENTITY_GENERIC_EXPLODE, 3.0f, 0.35f);
        world.playSound(location, Sound.ENTITY_TNT_PRIMED, 1.45f, 0.65f);
        world.playSound(location, Sound.BLOCK_STONE_BREAK, 1.8f, 0.35f);
        world.spawnParticle(Particle.CLOUD, location.clone().add(0, .3, 0), 80, 2.1, .18, 2.1, .25);
        world.spawnParticle(Particle.EXPLOSION, location.clone().add(0, .5, 0), 13, 1.8, 0, 1.8, 0);
        world.spawnParticle(Particle.LARGE_SMOKE, location.clone().add(0, .5, 0), 55, 1.9, .12, 1.9, .07);
        world.spawnParticle(Particle.DUST, location.clone().add(0, .6, 0), 45, 1.7, .35, 1.7, 0,
                new Particle.DustOptions(Color.fromRGB(80, 220, 255), 1.15f));
        MessageUtil.sendActionBar(player, plugin.getLocaleManager().msg("fall_impact"));
    }


    public boolean shouldReduceFallExplosionPlayerDamage(Player player) {
        long until = fallExplosionReductionUntil.getOrDefault(player.getUniqueId(), 0L);
        return until >= System.currentTimeMillis();
    }

    private void damageNearbyEntities(Player player, Location center) {
        double radius = plugin.getConfig().getDouble("abilities.sonic_boom.fall_impact_entity_radius", 9.0);
        double maxDamage = plugin.getConfig().getDouble("abilities.sonic_boom.fall_impact_entity_damage_hearts", 16.0) * 2.0;
        double playerDamageMultiplier = Math.max(0.0, plugin.getConfig().getDouble("abilities.sonic_boom.fall_impact_player_damage_multiplier", 0.8));
        double knockback = plugin.getConfig().getDouble("abilities.sonic_boom.fall_impact_knockback", 2.4);
        for (Entity entity : center.getWorld().getNearbyEntities(center, radius, radius, radius)) {
            if (!(entity instanceof LivingEntity target) || entity.equals(player)) continue;
            double distance = Math.max(0.1, target.getLocation().distance(center));
            double factor = Math.max(0.15, 1.0 - (distance / radius));
            double targetDamage = maxDamage * factor;
            if (target instanceof Player) {
                targetDamage *= playerDamageMultiplier;
            }
            target.damage(targetDamage, player);
            Vector direction = target.getLocation().toVector().subtract(center.toVector());
            if (direction.lengthSquared() < 0.0001) direction = new Vector(0, 1, 0);
            target.setVelocity(target.getVelocity().add(direction.normalize().multiply(knockback * factor).setY(0.45 + factor * 0.35)));
        }
    }

    private boolean isFallImpactCoolingDown(Player player) {
        long now = System.currentTimeMillis();
        long readyAt = fallImpactCooldown.getOrDefault(player.getUniqueId(), 0L);
        if (readyAt <= now) return false;
        long seconds = Math.max(1L, (long) Math.ceil((readyAt - now) / 1000.0));
        MessageUtil.sendActionBar(player, plugin.getLocaleManager().msg("fall_impact_cooldown",
                "seconds", Long.toString(seconds)));
        return true;
    }

    private void startFallImpactCooldown(Player player) {
        long cooldownMs = plugin.getConfig().getLong("abilities.sonic_boom.fall_impact_cooldown_ms", 60000L);
        fallImpactCooldown.put(player.getUniqueId(), System.currentTimeMillis() + Math.max(0L, cooldownMs));
    }

    private void triggerSoftFallImpact(Player player, Location location, World world) {
        world.playSound(location, Sound.ENTITY_GENERIC_EXPLODE, 0.8f, 0.85f);
        world.playSound(location, Sound.BLOCK_STONE_HIT, 0.9f, 0.65f);
        world.spawnParticle(Particle.CLOUD, location.clone().add(0, .18, 0), 22, .85, .08, .85, .09);
        world.spawnParticle(Particle.POOF, location.clone().add(0, .28, 0), 14, .65, .05, .65, 0.02);
        world.spawnParticle(Particle.LARGE_SMOKE, location.clone().add(0, .25, 0), 10, .6, .05, .6, .02);
        MessageUtil.sendActionBar(player, plugin.getLocaleManager().msg("fall_impact"));
    }

    public void fireSonicBeam(Player player) {
        UUID uuid = player.getUniqueId();
        long now = System.currentTimeMillis();
        long readyAt = sonicBeamCooldown.getOrDefault(uuid, 0L);
        if (readyAt > now) {
            long seconds = Math.max(1L, (long) Math.ceil((readyAt - now) / 1000.0));
            MessageUtil.sendActionBar(player, plugin.getLocaleManager().msg(
                    "sonic_boom.beam_cooldown", "seconds", Long.toString(seconds)));
            return;
        }

        long cooldownMs = plugin.getConfig().getLong("abilities.sonic_boom.sonic_beam_cooldown_ms", 5000L);
        sonicBeamCooldown.put(uuid, now + Math.max(0L, cooldownMs));

        double range = plugin.getConfig().getDouble("abilities.sonic_boom.sonic_beam_range", 30.0);
        double radius = plugin.getConfig().getDouble("abilities.sonic_boom.sonic_beam_radius", 1.85);
        double damage = plugin.getConfig().getDouble("abilities.sonic_boom.sonic_beam_damage_hearts", 5.0) * 2.0;
        double pveMultiplier = Math.max(0.0, plugin.getConfig().getDouble("abilities.sonic_boom.sonic_beam_pve_damage_multiplier", 2.0));
        double knockback = plugin.getConfig().getDouble("abilities.sonic_boom.sonic_beam_knockback", 1.85);
        double verticalKnockback = plugin.getConfig().getDouble("abilities.sonic_boom.sonic_beam_vertical_knockback", 0.35);

        Location eye = player.getEyeLocation();
        Vector dir = eye.getDirection().normalize();
        World world = player.getWorld();

        world.playSound(player.getLocation(), Sound.ENTITY_WARDEN_SONIC_BOOM, 1.6f, 1.0f);
        world.playSound(player.getLocation(), Sound.ENTITY_GENERIC_EXPLODE, 0.65f, 1.65f);
        Particle.DustOptions sonicBlue = new Particle.DustOptions(Color.fromRGB(92, 225, 255), 1.3f);
        Particle.DustOptions paleBlue = new Particle.DustOptions(Color.fromRGB(185, 250, 255), 0.95f);

        RayTraceResult blockHit = world.rayTraceBlocks(eye, dir, range, FluidCollisionMode.NEVER, true);
        double effectiveRange = range;
        if (blockHit != null && blockHit.getHitPosition() != null) {
            effectiveRange = Math.min(range, blockHit.getHitPosition().distance(eye.toVector()));
        }

        for (double d = 0.7; d <= effectiveRange; d += 0.50) {
            Location point = eye.clone().add(dir.clone().multiply(d));
            world.spawnParticle(Particle.DUST, point, 9, 0.22, 0.22, 0.22, 0, sonicBlue);
            if (d % 1.5 < 0.50) {
                world.spawnParticle(Particle.DUST, point, 5, 0.40, 0.40, 0.40, 0, paleBlue);
                world.spawnParticle(Particle.SONIC_BOOM, point, 1, 0.06, 0.06, 0.06, 0);
            }
        }

        Location center = eye.clone().add(dir.clone().multiply(effectiveRange * 0.5));
        boolean hitAny = false;
        for (Entity entity : world.getNearbyEntities(center, effectiveRange * 0.5 + radius, effectiveRange * 0.5 + radius, effectiveRange * 0.5 + radius)) {
            if (!(entity instanceof LivingEntity target) || entity.equals(player)) continue;
            if (!isNearBeam(eye, dir, effectiveRange, radius, target.getEyeLocation())) continue;

            double targetDamage = target instanceof Player ? damage : damage * pveMultiplier;
            target.damage(targetDamage, player);
            Vector push = target.getLocation().toVector().subtract(player.getLocation().toVector());
            if (push.lengthSquared() < 0.0001) push = dir.clone();
            push.normalize().multiply(knockback).setY(verticalKnockback);
            target.setVelocity(target.getVelocity().add(push));
            world.spawnParticle(Particle.SONIC_BOOM, target.getLocation().add(0, 1, 0), 1, 0.15, 0.15, 0.15, 0);
            hitAny = true;
        }

        Location impact = blockHit != null && blockHit.getHitPosition() != null
                ? blockHit.getHitPosition().toLocation(world)
                : eye.clone().add(dir.clone().multiply(effectiveRange));
        triggerSonicBeamImpact(player, impact);

        if (hitAny) {
            MessageUtil.sendActionBar(player, ChatColor.translateAlternateColorCodes('&', "&3Sonic Boom &ahit"));
        } else {
            MessageUtil.sendActionBar(player, ChatColor.translateAlternateColorCodes('&', "&3Sonic Boom &7released"));
        }
    }

    private void triggerSonicBeamImpact(Player player, Location impact) {
        World world = impact.getWorld();
        if (world == null) return;

        float power = (float) plugin.getConfig().getDouble("abilities.sonic_boom.sonic_beam_impact_power", 0.65);
        boolean blockDamage = plugin.getConfig().getBoolean("abilities.sonic_boom.sonic_beam_impact_block_damage", true);
        if (power > 0) {
            world.createExplosion(impact, power, false, blockDamage, player);
        }

        double radius = plugin.getConfig().getDouble("abilities.sonic_boom.sonic_beam_impact_radius", 2.5);
        double maxDamage = plugin.getConfig().getDouble("abilities.sonic_boom.sonic_beam_impact_damage_hearts", 2.0) * 2.0;
        for (Entity entity : world.getNearbyEntities(impact, radius, radius, radius)) {
            if (!(entity instanceof LivingEntity target) || entity.equals(player)) continue;
            double distance = Math.max(0.2, target.getLocation().distance(impact));
            double factor = Math.max(0.25, 1.0 - (distance / radius));
            target.damage(maxDamage * factor, player);
        }

        world.playSound(impact, Sound.ENTITY_GENERIC_EXPLODE, 1.15f, 1.25f);
        world.spawnParticle(Particle.EXPLOSION, impact, 3, 0.25, 0.20, 0.25, 0);
        world.spawnParticle(Particle.CLOUD, impact, 24, 0.65, 0.35, 0.65, 0.08);
        world.spawnParticle(Particle.DUST, impact, 32, 0.75, 0.45, 0.75, 0,
                new Particle.DustOptions(Color.fromRGB(92, 225, 255), 1.0f));
    }

    public void triggerSonicRing(Player player) {
        UUID uuid = player.getUniqueId();
        long now = System.currentTimeMillis();
        long readyAt = sonicRingCooldown.getOrDefault(uuid, 0L);
        if (readyAt > now) {
            long seconds = Math.max(1L, (long) Math.ceil((readyAt - now) / 1000.0));
            MessageUtil.sendActionBar(player, plugin.getLocaleManager().msg(
                    "sonic_boom.ring_cooldown", "seconds", Long.toString(seconds)));
            return;
        }

        long cooldownMs = plugin.getConfig().getLong("abilities.sonic_boom.sonic_ring_cooldown_ms", 60000L);
        sonicRingCooldown.put(uuid, now + Math.max(0L, cooldownMs));

        Location center = player.getLocation();
        World world = player.getWorld();
        double radius = plugin.getConfig().getDouble("abilities.sonic_boom.sonic_ring_radius", 5.0);
        double maxDamage = plugin.getConfig().getDouble("abilities.sonic_boom.sonic_ring_damage_hearts", 11.2) * 2.0;
        double knockback = plugin.getConfig().getDouble("abilities.sonic_boom.sonic_ring_knockback", 2.68);
        double vertical = plugin.getConfig().getDouble("abilities.sonic_boom.sonic_ring_vertical_knockback", 0.60);

        world.playSound(center, Sound.ENTITY_GENERIC_EXPLODE, 2.35f, 0.75f);
        world.playSound(center, Sound.ENTITY_WARDEN_SONIC_BOOM, 1.15f, 1.25f);
        world.spawnParticle(Particle.EXPLOSION, center.clone().add(0, 0.8, 0), 8, 1.0, 0.35, 1.0, 0);
        world.spawnParticle(Particle.SONIC_BOOM, center.clone().add(0, 1.0, 0), 2, 0.4, 0.15, 0.4, 0);
        world.spawnParticle(Particle.CLOUD, center.clone().add(0, 0.35, 0), 85, 2.7, 0.12, 2.7, 0.18);
        world.spawnParticle(Particle.DUST, center.clone().add(0, 0.9, 0), 80, radius * 0.65, 0.35, radius * 0.65, 0,
                new Particle.DustOptions(Color.fromRGB(92, 225, 255), 1.2f));

        for (Entity entity : world.getNearbyEntities(center, radius, radius, radius)) {
            if (!(entity instanceof LivingEntity target) || entity.equals(player)) continue;
            double distance = Math.max(0.25, target.getLocation().distance(center));
            if (distance > radius) continue;
            double factor = Math.max(0.35, 1.0 - (distance / radius));
            target.damage(maxDamage * factor, player);
            Vector push = target.getLocation().toVector().subtract(center.toVector());
            if (push.lengthSquared() < 0.0001) push = player.getLocation().getDirection().clone();
            push.normalize().multiply(knockback * factor).setY(vertical + factor * 0.35);
            target.setVelocity(target.getVelocity().add(push));
        }

        MessageUtil.sendActionBar(player, ChatColor.translateAlternateColorCodes('&', "&3Sonic Ring &7released"));
    }

    public void handleMeleeHit(Player attacker, LivingEntity target, double currentDamage, java.util.function.DoubleConsumer damageSetter) {
        int hitCount = meleeHitCounter.merge(attacker.getUniqueId(), 1, Integer::sum);
        if (hitCount % 2 != 0) return;

        boolean critical = isCriticalHit(attacker);
        double multiplier = critical
                ? plugin.getConfig().getDouble("abilities.sonic_boom.melee_critical_damage_multiplier", 1.20)
                : plugin.getConfig().getDouble("abilities.sonic_boom.melee_explosion_damage_multiplier", 1.15);
        damageSetter.accept(currentDamage * Math.max(0.0, multiplier));

        Location hitLocation = target.getLocation().add(0, Math.min(1.2, Math.max(0.4, target.getEyeHeight() * 0.65)), 0);
        float volume = critical ? 1.05f : 0.48f;
        float pitch = critical ? 0.85f : 1.45f;
        target.getWorld().playSound(hitLocation, Sound.ENTITY_GENERIC_EXPLODE, volume, pitch);
        target.getWorld().spawnParticle(Particle.EXPLOSION, hitLocation, critical ? 2 : 1, 0.12, 0.12, 0.12, 0);
        target.getWorld().spawnParticle(Particle.CLOUD, hitLocation, critical ? 14 : 7, 0.22, 0.22, 0.22, 0.035);
        target.getWorld().spawnParticle(Particle.DUST, hitLocation, critical ? 18 : 9, 0.24, 0.24, 0.24, 0,
                new Particle.DustOptions(Color.fromRGB(92, 225, 255), critical ? 1.05f : 0.75f));
    }

    private boolean isCriticalHit(Player attacker) {
        return attacker.getFallDistance() > 0.0f
                && !attacker.isOnGround()
                && !attacker.isInsideVehicle()
                && !attacker.isSprinting()
                && !attacker.hasPotionEffect(PotionEffects.BLINDNESS);
    }

    private boolean isNearBeam(Location origin, Vector dir, double range, double radius, Location target) {
        double along = distanceAlongBeam(origin, dir, target);
        if (along < 0 || along > range) return false;
        Vector closest = origin.toVector().add(dir.clone().multiply(along));
        return closest.distanceSquared(target.toVector()) <= radius * radius;
    }

    private double distanceAlongBeam(Location origin, Vector dir, Location target) {
        Vector rel = target.toVector().subtract(origin.toVector());
        return rel.dot(dir);
    }

}
