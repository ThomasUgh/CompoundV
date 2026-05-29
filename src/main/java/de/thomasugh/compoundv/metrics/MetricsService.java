package de.thomasugh.compoundv.metrics;

import org.bstats.bukkit.Metrics;
import org.bukkit.plugin.java.JavaPlugin;

public final class MetricsService {

    // CompoundV bStats plugin id.
    // Replace this value if bStats assigns a different id for the public listing.
    private static final int BSTATS_PLUGIN_ID = 31614;

    private final JavaPlugin plugin;
    private Metrics metrics;

    public MetricsService(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public void start() {
        try {
            metrics = new Metrics(plugin, BSTATS_PLUGIN_ID);
            plugin.getLogger().info("bStats metrics enabled.");
        } catch (Throwable ex) {
            plugin.getLogger().warning("Could not start bStats metrics: " + ex.getMessage());
        }
    }

    public boolean isStarted() {
        return metrics != null;
    }
}
