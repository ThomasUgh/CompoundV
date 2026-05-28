package de.thomasugh.compoundv.util;

import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.lang.reflect.Method;

public final class TeleportUtil {

    private TeleportUtil() {
    }

    public static void teleportSafely(Plugin plugin, Player player, Location destination) {
        if (player == null || destination == null) return;
        try {
            Method teleportAsync = player.getClass().getMethod("teleportAsync", Location.class);
            teleportAsync.invoke(player, destination);
            return;
        } catch (NoSuchMethodException ignored) {
            // Bukkit/Spigot fallback below.
        } catch (Throwable ex) {
            plugin.getLogger().warning("Async teleport failed, falling back to sync teleport: "
                    + (ex.getCause() != null && ex.getCause().getMessage() != null
                    ? ex.getCause().getMessage()
                    : ex.getClass().getSimpleName()));
        }

        player.teleport(destination);
    }
}
