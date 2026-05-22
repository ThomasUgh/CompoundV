package de.thomasugh.compoundv.ability.vone;

import de.thomasugh.compoundv.CompoundV;
import de.thomasugh.compoundv.ability.Ability;
import de.thomasugh.compoundv.util.MessageUtil;
import org.bukkit.Color;
import org.bukkit.FluidCollisionMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import de.thomasugh.compoundv.util.AttributeUtil;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import de.thomasugh.compoundv.util.PotionEffects;
import de.thomasugh.compoundv.server.SchedulerAdapter;
import de.thomasugh.compoundv.server.TaskHandle;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Vector;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class TheVeteranAbility implements Ability {

    private final CompoundV plugin;
    private final NamespacedKey healthModKey;
    private final Map<UUID, UUID> activeBurstTokens = new HashMap<>();
    private final Map<UUID, Long> burstCooldownUntil = new HashMap<>();
    private final Map<UUID, TaskHandle> chargeTasks = new HashMap<>();
    private final Map<UUID, TaskHandle> beamTasks = new HashMap<>();
    private final Map<UUID, TaskHandle> strengthRecoveryTasks = new HashMap<>();

    public TheVeteranAbility(CompoundV p) {
        plugin = p;
        healthModKey = new NamespacedKey(plugin, "veteran_hearts");
    }

    @Override public String    getId()          { return "the_veteran"; }
    @Override public String    getDisplayName() { return "The Veteran"; }
    @Override public int getColor()       { return 0xC0C0C0; }

    @Override
    public void apply(Player p) {
        UUID u = p.getUniqueId();
        resetBurstState(u, true);

        int res = plugin.getConfig().getInt("abilities.the_veteran.resistance_level", 4);
        double extraHp = plugin.getConfig().getDouble("abilities.the_veteran.extra_hearts", 20.0) * 2.0;
        applyStrength(p, plugin.getConfig().getInt("abilities.the_veteran.strength_level", 5));
        p.addPotionEffect(new PotionEffect(PotionEffects.RESISTANCE,
                Integer.MAX_VALUE, res - 1, false, false, true));
        p.addPotionEffect(new PotionEffect(PotionEffects.FIRE_RESISTANCE,
                Integer.MAX_VALUE, 0, false, false, true));
        setMaxHealthBonus(p, extraHp);
    }

    @Override
    public void remove(Player p) {
        UUID u = p.getUniqueId();
        resetBurstState(u, true);
        TaskHandle recoveryTask = strengthRecoveryTasks.remove(u);
        if (recoveryTask != null) recoveryTask.cancel();
        p.removePotionEffect(PotionEffects.STRENGTH);
        p.removePotionEffect(PotionEffects.RESISTANCE);
        p.removePotionEffect(PotionEffects.FIRE_RESISTANCE);
        setMaxHealthBonus(p, 0);
    }

    private void applyStrength(Player player, int level) {
        if (level <= 0) {
            player.removePotionEffect(PotionEffects.STRENGTH);
            return;
        }
        player.addPotionEffect(new PotionEffect(PotionEffects.STRENGTH,
                Integer.MAX_VALUE, Math.max(0, level - 1), false, false, true));
    }

    private void applyTemporaryStrengthPenalty(Player player) {
        UUID uuid = player.getUniqueId();
        int baseStrength = Math.max(1, plugin.getConfig().getInt("abilities.the_veteran.strength_level", 5));
        int reducedStrength = Math.max(1, (int) Math.floor(baseStrength * plugin.getConfig().getDouble(
                "abilities.the_veteran.post_burst_strength_multiplier", 0.5)));
        int durationTicks = plugin.getConfig().getInt("abilities.the_veteran.post_burst_strength_reduction_ticks",
                plugin.getConfig().getInt("abilities.the_veteran.mushroom_cloud_duration_ticks", 2400));

        applyStrength(player, reducedStrength);

        TaskHandle previous = strengthRecoveryTasks.remove(uuid);
        if (previous != null) previous.cancel();

        TaskHandle handle = SchedulerAdapter.runLater(plugin, () -> {
            strengthRecoveryTasks.remove(uuid);
            if (player.isOnline() && plugin.getAbilityManager().getAbility(player) == this) {
                applyStrength(player, plugin.getConfig().getInt("abilities.the_veteran.strength_level", 5));
            }
        }, Math.max(20, durationTicks));
        strengthRecoveryTasks.put(uuid, handle);
    }

    public boolean isBurstActive(Player p) {
        return activeBurstTokens.containsKey(p.getUniqueId());
    }

    public void handleSneakLeftClick(Player player) {
        UUID uuid = player.getUniqueId();
        if (isBurstActive(player)) return;
        startBurstCharge(player, uuid);
    }

    private void startBurstCharge(Player shooter, UUID uuid) {
        if (isOnCooldown(shooter)) return;
        if (activeBurstTokens.containsKey(uuid)) return;

        UUID token = UUID.randomUUID();
        activeBurstTokens.put(uuid, token);

        MessageUtil.sendActionBar(shooter, plugin.getLocaleManager().msg("veteran.charge_start"));
        shooter.playSound(shooter.getLocation(), Sound.BLOCK_BEACON_POWER_SELECT, 0.75f, 0.5f);
        chargeThenFire(shooter, uuid, token);
    }

    private boolean isOnCooldown(Player player) {
        UUID uuid = player.getUniqueId();
        long now = System.currentTimeMillis();
        long cooldownUntil = burstCooldownUntil.getOrDefault(uuid, 0L);
        if (now >= cooldownUntil) return false;

        long secs = ((cooldownUntil - now) / 1000L) + 1L;
        MessageUtil.sendActionBar(player, plugin.getLocaleManager().msg(
                "veteran.cooldown", "seconds", Long.toString(secs)));
        return true;
    }

    private boolean isCurrentBurst(UUID uuid, UUID token) {
        return token.equals(activeBurstTokens.get(uuid));
    }

    private void resetBurstState(UUID uuid, boolean clearCooldown) {
        activeBurstTokens.remove(uuid);
        TaskHandle chargeTask = chargeTasks.remove(uuid);
        if (chargeTask != null) chargeTask.cancel();

        TaskHandle beamTask = beamTasks.remove(uuid);
        if (beamTask != null) beamTask.cancel();

        if (clearCooldown) {
            burstCooldownUntil.remove(uuid);
        }
    }

    private void cancelActiveBurst(UUID uuid, UUID token) {
        if (!isCurrentBurst(uuid, token)) return;
        activeBurstTokens.remove(uuid);
        TaskHandle chargeTask = chargeTasks.remove(uuid);
        if (chargeTask != null) chargeTask.cancel();
        TaskHandle beamTask = beamTasks.remove(uuid);
        if (beamTask != null) beamTask.cancel();
    }

    private void chargeThenFire(Player shooter, UUID uuid, UUID token) {
        int chargeTicks = Math.max(100, plugin.getConfig().getInt("abilities.the_veteran.charge_duration_ticks", 100));
        int timerUpdateTicks = 20;
        long cooldownMs = plugin.getConfig().getLong("abilities.the_veteran.burst_cooldown_ms", 300_000L);

        final TaskHandle[] task = new TaskHandle[1];
        task[0] = SchedulerAdapter.runTimer(plugin, new Runnable() {
            int age = 0;
            int lastShownSecond = -1;

            @Override public void run() {
                if (!shooter.isOnline() || !isCurrentBurst(uuid, token)) {
                    if (task[0] != null) task[0].cancel();
                    return;
                }
                if (!shooter.isSneaking()) {
                    cancelActiveBurst(uuid, token);
                    MessageUtil.sendActionBar(shooter, plugin.getLocaleManager().msg("veteran.charge_cancelled"));
                    shooter.playSound(shooter.getLocation(), Sound.BLOCK_BEACON_DEACTIVATE, 0.9f, 0.65f);
                    if (task[0] != null) task[0].cancel();
                    return;
                }

                if (age >= chargeTicks) {
                    chargeTasks.remove(uuid);
                    burstCooldownUntil.put(uuid, System.currentTimeMillis() + cooldownMs);
                    MessageUtil.sendActionBar(shooter, plugin.getLocaleManager().msg("veteran.burst_start"));
                    shooter.playSound(shooter.getLocation(), Sound.ITEM_TOTEM_USE,            0.6f, 0.65f);
                    shooter.playSound(shooter.getLocation(), Sound.ENTITY_WITHER_SPAWN,       0.75f, 1.45f);
                    shooter.playSound(shooter.getLocation(), Sound.BLOCK_BEACON_POWER_SELECT, 0.9f, 0.65f);
                    triggerGroundZeroExplosion(shooter);
                    applyTemporaryStrengthPenalty(shooter);
                    fireChestBeam(shooter, uuid, token);
                    if (task[0] != null) task[0].cancel();
                    return;
                }

                animateCharge(shooter, age, chargeTicks);

                int remainingSeconds = Math.max(1, (int) Math.ceil((chargeTicks - age) / 20.0));
                if (age % timerUpdateTicks == 0 && remainingSeconds != lastShownSecond) {
                    lastShownSecond = remainingSeconds;
                    MessageUtil.sendActionBar(shooter, plugin.getLocaleManager().msg(
                            "veteran.charge_progress", "seconds", Integer.toString(remainingSeconds)));
                }

                age += 1;
            }
        }, 0L, 1L);
        chargeTasks.put(uuid, task[0]);
    }

    private void animateCharge(Player shooter, int age, int chargeTicks) {
        World w = shooter.getWorld();
        Location base = shooter.getLocation();
        double progress = Math.min(1.0, Math.max(0.0, age / Math.max(1.0, (double) chargeTicks)));

        double outerRadius = 2.8 - progress * 1.15;
        double innerRadius = 0.7 + progress * 0.45;
        int streamPoints = 3 + (int) Math.round(progress * 3.0);

        Particle.DustOptions yellow = new Particle.DustOptions(Color.fromRGB(255, 225, 40), 0.95f + (float) progress * 0.45f);
        Particle.DustOptions orange = new Particle.DustOptions(Color.fromRGB(255, 115, 10), 0.75f + (float) progress * 0.35f);

        for (int i = 0; i < streamPoints; i++) {
            double angle = age * 0.18 + (Math.PI * 2.0 / streamPoints) * i;
            double radius = outerRadius - ((age % 16) / 16.0) * (outerRadius - innerRadius);
            Location point = base.clone().add(Math.cos(angle) * radius, 0.10 + progress * 0.35, Math.sin(angle) * radius);
            w.spawnParticle(Particle.DUST, point, 2, 0.04, 0.04, 0.04, 0, i % 2 == 0 ? yellow : orange);
        }

        Location feet = base.clone().add(0, 0.12, 0);
        w.spawnParticle(Particle.CLOUD, feet, 3 + (int) (progress * 5), 0.75 + progress * 0.45, 0.03, 0.75 + progress * 0.45, 0.035 + progress * 0.02);
        if (age % 5 == 0) {
            Location core = base.clone().add(0, 0.85 + progress * 0.35, 0);
            w.spawnParticle(Particle.DUST, core, 2, 0.16, 0.12, 0.16, 0, yellow);
        }
        if (age <= 60 && age % 20 == 0) {
            w.playSound(base, Sound.BLOCK_BEACON_AMBIENT, 0.22f, (float) (0.45 + progress * 0.45));
        }
    }


    private void triggerGroundZeroExplosion(Player shooter) {
        World w = shooter.getWorld();
        Location base = shooter.getLocation();

        double radius = plugin.getConfig().getDouble("abilities.the_veteran.ground_zero_radius", 14.0);
        double maxDamage = plugin.getConfig().getDouble("abilities.the_veteran.ground_zero_damage", 525.0);
        double playerDamageMultiplier = Math.max(0.0, plugin.getConfig().getDouble("abilities.the_veteran.ground_zero_player_damage_multiplier", 0.25));
        double playerDamageCapFraction = Math.max(0.0, plugin.getConfig().getDouble("abilities.the_veteran.ground_zero_player_damage_cap_fraction", 0.25));
        double pveDamageMultiplier = Math.max(0.0, plugin.getConfig().getDouble("abilities.the_veteran.pve_damage_multiplier", 0.9));
        double knockback = plugin.getConfig().getDouble("abilities.the_veteran.ground_zero_knockback", 0.12);
        double verticalKnockback = plugin.getConfig().getDouble("abilities.the_veteran.ground_zero_vertical_knockback", 0.03);
        double maxHorizontalVelocity = plugin.getConfig().getDouble("abilities.the_veteran.ground_zero_max_horizontal_velocity", 0.18);
        double maxVerticalVelocity = plugin.getConfig().getDouble("abilities.the_veteran.ground_zero_max_vertical_velocity", 0.04);
        boolean setFire = plugin.getConfig().getBoolean("abilities.the_veteran.ground_zero_set_fire", true);

        double particleMultiplier = Math.max(0.05, plugin.getConfig().getDouble(
                "abilities.the_veteran.ground_zero_particle_multiplier", 0.75));

        w.playSound(base, Sound.ENTITY_GENERIC_EXPLODE, 5.0f, 0.25f);
        w.playSound(base, Sound.ENTITY_DRAGON_FIREBALL_EXPLODE, 3.5f, 0.35f);
        w.playSound(base, Sound.ENTITY_WITHER_BREAK_BLOCK, 2.5f, 0.35f);
        w.playSound(base, Sound.ITEM_FIRECHARGE_USE, 3.0f, 0.35f);
        w.spawnParticle(Particle.EXPLOSION, base.clone().add(0, 1.0, 0), scaledParticles(58, particleMultiplier), 6.2, 2.4, 6.2, 0.0);
        w.spawnParticle(Particle.LARGE_SMOKE, base.clone().add(0, 1.1, 0), scaledParticles(300, particleMultiplier), 7.0, 1.8, 7.0, 0.15);
        w.spawnParticle(Particle.CLOUD, base.clone().add(0, 0.35, 0), scaledParticles(150, particleMultiplier), 6.8, 0.18, 6.8, 0.42);
        w.spawnParticle(Particle.FLAME, base.clone().add(0, 0.8, 0), scaledParticles(170, particleMultiplier), 5.8, 1.1, 5.8, 0.18);
        w.spawnParticle(Particle.DUST, base.clone().add(0, 1.2, 0), scaledParticles(260, particleMultiplier), 6.2, 1.5, 6.2, 0,
                new Particle.DustOptions(Color.fromRGB(255, 215, 35), 2.4f));

        for (Entity entity : w.getNearbyEntities(base, radius, radius, radius)) {
            if (!(entity instanceof LivingEntity target) || entity.equals(shooter)) continue;

            double distance = Math.max(0.6, target.getLocation().distance(base));
            if (distance > radius) continue;

            double factor = 1.0 - (distance / radius);
            double damage = Math.max(18.0, maxDamage * Math.max(0.25, factor));
            if (target instanceof Player) {
                damage = 0.0;
            } else {
                damage *= pveDamageMultiplier;
            }
            if (damage > 0.0) {
                target.damage(damage, shooter);
            }
            if (setFire && !(target instanceof Player)) target.setFireTicks(220);

            Vector push = target.getLocation().toVector().subtract(base.toVector());
            if (push.lengthSquared() > 0.001) {
                push.normalize().multiply(knockback * Math.max(0.25, factor)).setY(verticalKnockback * Math.max(0.35, factor));
                target.setVelocity(limitVeteranKnockback(push, maxHorizontalVelocity, maxVerticalVelocity));
            }
        }
    }

    private void animateAtomicMushroom(Location base) {
        World w = base.getWorld();
        if (w == null) return;

        int durationTicks = plugin.getConfig().getInt("abilities.the_veteran.mushroom_cloud_duration_ticks", 2400);
        int periodTicks = Math.max(2, plugin.getConfig().getInt("abilities.the_veteran.mushroom_cloud_period_ticks", 12));
        double maxHeight = plugin.getConfig().getDouble("abilities.the_veteran.mushroom_cloud_height", 32.0);
        double maxRadius = plugin.getConfig().getDouble("abilities.the_veteran.mushroom_cloud_radius", 15.5);
        double particleMultiplier = Math.max(0.05, plugin.getConfig().getDouble(
                "abilities.the_veteran.mushroom_cloud_particle_multiplier", 0.78));
        int soundDurationTicks = Math.max(0, plugin.getConfig().getInt(
                "abilities.the_veteran.mushroom_cloud_sound_duration_ticks", 80));
        int soundIntervalTicks = Math.max(periodTicks, Math.max(1, Math.round(40.0f / periodTicks)) * periodTicks);

        final TaskHandle[] task = new TaskHandle[1];
        task[0] = SchedulerAdapter.runTimer(plugin, new Runnable() {
            int age = 0;

            @Override public void run() {
                if (age > durationTicks) {
                    task[0].cancel();
                    return;
                }

                double progress = Math.min(1.0, age / Math.max(1.0, (double) durationTicks));
                double build = Math.min(1.0, progress / 0.22);
                double fade = progress < 0.84 ? 1.0 : Math.max(0.0, 1.0 - ((progress - 0.84) / 0.16));
                double rise = Math.sin(build * Math.PI * 0.5);
                double thickness = 0.85 + fade * 0.75;
                double driftX = Math.sin(age * 0.031) * 0.75 * progress;
                double driftZ = Math.cos(age * 0.027) * 0.75 * progress;
                Location plumeBase = base.clone().add(driftX, 0, driftZ);

                double stemHeight = 5.0 + rise * (maxHeight * 0.56);
                double stemRadius = 1.8 + rise * 2.4;
                double capHeight = 11.0 + rise * (maxHeight * 0.66);
                double capRadius = 6.2 + rise * maxRadius;
                double outerRingRadius = capRadius * 1.08;

                int stemParticles = scaledParticles(12, 42.0 * fade, particleMultiplier);
                int capParticles = scaledParticles(34, 122.0 * fade, particleMultiplier);
                int ringParticles = scaledParticles(22, 54.0 * fade, particleMultiplier);
                int undersideParticles = scaledParticles(14, 48.0 * fade, particleMultiplier);

                Particle.DustOptions darkSmoke = new Particle.DustOptions(Color.fromRGB(44, 48, 46), 2.7f);
                Particle.DustOptions ash = new Particle.DustOptions(Color.fromRGB(104, 112, 106), 2.4f);
                Particle.DustOptions ember = new Particle.DustOptions(Color.fromRGB(255, 122, 28), 1.8f);
                Particle.DustOptions hotCore = new Particle.DustOptions(Color.fromRGB(255, 224, 112), 1.3f);
                Particle.DustOptions orangeUnderbelly = new Particle.DustOptions(Color.fromRGB(230, 82, 18), 2.0f);

                Location lowerStem = plumeBase.clone().add(0, stemHeight * 0.30, 0);
                Location midStem = plumeBase.clone().add(0, stemHeight * 0.70, 0);
                Location upperStem = plumeBase.clone().add(0, stemHeight, 0);
                Location cap = plumeBase.clone().add(0, capHeight, 0);
                Location capTop = cap.clone().add(0, 2.6 + rise * 1.4, 0);
                Location capUnderside = cap.clone().subtract(0, 2.2, 0);

                w.spawnParticle(Particle.LARGE_SMOKE, lowerStem, stemParticles, stemRadius * 0.92, 2.4 * thickness, stemRadius * 0.92, 0.04 + fade * 0.02);
                w.spawnParticle(Particle.CLOUD, lowerStem, Math.max(4, stemParticles / 3), stemRadius * 0.85, 1.15 * thickness, stemRadius * 0.85, 0.05 + fade * 0.04);
                w.spawnParticle(Particle.DUST, midStem, Math.max(7, stemParticles / 2), stemRadius * 0.9, 3.7 * thickness, stemRadius * 0.9, 0, darkSmoke);
                w.spawnParticle(Particle.DUST, midStem, Math.max(4, stemParticles / 4), stemRadius * 0.5, 2.3 * thickness, stemRadius * 0.5, 0, ember);
                w.spawnParticle(Particle.DUST, upperStem, Math.max(4, stemParticles / 5), stemRadius * 0.75, 1.8, stemRadius * 0.75, 0, hotCore);

                w.spawnParticle(Particle.LARGE_SMOKE, cap, capParticles, capRadius, 3.4 * thickness, capRadius, 0.035 + fade * 0.04);
                w.spawnParticle(Particle.CLOUD, cap, Math.max(12, capParticles / 2), capRadius * 0.96, 1.9 * thickness, capRadius * 0.96, 0.06 + fade * 0.10);
                w.spawnParticle(Particle.DUST, capTop, Math.max(8, capParticles / 3), capRadius * 0.82, 1.0, capRadius * 0.82, 0, ash);
                w.spawnParticle(Particle.DUST, cap, Math.max(5, capParticles / 5), capRadius * 0.56, 0.9, capRadius * 0.56, 0, darkSmoke);
                w.spawnParticle(Particle.DUST, capUnderside, undersideParticles, capRadius * 0.82, 0.55, capRadius * 0.82, 0, orangeUnderbelly);

                for (int i = 0; i < 2; i++) {
                    double ringY = capHeight - 1.4 + i * 1.05;
                    Location ringCenter = base.clone().add(0, ringY, 0);
                    double ringRadius = outerRingRadius * (0.84 + i * 0.08);
                    w.spawnParticle(Particle.SMOKE, ringCenter, ringParticles, ringRadius, 0.48, ringRadius, 0.015 + fade * 0.025);
                    w.spawnParticle(Particle.CLOUD, ringCenter, Math.max(5, ringParticles / 2), ringRadius * 0.94, 0.28, ringRadius * 0.94, 0.03 + fade * 0.04);
                }

                if (progress < 0.72) {
                    w.spawnParticle(Particle.FLAME, base.clone().add(0, 2.0 + rise * 4.4, 0), scaledParticles(18, particleMultiplier), 4.5 + rise * 3.0, 1.3, 4.5 + rise * 3.0, 0.06);
                    w.spawnParticle(Particle.DUST, capUnderside, scaledParticles(34, particleMultiplier), capRadius * 0.66, 0.85, capRadius * 0.66, 0, ember);
                    w.spawnParticle(Particle.DUST, base.clone().add(0, 2.2 + rise * 2.1, 0), scaledParticles(10, particleMultiplier), 2.5 + rise * 1.1, 0.75, 2.5 + rise * 1.1, 0, hotCore);
                }

                if (progress < 0.36) {
                    double shockRadius = 7.0 + progress * maxRadius * 1.25;
                    w.spawnParticle(Particle.CLOUD, base.clone().add(0, 0.35, 0), scaledParticles(12, particleMultiplier), shockRadius * 0.78, 0.12, shockRadius * 0.78, 0.08);
                    w.spawnParticle(Particle.LARGE_SMOKE, base.clone().add(0, 0.75, 0), scaledParticles(8, particleMultiplier), shockRadius * 0.56, 0.22, shockRadius * 0.56, 0.02);
                }

                if (progress > 0.18 && age % (periodTicks * 2) == 0) {
                    w.spawnParticle(Particle.SMOKE, cap.clone().subtract(0, 4.5, 0), scaledParticles(14, particleMultiplier), capRadius * 0.95, 2.2, capRadius * 0.95, 0.018);
                    w.spawnParticle(Particle.DUST, cap.clone().subtract(0, 3.7, 0), scaledParticles(8, particleMultiplier), capRadius * 0.75, 1.8, capRadius * 0.75, 0, ash);
                }

                if (age <= soundDurationTicks && age % soundIntervalTicks == 0) {
                    w.spawnParticle(Particle.EXPLOSION, base.clone().add(0, 2.6 + rise * 3.3, 0), scaledParticles(6, particleMultiplier), 6.5 + rise * 5.8, 1.5, 6.5 + rise * 5.8, 0.0);
                    w.playSound(base, Sound.ENTITY_GENERIC_EXPLODE, 1.45f, 0.22f + (float) progress * 0.24f);
                }

                age += periodTicks;
            }
        }, 0L, periodTicks);
    }

    private void fireChestBeam(Player shooter, UUID uuid, UUID token) {
        int durationTicks = plugin.getConfig().getInt("abilities.the_veteran.beam_duration_ticks", 100);
        final int periodTicks = Math.max(1, plugin.getConfig().getInt("abilities.the_veteran.beam_period_ticks", 2));
        final double range = plugin.getConfig().getDouble("abilities.the_veteran.beam_range", 48.0);
        final double radius = plugin.getConfig().getDouble("abilities.the_veteran.beam_radius", 1.35);
        double particleStep = Math.max(0.35, plugin.getConfig().getDouble("abilities.the_veteran.beam_particle_step", 0.65));
        double particleDensity = Math.max(0.25, plugin.getConfig().getDouble("abilities.the_veteran.beam_particle_density_multiplier", 0.70));
        double particleStartDistance = Math.max(0.4, plugin.getConfig().getDouble("abilities.the_veteran.beam_particle_start_distance", 1.8));

        double patriotDamage = plugin.getConfig().getDouble("abilities.the_patriot.v_one.heat_vision_damage_hearts", 4.725) * 2.0;
        double damageMultiplier = plugin.getConfig().getDouble("abilities.the_veteran.beam_damage_multiplier", 5.0);
        double configuredDamage = plugin.getConfig().getDouble("abilities.the_veteran.beam_damage_amount", patriotDamage * damageMultiplier);
        double pveDamageMultiplier = Math.max(0.0, plugin.getConfig().getDouble("abilities.the_veteran.pve_damage_multiplier", 0.9));
        final double entityDamage = configuredDamage
                * Math.max(0.0, plugin.getConfig().getDouble("abilities.the_veteran.beam_entity_damage_multiplier", 1.045))
                * pveDamageMultiplier;
        final double playerDamage = 0.0;
        final double fullDamageRange = plugin.getConfig().getDouble("abilities.the_veteran.beam_full_damage_range", 7.0);
        final double farDamageMultiplier = plugin.getConfig().getDouble("abilities.the_veteran.beam_far_damage_multiplier", 0.5);

        final int damageEveryTicks = Math.max(1, plugin.getConfig().getInt("abilities.the_veteran.beam_damage_interval_ticks", 2));
        final boolean breakBlocks = plugin.getConfig().getBoolean("abilities.the_veteran.beam_break_blocks", true);
        final boolean igniteBlocks = plugin.getConfig().getBoolean("abilities.the_veteran.beam_ignite_blocks", true);
        final int blockAffectEveryTicks = Math.max(1, plugin.getConfig().getInt("abilities.the_veteran.beam_block_affect_interval_ticks", 4));
        int maxBlocksPerPulse = Math.max(1, plugin.getConfig().getInt("abilities.the_veteran.beam_max_blocks_per_pulse", 5));
        int blockHitsToBreak = Math.max(1, plugin.getConfig().getInt("abilities.the_veteran.beam_block_hits_to_break", 5));
        double blockDamageMultiplier = Math.max(0.1, plugin.getConfig().getDouble("abilities.the_veteran.beam_block_damage_multiplier", 1.21));
        boolean surfaceOnly = plugin.getConfig().getBoolean("abilities.the_veteran.beam_surface_only", true);

        final TaskHandle[] task = new TaskHandle[1];
        task[0] = SchedulerAdapter.runTimer(plugin, new Runnable() {
            int age = 0;
            final Map<Block, Integer> weakenedBlocks = new HashMap<>();
            final Map<UUID, Double> playerDamageTaken = new HashMap<>();
            double blockBudgetCarry = 0.0;

            @Override public void run() {
                if (!shooter.isOnline() || !isCurrentBurst(uuid, token)) {
                    if (task[0] != null) task[0].cancel();
                    return;
                }
                if (age >= durationTicks) {
                    if (isCurrentBurst(uuid, token)) {
                        activeBurstTokens.remove(uuid);
                    }
                    beamTasks.remove(uuid);
                    int cloudDelayTicks = Math.max(20, plugin.getConfig().getInt("abilities.the_veteran.mushroom_cloud_delay_after_beam_ticks", 30));
                    Location cloudBase = shooter.getLocation().clone();
                    SchedulerAdapter.runLater(plugin, () -> animateAtomicMushroom(cloudBase), cloudDelayTicks);
                    if (task[0] != null) task[0].cancel();
                    return;
                }

                Location chest = shooter.getLocation().add(0, 0.95, 0);
                Vector dir = shooter.getEyeLocation().getDirection().normalize();
                boolean affectBlocksThisTick = age % blockAffectEveryTicks == 0;
                int effectiveMaxBlocksPerPulse = maxBlocksPerPulse;
                if (affectBlocksThisTick) {
                    double scaledBudget = (maxBlocksPerPulse * blockDamageMultiplier) + blockBudgetCarry;
                    effectiveMaxBlocksPerPulse = Math.max(1, (int) Math.floor(scaledBudget));
                    blockBudgetCarry = scaledBudget - effectiveMaxBlocksPerPulse;
                }

                renderAndApplyBeam(shooter, chest, dir, range, radius, particleStep, particleDensity, particleStartDistance,
                        entityDamage, playerDamage, fullDamageRange, farDamageMultiplier,
                        age % damageEveryTicks == 0,
                        affectBlocksThisTick,
                        breakBlocks, igniteBlocks, effectiveMaxBlocksPerPulse, blockHitsToBreak,
                        surfaceOnly, weakenedBlocks, playerDamageTaken);

                int soundDurationTicks = plugin.getConfig().getInt("abilities.the_veteran.beam_sound_duration_ticks", 40);
                int soundPeriodTicks = Math.max(1, plugin.getConfig().getInt("abilities.the_veteran.beam_sound_period_ticks", 20));
                if (age <= soundDurationTicks && age % soundPeriodTicks == 0) {
                    shooter.getWorld().playSound(chest, Sound.ITEM_FIRECHARGE_USE, 0.8f, 0.7f);
                    shooter.getWorld().playSound(chest, Sound.BLOCK_BEACON_AMBIENT, 0.35f, 0.55f);
                }

                age += periodTicks;
            }
        }, 0L, periodTicks);
        beamTasks.put(uuid, task[0]);
    }

    private void renderAndApplyBeam(Player shooter, Location origin, Vector dir,
                                    double range, double radius, double particleStep, double particleDensity, double particleStartDistance,
                                    double entityDamage, double playerDamage, double fullDamageRange, double farDamageMultiplier,
                                    boolean damageThisTick, boolean affectBlocksThisTick,
                                    boolean breakBlocks, boolean igniteBlocks,
                                    int maxBlocksPerPulse, int blockHitsToBreak,
                                    boolean surfaceOnly, Map<Block, Integer> weakenedBlocks,
                                    Map<UUID, Double> playerDamageTaken) {
        World w = shooter.getWorld();
        Particle.DustOptions yellow = new Particle.DustOptions(Color.fromRGB(255, 220, 35), 2.4f);
        Particle.DustOptions orange = new Particle.DustOptions(Color.fromRGB(255, 120, 0), 1.8f);
        Particle.DustOptions whiteHot = new Particle.DustOptions(Color.fromRGB(255, 245, 170), 1.2f);

        RayTraceResult blockHit = w.rayTraceBlocks(origin, dir, range, FluidCollisionMode.NEVER, true);
        double effectiveRange = range;
        if (blockHit != null && blockHit.getHitPosition() != null) {
            effectiveRange = Math.min(range, blockHit.getHitPosition().distance(origin.toVector()) + radius * 1.5);
        }

        Vector right = dir.clone().crossProduct(new Vector(0, 1, 0));
        if (right.lengthSquared() < 0.001) right = new Vector(1, 0, 0);
        right.normalize();
        Vector up = right.clone().crossProduct(dir).normalize();

        int[] blockBudget = new int[] { maxBlocksPerPulse };

        for (double d = particleStartDistance; d <= effectiveRange; d += particleStep) {
            Location center = origin.clone().add(dir.clone().multiply(d));
            w.spawnParticle(Particle.DUST, center, scaledParticles(3, particleDensity), 0.09, 0.09, 0.09, 0, yellow);
            w.spawnParticle(Particle.FLAME, center, scaledParticles(1, particleDensity), 0.10, 0.10, 0.10, 0.006);
            if (d % 1.95 < particleStep) {
                w.spawnParticle(Particle.DUST, center.clone().add(right.clone().multiply(radius * 0.38)), scaledParticles(1, particleDensity), 0.06, 0.06, 0.06, 0, orange);
                w.spawnParticle(Particle.DUST, center.clone().subtract(right.clone().multiply(radius * 0.38)), scaledParticles(1, particleDensity), 0.06, 0.06, 0.06, 0, orange);
                w.spawnParticle(Particle.DUST, center.clone().subtract(up.clone().multiply(radius * 0.22)), scaledParticles(1, particleDensity), 0.05, 0.05, 0.05, 0, whiteHot);
            }

            if (affectBlocksThisTick && (breakBlocks || igniteBlocks) && blockBudget[0] > 0) {
                affectBlocksAround(center, radius, breakBlocks, igniteBlocks, surfaceOnly,
                        blockHitsToBreak, blockBudget, weakenedBlocks);
            }
        }

        if (!damageThisTick) return;
        for (Entity entity : w.getNearbyEntities(origin.clone().add(dir.clone().multiply(effectiveRange / 2.0)), effectiveRange / 2.0 + radius, effectiveRange / 2.0 + radius, effectiveRange / 2.0 + radius)) {
            if (!(entity instanceof LivingEntity target) || entity.equals(shooter)) continue;
            if (!isNearBeam(origin, dir, effectiveRange, radius + 0.65, target.getEyeLocation())) continue;
            double along = distanceAlongBeam(origin, dir, target.getEyeLocation());
            double baseDamage = target instanceof Player ? playerDamage : entityDamage;
            double targetDamage = along <= fullDamageRange ? baseDamage : baseDamage * farDamageMultiplier;
            if (target instanceof Player playerTarget) {
                double capFraction = Math.max(0.0, plugin.getConfig().getDouble("abilities.the_veteran.beam_player_damage_cap_fraction", 0.20));
                targetDamage = capPlayerDamage(playerTarget, targetDamage, capFraction, playerDamageTaken);
            }
            if (targetDamage > 0.0) {
                target.damage(targetDamage, shooter);
            }
            if (!(target instanceof Player)) target.setFireTicks(160);
            double hitKnockback = plugin.getConfig().getDouble("abilities.the_veteran.beam_hit_knockback", 0.025);
            double hitVerticalKnockback = plugin.getConfig().getDouble("abilities.the_veteran.beam_hit_vertical_knockback", 0.0);
            double maxHitHorizontalVelocity = plugin.getConfig().getDouble("abilities.the_veteran.beam_hit_max_horizontal_velocity", 0.16);
            double maxHitVerticalVelocity = plugin.getConfig().getDouble("abilities.the_veteran.beam_hit_max_vertical_velocity", 0.02);
            if (hitKnockback > 0 || hitVerticalKnockback > 0) {
                Vector push = dir.clone().normalize().multiply(Math.max(0.0, hitKnockback));
                push.setY(Math.max(0.0, hitVerticalKnockback));
                target.setVelocity(limitVeteranKnockback(target.getVelocity().add(push), maxHitHorizontalVelocity, maxHitVerticalVelocity));
            }
            w.spawnParticle(Particle.FLAME, target.getLocation().add(0, 1, 0), 22, 0.4, 0.5, 0.4, 0.06);

            if (target instanceof Player playerTarget && plugin.getConfig().getBoolean("abilities.the_veteran.beam_removes_player_abilities", true)) {
                if (plugin.getAbilityManager().hasAbility(playerTarget)) {
                    plugin.getAbilityManager().removeAndForget(playerTarget);
                    playerTarget.sendMessage(plugin.getLocaleManager().msg(
                            "veteran.hit_by", "source", shooter.getName()));
                }
            }
        }
    }



    private Vector limitVeteranKnockback(Vector velocity, double maxHorizontal, double maxUpward) {
        Vector limited = velocity.clone();
        double horizontalLength = Math.sqrt(limited.getX() * limited.getX() + limited.getZ() * limited.getZ());
        double horizontalCap = Math.max(0.0, maxHorizontal);
        if (horizontalCap == 0.0) {
            limited.setX(0.0);
            limited.setZ(0.0);
        } else if (horizontalLength > horizontalCap) {
            double scale = horizontalCap / horizontalLength;
            limited.setX(limited.getX() * scale);
            limited.setZ(limited.getZ() * scale);
        }

        double upwardCap = Math.max(0.0, maxUpward);
        if (limited.getY() > upwardCap) {
            limited.setY(upwardCap);
        }
        return limited;
    }

    private double capPlayerDamage(Player target, double requestedDamage, double capFraction) {
        if (requestedDamage <= 0.0 || capFraction <= 0.0) return 0.0;
        return Math.min(requestedDamage, maxHealth(target) * capFraction);
    }

    private double capPlayerDamage(Player target, double requestedDamage, double capFraction,
                                   Map<UUID, Double> damageTaken) {
        if (requestedDamage <= 0.0 || capFraction <= 0.0) return 0.0;

        UUID targetId = target.getUniqueId();
        double cap = maxHealth(target) * capFraction;
        double alreadyTaken = damageTaken.getOrDefault(targetId, 0.0);
        double remaining = Math.max(0.0, cap - alreadyTaken);
        double applied = Math.min(requestedDamage, remaining);
        if (applied > 0.0) {
            damageTaken.put(targetId, alreadyTaken + applied);
        }
        return applied;
    }

    private double maxHealth(LivingEntity target) {
        Attribute attribute = resolveAttribute("MAX_HEALTH", "GENERIC_MAX_HEALTH");
        if (attribute != null) {
            AttributeInstance instance = target.getAttribute(attribute);
            if (instance != null) {
                return Math.max(1.0, instance.getValue());
            }
        }
        return 20.0;
    }

    private Attribute resolveAttribute(String... names) {
        for (String name : names) {
            try {
                return Attribute.valueOf(name);
            } catch (IllegalArgumentException ignored) {
                // Try the legacy/modern fallback name.
            }
        }
        return null;
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

    private void affectBlocksAround(Location center, double radius, boolean breakBlocks,
                                    boolean igniteBlocks, boolean surfaceOnly, int blockHitsToBreak,
                                    int[] blockBudget, Map<Block, Integer> weakenedBlocks) {
        int r = Math.max(1, (int) Math.ceil(radius));
        for (int x = -r; x <= r && blockBudget[0] > 0; x++) {
            for (int y = -r; y <= r && blockBudget[0] > 0; y++) {
                for (int z = -r; z <= r && blockBudget[0] > 0; z++) {
                    Location loc = center.clone().add(x, y, z);
                    if (loc.distanceSquared(center) > radius * radius) continue;
                    Block block = loc.getBlock();
                    Material type = block.getType();

                    if (breakBlocks && !type.isAir() && isBreakableByVeteranBeam(type)) {
                        if (surfaceOnly && !isSurfaceBlock(block)) continue;

                        int hits = weakenedBlocks.merge(block, 1, Integer::sum);
                        block.getWorld().spawnParticle(Particle.LARGE_SMOKE, block.getLocation().add(0.5, 0.5, 0.5), 3, 0.2, 0.2, 0.2, 0.02);
                        block.getWorld().spawnParticle(Particle.FLAME, block.getLocation().add(0.5, 0.5, 0.5), 2, 0.16, 0.16, 0.16, 0.02);
                        if (hits >= blockHitsToBreak) {
                            block.getWorld().playSound(block.getLocation().add(0.5, 0.5, 0.5), Sound.BLOCK_FIRE_EXTINGUISH, 0.55f, 1.7f);
                            block.setType(Material.AIR, false);
                            weakenedBlocks.remove(block);
                            blockBudget[0]--;
                        }
                        continue;
                    }

                    if (igniteBlocks && type.isAir() && block.getRelative(0, -1, 0).getType().isSolid()) {
                        block.setType(Material.FIRE, false);
                        blockBudget[0]--;
                    }
                }
            }
        }
    }

    private boolean isSurfaceBlock(Block block) {
        return isOpen(block.getRelative(1, 0, 0).getType())
                || isOpen(block.getRelative(-1, 0, 0).getType())
                || isOpen(block.getRelative(0, 1, 0).getType())
                || isOpen(block.getRelative(0, -1, 0).getType())
                || isOpen(block.getRelative(0, 0, 1).getType())
                || isOpen(block.getRelative(0, 0, -1).getType());
    }

    private boolean isOpen(Material type) {
        return type.isAir() || !type.isSolid() || type == Material.FIRE;
    }

    private boolean isBreakableByVeteranBeam(Material type) {
        String name = type.name();
        return !name.equals("BEDROCK")
                && !name.equals("BARRIER")
                && !name.equals("COMMAND_BLOCK")
                && !name.equals("CHAIN_COMMAND_BLOCK")
                && !name.equals("REPEATING_COMMAND_BLOCK")
                && !name.equals("STRUCTURE_BLOCK")
                && !name.equals("JIGSAW")
                && !name.equals("END_PORTAL")
                && !name.equals("END_PORTAL_FRAME")
                && !name.equals("NETHER_PORTAL")
                && !name.equals("VAULT")
                && !name.equals("TRIAL_SPAWNER");
    }

    private int scaledParticles(int baseCount, double multiplier) {
        return Math.max(1, (int) Math.round(baseCount * multiplier));
    }

    private int scaledParticles(int minimumCount, double baseCount, double multiplier) {
        int scaledMinimum = Math.max(1, (int) Math.round(minimumCount * multiplier));
        int scaledBase = Math.max(1, (int) Math.round(baseCount * multiplier));
        return Math.max(scaledMinimum, scaledBase);
    }

    private void setMaxHealthBonus(Player p, double amount) {
        AttributeUtil.setMaxHealthBonus(p, healthModKey, amount);
    }

}
