package de.thomasugh.compoundv.ability.compoundv;

import de.thomasugh.compoundv.CompoundV;
import de.thomasugh.compoundv.ability.Ability;
import de.thomasugh.compoundv.util.AttributeUtil;
import de.thomasugh.compoundv.util.PotionEffects;
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
import org.bukkit.util.Vector;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

public class TheWarriorAbility implements Ability {

    private final CompoundV plugin;
    private final NamespacedKey healthKey;
    private final Set<UUID> airJumpUsed = new HashSet<>();

    public TheWarriorAbility(CompoundV plugin) {
        this.plugin = plugin;
        this.healthKey = new NamespacedKey(plugin, "the_warrior_hearts");
    }

    @Override public String getId() { return "the_warrior"; }
    @Override public String getDisplayName() { return "The Warrior"; }
    @Override public int getColor() { return 0xC9A84E; }
    @Override public boolean needsTick() { return true; }

    @Override
    public void apply(Player player) {
        int strength = plugin.getConfig().getInt("abilities.the_warrior.strength_level", 3);
        int resistance = plugin.getConfig().getInt("abilities.the_warrior.resistance_level", 2);
        int regeneration = plugin.getConfig().getInt("abilities.the_warrior.regeneration_level", 1);
        player.addPotionEffect(new PotionEffect(PotionEffects.STRENGTH, Integer.MAX_VALUE, Math.max(0, strength - 1), false, false, true));
        player.addPotionEffect(new PotionEffect(PotionEffects.RESISTANCE, Integer.MAX_VALUE, Math.max(0, resistance - 1), false, false, true));
        player.addPotionEffect(new PotionEffect(PotionEffects.REGENERATION, Integer.MAX_VALUE, Math.max(0, regeneration - 1), false, false, true));
        double extraHearts = plugin.getConfig().getDouble("abilities.the_warrior.extra_hearts", 10.0);
        AttributeUtil.setMaxHealthBonus(player, healthKey, extraHearts * 2.0);
        if (player.getGameMode() != GameMode.CREATIVE && player.getGameMode() != GameMode.SPECTATOR) {
            player.setAllowFlight(true);
        }
    }

    @Override
    public void remove(Player player) {
        airJumpUsed.remove(player.getUniqueId());
        AttributeUtil.setMaxHealthBonus(player, healthKey, 0.0);
        player.removePotionEffect(PotionEffects.STRENGTH);
        player.removePotionEffect(PotionEffects.RESISTANCE);
        player.removePotionEffect(PotionEffects.REGENERATION);
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

    public void tryDoubleJump(Player player) {
        if (player.getGameMode() == GameMode.CREATIVE || player.getGameMode() == GameMode.SPECTATOR) return;
        UUID uuid = player.getUniqueId();
        if (!airJumpUsed.add(uuid)) return;
        player.setFlying(false);
        player.setAllowFlight(false);
        Vector look = player.getLocation().getDirection().multiply(0.18);
        double min = plugin.getConfig().getDouble("abilities.the_warrior.double_jump_min_velocity", 1.25);
        double max = plugin.getConfig().getDouble("abilities.the_warrior.double_jump_max_velocity", 1.95);
        double vertical = Math.max(min, Math.min(max, min + Math.max(0.0, -player.getLocation().getPitch()) / 90.0 * (max - min)));
        player.setVelocity(new Vector(look.getX(), vertical, look.getZ()));
        Location loc = player.getLocation();
        player.getWorld().spawnParticle(Particle.CLOUD, loc, 28, 0.45, 0.15, 0.45, 0.08);
        player.getWorld().spawnParticle(Particle.CRIT, loc.add(0, 0.4, 0), 18, 0.45, 0.35, 0.45, 0.05);
        player.playSound(player.getLocation(), Sound.ENTITY_IRON_GOLEM_ATTACK, 0.65f, 1.45f);
    }

    public void triggerFallImpact(Player player, double fallenBlocks) {
        double minFall = plugin.getConfig().getDouble("abilities.the_warrior.fall_impact_min_blocks", 6.0);
        if (fallenBlocks < minFall) return;
        Location center = player.getLocation();
        World world = player.getWorld();
        float power = (float) plugin.getConfig().getDouble("abilities.the_warrior.fall_impact_power", 0.75);
        boolean blockDamage = plugin.getConfig().getBoolean("abilities.the_warrior.fall_impact_block_damage", true);
        if (power > 0) world.createExplosion(center, power, false, blockDamage, player);

        double radius = plugin.getConfig().getDouble("abilities.the_warrior.fall_impact_radius", 3.0);
        double damage = plugin.getConfig().getDouble("abilities.the_warrior.fall_impact_damage_hearts", 2.0) * 2.0;
        double knockback = plugin.getConfig().getDouble("abilities.the_warrior.fall_impact_knockback", 1.3);
        for (Entity entity : world.getNearbyEntities(center, radius, radius, radius)) {
            if (!(entity instanceof LivingEntity target) || target.equals(player)) continue;
            double distance = Math.max(0.2, target.getLocation().distance(center));
            if (distance > radius) continue;
            double factor = Math.max(0.25, 1.0 - (distance / radius));
            target.damage(damage * factor, player);
            Vector push = target.getLocation().toVector().subtract(center.toVector());
            if (push.lengthSquared() < 0.0001) push = player.getLocation().getDirection().clone();
            target.setVelocity(target.getVelocity().add(push.normalize().multiply(knockback * factor).setY(0.35 + factor * 0.28)));
        }
        world.spawnParticle(Particle.EXPLOSION, center.clone().add(0, 0.25, 0), 1, 0.12, 0.05, 0.12, 0);
        world.spawnParticle(Particle.CLOUD, center.clone().add(0, 0.15, 0), 34, 1.1, 0.08, 1.1, 0.08);
        world.playSound(center, Sound.ENTITY_GENERIC_EXPLODE, 0.85f, 1.15f);
    }
}
