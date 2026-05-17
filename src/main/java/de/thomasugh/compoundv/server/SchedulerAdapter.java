package de.thomasugh.compoundv.server;

import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;

import java.lang.reflect.Method;
import java.util.function.Consumer;

public final class SchedulerAdapter {

    private SchedulerAdapter() {
    }

    public static TaskHandle runLater(Plugin plugin, Runnable runnable, long delayTicks) {
        if (ServerCompatibility.detectFolia()) {
            TaskHandle handle = tryFoliaLater(plugin, runnable, delayTicks);
            if (handle != null) return handle;
        }

        BukkitTask task = Bukkit.getScheduler().runTaskLater(plugin, runnable, delayTicks);
        return task::cancel;
    }

    public static TaskHandle runTimer(Plugin plugin, Runnable runnable, long delayTicks, long periodTicks) {
        if (ServerCompatibility.detectFolia()) {
            TaskHandle handle = tryFoliaTimer(plugin, runnable, delayTicks, periodTicks);
            if (handle != null) return handle;
        }

        BukkitTask task = Bukkit.getScheduler().runTaskTimer(plugin, runnable, delayTicks, periodTicks);
        return task::cancel;
    }

    private static TaskHandle tryFoliaLater(Plugin plugin, Runnable runnable, long delayTicks) {
        try {
            Object scheduler = Bukkit.getServer().getClass().getMethod("getGlobalRegionScheduler").invoke(Bukkit.getServer());
            Method runDelayed = scheduler.getClass().getMethod("runDelayed", Plugin.class, Consumer.class, long.class);
            Object task = runDelayed.invoke(scheduler, plugin, (Consumer<Object>) ignored -> runnable.run(), Math.max(1L, delayTicks));
            return cancelHandle(task);
        } catch (Throwable ex) {
            plugin.getLogger().warning("Folia delayed scheduler bridge failed, falling back to Bukkit scheduler: " + ex.getMessage());
            return null;
        }
    }

    private static TaskHandle tryFoliaTimer(Plugin plugin, Runnable runnable, long delayTicks, long periodTicks) {
        try {
            Object scheduler = Bukkit.getServer().getClass().getMethod("getGlobalRegionScheduler").invoke(Bukkit.getServer());
            Method runAtFixedRate = scheduler.getClass().getMethod("runAtFixedRate", Plugin.class, Consumer.class, long.class, long.class);
            Object task = runAtFixedRate.invoke(
                    scheduler,
                    plugin,
                    (Consumer<Object>) ignored -> runnable.run(),
                    Math.max(1L, delayTicks),
                    Math.max(1L, periodTicks)
            );
            return cancelHandle(task);
        } catch (Throwable ex) {
            plugin.getLogger().warning("Folia timer scheduler bridge failed, falling back to Bukkit scheduler: " + ex.getMessage());
            return null;
        }
    }

    private static TaskHandle cancelHandle(Object task) {
        return () -> {
            try {
                task.getClass().getMethod("cancel").invoke(task);
            } catch (ReflectiveOperationException ignored) {
                // No-op: scheduler task is already gone or implementation changed.
            }
        };
    }
}
