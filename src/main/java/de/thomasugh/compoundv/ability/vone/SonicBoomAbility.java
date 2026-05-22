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
        world.playSound(location, Sound.ENTITY_GENERIC_EXPLODE, 2.0f, 0.5f);
        world.playSound(location, Sound.ITEM_FIRECHARGE_USE, 1.5f, 0.6f);
        world.spawnParticle(Particle.CLOUD, location.clone().add(0, 0.2, 0), 50, 1.2, 0.15, 1.2, 0.25);
        world.spawnParticle(Particle.POOF, location.clone().add(0, 0.4, 0), 28, 1.0, 0.15, 1.0, 0);
        world.spawnParticle(Particle.EXPLOSION, location.clone().add(0, 0.5, 0), 6, 0.8, 0.05, 0.8, 0);
        world.spawnParticle(Particle.LARGE_SMOKE, location.clone().add(0, 0.3, 0), 25, 1.0, 0.10, 1.0, 0.05);
        world.spawnParticle(Particle.DUST, location.clone().add(0, 0.8, 0), 25, 1.1, 0.3, 1.1, 0,
                new Particle.DustOptions(Color.fromRGB(80, 220, 255), 1f));

        player.setFlying(false);
        player.setAllowFlight(false);

        double velocity = plugin.getConfig().getDouble("abilities.sonic_boom.launch_velocity",
                plugin.getConfig().getDouble("abilities.the_patriot.shared.launch_velocity", 3.5));
        Vector look = player.getLocation().getDirection();
        player.setVelocity(new Vector(look.getX() * 0.25, velocity, look.getZ() * 0.25));

        int peakTicks = plugin.getConfig().getInt("abilities.sonic_boom.launch_peak_ticks",
                plugin.getConfig().getInt("abilities.the_patriot.shared.launch_peak_ticks", 28));
        double flySpeed = plugin.getConfig().getDouble("abilities.sonic_boom.launch_fly_speed",
                plugin.getConfig().getDouble("abilities.the_patriot.shared.launch_fly_speed", 0.375));
        SchedulerAdapter.runLater(plugin, () -> {
            launching.remove(uuid);
            if (player.isOnline()) {
                player.setAllowFlight(true);
                player.setFlying(true);
                player.setFlySpeed((float) flySpeed);
            }
        }, peakTicks);
    }
}
