package de.thomasugh.compoundv.server;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;

import java.lang.reflect.Method;
import java.util.function.Consumer;

public final class SchedulerAdapter {

    private SchedulerAdapter() {
    }

    public static boolean isFolia() {
        return ServerCompatibility.detectFolia();
    }

    public static void runNow(Plugin plugin, Runnable runnable) {
        if (isFolia()) {
            TaskHandle handle = tryFoliaGlobalLater(plugin, safe(plugin, runnable), 1L);
            if (handle != null) return;
        }
        Bukkit.getScheduler().runTask(plugin, safe(plugin, runnable));
    }

    public static void runNow(Plugin plugin, Player player, Runnable runnable) {
        if (player == null) {
            runNow(plugin, runnable);
            return;
        }
        if (isFolia()) {
            TaskHandle handle = tryFoliaEntityLater(plugin, player, safe(plugin, runnable), 1L);
            if (handle != null) return;
            // Last-resort Folia fallback: global scheduler. This should only happen if the
            // Folia API signature changes. It avoids hard-crashing older mixed forks.
            TaskHandle global = tryFoliaGlobalLater(plugin, safe(plugin, runnable), 1L);
            if (global != null) return;
        }
        Bukkit.getScheduler().runTask(plugin, safe(plugin, runnable));
    }

    public static TaskHandle runLater(Plugin plugin, Runnable runnable, long delayTicks) {
        if (isFolia()) {
            TaskHandle handle = tryFoliaGlobalLater(plugin, safe(plugin, runnable), delayTicks);
            if (handle != null) return handle;
        }

        BukkitTask task = Bukkit.getScheduler().runTaskLater(plugin, safe(plugin, runnable), delayTicks);
        return task::cancel;
    }

    public static TaskHandle runLater(Plugin plugin, Player player, Runnable runnable, long delayTicks) {
        if (player == null) return runLater(plugin, runnable, delayTicks);
        if (isFolia()) {
            TaskHandle handle = tryFoliaEntityLater(plugin, player, safe(plugin, runnable), delayTicks);
            if (handle != null) return handle;
            TaskHandle global = tryFoliaGlobalLater(plugin, safe(plugin, runnable), delayTicks);
            if (global != null) return global;
        }
        BukkitTask task = Bukkit.getScheduler().runTaskLater(plugin, safe(plugin, runnable), delayTicks);
        return task::cancel;
    }

    public static TaskHandle runLater(Plugin plugin, Entity entity, Runnable runnable, long delayTicks) {
        if (entity == null) return runLater(plugin, runnable, delayTicks);
        if (entity instanceof Player player) return runLater(plugin, player, runnable, delayTicks);
        if (isFolia()) {
            TaskHandle handle = tryFoliaEntityLater(plugin, entity, safe(plugin, runnable), delayTicks);
            if (handle != null) return handle;
            TaskHandle global = tryFoliaGlobalLater(plugin, safe(plugin, runnable), delayTicks);
            if (global != null) return global;
        }
        BukkitTask task = Bukkit.getScheduler().runTaskLater(plugin, safe(plugin, runnable), delayTicks);
        return task::cancel;
    }

    public static TaskHandle runLaterAt(Plugin plugin, Location location, Runnable runnable, long delayTicks) {
        if (location == null) return runLater(plugin, runnable, delayTicks);
        if (isFolia()) {
            TaskHandle handle = tryFoliaRegionLater(plugin, location, safe(plugin, runnable), delayTicks);
            if (handle != null) return handle;
            TaskHandle global = tryFoliaGlobalLater(plugin, safe(plugin, runnable), delayTicks);
            if (global != null) return global;
        }
        BukkitTask task = Bukkit.getScheduler().runTaskLater(plugin, safe(plugin, runnable), delayTicks);
        return task::cancel;
    }

