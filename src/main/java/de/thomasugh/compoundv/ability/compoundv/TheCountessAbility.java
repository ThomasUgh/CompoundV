package de.thomasugh.compoundv.ability.compoundv;
import de.thomasugh.compoundv.util.AbilityKillTracker;


import de.thomasugh.compoundv.CompoundV;
import de.thomasugh.compoundv.ability.Ability;
import de.thomasugh.compoundv.server.SchedulerAdapter;
import de.thomasugh.compoundv.util.MessageUtil;
import de.thomasugh.compoundv.util.PotionEffects;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Fireball;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.potion.PotionEffect;
import org.bukkit.projectiles.ProjectileSource;
import org.bukkit.util.Vector;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

public class TheCountessAbility implements Ability {

    private final CompoundV plugin;
    private final Map<UUID, Long> fireballCooldown = new HashMap<>();
    private final Map<UUID, UUID> fireballOwners = new HashMap<>();
    private final Set<UUID> airJumpUsed = new HashSet<>();

    public TheCountessAbility(CompoundV plugin) {
        this.plugin = plugin;
    }

    @Override public String getId() { return "the_countess"; }
    @Override public String getDisplayName() { return "The Countess"; }
    @Override public int getColor() { return 0xFF4A2D; }
    @Override public boolean hasToggle() { return true; }
    @Override public boolean needsTick() { return true; }

    @Override
    public void apply(Player player) {
        int strength = plugin.getConfig().getInt("abilities.the_countess.strength_level", 2);
        int resistance = plugin.getConfig().getInt("abilities.the_countess.resistance_level", 1);
        player.addPotionEffect(new PotionEffect(PotionEffects.FIRE_RESISTANCE, Integer.MAX_VALUE, 0, false, false, true));
        player.addPotionEffect(new PotionEffect(PotionEffects.STRENGTH, Integer.MAX_VALUE, Math.max(0, strength - 1), false, false, true));
        player.addPotionEffect(new PotionEffect(PotionEffects.RESISTANCE, Integer.MAX_VALUE, Math.max(0, resistance - 1), false, false, true));
        if (player.getGameMode() != GameMode.CREATIVE && player.getGameMode() != GameMode.SPECTATOR) {
            player.setAllowFlight(true);
        }
    }

    @Override
    public void remove(Player player) {
        fireballCooldown.remove(player.getUniqueId());
        airJumpUsed.remove(player.getUniqueId());
        player.removePotionEffect(PotionEffects.FIRE_RESISTANCE);
        player.removePotionEffect(PotionEffects.STRENGTH);
        player.removePotionEffect(PotionEffects.RESISTANCE);
        if (player.getGameMode() != GameMode.CREATIVE && player.getGameMode() != GameMode.SPECTATOR) {
            player.setFlying(false);
            player.setAllowFlight(false);
        }
    }

    @Override
    public void onTick(Player player) {
        if (player.isOnGround()) {
            airJumpUsed.remove(player.getUniqueId());
            if (player.getGameMode() != GameMode.CREATIVE && player.getGameMode() != GameMode.SPECTATOR) {
                player.setAllowFlight(true);
            }
        }
    }

    @Override
    public void onToggle(Player player) {
        UUID uuid = player.getUniqueId();
        long now = System.currentTimeMillis();
        long readyAt = fireballCooldown.getOrDefault(uuid, 0L);
        if (readyAt > now) {
            long seconds = Math.max(1L, (long) Math.ceil((readyAt - now) / 1000.0));
            MessageUtil.sendActionBar(player, plugin.getLocaleManager().msg("the_countess.fireball_cooldown", "seconds", Long.toString(seconds)));
            return;
        }
        fireballCooldown.put(uuid, now + plugin.getConfig().getLong("abilities.the_countess.fireball_cooldown_ms", 5000L));
        launchFireball(player);
    }

    public void handleMeleeHit(Player attacker, LivingEntity target) {
        int fireTicks = plugin.getConfig().getInt("abilities.the_countess.melee_fire_ticks", 60);
        target.setFireTicks(Math.max(target.getFireTicks(), fireTicks));
        target.getWorld().spawnParticle(Particle.FLAME, target.getLocation().add(0, 1, 0), 8, 0.20, 0.28, 0.20, 0.025);
    }

