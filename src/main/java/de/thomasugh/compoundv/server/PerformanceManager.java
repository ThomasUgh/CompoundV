package de.thomasugh.compoundv.server;

import de.thomasugh.compoundv.CompoundV;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class PerformanceManager {

    private final CompoundV plugin;

    private volatile double particleMultiplier = 1.0;
    private volatile boolean tpsAdaptiveParticles = true;
    private volatile double minTps = 17.0;
    private volatile int maxScanEntities = 60;
    private volatile int maxConcurrentCinematics = 3;

    private volatile double currentTps = 20.0;
    private long lastTickNanos = 0L;
    private TaskHandle tickTask = TaskHandle.NOOP;

    private final Set<UUID> activeCinematics = ConcurrentHashMap.newKeySet();

    public PerformanceManager(CompoundV plugin) {
        this.plugin = plugin;
        reload();
    }

    public void start() {
        lastTickNanos = System.nanoTime();
        tickTask = SchedulerAdapter.runTimer(plugin, this::sampleTps, 1L, 1L);
    }

    public void shutdown() {
        if (tickTask != null) tickTask.cancel();
        activeCinematics.clear();
    }

    public void reload() {
        particleMultiplier = clampDouble(plugin.getConfig().getDouble("performance.particle_multiplier", 1.0), 0.1, 3.0);
        tpsAdaptiveParticles = plugin.getConfig().getBoolean("performance.tps_adaptive_particles", true);
        minTps = clampDouble(plugin.getConfig().getDouble("performance.min_tps", 17.0), 5.0, 20.0);
        maxScanEntities = (int) clampDouble(plugin.getConfig().getInt("performance.max_scan_entities", 60), 8, 512);
        maxConcurrentCinematics = (int) clampDouble(plugin.getConfig().getInt("performance.max_concurrent_cinematics", 3), 1, 32);
    }

    private void sampleTps() {
        long now = System.nanoTime();
        long delta = now - lastTickNanos;
        lastTickNanos = now;
        if (delta <= 0L) return;
        double instant = 1_000_000_000.0 / delta;
        if (instant > 20.0) instant = 20.0;
        currentTps = (currentTps * 0.9) + (instant * 0.1);
    }

    public double getCurrentTps() {
        return currentTps;
    }

    public boolean isUnderLoad() {
        return tpsAdaptiveParticles && currentTps < minTps;
    }

    public int scaleParticles(int base) {
        if (base <= 0) return 0;
        double factor = particleMultiplier;
        if (tpsAdaptiveParticles && currentTps < minTps) {
            double ratio = currentTps / 20.0;
            if (ratio < 0.25) ratio = 0.25;
            factor *= ratio;
        }
        int scaled = (int) Math.round(base * factor);
        return Math.max(1, scaled);
    }

    public int capScan(int requested) {
        if (requested <= 0) return maxScanEntities;
        return Math.min(requested, maxScanEntities);
    }

    public int maxScanEntities() {
        return maxScanEntities;
    }

    public boolean tryAcquireCinematic(UUID id) {
        if (id == null) return true;
        if (activeCinematics.contains(id)) return true;
        if (activeCinematics.size() >= maxConcurrentCinematics) return false;
        activeCinematics.add(id);
        return true;
    }

    public void releaseCinematic(UUID id) {
        if (id != null) activeCinematics.remove(id);
    }

    public int activeCinematics() {
        return activeCinematics.size();
    }

    private static double clampDouble(double value, double min, double max) {
        if (value < min) return min;
        if (value > max) return max;
        return value;
    }
}
