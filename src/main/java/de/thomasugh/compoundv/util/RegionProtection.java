package de.thomasugh.compoundv.util;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;

import java.lang.reflect.Array;
import java.lang.reflect.Method;
import java.util.logging.Logger;

public final class RegionProtection {

    private static volatile boolean initialized = false;
    private static volatile boolean available = false;
    private static volatile boolean warned = false;

    private static Object regionContainer;
    private static Method createQueryMethod;
    private static Method testStateMethod;
    private static Method adaptMethod;
    private static Object wgPluginInstance;
    private static Method wrapPlayerMethod;
    private static Object stateFlagArrayWithPvp;

    private RegionProtection() {
    }

    public static boolean isAvailable() {
        ensureInit();
        return available;
    }

    public static boolean isPvpAllowed(Player attacker, Location location) {
        if (attacker == null || location == null) return true;
        ensureInit();
        if (!available) return true;
        try {
            Object query = createQueryMethod.invoke(regionContainer);
            Object weLoc = adaptMethod.invoke(null, location);
            Object localPlayer = wrapPlayerMethod.invoke(wgPluginInstance, attacker);
            Object result = testStateMethod.invoke(query, weLoc, localPlayer, stateFlagArrayWithPvp);
            return !(result instanceof Boolean) || (Boolean) result;
        } catch (Throwable t) {
            warnOnce(Bukkit.getLogger(), t);
            return true;
        }
    }

    private static void ensureInit() {
        if (initialized) return;
        synchronized (RegionProtection.class) {
            if (initialized) return;
            initialized = true;
            try {
                if (Bukkit.getPluginManager().getPlugin("WorldGuard") == null) {
                    return;
                }

                Class<?> worldGuard = Class.forName("com.sk89q.worldguard.WorldGuard");
                Object wgInstance = worldGuard.getMethod("getInstance").invoke(null);
                Object platform = worldGuard.getMethod("getPlatform").invoke(wgInstance);
                regionContainer = platform.getClass().getMethod("getRegionContainer").invoke(platform);
                createQueryMethod = regionContainer.getClass().getMethod("createQuery");

                Class<?> bukkitAdapter = Class.forName("com.sk89q.worldedit.bukkit.BukkitAdapter");
                adaptMethod = bukkitAdapter.getMethod("adapt", Location.class);

                Class<?> wgPluginClass = Class.forName("com.sk89q.worldguard.bukkit.WorldGuardPlugin");
                wgPluginInstance = wgPluginClass.getMethod("inst").invoke(null);
                wrapPlayerMethod = wgPluginClass.getMethod("wrapPlayer", Player.class);

                Class<?> flagsClass = Class.forName("com.sk89q.worldguard.protection.flags.Flags");
                Object pvpFlag = flagsClass.getField("PVP").get(null);

                Class<?> stateFlagClass = Class.forName("com.sk89q.worldguard.protection.flags.StateFlag");
                Object flagArray = Array.newInstance(stateFlagClass, 1);
                Array.set(flagArray, 0, pvpFlag);
                stateFlagArrayWithPvp = flagArray;

                Class<?> weLocationClass = Class.forName("com.sk89q.worldedit.util.Location");
                Class<?> regionAssociableClass =
                        Class.forName("com.sk89q.worldguard.protection.association.RegionAssociable");
                Object sampleQuery = createQueryMethod.invoke(regionContainer);
                testStateMethod = sampleQuery.getClass().getMethod(
                        "testState", weLocationClass, regionAssociableClass, flagArray.getClass());

                available = true;
            } catch (Throwable t) {
                available = false;
                warnOnce(Bukkit.getLogger(), t);
            }
        }
    }

    private static void warnOnce(Logger logger, Throwable t) {
        if (warned) return;
        warned = true;
        if (logger != null) {
            logger.warning("[CompoundV] WorldGuard region check unavailable, using default ability protection ("
                    + t.getClass().getSimpleName() + ").");
        }
    }
}
