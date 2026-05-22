package de.thomasugh.compoundv.ability.compoundv;

import de.thomasugh.compoundv.CompoundV;
import de.thomasugh.compoundv.ability.Ability;
import de.thomasugh.compoundv.util.MessageUtil;
import de.thomasugh.compoundv.util.PrivateGlowUtil;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Color;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import de.thomasugh.compoundv.util.PotionEffects;
import org.bukkit.scoreboard.Team;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public class VisionAbility implements Ability {

    private final CompoundV plugin;
    private final Map<UUID, Boolean> xrayActive = new HashMap<>();
    private final Map<UUID, Integer> ticker = new HashMap<>();
    private final Map<UUID, Set<UUID>> visibleTargets = new HashMap<>();

    public VisionAbility(CompoundV plugin) {
        this.plugin = plugin;
    }

    @Override public String getId() { return "vision"; }
    @Override public String getDisplayName() { return "Vision"; }
    @Override public int getColor() { return 0x7DDCFF; }
    @Override public boolean needsTick() { return true; }

    @Override public void apply(Player player) {}

    @Override
    public void remove(Player player) {
        if (xrayActive.getOrDefault(player.getUniqueId(), false)) clearXray(player);
        xrayActive.remove(player.getUniqueId());
        ticker.remove(player.getUniqueId());
        player.removePotionEffect(PotionEffects.NIGHT_VISION);
    }

    @Override
    public void onTick(Player player) {
        if (!xrayActive.getOrDefault(player.getUniqueId(), false)) return;
        if (ticker.merge(player.getUniqueId(), 1, Integer::sum) % 20 == 0) refreshXray(player);
    }

    public void toggleXray(Player player) {
        boolean next = !xrayActive.getOrDefault(player.getUniqueId(), false);
        xrayActive.put(player.getUniqueId(), next);
        if (next) {
            refreshXray(player);
            player.addPotionEffect(new PotionEffect(PotionEffects.NIGHT_VISION,
                    Integer.MAX_VALUE, 0, false, false, false));
            MessageUtil.sendActionBar(player, plugin.getLocaleManager().msg("toggle.xray_on"));
            player.playSound(player.getLocation(), Sound.BLOCK_BEACON_ACTIVATE, 0.45f, 1.65f);
        } else {
            clearXray(player);
            player.removePotionEffect(PotionEffects.NIGHT_VISION);
            MessageUtil.sendActionBar(player, plugin.getLocaleManager().msg("toggle.xray_off"));
            player.playSound(player.getLocation(), Sound.BLOCK_BEACON_DEACTIVATE, 0.35f, 1.35f);
        }
    }

    private void refreshXray(Player player) {
        double radius = plugin.getConfig().getDouble("abilities.vision.xray_radius", 35.0);
        Particle.DustOptions aqua = new Particle.DustOptions(Color.fromRGB(125, 220, 255), 1.05f);
        Set<UUID> currentTargets = new HashSet<>();
        for (Entity entity : player.getNearbyEntities(radius, radius, radius)) {
            if (!(entity instanceof LivingEntity living) || entity.equals(player)) continue;
            currentTargets.add(living.getUniqueId());
            if (!PrivateGlowUtil.showGlowing(player, living, ChatColor.AQUA, "cv_vision_glow")) {
                renderPrivateOutline(player, living, aqua);
            }
        }
        clearStaleGlow(player, currentTargets);
        visibleTargets.put(player.getUniqueId(), currentTargets);
    }

    private void clearXray(Player player) {
        Set<UUID> oldTargets = visibleTargets.remove(player.getUniqueId());
        if (oldTargets == null) return;
        for (UUID targetId : oldTargets) {
            Entity entity = Bukkit.getEntity(targetId);
            if (entity instanceof LivingEntity living) {
                PrivateGlowUtil.clearGlowing(player, living);
            }
        }
    }

    private void clearStaleGlow(Player player, Set<UUID> currentTargets) {
        Set<UUID> oldTargets = visibleTargets.get(player.getUniqueId());
        if (oldTargets == null) return;
        for (UUID targetId : oldTargets) {
            if (currentTargets.contains(targetId)) continue;
            Entity entity = Bukkit.getEntity(targetId);
            if (entity instanceof LivingEntity living) {
                PrivateGlowUtil.clearGlowing(player, living);
            }
        }
    }

    private void renderPrivateOutline(Player viewer, LivingEntity target, Particle.DustOptions dust) {
        LocationSafe.spawnEntityOutline(viewer, target, dust);
    }

    @SuppressWarnings("deprecation")
    private Team visionTeam() {
        var sb = Bukkit.getScoreboardManager().getMainScoreboard();
        Team team = sb.getTeam("cv_vision_glow");
        if (team == null) {
            team = sb.registerNewTeam("cv_vision_glow");
            team.setColor(ChatColor.AQUA);
        }
        return team;
    }
    private static final class LocationSafe {
        private static void spawnEntityOutline(Player viewer, LivingEntity target, Particle.DustOptions dust) {
            double eyeHeight = Math.max(0.8, target.getEyeHeight());
            org.bukkit.Location base = target.getLocation().add(0, Math.min(1.15, eyeHeight * 0.55), 0);
            viewer.spawnParticle(Particle.DUST, base, 8, 0.28, Math.min(0.7, eyeHeight * 0.35), 0.28, 0, dust);
            viewer.spawnParticle(Particle.END_ROD, base, 2, 0.18, 0.28, 0.18, 0.01);
        }
    }

}
