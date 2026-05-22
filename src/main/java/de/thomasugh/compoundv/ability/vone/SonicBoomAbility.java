package de.thomasugh.compoundv.ability.vone;

import de.thomasugh.compoundv.CompoundV;
import de.thomasugh.compoundv.ability.Ability;
import de.thomasugh.compoundv.server.SchedulerAdapter;
import de.thomasugh.compoundv.util.MessageUtil;
import de.thomasugh.compoundv.util.PotionEffects;
import org.bukkit.Color;
import org.bukkit.GameMode;
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

public class SonicBoomAbility implements Ability {

    private final CompoundV plugin;
    private final Set<UUID> launching = new HashSet<>();
    private final Map<UUID, Long> launchCooldown = new HashMap<>();
    private final Map<UUID, Long> fallImpactCooldown = new HashMap<>();

    public SonicBoomAbility(CompoundV plugin) {
        this.plugin = plugin;
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
    }

    @Override
    public void remove(Player player) {
        launching.remove(player.getUniqueId());
        launchCooldown.remove(player.getUniqueId());
        fallImpactCooldown.remove(player.getUniqueId());
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

        player.setFlying(false);
        player.setAllowFlight(false);

        double velocity = plugin.getConfig().getDouble("abilities.sonic_boom.launch_velocity", 3.05);
        Vector look = player.getLocation().getDirection();
        player.setVelocity(new Vector(look.getX() * 0.25, velocity, look.getZ() * 0.25));

        int peakTicks = plugin.getConfig().getInt("abilities.sonic_boom.launch_peak_ticks", 24);
        double flySpeed = plugin.getConfig().getDouble("abilities.sonic_boom.launch_fly_speed", 0.3);
        SchedulerAdapter.runLater(plugin, () -> {
            launching.remove(uuid);
            if (player.isOnline()) {
                player.setAllowFlight(true);
                player.setFlying(true);
                player.setFlySpeed((float) flySpeed);
            }
        }, peakTicks);
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

    private void damageNearbyEntities(Player player, Location center) {
        double radius = plugin.getConfig().getDouble("abilities.sonic_boom.fall_impact_entity_radius", 9.0);
        double maxDamage = plugin.getConfig().getDouble("abilities.sonic_boom.fall_impact_entity_damage_hearts", 16.0) * 2.0;
        double knockback = plugin.getConfig().getDouble("abilities.sonic_boom.fall_impact_knockback", 2.4);
        for (Entity entity : center.getWorld().getNearbyEntities(center, radius, radius, radius)) {
            if (!(entity instanceof LivingEntity target) || entity.equals(player)) continue;
            double distance = Math.max(0.1, target.getLocation().distance(center));
            double factor = Math.max(0.15, 1.0 - (distance / radius));
            target.damage(maxDamage * factor, player);
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
}
