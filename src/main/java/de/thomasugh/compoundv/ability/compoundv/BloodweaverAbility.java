package de.thomasugh.compoundv.ability.compoundv;

import de.thomasugh.compoundv.CompoundV;
import de.thomasugh.compoundv.ability.Ability;
import de.thomasugh.compoundv.server.SchedulerAdapter;
import de.thomasugh.compoundv.server.TaskHandle;
import de.thomasugh.compoundv.util.AbilityKillTracker;
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
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Vector;

import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

public final class BloodweaverAbility implements Ability {

    private static final Color BLOOD_RED = Color.fromRGB(180, 0, 32);
    private static final Color BRIGHT_RED = Color.fromRGB(245, 18, 42);

    private final CompoundV plugin;
    private final String id;
    private final String tierKey;
    private final int color;
    private final NamespacedKey healthKey;

    private final Map<UUID, Long> lashCooldownUntil = new HashMap<>();
    private final Map<UUID, Long> ruptureCooldownUntil = new HashMap<>();
    private final Map<UUID, UUID> activeChargeTokens = new HashMap<>();
    private final Map<UUID, TaskHandle> chargeTasks = new HashMap<>();
    private final Map<UUID, Set<TaskHandle>> lockTasks = new HashMap<>();
    private final Map<UUID, Set<LivingEntity>> lockedTargets = new HashMap<>();
    private final Map<UUID, Integer> meleeHitCounts = new HashMap<>();

    public BloodweaverAbility(CompoundV plugin, String id, String tierKey, int color) {
        this.plugin = plugin;
        this.id = id;
        this.tierKey = tierKey;
        this.color = color;
        this.healthKey = new NamespacedKey(plugin, id + "_hearts");
    }

    @Override public String getId() { return id; }
    @Override public String getDisplayName() { return isVOne() ? "Bloodweaver V One" : "Bloodweaver"; }
    @Override public int getColor() { return color; }
    @Override public String getDescriptionKey() { return "ability." + id + ".description"; }

    @Override
    public void apply(Player player) {
        player.addPotionEffect(new PotionEffect(PotionEffects.RESISTANCE,
                Integer.MAX_VALUE, Math.max(0, configInt("resistance_level", isVOne() ? 3 : 3) - 1), false, false, true));
        player.addPotionEffect(new PotionEffect(PotionEffects.STRENGTH,
                Integer.MAX_VALUE, Math.max(0, configInt("strength_level", 2) - 1), false, false, true));
        player.addPotionEffect(new PotionEffect(PotionEffects.REGENERATION,
                Integer.MAX_VALUE, Math.max(0, configInt("regen_level", 2) - 1), false, false, true));

        double extraHearts = configDouble("extra_hearts", isVOne() ? 10.0 : 5.0);
        AttributeUtil.setMaxHealthBonus(player, healthKey, Math.max(0.0, extraHearts) * 2.0);
    }

    @Override
    public void remove(Player player) {
        UUID uuid = player.getUniqueId();
        lashCooldownUntil.remove(uuid);
        ruptureCooldownUntil.remove(uuid);
        meleeHitCounts.remove(uuid);
        cancelCharge(uuid);
        cancelLockTasks(uuid);
        player.removePotionEffect(PotionEffects.RESISTANCE);
        player.removePotionEffect(PotionEffects.STRENGTH);
        player.removePotionEffect(PotionEffects.REGENERATION);
        player.removePotionEffect(PotionEffects.WEAKNESS);
        AttributeUtil.setMaxHealthBonus(player, healthKey, 0.0);
    }

