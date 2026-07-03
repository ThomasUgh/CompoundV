package de.thomasugh.compoundv.ability.compoundv;
import de.thomasugh.compoundv.util.AbilityKillTracker;

import de.thomasugh.compoundv.CompoundV;
import de.thomasugh.compoundv.ability.Ability;
import de.thomasugh.compoundv.util.AttributeUtil;
import de.thomasugh.compoundv.util.MessageUtil;
import de.thomasugh.compoundv.util.PotionEffects;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.util.BoundingBox;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class TheRunnerAbility implements Ability {

    private final CompoundV plugin;
    private final NamespacedKey healthModKey;
    private final NamespacedKey attackSpeedKey;
    private final NamespacedKey stepKey;
    private final Map<UUID, Integer> speedLevels = new HashMap<>();
    private final Map<UUID, Map<UUID, Long>> impactCooldowns = new HashMap<>();
    private final Map<UUID, Long> lastRunSoundAt = new HashMap<>();
    private final Map<UUID, Double> lastMoveDelta = new HashMap<>();
    private final Map<UUID, Vector> lastMoveDirection = new HashMap<>();
    private final Map<UUID, Long> wallCrashCooldownUntil = new HashMap<>();

    public TheRunnerAbility(CompoundV plugin) {
        this.plugin = plugin;
        this.healthModKey = new NamespacedKey(plugin, "runner_hearts");
        this.attackSpeedKey = new NamespacedKey(plugin, "runner_attack_speed");
        this.stepKey = new NamespacedKey(plugin, "runner_step");
    }

    @Override public String getId() { return "the_runner"; }
    @Override public String getDisplayName() { return "The Runner"; }
    @Override public int getColor() { return 0xFFE066; }
    @Override public boolean hasToggle() { return true; }

    @Override
    public void apply(Player player) {
        int resistance = plugin.getConfig().getInt("abilities.the_runner.resistance_level", 1);
        int strength = plugin.getConfig().getInt("abilities.the_runner.strength_level", 2);
        int startSpeed = configuredSpeedLevels().get(0);
        int configuredDefault = plugin.getConfig().getInt("abilities.the_runner.default_speed_level", startSpeed);
        if (configuredSpeedLevels().contains(configuredDefault)) {
            startSpeed = configuredDefault;
        }

        if (resistance > 0) {
            player.addPotionEffect(new PotionEffect(PotionEffects.RESISTANCE,
                    Integer.MAX_VALUE, resistance - 1, false, false, true));
        }
        if (strength > 0) {
            player.addPotionEffect(new PotionEffect(PotionEffects.STRENGTH,
                    Integer.MAX_VALUE, strength - 1, false, false, true));
        }

        AttributeUtil.setMaxHealthBonus(player, healthModKey,
                plugin.getConfig().getDouble("abilities.the_runner.extra_hearts", 5.0) * 2.0);
        AttributeUtil.setAttackSpeedBonus(player, attackSpeedKey,
                plugin.getConfig().getDouble("abilities.the_runner.attack_speed_bonus", 1024.0));

        AttributeUtil.setStepHeightBonus(player, stepKey,
                plugin.getConfig().getDouble("abilities.the_runner.step_height_bonus", 0.9));

        speedLevels.put(player.getUniqueId(), startSpeed);
        applySpeed(player, startSpeed);
    }

    @Override
    public void remove(Player player) {
        UUID uuid = player.getUniqueId();
        speedLevels.remove(uuid);
        impactCooldowns.remove(uuid);
        lastRunSoundAt.remove(uuid);
        lastMoveDelta.remove(uuid);
        lastMoveDirection.remove(uuid);
        wallCrashCooldownUntil.remove(uuid);
        player.removePotionEffect(PotionEffects.SPEED);
        player.removePotionEffect(PotionEffects.RESISTANCE);
        player.removePotionEffect(PotionEffects.STRENGTH);
        AttributeUtil.setMaxHealthBonus(player, healthModKey, 0);
        AttributeUtil.setAttackSpeedBonus(player, attackSpeedKey, 0);
        AttributeUtil.setStepHeightBonus(player, stepKey, 0);
    }

    @Override
    public void onToggle(Player player) {
        List<Integer> levels = configuredSpeedLevels();
        UUID uuid = player.getUniqueId();
        Integer current = speedLevels.get(uuid);

        if (current == null) {
            int first = levels.get(0);
            speedLevels.put(uuid, first);
            applySpeed(player, first);
            MessageUtil.sendActionBar(player, plugin.getLocaleManager().msg("toggle.runner_speed_level",
                    "level", Integer.toString(first)));
            return;
        }

        int index = levels.indexOf(current);
        if (index < 0 || index >= levels.size() - 1) {
            speedLevels.remove(uuid);
            player.removePotionEffect(PotionEffects.SPEED);
            MessageUtil.sendActionBar(player, plugin.getLocaleManager().msg("toggle.runner_speed_off"));
            player.playSound(player.getLocation(), Sound.BLOCK_BEACON_DEACTIVATE, 0.45f, 1.6f);
            return;
        }

        int next = levels.get(index + 1);
        speedLevels.put(uuid, next);
        applySpeed(player, next);
        MessageUtil.sendActionBar(player, plugin.getLocaleManager().msg("toggle.runner_speed_level",
                "level", Integer.toString(next)));
    }

    public void handleMoveThroughEntities(Player player, Location from, Location to) {
        if (to == null || player.isSneaking()) return;

        Integer level = speedLevels.get(player.getUniqueId());
        int minImpactLevel = plugin.getConfig().getInt("abilities.the_runner.impact_min_speed_level", 10);
        if (level == null || level < minImpactLevel) return;

        double dx = to.getX() - from.getX();
        double dz = to.getZ() - from.getZ();
        double horizontalDistance = Math.sqrt(dx * dx + dz * dz);

        double minMoveDelta = plugin.getConfig().getDouble("abilities.the_runner.impact_min_move_delta", 0.08);
        double minHorizontalSpeed = plugin.getConfig().getDouble("abilities.the_runner.impact_min_horizontal_speed", 0.32);
        if (horizontalDistance < Math.max(minMoveDelta, minHorizontalSpeed)) return;

        double radius = plugin.getConfig().getDouble("abilities.the_runner.impact_radius", 0.65);
        double verticalRadius = plugin.getConfig().getDouble("abilities.the_runner.impact_vertical_radius", 0.55);
        double pathStep = Math.max(0.18, plugin.getConfig().getDouble("abilities.the_runner.impact_path_step", 0.28));
        long cooldownMs = plugin.getConfig().getLong("abilities.the_runner.impact_cooldown_ms", 750L);
        long now = System.currentTimeMillis();

        Map<UUID, Long> playerCooldowns = impactCooldowns.computeIfAbsent(player.getUniqueId(), ignored -> new HashMap<>());
        playerCooldowns.entrySet().removeIf(entry -> entry.getValue() <= now);

        Location midpoint = from.clone().add(to.toVector().subtract(from.toVector()).multiply(0.5));
        double searchRadius = Math.max(radius + 1.0, (from.distance(to) * 0.5) + radius + 1.0);

        for (Entity entity : player.getWorld().getNearbyEntities(midpoint, searchRadius, 2.5 + verticalRadius, searchRadius)) {
            if (!(entity instanceof LivingEntity target) || entity.equals(player)) continue;
            if (!didHitEntityOnPath(from, to, target, radius, verticalRadius, pathStep)) continue;

            UUID targetId = target.getUniqueId();
            if (playerCooldowns.getOrDefault(targetId, 0L) > now) continue;

            double damage = impactDamage(level);
            if (!AbilityKillTracker.damage(plugin, target, player, damage, "death_messages.the_runner", true)) continue;
            applyImpactKnockback(player, target);
            playImpactEffects(player, target);
            playerCooldowns.put(targetId, now + cooldownMs);
        }
    }

    public void tryRunSound(Player player, Location from, Location to) {
        if (to == null || !plugin.getConfig().getBoolean("abilities.the_runner.run_sound_enabled", true)) return;
        if (!speedLevels.containsKey(player.getUniqueId())) return;
        if (player.isFlying() || !player.isOnGround()) return;

        double dx = to.getX() - from.getX();
        double dz = to.getZ() - from.getZ();
        double horizontal = Math.sqrt(dx * dx + dz * dz);
        double minDistance = Math.max(0.0, plugin.getConfig().getDouble("abilities.the_runner.run_sound_min_distance", 0.16));
        if (horizontal < minDistance) return;

        UUID uuid = player.getUniqueId();
        long intervalMs = Math.max(0L, plugin.getConfig().getLong("abilities.the_runner.run_sound_interval_ms", 240L));
        long now = System.currentTimeMillis();
        if (now - lastRunSoundAt.getOrDefault(uuid, 0L) < intervalMs) return;
        lastRunSoundAt.put(uuid, now);

        String sound = plugin.getConfig().getString("abilities.the_runner.run_sound", "entity.horse.gallop");
        if (sound == null || sound.isBlank()) return;
        float volume = (float) plugin.getConfig().getDouble("abilities.the_runner.run_sound_volume", 0.45);
        float pitch = (float) plugin.getConfig().getDouble("abilities.the_runner.run_sound_pitch", 1.35);
        Location loc = player.getLocation();
        loc.getWorld().playSound(loc, sound, Math.max(0.0f, volume), Math.max(0.01f, pitch));
        loc.getWorld().spawnParticle(Particle.CLOUD, loc.clone().add(0, 0.08, 0), 3, 0.18, 0.02, 0.18, 0.008);
    }

    public void tryWallCrash(Player player, Location from, Location to) {
        if (to == null || !plugin.getConfig().getBoolean("abilities.the_runner.wall_crash_enabled", true)) return;

        UUID uuid = player.getUniqueId();
        double dx = to.getX() - from.getX();
        double dz = to.getZ() - from.getZ();
        double horizontal = Math.sqrt(dx * dx + dz * dz);
        Double previousDelta = lastMoveDelta.put(uuid, horizontal);
        Vector previousDirection = lastMoveDirection.get(uuid);
        if (horizontal > 0.01) {
            lastMoveDirection.put(uuid, new Vector(dx, 0.0, dz).normalize());
        }

        Integer level = speedLevels.get(uuid);
        int minLevel = plugin.getConfig().getInt("abilities.the_runner.wall_crash_min_speed_level", 11);
        if (level == null || level < minLevel) return;
        if (player.isFlying() || player.isInsideVehicle()) return;
        if (previousDelta == null || previousDirection == null) return;

        double minSpeed = plugin.getConfig().getDouble("abilities.the_runner.wall_crash_min_speed", 0.42);
        double maxStopDelta = plugin.getConfig().getDouble("abilities.the_runner.wall_crash_max_stop_delta", 0.05);
        if (previousDelta < minSpeed || horizontal > maxStopDelta) return;
        if (!isSolidWallAhead(player, previousDirection)) return;

        long now = System.currentTimeMillis();
        long cooldownMs = Math.max(0L, plugin.getConfig().getLong("abilities.the_runner.wall_crash_cooldown_ms", 1500L));
        if (wallCrashCooldownUntil.getOrDefault(uuid, 0L) > now) return;
        wallCrashCooldownUntil.put(uuid, now + cooldownMs);

        double baseHearts = Math.max(0.0, plugin.getConfig().getDouble("abilities.the_runner.wall_crash_damage_hearts", 2.0));
        double perLevelHearts = Math.max(0.0, plugin.getConfig().getDouble("abilities.the_runner.wall_crash_damage_per_speed_level_hearts", 0.25));
        double damage = (baseHearts + Math.max(0, level - minLevel) * perLevelHearts) * 2.0;
        if (damage <= 0.0) return;

        player.damage(damage);
        Location loc = player.getLocation().add(0, 1.0, 0);
        loc.getWorld().spawnParticle(Particle.CRIT, loc, 16, 0.30, 0.35, 0.30, 0.12);
        loc.getWorld().spawnParticle(Particle.CLOUD, loc, 10, 0.28, 0.25, 0.28, 0.05);
        loc.getWorld().playSound(loc, Sound.ENTITY_PLAYER_BIG_FALL, 0.85f, 0.75f);
        loc.getWorld().playSound(loc, Sound.BLOCK_STONE_HIT, 0.85f, 0.55f);
        MessageUtil.sendActionBar(player, plugin.getLocaleManager().msg("the_runner.wall_crash"));
    }

    private boolean isSolidWallAhead(Player player, Vector direction) {
        Location base = player.getLocation();
        Location feet = base.clone().add(direction.clone().multiply(0.7)).add(0, 0.3, 0);
        Location head = base.clone().add(direction.clone().multiply(0.7)).add(0, 1.4, 0);
        return feet.getBlock().getType().isSolid() || head.getBlock().getType().isSolid();
    }

    private void applySpeed(Player player, int level) {
        player.addPotionEffect(new PotionEffect(PotionEffects.SPEED,
                Integer.MAX_VALUE, Math.max(0, level - 1), false, false, true));
        player.playSound(player.getLocation(), Sound.BLOCK_BEACON_POWER_SELECT, 0.45f, 1.9f);
    }

    private List<Integer> configuredSpeedLevels() {
        List<Integer> configured = plugin.getConfig().getIntegerList("abilities.the_runner.speed_levels");
        if (configured.isEmpty()) {
            configured = List.of(11, 12, 15, 20);
        }

        List<Integer> levels = new ArrayList<>();
        for (Integer level : configured) {
            if (level == null || level < 1 || levels.contains(level)) continue;
            levels.add(level);
        }
        if (levels.isEmpty()) {
            levels.add(11);
            levels.add(12);
            levels.add(15);
            levels.add(20);
        }
        return levels;
    }

    private boolean didHitEntityOnPath(Location from, Location to, LivingEntity target,
                                      double radius, double verticalRadius, double pathStep) {
        if (from.getWorld() == null || target.getWorld() == null || !from.getWorld().equals(target.getWorld())) {
            return false;
        }

        Vector start = from.toVector();
        Vector end = to.toVector();
        Vector movement = end.clone().subtract(start);
        double length = movement.length();
        if (length < 0.001) return false;

        BoundingBox hitBox = target.getBoundingBox().expand(radius, verticalRadius, radius);
        int samples = Math.max(2, (int) Math.ceil(length / pathStep));
        for (int i = 0; i <= samples; i++) {
            double progress = i / (double) samples;
            Vector base = start.clone().add(movement.clone().multiply(progress));
            if (hitBox.contains(base.clone().add(new Vector(0, 0.25, 0)))
                    || hitBox.contains(base.clone().add(new Vector(0, 0.95, 0)))
                    || hitBox.contains(base.clone().add(new Vector(0, 1.55, 0)))) {
                return true;
            }
        }
        return false;
    }

    private double impactDamage(int speedLevel) {
        double baseHearts = plugin.getConfig().getDouble("abilities.the_runner.impact_base_damage_hearts", 6.0);
        double perLevelHearts = plugin.getConfig().getDouble("abilities.the_runner.impact_damage_per_speed_level_hearts", 1.0);
        int minImpactLevel = plugin.getConfig().getInt("abilities.the_runner.impact_min_speed_level", 10);
        double hearts = baseHearts + Math.max(0, speedLevel - minImpactLevel) * perLevelHearts;
        if (speedLevel >= 20) {
            hearts += plugin.getConfig().getDouble("abilities.the_runner.impact_level_20_bonus_hearts", 3.0);
        }
        return Math.max(0.0, hearts * 2.0);
    }

    private void applyImpactKnockback(Player player, LivingEntity target) {
        double knockback = plugin.getConfig().getDouble("abilities.the_runner.impact_knockback", 1.15);
        if (knockback <= 0) return;

        Vector direction = target.getLocation().toVector().subtract(player.getLocation().toVector());
        if (direction.lengthSquared() < 0.001) {
            direction = player.getLocation().getDirection().clone();
        }
        direction.setY(0).normalize().multiply(knockback).setY(0.28);
        target.setVelocity(target.getVelocity().add(direction));
    }

    private void playImpactEffects(Player player, LivingEntity target) {
        target.getWorld().spawnParticle(Particle.CLOUD, target.getLocation().add(0, 1.0, 0), 12, 0.25, 0.25, 0.25, 0.08);
        target.getWorld().playSound(target.getLocation(), Sound.ENTITY_PLAYER_ATTACK_KNOCKBACK, 0.55f, 1.45f);
        player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_ATTACK_SWEEP, 0.35f, 1.7f);
    }
}
