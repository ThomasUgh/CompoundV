package de.thomasugh.compoundv.ability.compoundv;

import de.thomasugh.compoundv.CompoundV;
import de.thomasugh.compoundv.ability.Ability;
import de.thomasugh.compoundv.server.SchedulerAdapter;
import de.thomasugh.compoundv.util.MessageUtil;
import de.thomasugh.compoundv.util.PotionEffects;
import org.bukkit.Color;
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
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public class ShockwaveAbility implements Ability {

    private final CompoundV plugin;
    private final Map<UUID, Long> cooldownUntil = new HashMap<>();

    public ShockwaveAbility(CompoundV plugin) {
        this.plugin = plugin;
    }

    @Override public String getId() { return "shockwave"; }
    @Override public String getDisplayName() { return "Shockwave"; }
    @Override public int getColor() { return 0xF2C94C; }
    @Override public boolean hasToggle() { return true; }

    @Override
    public void apply(Player player) {
        int strength = plugin.getConfig().getInt("abilities.shockwave.strength_level", 2);
        int resistance = plugin.getConfig().getInt("abilities.shockwave.resistance_level", 1);
        if (strength > 0) {
            player.addPotionEffect(new PotionEffect(PotionEffects.STRENGTH,
                    Integer.MAX_VALUE, Math.max(0, strength - 1), false, false, true));
        }
        if (resistance > 0) {
            player.addPotionEffect(new PotionEffect(PotionEffects.RESISTANCE,
                    Integer.MAX_VALUE, Math.max(0, resistance - 1), false, false, true));
        }
    }

    @Override
    public void remove(Player player) {
        cooldownUntil.remove(player.getUniqueId());
        player.removePotionEffect(PotionEffects.STRENGTH);
        player.removePotionEffect(PotionEffects.RESISTANCE);
    }

    @Override
    public void onToggle(Player player) {
        UUID uuid = player.getUniqueId();
        long now = System.currentTimeMillis();
        long readyAt = cooldownUntil.getOrDefault(uuid, 0L);
        if (readyAt > now) {
            long seconds = Math.max(1L, (long) Math.ceil((readyAt - now) / 1000.0));
            MessageUtil.sendActionBar(player, plugin.getLocaleManager().msg("shockwave.cooldown", "seconds", Long.toString(seconds)));
            return;
        }

        long cooldownMs = plugin.getConfig().getLong("abilities.shockwave.cooldown_ms", 120000L);
        cooldownUntil.put(uuid, now + Math.max(0L, cooldownMs));
        releaseShockwave(player);
    }

    public void handleMeleeHit(Player attacker, LivingEntity target) {
        double knockback = plugin.getConfig().getDouble("abilities.shockwave.melee_knockback", 1.15);
        double vertical = plugin.getConfig().getDouble("abilities.shockwave.melee_vertical_knockback", 0.28);
        Vector push = target.getLocation().toVector().subtract(attacker.getLocation().toVector());
        if (push.lengthSquared() < 0.0001) push = attacker.getLocation().getDirection().clone();
        push.setY(0).normalize().multiply(knockback).setY(vertical);
        target.setVelocity(target.getVelocity().add(push));
        target.getWorld().spawnParticle(Particle.CRIT, target.getLocation().add(0, 1, 0), 8, 0.18, 0.22, 0.18, 0.04);

        if (isCriticalHit(attacker)) {
            releaseMiniShockwave(attacker, target.getLocation());
        }
    }

    private void releaseMiniShockwave(Player attacker, Location center) {
        World world = center.getWorld();
        if (world == null) return;
        double radius = plugin.getConfig().getDouble("abilities.shockwave.crit_radius", 3.0);
        double damage = plugin.getConfig().getDouble("abilities.shockwave.crit_damage_hearts", 1.5) * 2.0;
        double knockback = plugin.getConfig().getDouble("abilities.shockwave.crit_knockback", 1.45);
        world.playSound(center, Sound.ENTITY_GENERIC_EXPLODE, 0.5f, 1.35f);
        world.spawnParticle(Particle.EXPLOSION, center.clone().add(0, 0.6, 0), 1, 0.08, 0.08, 0.08, 0);
        world.spawnParticle(Particle.CLOUD, center.clone().add(0, 0.35, 0), 20, 0.65, 0.10, 0.65, 0.05);
        for (Entity entity : world.getNearbyEntities(center, radius, radius, radius)) {
            if (!(entity instanceof LivingEntity victim) || victim.equals(attacker)) continue;
            double distance = Math.max(0.2, victim.getLocation().distance(center));
            if (distance > radius) continue;
            double factor = Math.max(0.3, 1.0 - (distance / radius));
            victim.damage(damage * factor);
            Vector push = victim.getLocation().toVector().subtract(center.toVector());
            if (push.lengthSquared() < 0.0001) push = attacker.getLocation().getDirection().clone();
            victim.setVelocity(victim.getVelocity().add(push.normalize().multiply(knockback * factor).setY(0.28 + factor * 0.22)));
        }
    }

    private boolean isCriticalHit(Player attacker) {
        return attacker.getFallDistance() > 0.0f
                && !attacker.isOnGround()
                && !attacker.isInsideVehicle()
                && !attacker.isSprinting()
                && !attacker.hasPotionEffect(PotionEffects.BLINDNESS);
    }

    private void releaseShockwave(Player player) {
        Location center = player.getLocation();
        World world = player.getWorld();
        double maxRadius = plugin.getConfig().getDouble("abilities.shockwave.radius", 10.0);
        double step = plugin.getConfig().getDouble("abilities.shockwave.animation_step", 1.0);
        long period = plugin.getConfig().getLong("abilities.shockwave.animation_period_ticks", 2L);
        Set<UUID> hit = new HashSet<>();

        world.playSound(center, Sound.ENTITY_GENERIC_EXPLODE, 1.15f, 0.95f);
        world.playSound(center, Sound.BLOCK_BEACON_POWER_SELECT, 0.9f, 0.75f);
        MessageUtil.sendActionBar(player, plugin.getLocaleManager().msg("shockwave.released"));

        for (int i = 1; i <= Math.ceil(maxRadius / step); i++) {
            final double radius = Math.min(maxRadius, i * step);
            SchedulerAdapter.runLater(plugin, () -> animateAndDamage(player, center, radius, maxRadius, hit), period * i);
        }
    }

    private void animateAndDamage(Player player, Location center, double radius, double maxRadius, Set<UUID> hit) {
        World world = center.getWorld();
        if (world == null) return;

        Particle.DustOptions dust = new Particle.DustOptions(Color.fromRGB(242, 201, 76), 1.15f);
        int points = Math.max(24, (int) Math.round(radius * 14));
        for (int i = 0; i < points; i++) {
            double angle = (Math.PI * 2.0 * i) / points;
            Location point = center.clone().add(Math.cos(angle) * radius, 0.18, Math.sin(angle) * radius);
            world.spawnParticle(Particle.DUST, point, 2, 0.06, 0.04, 0.06, 0, dust);
            if (i % 4 == 0) {
                world.spawnParticle(Particle.CLOUD, point, 1, 0.08, 0.02, 0.08, 0.02);
            }
        }

        double playerDamage = plugin.getConfig().getDouble("abilities.shockwave.damage_hearts", 3.0) * 2.0;
        double pveDamage = plugin.getConfig().getDouble("abilities.shockwave.pve_damage_hearts", 6.0) * 2.0;
        double knockback = plugin.getConfig().getDouble("abilities.shockwave.knockback", 3.25);
        double vertical = plugin.getConfig().getDouble("abilities.shockwave.vertical_knockback", 0.75);
        double band = Math.max(1.5, plugin.getConfig().getDouble("abilities.shockwave.hit_band", 1.65));

        for (Entity entity : world.getNearbyEntities(center, radius + band, radius + band, radius + band)) {
            if (!(entity instanceof LivingEntity target) || entity.equals(player)) continue;
            if (!hit.add(target.getUniqueId())) continue;
            double distance = target.getLocation().distance(center);
            if (distance > maxRadius || Math.abs(distance - radius) > band) {
                hit.remove(target.getUniqueId());
                continue;
            }

            double damage = target instanceof Player ? playerDamage : pveDamage;
            target.damage(damage, player);
            Vector push = target.getLocation().toVector().subtract(center.toVector());
            if (push.lengthSquared() < 0.0001) push = player.getLocation().getDirection().clone();
            double factor = Math.max(0.45, 1.0 - (distance / (maxRadius * 1.35)));
            target.setVelocity(target.getVelocity().add(push.normalize().multiply(knockback * factor).setY(vertical + factor * 0.45)));
            world.spawnParticle(Particle.EXPLOSION, target.getLocation().add(0, 0.9, 0), 1, 0.12, 0.12, 0.12, 0);
        }
    }
}