    public void shootBloodLash(Player player) {
        UUID uuid = player.getUniqueId();
        if (isOnCooldown(player, lashCooldownUntil, "bloodweaver.lash_cooldown")) return;

        long cooldownMs = configLong("lash.cooldown_ms", 3000L);
        lashCooldownUntil.put(uuid, System.currentTimeMillis() + Math.max(0L, cooldownMs));

        double range = configDouble("lash.range", 25.0);
        double hitRadius = Math.max(0.05, configDouble("lash.hit_radius", 0.75));
        Location eye = player.getEyeLocation();
        Vector direction = eye.getDirection().normalize();
        World world = player.getWorld();

        RayTraceResult hit = world.rayTrace(eye, direction, range, FluidCollisionMode.NEVER, true, hitRadius,
                entity -> entity instanceof LivingEntity && entity != player);
        double distance = hit != null && hit.getHitPosition() != null ? hit.getHitPosition().distance(eye.toVector()) : range;
        renderLash(world, eye, direction, Math.min(range, distance));

        world.playSound(eye, Sound.ENTITY_PLAYER_ATTACK_SWEEP, 0.85f, 0.72f);
        world.playSound(eye, Sound.BLOCK_HONEY_BLOCK_BREAK, 0.35f, 0.55f);

        if (hit == null || !(hit.getHitEntity() instanceof LivingEntity target)) {
            MessageUtil.sendActionBar(player, plugin.getLocaleManager().msg("bloodweaver.lash_missed"));
            return;
        }

        double minHearts = configDouble("lash.min_damage_hearts", isVOne() ? 2.5 : 2.0);
        double maxHearts = configDouble("lash.max_damage_hearts", isVOne() ? 5.0 : 4.0);
        if (maxHearts < minHearts) maxHearts = minHearts;
        double damage = (minHearts + ThreadLocalRandom.current().nextDouble(maxHearts - minHearts + 0.0001)) * 2.0;
        AbilityKillTracker.damage(plugin, target, player, Math.max(0.0, damage), "death_messages.bloodweaver_lash", true);

        int slownessTicks = Math.max(20, configInt("lash.slowness_ticks", 60));
        int slownessLevel = Math.max(1, configInt("lash.slowness_level", 2));
        target.addPotionEffect(new PotionEffect(PotionEffects.SLOWNESS, slownessTicks, slownessLevel - 1, false, true, true));
        applyLashBleed(player, target);

        Location impact = target.getLocation().add(0, Math.min(1.4, Math.max(0.8, target.getEyeHeight())), 0);
        world.spawnParticle(Particle.DUST, impact, 34, 0.25, 0.28, 0.25, 0,
                new Particle.DustOptions(BRIGHT_RED, 1.15f));
        world.spawnParticle(Particle.DAMAGE_INDICATOR, impact, 8, 0.16, 0.20, 0.16, 0.08);
        world.playSound(impact, Sound.ENTITY_GENERIC_HURT, 0.55f, 0.75f);
        MessageUtil.sendActionBar(player, plugin.getLocaleManager().msg("bloodweaver.lash_hit"));
    }

    public void handleMeleeHit(Player player, LivingEntity target) {
        if (target == null || target.isDead() || !target.isValid()) return;

        UUID uuid = player.getUniqueId();
        int requiredHits = Math.max(1, configInt("melee_bleed.required_hits", 3));
        int hits = meleeHitCounts.getOrDefault(uuid, 0) + 1;
        if (hits < requiredHits) {
            meleeHitCounts.put(uuid, hits);
            return;
        }
        meleeHitCounts.put(uuid, 0);

        int ticks = Math.max(20, configInt("melee_bleed.duration_ticks", 40));
        int interval = Math.max(1, configInt("melee_bleed.interval_ticks", 20));
        double damage = Math.max(0.0, configDouble("melee_bleed.damage_hearts", 0.5)) * 2.0;
        applyTimedBleed(player, target, ticks, interval, damage, "death_messages.bloodweaver_lash");

        Location loc = target.getLocation().add(0, Math.min(1.4, Math.max(0.8, target.getEyeHeight())), 0);
        target.getWorld().spawnParticle(Particle.DUST, loc, 14, 0.18, 0.20, 0.18, 0,
                new Particle.DustOptions(BLOOD_RED, 0.85f));
        target.getWorld().playSound(loc, Sound.BLOCK_HONEY_BLOCK_BREAK, 0.22f, 0.60f);
    }

