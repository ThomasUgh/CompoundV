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

public class TheDiverAbility implements Ability {

    private final CompoundVPlugin plugin;
    private final Map<UUID, Boolean> sonarActive = new HashMap<>();
    private final Map<UUID, Integer> ticker = new HashMap<>();

    public TheDiverAbility(CompoundVPlugin plugin) {
        this.plugin = plugin;
    }

    @Override public String getId() { return "the_diver"; }
    @Override public String getDisplayName() { return "The Diver"; }
    @Override public TextColor getColor() { return TextColor.color(0x1EA7FF); }
    @Override public boolean needsTick() { return true; }

    @Override
    public void apply(Player player) {
        applyStaticEffects(player);
        applyWaterAwareCombatEffects(player);
    }

    @Override
    public void remove(Player player) {
        if (sonarActive.getOrDefault(player.getUniqueId(), false)) clearSonar(player);
        sonarActive.remove(player.getUniqueId());
        ticker.remove(player.getUniqueId());
        player.removePotionEffect(PotionEffectType.WATER_BREATHING);
        player.removePotionEffect(PotionEffectType.DOLPHINS_GRACE);
        player.removePotionEffect(PotionEffectType.CONDUIT_POWER);
        player.removePotionEffect(PotionEffectType.STRENGTH);
        player.removePotionEffect(PotionEffectType.RESISTANCE);
        player.removePotionEffect(PotionEffectType.NIGHT_VISION);
    }

    @Override
    public void onTick(Player player) {
        int tick = ticker.merge(player.getUniqueId(), 1, Integer::sum);
        if (tick % 20 == 0) {
            applyStaticEffects(player);
            applyWaterAwareCombatEffects(player);
        }

        if (!sonarActive.getOrDefault(player.getUniqueId(), false)) return;
        if (!player.isInWater()) {
            sonarActive.put(player.getUniqueId(), false);
            clearSonar(player);
            player.removePotionEffect(PotionEffectType.NIGHT_VISION);
            player.sendActionBar(plugin.getLocaleManager().msg("toggle.sonar_water_only"));
            return;
        }
        if (tick % 15 == 0) refreshSonar(player);
    }

    public void toggleSonar(Player player) {
        if (!player.isInWater()) {
            player.sendActionBar(plugin.getLocaleManager().msg("toggle.sonar_water_only"));
            player.playSound(player.getLocation(), Sound.BLOCK_BUBBLE_COLUMN_WHIRLPOOL_INSIDE, 0.45f, 0.65f);
            return;
        }

        boolean next = !sonarActive.getOrDefault(player.getUniqueId(), false);
        sonarActive.put(player.getUniqueId(), next);
        if (next) {
            refreshSonar(player);
            player.addPotionEffect(new PotionEffect(PotionEffectType.NIGHT_VISION,
                    Integer.MAX_VALUE, 0, false, false, false));
            player.sendActionBar(plugin.getLocaleManager().msg("toggle.sonar_on"));
            player.playSound(player.getLocation(), Sound.ENTITY_DOLPHIN_AMBIENT_WATER, 0.75f, 1.25f);
        } else {
            clearSonar(player);
            player.removePotionEffect(PotionEffectType.NIGHT_VISION);
            player.sendActionBar(plugin.getLocaleManager().msg("toggle.sonar_off"));
            player.playSound(player.getLocation(), Sound.ENTITY_DOLPHIN_SPLASH, 0.5f, 0.9f);
        }
    }

    private void applyStaticEffects(Player player) {
        int waterBreathing = plugin.getConfig().getInt("abilities.the_diver.water_breathing_level", 1);
        int dolphinsGrace = plugin.getConfig().getInt("abilities.the_diver.dolphins_grace_level", 3);
        int conduit = plugin.getConfig().getInt("abilities.the_diver.conduit_power_level", 2);
        player.addPotionEffect(new PotionEffect(PotionEffectType.WATER_BREATHING,
                Integer.MAX_VALUE, Math.max(0, waterBreathing - 1), false, false, true));
        player.addPotionEffect(new PotionEffect(PotionEffectType.DOLPHINS_GRACE,
                Integer.MAX_VALUE, Math.max(0, dolphinsGrace - 1), false, false, true));
        player.addPotionEffect(new PotionEffect(PotionEffectType.CONDUIT_POWER,
                Integer.MAX_VALUE, Math.max(0, conduit - 1), false, false, true));
    }

    private void applyWaterAwareCombatEffects(Player player) {
        int strength = plugin.getConfig().getInt("abilities.the_diver.strength_level", 2);
        int resistance = plugin.getConfig().getInt("abilities.the_diver.resistance_level", 1);
        int bonus = player.isInWater() ? plugin.getConfig().getInt("abilities.the_diver.water_bonus_levels", 1) : 0;
        player.addPotionEffect(new PotionEffect(PotionEffectType.STRENGTH,
                Integer.MAX_VALUE, Math.max(0, strength + bonus - 1), false, false, true));
        player.addPotionEffect(new PotionEffect(PotionEffectType.RESISTANCE,
                Integer.MAX_VALUE, Math.max(0, resistance + bonus - 1), false, false, true));
    }

    private void refreshSonar(Player player) {
        double radius = plugin.getConfig().getDouble("abilities.the_diver.sonar_radius", 45.0);
        Team team = sonarTeam();
        for (Entity entity : player.getNearbyEntities(radius, radius, radius)) {
            if (!(entity instanceof LivingEntity living) || entity.equals(player)) continue;
            living.addPotionEffect(new PotionEffect(PotionEffectType.GLOWING,
                    45, 0, false, false, false));
            team.addEntry(entity.getUniqueId().toString());
        }
    }

    private void clearSonar(Player player) {
        double radius = plugin.getConfig().getDouble("abilities.the_diver.sonar_radius", 45.0) + 15.0;
        Team team = sonarTeam();
        for (Entity entity : player.getNearbyEntities(radius, radius, radius)) {
            if (entity instanceof LivingEntity living) living.removePotionEffect(PotionEffectType.GLOWING);
            team.removeEntry(entity.getUniqueId().toString());
        }
    }

    @SuppressWarnings("deprecation")
    private Team sonarTeam() {
        var sb = Bukkit.getScoreboardManager().getMainScoreboard();
        Team team = sb.getTeam("cv_sonar_glow");
        if (team == null) {
            team = sb.registerNewTeam("cv_sonar_glow");
            team.setColor(ChatColor.BLUE);
        }
        return team;
    }
}
