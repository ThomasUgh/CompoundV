package de.thomasugh.compoundv.server;

import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;

public final class ServerCompatibility {

    private final Plugin plugin;
    private final boolean folia;
    private final String serverName;

    public ServerCompatibility(Plugin plugin) {
        this.plugin = plugin;
        this.serverName = Bukkit.getName();
        this.folia = detectFolia();

        plugin.getLogger().info("Compatibility mode: " + serverName
                + " / Bukkit " + Bukkit.getBukkitVersion()
                + " / Folia=" + folia);
    }

    public String serverName() {
        return serverName;
    }

    public boolean isFolia() {
        return folia;
    }

    public static boolean detectFolia() {
        try {
            Bukkit.getServer().getClass().getMethod("getGlobalRegionScheduler");
            return true;
        } catch (ReflectiveOperationException ignored) {
            return false;
        }
    }
}