    public void tryDoubleJump(Player player) {
        if (player.getGameMode() == GameMode.CREATIVE || player.getGameMode() == GameMode.SPECTATOR) return;
        UUID uuid = player.getUniqueId();
        if (!airJumpUsed.add(uuid)) return;
        player.setAllowFlight(false);
        player.setFlying(false);
        Vector direction = player.getLocation().getDirection().multiply(0.25);
        double vertical = plugin.getConfig().getDouble("abilities.the_countess.double_jump_velocity", 0.85);
        player.setVelocity(new Vector(direction.getX(), vertical, direction.getZ()));
        Location loc = player.getLocation();
        player.getWorld().spawnParticle(Particle.FLAME, loc, 28, 0.45, 0.18, 0.45, 0.05);
        player.getWorld().spawnParticle(Particle.SMOKE, loc, 18, 0.42, 0.15, 0.42, 0.03);
        player.playSound(loc, Sound.ITEM_FIRECHARGE_USE, 0.7f, 1.5f);
    }

    public boolean isOwnedFireball(UUID projectileId) {
        return fireballOwners.containsKey(projectileId);
    }

    public void handleProjectileHit(ProjectileHitEvent event) {
        UUID projectileId = event.getEntity().getUniqueId();
        UUID ownerId = fireballOwners.remove(projectileId);
        if (ownerId == null) return;
        Player owner = plugin.getServer().getPlayer(ownerId);
        if (owner == null) return;

        Location impact = event.getEntity().getLocation();
        World world = impact.getWorld();
        if (world == null) return;
        event.getEntity().remove();

        double radius = plugin.getConfig().getDouble("abilities.the_countess.fireball_damage_radius", 2.6);
        double minHearts = plugin.getConfig().getDouble("abilities.the_countess.fireball_damage_min_hearts", 2.0);
        double maxHearts = plugin.getConfig().getDouble("abilities.the_countess.fireball_damage_max_hearts", 4.0);
        if (maxHearts < minHearts) maxHearts = minHearts;
        double damage = ThreadLocalRandom.current().nextDouble(minHearts, maxHearts + 0.0001) * 2.0;
        world.spawnParticle(Particle.EXPLOSION, impact, 2, 0.3, 0.3, 0.3, 0);
        world.playSound(impact, Sound.ENTITY_GENERIC_EXPLODE, 1.0f, 1.2f);
        for (Entity entity : world.getNearbyEntities(impact, radius, radius, radius)) {
            if (!(entity instanceof LivingEntity target) || target.equals(owner)) continue;
            double distance = Math.max(0.2, target.getLocation().distance(impact));
            if (distance > radius) continue;
            double factor = Math.max(0.35, 1.0 - (distance / radius));
            if (!AbilityKillTracker.damage(plugin, target, owner, damage * factor, "death_messages.the_countess", true)) continue;
            target.setFireTicks(Math.max(target.getFireTicks(), 80));
            Vector push = target.getLocation().toVector().subtract(impact.toVector());
            if (push.lengthSquared() < 0.0001) push = owner.getLocation().getDirection().clone();
            target.setVelocity(target.getVelocity().add(push.normalize().multiply(0.6 * factor).setY(0.3 * factor)));
        }
        world.spawnParticle(Particle.FLAME, impact, 45, 0.75, 0.45, 0.75, 0.08);
    }

    private void launchFireball(Player player) {
        Fireball fireball = player.launchProjectile(Fireball.class);
        fireball.setVelocity(player.getEyeLocation().getDirection().normalize().multiply(
                plugin.getConfig().getDouble("abilities.the_countess.fireball_speed", 1.15)));
        fireball.setYield(0.0f);
        fireball.setIsIncendiary(false);
        fireballOwners.put(fireball.getUniqueId(), player.getUniqueId());
        player.getWorld().playSound(player.getLocation(), Sound.ITEM_FIRECHARGE_USE, 0.8f, 1.2f);
        player.getWorld().spawnParticle(Particle.FLAME, player.getEyeLocation(), 18, 0.18, 0.18, 0.18, 0.04);
    }

    public void cleanupProjectile(UUID projectileId) {
        fireballOwners.remove(projectileId);
    }
}