    public void handleSneakLeftClick(Player player) {
        UUID uuid = player.getUniqueId();
        if (activeChargeTokens.containsKey(uuid)) return;
        if (isOnCooldown(player, ruptureCooldownUntil, "bloodweaver.rupture_cooldown")) return;
        startRuptureCharge(player, uuid);
    }

    private void startRuptureCharge(Player player, UUID uuid) {
        UUID token = UUID.randomUUID();
        activeChargeTokens.put(uuid, token);
        int chargeTicks = Math.max(20, configInt("rupture.charge_ticks", 60));

        MessageUtil.sendActionBar(player, plugin.getLocaleManager().msg("bloodweaver.rupture_charge_start"));
        player.playSound(player.getLocation(), Sound.ENTITY_WARDEN_HEARTBEAT, 0.55f, 0.55f);

        final TaskHandle[] handle = new TaskHandle[1];
        handle[0] = SchedulerAdapter.runTimer(plugin, player, new Runnable() {
            int age = 0;
            int lastSecond = -1;

            @Override public void run() {
                if (!player.isOnline() || !token.equals(activeChargeTokens.get(uuid))) {
                    if (handle[0] != null) handle[0].cancel();
                    return;
                }
                if (!player.isSneaking()) {
                    cancelCharge(uuid);
                    MessageUtil.sendActionBar(player, plugin.getLocaleManager().msg("bloodweaver.rupture_cancelled"));
                    player.playSound(player.getLocation(), Sound.BLOCK_RESPAWN_ANCHOR_DEPLETE, 0.42f, 0.65f);
                    if (handle[0] != null) handle[0].cancel();
                    return;
                }
                if (age >= chargeTicks) {
                    chargeTasks.remove(uuid);
                    activeChargeTokens.remove(uuid);
                    triggerBloodRupture(player);
                    if (handle[0] != null) handle[0].cancel();
                    return;
                }

                animateCharge(player, age);
                int remainingSeconds = Math.max(1, (int) Math.ceil((chargeTicks - age) / 20.0));
                if (age % 20 == 0 && remainingSeconds != lastSecond) {
                    lastSecond = remainingSeconds;
                    Map<String, String> values = new LinkedHashMap<>();
                    values.put("seconds", Integer.toString(remainingSeconds));
                    MessageUtil.sendActionBar(player, plugin.getLocaleManager().msg("bloodweaver.rupture_charge_progress", values));
                }
                age++;
            }
        }, 0L, 1L);
        chargeTasks.put(uuid, handle[0]);
    }

    private void triggerBloodRupture(Player player) {
        UUID uuid = player.getUniqueId();
        long cooldownMs = configLong("rupture.cooldown_ms", 45000L);
        ruptureCooldownUntil.put(uuid, System.currentTimeMillis() + Math.max(0L, cooldownMs));

        double selfCostHearts = Math.max(0.0, configDouble("rupture.self_cost_hearts", 2.0));
        applySelfHealthCost(player, selfCostHearts * 2.0);
        player.addPotionEffect(new PotionEffect(PotionEffects.WEAKNESS,
                Math.max(20, configInt("rupture.self_weakness_ticks", 400)),
                Math.max(0, configInt("rupture.self_weakness_level", 1) - 1), false, true, true));

        Location center = player.getLocation();
        World world = player.getWorld();
        double radius = configDouble("rupture.radius", 15.0);
        int durationTicks = Math.max(20, configInt("rupture.duration_ticks", isVOne() ? 140 : 100));
        int affected = 0;

        Set<TaskHandle> tasks = lockTasks.computeIfAbsent(uuid, ignored -> new HashSet<>());
        world.playSound(center, Sound.ENTITY_WITHER_AMBIENT, 0.72f, 0.48f);
        world.playSound(center, Sound.ENTITY_WARDEN_HEARTBEAT, 0.85f, 0.55f);
        world.spawnParticle(Particle.DUST, center.clone().add(0, 1.1, 0), 120,
                radius * 0.35, 0.65, radius * 0.35, 0,
                new Particle.DustOptions(BLOOD_RED, 1.35f));

        for (Entity entity : world.getNearbyEntities(center, radius, radius, radius)) {
            if (!(entity instanceof LivingEntity target) || target == player) continue;
            if (!target.isValid() || target.isDead()) continue;
            if (target instanceof Player targetPlayer && shouldIgnorePlayer(targetPlayer)) continue;
            if (target.getLocation().distanceSquared(center) > radius * radius) continue;
            affected++;
            lockedTargets.computeIfAbsent(uuid, ignored -> new HashSet<>()).add(target);
            lockAndDamageTarget(player, target, durationTicks, tasks);
        }

        Map<String, String> values = new LinkedHashMap<>();
        values.put("targets", Integer.toString(affected));
        MessageUtil.sendActionBar(player, plugin.getLocaleManager().msg("bloodweaver.rupture_released", values));
    }

