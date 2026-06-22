package de.thomasugh.compoundv.util;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.Team;

import java.lang.reflect.Method;

public final class PrivateGlowUtil {

    private static Method sendPotionEffectChange;
    private static Method sendPotionEffectChangeRemove;
    private static boolean methodsResolved;

    private PrivateGlowUtil() {
    }

    public static boolean showGlowing(Player viewer, LivingEntity target, ChatColor color, String teamName) {
        if (viewer == null || target == null || PotionEffects.GLOWING == null) return false;
        ensureTeam(target, color, teamName);
        resolveMethods();
        if (sendPotionEffectChange == null) return false;

        try {
            PotionEffect effect = new PotionEffect(PotionEffects.GLOWING, 60, 0, false, false, false);
            sendPotionEffectChange.invoke(viewer, target, effect);
            return true;
        } catch (Throwable ignored) {
            return false;
        }
    }

    public static void clearGlowing(Player viewer, LivingEntity target) {
        if (viewer == null || target == null || PotionEffects.GLOWING == null) return;
        resolveMethods();

        try {
            if (sendPotionEffectChangeRemove != null) {
                sendPotionEffectChangeRemove.invoke(viewer, target, PotionEffects.GLOWING);
                return;
            }
            if (sendPotionEffectChange != null) {
                PotionEffect clearEffect = new PotionEffect(PotionEffects.GLOWING, 1, 0, false, false, false);
                sendPotionEffectChange.invoke(viewer, target, clearEffect);
            }
        } catch (Throwable ignored) {
            // Older Bukkit builds may not expose private potion effect packets.
        }
    }

    public static boolean isPrivateSupported() {
        resolveMethods();
        return sendPotionEffectChange != null;
    }

    public static void applyGlow(Player viewer, LivingEntity target, ChatColor color, String teamName, int durationTicks) {
        if (viewer == null || target == null) return;
        ensureTeam(target, color, teamName);
        resolveMethods();
        if (sendPotionEffectChange != null && PotionEffects.GLOWING != null) {
            try {
                PotionEffect effect = new PotionEffect(PotionEffects.GLOWING, durationTicks, 0, false, false, false);
                sendPotionEffectChange.invoke(viewer, target, effect);
                return;
            } catch (Throwable ignored) {
            }
        }
        if (PotionEffects.GLOWING != null) {
            target.addPotionEffect(new PotionEffect(PotionEffects.GLOWING, durationTicks, 0, false, false, false));
        }
    }

    public static void clearGlow(Player viewer, LivingEntity target, String teamName) {
        if (target == null) return;
        resolveMethods();
        boolean cleared = false;
        try {
            if (viewer != null && sendPotionEffectChangeRemove != null && PotionEffects.GLOWING != null) {
                sendPotionEffectChangeRemove.invoke(viewer, target, PotionEffects.GLOWING);
                cleared = true;
            } else if (viewer != null && sendPotionEffectChange != null && PotionEffects.GLOWING != null) {
                PotionEffect clearEffect = new PotionEffect(PotionEffects.GLOWING, 1, 0, false, false, false);
                sendPotionEffectChange.invoke(viewer, target, clearEffect);
                cleared = true;
            }
        } catch (Throwable ignored) {
        }
        if (!cleared && PotionEffects.GLOWING != null) {
            target.removePotionEffect(PotionEffects.GLOWING);
        }
        removeFromTeam(target, teamName);
    }

    @SuppressWarnings("deprecation")
    private static void removeFromTeam(Entity entity, String teamName) {
        if (teamName == null) return;
        try {
            Scoreboard scoreboard = Bukkit.getScoreboardManager().getMainScoreboard();
            Team team = scoreboard.getTeam(teamName);
            if (team == null) return;
            String entry = scoreboardEntry(entity);
            if (team.hasEntry(entry)) {
                team.removeEntry(entry);
            }
        } catch (Throwable ignored) {
        }
    }

    @SuppressWarnings("deprecation")
    private static void ensureTeam(Entity entity, ChatColor color, String teamName) {
        try {
            Scoreboard scoreboard = Bukkit.getScoreboardManager().getMainScoreboard();
            Team team = scoreboard.getTeam(teamName);
            if (team == null) {
                team = scoreboard.registerNewTeam(teamName);
            }
            team.setColor(color);
            String entry = scoreboardEntry(entity);
            if (!team.hasEntry(entry)) {
                team.addEntry(entry);
            }
        } catch (Throwable ignored) {
            // Coloring is optional. The private glowing packet is the important part.
        }
    }

    private static String scoreboardEntry(Entity entity) {
        if (entity instanceof Player player) {
            return player.getName();
        }
        return entity.getUniqueId().toString();
    }

    private static void resolveMethods() {
        if (methodsResolved) return;
        methodsResolved = true;
        try {
            sendPotionEffectChange = Player.class.getMethod("sendPotionEffectChange", LivingEntity.class, PotionEffect.class);
        } catch (Throwable ignored) {
            sendPotionEffectChange = null;
        }
        try {
            sendPotionEffectChangeRemove = Player.class.getMethod("sendPotionEffectChangeRemove", LivingEntity.class, org.bukkit.potion.PotionEffectType.class);
        } catch (Throwable ignored) {
            sendPotionEffectChangeRemove = null;
        }
    }
}
