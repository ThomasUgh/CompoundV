package de.thomasugh.compoundv.util;

import de.thomasugh.compoundv.CompoundV;
import org.bukkit.GameMode;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class AbilityKillTracker {

    private static final long MARK_TTL_MS = 10000L;
    private static final Map<UUID, KillMarker> LAST_ABILITY_DAMAGE = new ConcurrentHashMap<>();

    private AbilityKillTracker() {
    }

    public static void mark(LivingEntity target, Player killer, String messageKey) {
        if (target == null || killer == null || messageKey == null || messageKey.isBlank()) return;
        LAST_ABILITY_DAMAGE.put(target.getUniqueId(), new KillMarker(
                killer.getUniqueId(),
                killer.getName(),
                messageKey,
                System.currentTimeMillis() + MARK_TTL_MS
        ));
    }

    public static KillMarker consume(Player victim) {
        if (victim == null) return null;
        KillMarker marker = LAST_ABILITY_DAMAGE.remove(victim.getUniqueId());
        if (marker == null || marker.expiresAt() < System.currentTimeMillis()) return null;
        return marker;
    }

    public static void damage(CompoundV plugin, LivingEntity target, Player attacker,
                              double damage, String messageKey, boolean allowDirectFallback) {
        if (plugin == null || target == null || attacker == null || damage <= 0.0) return;

        mark(target, attacker, messageKey);
        double healthBefore = safeHealth(target);
        double absorptionBefore = safeAbsorption(target);

        target.damage(Math.max(0.0, damage), attacker);

        if (!shouldDirectFallback(plugin, target, attacker, allowDirectFallback, healthBefore, absorptionBefore)) {
            return;
        }

        mark(target, attacker, messageKey);
        double nextHealth = Math.max(0.0, target.getHealth() - Math.max(0.0, damage));
        target.setHealth(nextHealth);
    }

    private static boolean shouldDirectFallback(CompoundV plugin, LivingEntity target, Player attacker,
                                                boolean allowDirectFallback, double healthBefore,
                                                double absorptionBefore) {
        boolean globalFallback = plugin.getConfig().getBoolean(
                "combat.protection_bypass.apply_to_all_ability_damage", true);
        if (!allowDirectFallback && !globalFallback) return false;
        if (target.isDead() || target.getHealth() <= 0.0) return false;
        if (target instanceof Player targetPlayer
                && (targetPlayer.getGameMode() == GameMode.CREATIVE || targetPlayer.getGameMode() == GameMode.SPECTATOR)) {
            return false;
        }
        if (!plugin.getConfig().getBoolean("combat.protection_bypass.enabled", false)) return false;

        String permission = plugin.getConfig().getString("combat.protection_bypass.permission", "compoundv.damage-bypass");
        boolean permitted = attacker.hasPermission("compoundv.admin")
                || (permission != null && !permission.isBlank() && attacker.hasPermission(permission));
        if (!permitted) return false;

        double healthAfter = safeHealth(target);
        double absorptionAfter = safeAbsorption(target);
        return healthAfter >= healthBefore - 0.0001 && absorptionAfter >= absorptionBefore - 0.0001;
    }

    private static double safeHealth(LivingEntity entity) {
        try {
            return entity.getHealth();
        } catch (Throwable ignored) {
            return 0.0;
        }
    }

    private static double safeAbsorption(LivingEntity entity) {
        try {
            return entity.getAbsorptionAmount();
        } catch (Throwable ignored) {
            return 0.0;
        }
    }

    public record KillMarker(UUID killerId, String killerName, String messageKey, long expiresAt) {
    }
}