    private void lockAndDamageTarget(Player owner, LivingEntity target, int durationTicks, Set<TaskHandle> ownerTasks) {
        Location start = target.getLocation().clone();
        Vector ownerOrigin = owner.getLocation().toVector();
        int liftTicks = Math.min(durationTicks, Math.max(5, configInt("rupture.lift_ticks", 16)));
        double liftHeight = Math.max(0.0, configDouble("rupture.lift_height", 3.0));
        double liftVelocity = liftTicks <= 0 ? 0.0 : Math.min(0.42, liftHeight / Math.max(1.0, liftTicks) * 1.08);
        double damagePerSecond = Math.max(0.0, configDouble("rupture.damage_hearts_per_second", 2.0)) * 2.0;
        int tickPeriod = Math.max(1, configInt("rupture.tick_period", 2));
        int particleInterval = Math.max(tickPeriod, configInt("rupture.particle_interval_ticks", 4));
        int damageInterval = Math.max(1, configInt("rupture.damage_interval_ticks", 20));

        final TaskHandle[] handle = new TaskHandle[1];
        handle[0] = SchedulerAdapter.runTimer(plugin, target, new Runnable() {
            int age = 0;
            boolean initialized = false;

            @Override public void run() {
                if (!target.isValid() || target.isDead() || !start.getWorld().equals(target.getWorld())) {
                    releaseTarget(ownerOrigin, target, false);
                    removeLockedTarget(owner.getUniqueId(), target);
                    removeTask(owner.getUniqueId(), handle[0]);
                    if (handle[0] != null) handle[0].cancel();
                    return;
                }

                if (!initialized) {
                    initialized = true;
                    target.addPotionEffect(new PotionEffect(PotionEffects.GLOWING, durationTicks + 15, 0, false, false, false));
                    target.addPotionEffect(new PotionEffect(PotionEffects.SLOWNESS, durationTicks + 15, 9, false, false, false));
                    target.setGravity(false);
                    if (target instanceof Mob mob) mob.setAI(false);
                }

                if (age >= durationTicks) {
                    releaseTarget(ownerOrigin, target, true);
                    removeLockedTarget(owner.getUniqueId(), target);
                    removeTask(owner.getUniqueId(), handle[0]);
                    if (handle[0] != null) handle[0].cancel();
                    return;
                }

                double yVelocity = age < liftTicks ? liftVelocity : 0.0;
                target.setVelocity(new Vector(0, yVelocity, 0));

                if (age % particleInterval == 0) renderRuptureTarget(target, age);

                if (age > 0 && age % damageInterval == 0 && owner.isOnline()) {
                    AbilityKillTracker.damage(plugin, target, owner, damagePerSecond, "death_messages.bloodweaver_rupture", true);
                }
                age += tickPeriod;
            }
        }, 0L, tickPeriod);
        ownerTasks.add(handle[0]);
    }

    private void releaseTarget(Vector ownerOrigin, LivingEntity target, boolean knockback) {
        if (target == null || !target.isValid()) return;
        target.setGravity(true);
        if (target instanceof Mob mob) mob.setAI(true);
        target.removePotionEffect(PotionEffects.SLOWNESS);
        target.removePotionEffect(PotionEffects.GLOWING);
        if (!knockback || target.isDead()) return;

        Vector away = target.getLocation().toVector().subtract(ownerOrigin);
        away.setY(0);
        if (away.lengthSquared() < 0.001) away = new Vector(0, 0, 1);
        away.normalize().multiply(configDouble("rupture.knockback_horizontal", 1.15));
        away.setY(configDouble("rupture.knockback_vertical", 0.35));
        target.setVelocity(away);
        target.getWorld().playSound(target.getLocation(), Sound.ENTITY_PLAYER_ATTACK_KNOCKBACK, 0.7f, 0.75f);
    }

