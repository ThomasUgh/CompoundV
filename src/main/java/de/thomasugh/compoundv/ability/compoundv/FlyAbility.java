package de.thomasugh.compoundv.ability.compoundv;

import de.thomasugh.compoundv.CompoundV;
import de.thomasugh.compoundv.ability.Ability;
import de.thomasugh.compoundv.server.SchedulerAdapter;
import de.thomasugh.compoundv.util.MessageUtil;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public class FlyAbility implements Ability {

    private final CompoundV plugin;
    private final Set<UUID> launching = new HashSet<>();
    private final Map<UUID, Long> launchCooldown = new HashMap<>();

    public FlyAbility(CompoundV plugin) {
        this.plugin = plugin;
    }

    @Override public String getId() { return "fly"; }
    @Override public String getDisplayName() { return "Flight"; }
    @Override public int getColor() { return 0x44DDFF; }

    @Override
    public void apply(Player player) {
        player.setAllowFlight(true);
    }

    @Override
    public void remove(Player player) {
        launching.remove(player.getUniqueId());
        launchCooldown.remove(player.getUniqueId());
        player.setFlySpeed(0.1f);
        if (player.getGameMode() == GameMode.CREATIVE || player.getGameMode() == GameMode.SPECTATOR) {
            return;
        }
        player.setFlying(false);
        player.setAllowFlight(false);
    }

    public boolean isLaunching(Player player) {
        return launching.contains(player.getUniqueId());
    }

    public void tryLaunch(Player player) {
        UUID uuid = player.getUniqueId();
        if (launching.contains(uuid) || player.isFlying()) return;

        long cooldownMs = plugin.getConfig().getLong("abilities.fly.launch_cooldown_ms", 10000L);
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
        world.playSound(location, Sound.ENTITY_GENERIC_EXPLODE, 0.50f, 0.9f);
        world.playSound(location, Sound.ITEM_FIRECHARGE_USE, 0.38f, 1.1f);
        world.spawnParticle(Particle.CLOUD, location.clone().add(0, 0.18, 0), 12, 0.55, 0.08, 0.55, 0.08);
        world.spawnParticle(Particle.POOF, location.clone().add(0, 0.35, 0), 6, 0.35, 0.05, 0.35, 0.01);
        player.setFlying(false);
        player.setAllowFlight(false);

        double velocity = plugin.getConfig().getDouble("abilities.fly.launch_velocity", 1.75);
        Vector look = player.getLocation().getDirection();
        player.setVelocity(new Vector(look.getX() * 0.15, velocity, look.getZ() * 0.15));

        int peakTicks = plugin.getConfig().getInt("abilities.fly.launch_peak_ticks", 14);
        double flySpeed = plugin.getConfig().getDouble("abilities.fly.launch_fly_speed", 0.15);
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
