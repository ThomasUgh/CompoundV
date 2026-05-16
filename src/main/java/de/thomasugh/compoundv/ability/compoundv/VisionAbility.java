package de.thomasugh.compoundv.ability.compoundv;

import de.thomasugh.compoundv.CompoundVPlugin;
import de.thomasugh.compoundv.ability.Ability;
import net.kyori.adventure.text.format.TextColor;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Sound;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scoreboard.Team;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class VisionAbility implements Ability {

    private final CompoundVPlugin plugin;
    private final Map<UUID, Boolean> xrayActive = new HashMap<>();
    private final Map<UUID, Integer> ticker = new HashMap<>();

    public VisionAbility(CompoundVPlugin plugin) {
        this.plugin = plugin;
    }

    @Override public String getId() { return "vision"; }
    @Override public String getDisplayName() { return "Vision"; }
    @Override public TextColor getColor() { return TextColor.color(0x7DDCFF); }
    @Override public boolean needsTick() { return true; }

    @Override public void apply(Player player) {}

    @Override
    public void remove(Player player) {
        if (xrayActive.getOrDefault(player.getUniqueId(), false)) clearXray(player);
        xrayActive.remove(player.getUniqueId());
        ticker.remove(player.getUniqueId());
        player.removePotionEffect(PotionEffectType.NIGHT_VISION);
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
            player.addPotionEffect(new PotionEffect(PotionEffectType.NIGHT_VISION,
                    Integer.MAX_VALUE, 0, false, false, false));
            player.sendActionBar(plugin.getLocaleManager().msg("toggle.xray_on"));
            player.playSound(player.getLocation(), Sound.BLOCK_BEACON_ACTIVATE, 0.45f, 1.65f);
        } else {
            clearXray(player);
            player.removePotionEffect(PotionEffectType.NIGHT_VISION);
            player.sendActionBar(plugin.getLocaleManager().msg("toggle.xray_off"));
            player.playSound(player.getLocation(), Sound.BLOCK_BEACON_DEACTIVATE, 0.35f, 1.35f);
        }
    }

    private void refreshXray(Player player) {
        double radius = plugin.getConfig().getDouble("abilities.vision.xray_radius", 35.0);
        Team team = visionTeam();
        for (Entity entity : player.getNearbyEntities(radius, radius, radius)) {
            if (!(entity instanceof LivingEntity living) || entity.equals(player)) continue;
            living.addPotionEffect(new PotionEffect(PotionEffectType.GLOWING,
                    45, 0, false, false, false));
            team.addEntry(entity.getUniqueId().toString());
        }
    }

    private void clearXray(Player player) {
        double radius = plugin.getConfig().getDouble("abilities.vision.xray_radius", 35.0) + 12.0;
        Team team = visionTeam();
        for (Entity entity : player.getNearbyEntities(radius, radius, radius)) {
            if (entity instanceof LivingEntity living) living.removePotionEffect(PotionEffectType.GLOWING);
            team.removeEntry(entity.getUniqueId().toString());
        }
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
}
