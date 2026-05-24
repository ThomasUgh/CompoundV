package de.thomasugh.compoundv.ability.vone;

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
import java.util.concurrent.ThreadLocalRandom;

public class StormstrikeAbility implements Ability {

    private final CompoundV plugin;
    private final Set<UUID> launching = new HashSet<>();
    private final Map<UUID, Long> launchCooldown = new HashMap<>();
    private final Map<UUID, Long> lightningCooldown = new HashMap<>();
    private final Map<UUID, Long> lastHandledAt = new HashMap<>();

    public StormstrikeAbility(CompoundV plugin) {
        this.plugin = plugin;
    }

    @Override public String getId() { return "stormstrike"; }
    @Override public String getDisplayName() { return "Stormstrike"; }
    @Override public int getColor() { return 0x55D6FF; }

    @Override
    public void apply(Player player) {
        player.setAllowFlight(true);
        player.setFlySpeed((float) flySpeed());
        int strength = plugin.getConfig().getInt("abilities.stormstrike.strength_level", 1);
        int resistance = plugin.getConfig().getInt("abilities.stormstrike.resistance_level", 1);
        player.addPotionEffect(new PotionEffect(PotionEffects.STRENGTH,
                Integer.MAX_VALUE, Math.max(0, strength - 1), false, false, true));
        player.addPotionEffect(new PotionEffect(PotionEffects.RESISTANCE,
                Integer.MAX_VALUE, Math.max(0, resistance - 1), false, false, true));
    }

    @Override
    public void remove(Player player) {
        UUID uuid = player.getUniqueId();
        launching.remove(uuid);
        launchCooldown.remove(uuid);
        lightningCooldown.remove(uuid);
        lastHandledAt.remove(uuid);
        player.setFlySpeed(0.1f);
        player.removePotionEffect(PotionEffects.STRENGTH);
        player.removePotionEffect(PotionEffects.RESISTANCE);
        if (player.getGameMode() == GameMode.CREATIVE || player.getGameMode() == GameMode.SPECTATOR) return;
        player.setFlying(false);
        player.setAllowFlight(false);
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
        world.playSound(location, Sound.ENTITY_LIGHTNING_BOLT_THUNDER, 0.9f, 1.65f);
        world.playSound(location, Sound.ENTITY_FIREWORK_ROCKET_LAUNCH, 0.75f, 1.35f);
        world.spawnParticle(Particle.ELECTRIC_SPARK, location.clone().add(0, 0.4, 0), 48, 0.85, 0.25, 0.85, 0.22);
        world.spawnParticle(Particle.CLOUD, location.clone().add(0, 0.25, 0), 30, 0.9, 0.12, 0.9, 0.16);
        world.spawnParticle(Particle.END_ROD, location.clone().add(0, 0.7, 0), 24, 0.65, 0.45, 0.65, 0.05);

        player.setFlying(false);
        player.setAllowFlight(false);

        double velocity = plugin.getConfig().getDouble("abilities.stormstrike.launch_velocity", 2.75);
        Vector look = player.getLocation().getDirection();
        player.setVelocity(new Vector(look.getX() * 0.20, velocity, look.getZ() * 0.20));

        int peakTicks = plugin.getConfig().getInt("abilities.stormstrike.launch_peak_ticks", 22);
        SchedulerAdapter.runLater(plugin, () -> {
            launching.remove(uuid);
            if (player.isOnline()) {
                player.setAllowFlight(true);
                player.setFlying(true);
                player.setFlySpeed((float) flySpeed());
            }
        }, peakTicks);
    }

    public void strikeLightning(Player player) {
        UUID uuid = player.getUniqueId();
        long handledNow = System.currentTimeMillis();
        long lastHandled = lastHandledAt.getOrDefault(uuid, 0L);
        if (handledNow - lastHandled < 250L) return;
        lastHandledAt.put(uuid, handledNow);

        long cooldownMs = plugin.getConfig().getLong("abilities.stormstrike.lightning_cooldown_ms", 3000L);
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
        int maxBolts = plugin.getConfig().getInt("abilities.stormstrike.lightning_max_bolts", 3);
        if (maxBolts < minBolts) maxBolts = minBolts;
        int bolts = minBolts + ThreadLocalRandom.current().nextInt(Math.max(1, maxBolts - minBolts + 1));
        double spread = plugin.getConfig().getDouble("abilities.stormstrike.lightning_spread", 1.4);

        player.getWorld().playSound(player.getLocation(), Sound.ENTITY_LIGHTNING_BOLT_THUNDER, 0.7f, 1.55f);
        for (int i = 0; i < bolts; i++) {
            Location bolt = target.clone().add(
                    ThreadLocalRandom.current().nextDouble(-spread, spread),
                    0,
                    ThreadLocalRandom.current().nextDouble(-spread, spread));
            player.getWorld().strikeLightning(bolt);
            player.getWorld().spawnParticle(Particle.ELECTRIC_SPARK, bolt.clone().add(0, 1, 0), 28, 0.35, 0.55, 0.35, 0.12);
        }
        MessageUtil.sendActionBar(player, plugin.getLocaleManager().msg("stormstrike.released"));
    }

    private Location findStrikeTarget(Player player) {
        double range = plugin.getConfig().getDouble("abilities.stormstrike.lightning_range", 36.0);
        Location eye = player.getEyeLocation();
        Vector direction = eye.getDirection().normalize();
        RayTraceResult result = player.getWorld().rayTrace(eye, direction, range,
                org.bukkit.FluidCollisionMode.NEVER, true, 0.35,
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