    public static TaskHandle runTimer(Plugin plugin, Runnable runnable, long delayTicks, long periodTicks) {
        if (isFolia()) {
            TaskHandle handle = tryFoliaGlobalTimer(plugin, safe(plugin, runnable), delayTicks, periodTicks);
            if (handle != null) return handle;
        }

        BukkitTask task = Bukkit.getScheduler().runTaskTimer(plugin, safe(plugin, runnable), delayTicks, periodTicks);
        return task::cancel;
    }

    public static TaskHandle runTimer(Plugin plugin, Player player, Runnable runnable, long delayTicks, long periodTicks) {
        if (player == null) return runTimer(plugin, runnable, delayTicks, periodTicks);
        if (isFolia()) {
            TaskHandle handle = tryFoliaEntityTimer(plugin, player, safe(plugin, runnable), delayTicks, periodTicks);
            if (handle != null) return handle;
            TaskHandle global = tryFoliaGlobalTimer(plugin, safe(plugin, runnable), delayTicks, periodTicks);
            if (global != null) return global;
        }
        BukkitTask task = Bukkit.getScheduler().runTaskTimer(plugin, safe(plugin, runnable), delayTicks, periodTicks);
        return task::cancel;
    }

    public static TaskHandle runTimer(Plugin plugin, Entity entity, Runnable runnable, long delayTicks, long periodTicks) {
        if (entity == null) return runTimer(plugin, runnable, delayTicks, periodTicks);
        if (entity instanceof Player player) return runTimer(plugin, player, runnable, delayTicks, periodTicks);
        if (isFolia()) {
            TaskHandle handle = tryFoliaEntityTimer(plugin, entity, safe(plugin, runnable), delayTicks, periodTicks);
            if (handle != null) return handle;
            TaskHandle global = tryFoliaGlobalTimer(plugin, safe(plugin, runnable), delayTicks, periodTicks);
            if (global != null) return global;
        }
        BukkitTask task = Bukkit.getScheduler().runTaskTimer(plugin, safe(plugin, runnable), delayTicks, periodTicks);
        return task::cancel;
    }

    public static TaskHandle runTimerAt(Plugin plugin, Location location, Runnable runnable, long delayTicks, long periodTicks) {
        if (location == null) return runTimer(plugin, runnable, delayTicks, periodTicks);
        if (isFolia()) {
            TaskHandle handle = tryFoliaRegionTimer(plugin, location, safe(plugin, runnable), delayTicks, periodTicks);
            if (handle != null) return handle;
            TaskHandle global = tryFoliaGlobalTimer(plugin, safe(plugin, runnable), delayTicks, periodTicks);
            if (global != null) return global;
        }
        BukkitTask task = Bukkit.getScheduler().runTaskTimer(plugin, safe(plugin, runnable), delayTicks, periodTicks);
        return task::cancel;
    }

    private static TaskHandle tryFoliaEntityLater(Plugin plugin, Entity entity, Runnable runnable, long delayTicks) {
        try {
            Object scheduler = entity.getClass().getMethod("getScheduler").invoke(entity);
            Method runDelayed = scheduler.getClass().getMethod("runDelayed", Plugin.class, Consumer.class, Runnable.class, long.class);
            Object task = runDelayed.invoke(scheduler, plugin, (Consumer<Object>) ignored -> runnable.run(), (Runnable) () -> { }, Math.max(1L, delayTicks));
            return cancelHandle(task);
        } catch (Throwable ex) {
            plugin.getLogger().warning("Folia entity delayed scheduler bridge failed: " + message(ex));
            return null;
        }
    }

    private static TaskHandle tryFoliaEntityTimer(Plugin plugin, Entity entity, Runnable runnable, long delayTicks, long periodTicks) {
        try {
            Object scheduler = entity.getClass().getMethod("getScheduler").invoke(entity);
            Method runAtFixedRate = scheduler.getClass().getMethod("runAtFixedRate", Plugin.class, Consumer.class, Runnable.class, long.class, long.class);
            Object task = runAtFixedRate.invoke(
                    scheduler,
                    plugin,
                    (Consumer<Object>) ignored -> runnable.run(),
                    (Runnable) () -> { },
                    Math.max(1L, delayTicks),
                    Math.max(1L, periodTicks)
            );
            return cancelHandle(task);
        } catch (Throwable ex) {
            plugin.getLogger().warning("Folia entity timer scheduler bridge failed: " + message(ex));
            return null;
        }
    }

