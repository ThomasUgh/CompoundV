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

    private static volatile Boolean foliaDetected;

    public static boolean detectFolia() {
        Boolean cached = foliaDetected;
        if (cached != null) return cached;
        boolean result;
        try {
            Bukkit.getServer().getClass().getMethod("getGlobalRegionScheduler");
            result = true;
        } catch (ReflectiveOperationException ignored) {
            result = false;
        }
        foliaDetected = result;
        return result;
    }
}
