package de.thomasugh.compoundv.util;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.scoreboard.Team;

public final class RedGlowUtil {

    private static final String RED_TEAM = "cv_red_glow";

    private RedGlowUtil() {
    }

    public static void applyRedGlow(LivingEntity target) {
        addToRedTeam(target);
        if (!target.isGlowing()) target.setGlowing(true);
    }

    public static void removeRedGlow(LivingEntity target) {
        removeFromRedTeam(target);
        if (target.isGlowing()) target.setGlowing(false);
    }

    private static String scoreboardEntry(Entity entity) {
        if (entity instanceof Player player) {
            return player.getName();
        }
        return entity.getUniqueId().toString();
    }

    @SuppressWarnings("deprecation")
    private static void addToRedTeam(Entity entity) {
        Team team = redTeam();
        if (team == null) return;
        try {
            String entry = scoreboardEntry(entity);
            if (!team.hasEntry(entry)) team.addEntry(entry);
        } catch (Throwable ignored) {
        }
    }

    @SuppressWarnings("deprecation")
    private static void removeFromRedTeam(Entity entity) {
        try {
            var manager = Bukkit.getScoreboardManager();
            if (manager == null) return;
            Team team = manager.getMainScoreboard().getTeam(RED_TEAM);
            if (team == null) return;
            String entry = scoreboardEntry(entity);
            if (team.hasEntry(entry)) team.removeEntry(entry);
        } catch (Throwable ignored) {
        }
    }

    @SuppressWarnings("deprecation")
    private static Team redTeam() {
        try {
            var manager = Bukkit.getScoreboardManager();
            if (manager == null) return null;
            var sb = manager.getMainScoreboard();
            Team t = sb.getTeam(RED_TEAM);
            if (t == null) t = sb.registerNewTeam(RED_TEAM);
            t.setColor(ChatColor.RED);
            return t;
        } catch (Throwable ignored) {
            return null;
        }
    }
}
