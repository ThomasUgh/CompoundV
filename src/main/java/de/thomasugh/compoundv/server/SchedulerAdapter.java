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

    private static volatile Boolean foliaCached;

    private static volatile Method entityGetScheduler;
    private static volatile Method entityRunDelayed;
    private static volatile Method entityRunAtFixedRate;

    private static volatile Object globalScheduler;
    private static volatile Method globalRunDelayed;
    private static volatile Method globalRunAtFixedRate;

    private static volatile Object regionScheduler;
    private static volatile Method regionRunDelayed;
    private static volatile Method regionRunAtFixedRate;

    public static boolean isFolia() {
        Boolean cached = foliaCached;
        if (cached == null) {
            cached = ServerCompatibility.detectFolia();
            foliaCached = cached;
        }
        return cached;
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

    @SuppressWarnings("unchecked")
    private static TaskHandle tryFoliaEntityLater(Plugin plugin, Entity entity, Runnable runnable, long delayTicks) {
        try {
            Object scheduler = entityGetScheduler(entity).invoke(entity);
            if (scheduler == null) return null;
            Method runDelayed = entityRunDelayed(scheduler);
            Object task = runDelayed.invoke(scheduler, plugin,
                    (Consumer<Object>) ignored -> runnable.run(), (Runnable) () -> { }, Math.max(1L, delayTicks));
            return cancelHandle(task);
        } catch (Throwable ex) {
            plugin.getLogger().warning("Folia entity delayed scheduler bridge failed: " + message(ex));
            return null;
        }
    }

    @SuppressWarnings("unchecked")
    private static TaskHandle tryFoliaEntityTimer(Plugin plugin, Entity entity, Runnable runnable, long delayTicks, long periodTicks) {
        try {
            Object scheduler = entityGetScheduler(entity).invoke(entity);
            if (scheduler == null) return null;
            Method runAtFixedRate = entityRunAtFixedRate(scheduler);
            Object task = runAtFixedRate.invoke(scheduler, plugin,
                    (Consumer<Object>) ignored -> runnable.run(), (Runnable) () -> { },
                    Math.max(1L, delayTicks), Math.max(1L, periodTicks));
            return cancelHandle(task);
        } catch (Throwable ex) {
            plugin.getLogger().warning("Folia entity timer scheduler bridge failed: " + message(ex));
            return null;
        }
    }

    @SuppressWarnings("unchecked")
    private static TaskHandle tryFoliaRegionLater(Plugin plugin, Location location, Runnable runnable, long delayTicks) {
        try {
            Object scheduler = regionScheduler();
            Object task = regionRunDelayed.invoke(scheduler, plugin, location,
                    (Consumer<Object>) ignored -> runnable.run(), Math.max(1L, delayTicks));
            return cancelHandle(task);
        } catch (Throwable ex) {
            plugin.getLogger().warning("Folia region delayed scheduler bridge failed: " + message(ex));
            return null;
        }
    }

    @SuppressWarnings("unchecked")
    private static TaskHandle tryFoliaRegionTimer(Plugin plugin, Location location, Runnable runnable, long delayTicks, long periodTicks) {
        try {
            Object scheduler = regionScheduler();
            Object task = regionRunAtFixedRate.invoke(scheduler, plugin, location,
                    (Consumer<Object>) ignored -> runnable.run(), Math.max(1L, delayTicks), Math.max(1L, periodTicks));
            return cancelHandle(task);
        } catch (Throwable ex) {
            plugin.getLogger().warning("Folia region timer scheduler bridge failed: " + message(ex));
            return null;
        }
    }

    @SuppressWarnings("unchecked")
    private static TaskHandle tryFoliaGlobalLater(Plugin plugin, Runnable runnable, long delayTicks) {
        try {
            Object scheduler = globalScheduler();
            Object task = globalRunDelayed.invoke(scheduler, plugin,
                    (Consumer<Object>) ignored -> runnable.run(), Math.max(1L, delayTicks));
            return cancelHandle(task);
        } catch (Throwable ex) {
            plugin.getLogger().warning("Folia global delayed scheduler bridge failed: " + message(ex));
            return null;
        }
    }

    @SuppressWarnings("unchecked")
    private static TaskHandle tryFoliaGlobalTimer(Plugin plugin, Runnable runnable, long delayTicks, long periodTicks) {
        try {
            Object scheduler = globalScheduler();
            Object task = globalRunAtFixedRate.invoke(scheduler, plugin,
                    (Consumer<Object>) ignored -> runnable.run(), Math.max(1L, delayTicks), Math.max(1L, periodTicks));
            return cancelHandle(task);
        } catch (Throwable ex) {
            plugin.getLogger().warning("Folia global timer scheduler bridge failed: " + message(ex));
            return null;
        }
    }

    private static Method entityGetScheduler(Entity entity) throws ReflectiveOperationException {
        Method method = entityGetScheduler;
        if (method == null) {
            method = entity.getClass().getMethod("getScheduler");
            entityGetScheduler = method;
        }
        return method;
    }

    private static Method entityRunDelayed(Object scheduler) throws ReflectiveOperationException {
        Method method = entityRunDelayed;
        if (method == null) {
            method = scheduler.getClass().getMethod("runDelayed",
                    Plugin.class, Consumer.class, Runnable.class, long.class);
            entityRunDelayed = method;
        }
        return method;
    }

    private static Method entityRunAtFixedRate(Object scheduler) throws ReflectiveOperationException {
        Method method = entityRunAtFixedRate;
        if (method == null) {
            method = scheduler.getClass().getMethod("runAtFixedRate",
                    Plugin.class, Consumer.class, Runnable.class, long.class, long.class);
            entityRunAtFixedRate = method;
        }
        return method;
    }

    private static Object globalScheduler() throws ReflectiveOperationException {
        Object scheduler = globalScheduler;
        if (scheduler == null) {
            scheduler = Bukkit.getServer().getClass().getMethod("getGlobalRegionScheduler").invoke(Bukkit.getServer());
            globalRunDelayed = scheduler.getClass().getMethod("runDelayed", Plugin.class, Consumer.class, long.class);
            globalRunAtFixedRate = scheduler.getClass().getMethod("runAtFixedRate", Plugin.class, Consumer.class, long.class, long.class);
            globalScheduler = scheduler;
        }
        return scheduler;
    }

    private static Object regionScheduler() throws ReflectiveOperationException {
        Object scheduler = regionScheduler;
        if (scheduler == null) {
            scheduler = Bukkit.getServer().getClass().getMethod("getRegionScheduler").invoke(Bukkit.getServer());
            regionRunDelayed = scheduler.getClass().getMethod("runDelayed", Plugin.class, Location.class, Consumer.class, long.class);
            regionRunAtFixedRate = scheduler.getClass().getMethod("runAtFixedRate", Plugin.class, Location.class, Consumer.class, long.class, long.class);
            regionScheduler = scheduler;
        }
        return scheduler;
    }

    private static TaskHandle cancelHandle(Object task) {
        return () -> {
            if (task == null) return;
            try {
                task.getClass().getMethod("cancel").invoke(task);
            } catch (ReflectiveOperationException ignored) {
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
