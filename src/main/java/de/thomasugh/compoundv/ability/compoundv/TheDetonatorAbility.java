package de.thomasugh.compoundv.ability.compoundv;

import de.thomasugh.compoundv.CompoundV;
import de.thomasugh.compoundv.ability.Ability;
import de.thomasugh.compoundv.server.SchedulerAdapter;
import de.thomasugh.compoundv.server.TaskHandle;
import de.thomasugh.compoundv.util.MessageUtil;
import de.thomasugh.compoundv.util.PotionEffects;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.util.Vector;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class TheDetonatorAbility implements Ability {

    private final CompoundV plugin;
    private final Map<UUID, Long> explosionCooldownUntil = new HashMap<>();
    private final Map<UUID, Long> selfExplosionProtectionUntil = new HashMap<>();
    private final Map<UUID, Long> lastHandledAt = new HashMap<>();
    private final Map<UUID, Integer> meleeCounter = new HashMap<>();
    private final Map<UUID, UUID> activeChargeTokens = new HashMap<>();
    private final Map<UUID, TaskHandle> chargeTasks = new HashMap<>();

    public TheDetonatorAbility(CompoundV plugin) {
        this.plugin = plugin;
    }

    @Override public String getId() { return "the_detonator"; }
    @Override public String getDisplayName() { return "The Detonator"; }
    @Override public int getColor() { return 0xFF3B1F; }

    @Override
    public void apply(Player player) {
        int strength = plugin.getConfig().getInt("abilities.the_detonator.strength_level", 2);
        int resistance = plugin.getConfig().getInt("abilities.the_detonator.resistance_level", 1);
        player.addPotionEffect(new PotionEffect(PotionEffects.STRENGTH, Integer.MAX_VALUE, Math.max(0, strength - 1), false, false, true));
        player.addPotionEffect(new PotionEffect(PotionEffects.RESISTANCE, Integer.MAX_VALUE, Math.max(0, resistance - 1), false, false, true));
        player.addPotionEffect(new PotionEffect(PotionEffects.FIRE_RESISTANCE, Integer.MAX_VALUE, 0, false, false, true));
    }

    @Override
    public void remove(Player player) {
        UUID uuid = player.getUniqueId();
        cancelCharge(uuid);
        explosionCooldownUntil.remove(uuid);
        selfExplosionProtectionUntil.remove(uuid);
        lastHandledAt.remove(uuid);
        meleeCounter.remove(uuid);
        player.removePotionEffect(PotionEffects.STRENGTH);
        player.removePotionEffect(PotionEffects.RESISTANCE);
        player.removePotionEffect(PotionEffects.FIRE_RESISTANCE);
    }

    public void triggerExplosion(Player player) {
        UUID uuid = player.getUniqueId();
        long handledNow = System.currentTimeMillis();
        long lastHandled = lastHandledAt.getOrDefault(uuid, 0L);
        if (handledNow - lastHandled < 350L) return;
        lastHandledAt.put(uuid, handledNow);

        if (activeChargeTokens.containsKey(uuid)) return;

        long now = System.currentTimeMillis();
        long readyAt = explosionCooldownUntil.getOrDefault(uuid, 0L);
        if (readyAt > now) {
            long seconds = Math.max(1L, (long) Math.ceil((readyAt - now) / 1000.0));
            MessageUtil.sendActionBar(player, plugin.getLocaleManager().msg("the_detonator.cooldown", "seconds", Long.toString(seconds)));
            player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 0.45f, 0.65f);
            return;
        }

        startExplosionCharge(player);
    }

    private void startExplosionCharge(Player player) {
        UUID uuid = player.getUniqueId();
        UUID token = UUID.randomUUID();
        activeChargeTokens.put(uuid, token);

        int chargeTicks = Math.max(20, plugin.getConfig().getInt("abilities.the_detonator.explosion_charge_ticks", 60));
        MessageUtil.sendActionBar(player, plugin.getLocaleManager().msg("the_detonator.charge_start"));
        player.getWorld().playSound(player.getLocation(), Sound.BLOCK_BEACON_POWER_SELECT, 0.75f, 0.45f);

        final TaskHandle[] task = new TaskHandle[1];
        task[0] = SchedulerAdapter.runTimer(plugin, new Runnable() {
            int age = 0;
            int lastSecond = -1;

            @Override public void run() {
                if (!player.isOnline() || !token.equals(activeChargeTokens.get(uuid))) {
                    if (task[0] != null) task[0].cancel();
                    return;
                }

                if (!player.isSneaking()) {
                    cancelCharge(uuid);
                    MessageUtil.sendActionBar(player, plugin.getLocaleManager().msg("the_detonator.charge_cancelled"));
                    player.playSound(player.getLocation(), Sound.BLOCK_BEACON_DEACTIVATE, 0.85f, 0.55f);
                    if (task[0] != null) task[0].cancel();
                    return;
                }

                animateCharge(player, age, chargeTicks);
                int remaining = Math.max(1, (int) Math.ceil((chargeTicks - age) / 20.0));
                if (age % 20 == 0 && remaining != lastSecond) {
                    lastSecond = remaining;
                    MessageUtil.sendActionBar(player, plugin.getLocaleManager().msg("the_detonator.charge_progress", "seconds", Integer.toString(remaining)));
                    player.playSound(player.getLocation(), Sound.BLOCK_RESPAWN_ANCHOR_CHARGE, 0.55f, 0.55f + (age / (float) chargeTicks) * 0.35f);
                }

                if (age >= chargeTicks) {
                    activeChargeTokens.remove(uuid);
                    chargeTasks.remove(uuid);
                    executeExplosion(player);
                    if (task[0] != null) task[0].cancel();
                    return;
                }
                age++;
            }
        }, 0L, 1L);
        chargeTasks.put(uuid, task[0]);
    }

    private void cancelCharge(UUID uuid) {
        activeChargeTokens.remove(uuid);
        TaskHandle task = chargeTasks.remove(uuid);
        if (task != null) task.cancel();
    }

    private void animateCharge(Player player, int age, int chargeTicks) {
        Location center = player.getLocation().add(0, 0.85, 0);
        World world = player.getWorld();
        double progress = Math.min(1.0, age / (double) Math.max(1, chargeTicks));
        double radius = 0.65 + progress * 1.45;
        world.spawnParticle(Particle.FLAME, center, 8 + (int) (progress * 18), radius * 0.28, 0.35, radius * 0.28, 0.04 + progress * 0.05);
        world.spawnParticle(Particle.SMOKE, center, 4 + (int) (progress * 12), radius * 0.20, 0.25, radius * 0.20, 0.02);
        if (age % 8 == 0) {
            world.spawnParticle(Particle.LAVA, center, 2, radius * 0.18, 0.16, radius * 0.18, 0.01);
        }
    }

    private void executeExplosion(Player player) {
        UUID uuid = player.getUniqueId();
        long now = System.currentTimeMillis();
        long cooldownMs = plugin.getConfig().getLong("abilities.the_detonator.explosion_cooldown_ms", 10000L);
        explosionCooldownUntil.put(uuid, now + Math.max(0L, cooldownMs));
        selfExplosionProtectionUntil.put(uuid, now + 3500L);
        player.setNoDamageTicks(Math.max(player.getNoDamageTicks(), 70));

        Location center = player.getLocation();
        World world = player.getWorld();
        float power = (float) plugin.getConfig().getDouble("abilities.the_detonator.explosion_power", 10.0);
        boolean blockDamage = plugin.getConfig().getBoolean("abilities.the_detonator.explosion_block_damage", true);
        double radius = plugin.getConfig().getDouble("abilities.the_detonator.explosion_radius", 13.0);
        double damage = plugin.getConfig().getDouble("abilities.the_detonator.explosion_damage_hearts", 12.0) * 2.0;
        double knockback = plugin.getConfig().getDouble("abilities.the_detonator.explosion_knockback", 2.2);

        world.playSound(center, Sound.ENTITY_GENERIC_EXPLODE, 1.0f, 0.62f);
        world.playSound(center, Sound.ENTITY_WITHER_BREAK_BLOCK, 0.75f, 0.72f);
        world.spawnParticle(Particle.EXPLOSION, center.clone().add(0, 0.4, 0), 3, 0.85, 0.25, 0.85, 0.02);
        world.spawnParticle(Particle.FLAME, center.clone().add(0, 1.0, 0), 130, 2.0, 1.1, 2.0, 0.18);
        world.spawnParticle(Particle.LAVA, center.clone().add(0, 0.8, 0), 45, 1.4, 0.65, 1.4, 0.04);
        world.createExplosion(center, power, true, blockDamage, player);

        for (Entity entity : world.getNearbyEntities(center, radius, radius, radius)) {
            if (!(entity instanceof LivingEntity target) || target.equals(player)) continue;
            double distance = Math.max(0.5, target.getLocation().distance(center));
            if (distance > radius) continue;
            double factor = Math.max(0.22, 1.0 - distance / radius);
            target.damage(damage * factor, player);
            target.setFireTicks(Math.max(target.getFireTicks(), plugin.getConfig().getInt("abilities.the_detonator.explosion_fire_ticks", 120)));
            Vector push = target.getLocation().toVector().subtract(center.toVector());
            if (push.lengthSquared() < 0.0001) push = player.getLocation().getDirection().clone();
            target.setVelocity(target.getVelocity().add(push.normalize().multiply(knockback * factor).setY(0.35 + factor * 0.55)));
        }

        MessageUtil.sendActionBar(player, plugin.getLocaleManager().msg("the_detonator.released"));
    }

    public void handleMeleeHit(Player attacker, LivingEntity target) {
        int fireTicks = plugin.getConfig().getInt("abilities.the_detonator.melee_fire_ticks", 60);
        target.setFireTicks(Math.max(target.getFireTicks(), fireTicks));
        target.getWorld().spawnParticle(Particle.FLAME, target.getLocation().add(0, 1.0, 0), 9, 0.22, 0.28, 0.22, 0.03);

        int hitCount = meleeCounter.merge(attacker.getUniqueId(), 1, Integer::sum);
        int interval = Math.max(1, plugin.getConfig().getInt("abilities.the_detonator.melee_explosion_every_hits", 3));
        if (hitCount % interval != 0) return;

        Location center = target.getLocation();
        World world = target.getWorld();
        float power = (float) plugin.getConfig().getDouble("abilities.the_detonator.melee_explosion_power", 1.15);
        double damage = plugin.getConfig().getDouble("abilities.the_detonator.melee_explosion_damage_hearts", 2.0) * 2.0;
        selfExplosionProtectionUntil.put(attacker.getUniqueId(), System.currentTimeMillis() + 1500L);
        attacker.setNoDamageTicks(Math.max(attacker.getNoDamageTicks(), 35));
        world.createExplosion(center, power, true, false, attacker);
        target.damage(Math.max(0.0, damage), attacker);
        world.spawnParticle(Particle.FLAME, center.add(0, 1.0, 0), 22, 0.35, 0.42, 0.35, 0.08);
    }

    public boolean shouldCancelSelfExplosionDamage(Player player) {
        long until = selfExplosionProtectionUntil.getOrDefault(player.getUniqueId(), 0L);
        return until > System.currentTimeMillis();
    }
}