    private void applyLashBleed(Player owner, LivingEntity target) {
        int ticks = Math.max(20, configInt("lash.bleed_ticks", 60));
        int interval = Math.max(1, configInt("lash.bleed_interval_ticks", 20));
        double damage = Math.max(0.0, configDouble("lash.bleed_damage_hearts", 0.5)) * 2.0;
        applyTimedBleed(owner, target, ticks, interval, damage, "death_messages.bloodweaver_lash");
    }

    private void applyTimedBleed(Player owner, LivingEntity target, int ticks, int interval, double damage, String deathMessageKey) {
        if (damage <= 0.0 || target == null || target.isDead() || !target.isValid()) return;

        int applications = Math.max(1, ticks / interval);
        for (int i = 1; i <= applications; i++) {
            SchedulerAdapter.runLater(plugin, target, () -> {
                if (!owner.isOnline() || target.isDead() || !target.isValid()) return;
                AbilityKillTracker.damage(plugin, target, owner, damage, deathMessageKey, true);
                Location loc = target.getLocation().add(0, Math.min(1.4, Math.max(0.8, target.getEyeHeight())), 0);
                target.getWorld().spawnParticle(Particle.DUST, loc, 8, 0.16, 0.16, 0.16, 0,
                        new Particle.DustOptions(BLOOD_RED, 0.82f));
            }, (long) i * interval);
        }
    }