    private static TaskHandle tryFoliaRegionLater(Plugin plugin, Location location, Runnable runnable, long delayTicks) {
        try {
            Object scheduler = Bukkit.getServer().getClass().getMethod("getRegionScheduler").invoke(Bukkit.getServer());
            Method runDelayed = scheduler.getClass().getMethod("runDelayed", Plugin.class, Location.class, Consumer.class, long.class);
            Object task = runDelayed.invoke(scheduler, plugin, location, (Consumer<Object>) ignored -> runnable.run(), Math.max(1L, delayTicks));
            return cancelHandle(task);
        } catch (Throwable ex) {
            plugin.getLogger().warning("Folia region delayed scheduler bridge failed: " + message(ex));
            return null;
        }
    }

    private static TaskHandle tryFoliaRegionTimer(Plugin plugin, Location location, Runnable runnable, long delayTicks, long periodTicks) {
        try {
            Object scheduler = Bukkit.getServer().getClass().getMethod("getRegionScheduler").invoke(Bukkit.getServer());
            Method runAtFixedRate = scheduler.getClass().getMethod("runAtFixedRate", Plugin.class, Location.class, Consumer.class, long.class, long.class);
            Object task = runAtFixedRate.invoke(
                    scheduler,
                    plugin,
                    location,
                    (Consumer<Object>) ignored -> runnable.run(),
                    Math.max(1L, delayTicks),
                    Math.max(1L, periodTicks)
            );
            return cancelHandle(task);
        } catch (Throwable ex) {
            plugin.getLogger().warning("Folia region timer scheduler bridge failed: " + message(ex));
            return null;
        }
    }

    private static TaskHandle tryFoliaGlobalLater(Plugin plugin, Runnable runnable, long delayTicks) {
        try {
            Object scheduler = Bukkit.getServer().getClass().getMethod("getGlobalRegionScheduler").invoke(Bukkit.getServer());
            Method runDelayed = scheduler.getClass().getMethod("runDelayed", Plugin.class, Consumer.class, long.class);
            Object task = runDelayed.invoke(scheduler, plugin, (Consumer<Object>) ignored -> runnable.run(), Math.max(1L, delayTicks));
            return cancelHandle(task);
        } catch (Throwable ex) {
            plugin.getLogger().warning("Folia global delayed scheduler bridge failed: " + message(ex));
            return null;
        }
    }

    private static TaskHandle tryFoliaGlobalTimer(Plugin plugin, Runnable runnable, long delayTicks, long periodTicks) {
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
            plugin.getLogger().warning("Folia global timer scheduler bridge failed: " + message(ex));
            return null;
        }
    }

    private static TaskHandle cancelHandle(Object task) {
        return () -> {
            if (task == null) return;
            try {
                task.getClass().getMethod("cancel").invoke(task);
            } catch (ReflectiveOperationException ignored) {
                // No-op: scheduler task is already gone or implementation changed.
            }
        };
    }

    private static Runnable safe(Plugin plugin, Runnable runnable) {
        return () -> {
            try {
                runnable.run();
            } catch (Throwable ex) {
                plugin.getLogger().warning("Scheduled task failed: " + ex.getClass().getSimpleName() + " - " + message(ex));
                ex.printStackTrace();
            }
        };
    }

    private static String message(Throwable throwable) {
        Throwable cause = throwable.getCause() != null ? throwable.getCause() : throwable;
        return cause.getMessage() == null ? cause.getClass().getSimpleName() : cause.getMessage();
    }
}