    private void applySelfHealthCost(Player player, double healthCost) {
        if (healthCost <= 0.0) return;
        double next = Math.max(1.0, player.getHealth() - healthCost);
        player.setHealth(next);
        player.getWorld().spawnParticle(Particle.DAMAGE_INDICATOR, player.getLocation().add(0, 1, 0),
                8, 0.28, 0.32, 0.28, 0.08);
        player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_HURT, 0.55f, 0.75f);
    }

    private boolean isOnCooldown(Player player, Map<UUID, Long> cooldownMap, String key) {
        long now = System.currentTimeMillis();
        long readyAt = cooldownMap.getOrDefault(player.getUniqueId(), 0L);
        if (readyAt <= now) return false;
        long seconds = Math.max(1L, (long) Math.ceil((readyAt - now) / 1000.0));
        MessageUtil.sendActionBar(player, plugin.getLocaleManager().msg(key, "seconds", Long.toString(seconds)));
        player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 0.35f, 0.85f);
        return true;
    }

    private void renderLash(World world, Location origin, Vector direction, double distance) {
        Particle.DustOptions core = new Particle.DustOptions(BLOOD_RED, 1.05f);
        Particle.DustOptions glow = new Particle.DustOptions(BRIGHT_RED, 0.72f);
        for (double d = 0.35; d <= distance; d += 0.32) {
            Location point = origin.clone().add(direction.clone().multiply(d));
            world.spawnParticle(Particle.DUST, point, 4, 0.025, 0.025, 0.025, 0, core);
            if (d % 0.8 < 0.32) {
                world.spawnParticle(Particle.DUST, point, 2, 0.05, 0.05, 0.05, 0, glow);
            }
        }
    }

    private void animateCharge(Player player, int age) {
        Location base = player.getLocation();
        World world = player.getWorld();
        Particle.DustOptions red = new Particle.DustOptions(BLOOD_RED, 0.85f);
        double radius = 1.2 + (age % 20) * 0.035;
        int points = 5;
        for (int i = 0; i < points; i++) {
            double angle = age * 0.22 + (Math.PI * 2.0 / points) * i;
            Location point = base.clone().add(Math.cos(angle) * radius, 0.2 + (i % 2) * 0.22, Math.sin(angle) * radius);
            world.spawnParticle(Particle.DUST, point, 2, 0.025, 0.025, 0.025, 0, red);
        }
        if (age % 20 == 0) world.playSound(base, Sound.ENTITY_WARDEN_HEARTBEAT, 0.28f, 0.65f);
    }

    private void renderRuptureTarget(LivingEntity target, int age) {
        Location loc = target.getLocation().add(0, Math.min(1.45, Math.max(0.85, target.getEyeHeight())), 0);
        World world = target.getWorld();
        world.spawnParticle(Particle.DUST, loc, 5, 0.24, 0.30, 0.24, 0,
                new Particle.DustOptions(BLOOD_RED, 0.9f));
        if (age % 20 == 0) {
            world.spawnParticle(Particle.DAMAGE_INDICATOR, loc, 2, 0.14, 0.14, 0.14, 0.035);
            world.playSound(loc, Sound.ENTITY_WARDEN_HEARTBEAT, 0.14f, 0.70f);
        }
    }

    private Location interpolate(Location from, Location to, double progress) {
        progress = Math.max(0.0, Math.min(1.0, progress));
        return from.clone().add(
                (to.getX() - from.getX()) * progress,
                (to.getY() - from.getY()) * progress,
                (to.getZ() - from.getZ()) * progress
        );
    }

    private boolean shouldIgnorePlayer(Player player) {
        return player.getGameMode() == GameMode.CREATIVE || player.getGameMode() == GameMode.SPECTATOR;
    }

    private void cancelCharge(UUID uuid) {
        activeChargeTokens.remove(uuid);
        TaskHandle task = chargeTasks.remove(uuid);
        if (task != null) task.cancel();
    }

    private void cancelLockTasks(UUID uuid) {
        Set<TaskHandle> tasks = lockTasks.remove(uuid);
        if (tasks != null) {
            for (TaskHandle task : tasks) {
                if (task != null) task.cancel();
            }
        }
        Set<LivingEntity> targets = lockedTargets.remove(uuid);
        if (targets == null) return;
        for (LivingEntity target : targets) {
            if (target != null && target.isValid() && !target.isDead()) {
                SchedulerAdapter.runLater(plugin, target, () -> {
                    if (!target.isValid() || target.isDead()) return;
                    target.setGravity(true);
                    if (target instanceof Mob mob) mob.setAI(true);
                    target.removePotionEffect(PotionEffects.SLOWNESS);
                    target.removePotionEffect(PotionEffects.GLOWING);
                }, 1L);
            }
        }
    }

    private void removeLockedTarget(UUID uuid, LivingEntity target) {
        Set<LivingEntity> targets = lockedTargets.get(uuid);
        if (targets == null) return;
        targets.remove(target);
        if (targets.isEmpty()) lockedTargets.remove(uuid);
    }

    private void removeTask(UUID uuid, TaskHandle task) {
        Set<TaskHandle> tasks = lockTasks.get(uuid);
        if (tasks == null) return;
        tasks.remove(task);
        if (tasks.isEmpty()) lockTasks.remove(uuid);
    }

    private boolean isVOne() {
        return "v_one".equalsIgnoreCase(tierKey);
    }

    private String path(String key) {
        return "abilities.bloodweaver." + tierKey + "." + key;
    }

    private String sharedPath(String key) {
        return "abilities.bloodweaver.shared." + key;
    }

    private int configInt(String key, int def) {
        String tierPath = path(key);
        if (plugin.getConfig().contains(tierPath)) return plugin.getConfig().getInt(tierPath, def);
        return plugin.getConfig().getInt(sharedPath(key), def);
    }

    private long configLong(String key, long def) {
        String tierPath = path(key);
        if (plugin.getConfig().contains(tierPath)) return plugin.getConfig().getLong(tierPath, def);
        return plugin.getConfig().getLong(sharedPath(key), def);
    }

    private double configDouble(String key, double def) {
        String tierPath = path(key);
        if (plugin.getConfig().contains(tierPath)) return plugin.getConfig().getDouble(tierPath, def);
        return plugin.getConfig().getDouble(sharedPath(key), def);
    }
}
