package de.thomasugh.compoundv.ability.compoundv;

import de.thomasugh.compoundv.CompoundV;
import de.thomasugh.compoundv.ability.Ability;
import de.thomasugh.compoundv.util.MessageUtil;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import de.thomasugh.compoundv.util.PotionEffects;
import org.bukkit.scoreboard.Team;
import org.bukkit.util.Vector;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class TheDiverAbility implements Ability {

    private final CompoundV plugin;
    private final Map<UUID, Boolean> sonarActive = new HashMap<>();
    private final Map<UUID, Integer> ticker = new HashMap<>();
    private final Map<UUID, Long> riptideCooldowns = new HashMap<>();

    public TheDiverAbility(CompoundV plugin) {
        this.plugin = plugin;
    }

    @Override public String getId() { return "the_diver"; }
    @Override public String getDisplayName() { return "The Diver"; }
    @Override public int getColor() { return 0x1EA7FF; }
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
        riptideCooldowns.remove(player.getUniqueId());
        player.removePotionEffect(PotionEffects.WATER_BREATHING);
        player.removePotionEffect(PotionEffects.DOLPHINS_GRACE);
        player.removePotionEffect(PotionEffects.CONDUIT_POWER);
        player.removePotionEffect(PotionEffects.STRENGTH);
        player.removePotionEffect(PotionEffects.RESISTANCE);
        player.removePotionEffect(PotionEffects.NIGHT_VISION);
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
            player.removePotionEffect(PotionEffects.NIGHT_VISION);
            MessageUtil.sendActionBar(player, plugin.getLocaleManager().msg("toggle.sonar_water_only"));
            return;
        }
        if (tick % 15 == 0) refreshSonar(player);
    }

    public void toggleSonar(Player player) {
        if (!player.isInWater()) {
            MessageUtil.sendActionBar(player, plugin.getLocaleManager().msg("toggle.sonar_water_only"));
            player.playSound(player.getLocation(), Sound.BLOCK_BUBBLE_COLUMN_WHIRLPOOL_INSIDE, 0.45f, 0.65f);
            return;
        }

        boolean next = !sonarActive.getOrDefault(player.getUniqueId(), false);
        sonarActive.put(player.getUniqueId(), next);
        if (next) {
            refreshSonar(player);
            player.addPotionEffect(new PotionEffect(PotionEffects.NIGHT_VISION,
                    Integer.MAX_VALUE, 0, false, false, false));
            MessageUtil.sendActionBar(player, plugin.getLocaleManager().msg("toggle.sonar_on"));
            player.playSound(player.getLocation(), Sound.ENTITY_DOLPHIN_AMBIENT_WATER, 0.75f, 1.25f);
        } else {
            clearSonar(player);
            player.removePotionEffect(PotionEffects.NIGHT_VISION);
            MessageUtil.sendActionBar(player, plugin.getLocaleManager().msg("toggle.sonar_off"));
            player.playSound(player.getLocation(), Sound.ENTITY_DOLPHIN_SPLASH, 0.5f, 0.9f);
        }
    }

    public void useRiptide(Player player) {
        if (!canUseRiptide(player)) {
            MessageUtil.sendActionBar(player, plugin.getLocaleManager().msg("toggle.diver_riptide_water_or_rain"));
            player.playSound(player.getLocation(), Sound.BLOCK_BUBBLE_COLUMN_WHIRLPOOL_INSIDE, 0.45f, 0.7f);
            return;
        }

        long now = System.currentTimeMillis();
        long cooldownMs = plugin.getConfig().getLong("abilities.the_diver.riptide_cooldown_ms", 1800L);
        long readyAt = riptideCooldowns.getOrDefault(player.getUniqueId(), 0L);
        if (readyAt > now) {
            long seconds = Math.max(1L, (long) Math.ceil((readyAt - now) / 1000.0));
            MessageUtil.sendActionBar(player, plugin.getLocaleManager().msg(
                    "toggle.diver_riptide_cooldown", "seconds", Long.toString(seconds)));
            return;
        }

        int level = Math.max(1, plugin.getConfig().getInt("abilities.the_diver.riptide_level", 4));
        double baseVelocity = plugin.getConfig().getDouble("abilities.the_diver.riptide_velocity", 1.25 + level * 0.65);
        double verticalBoost = plugin.getConfig().getDouble("abilities.the_diver.riptide_vertical_boost", 0.22);

        Vector direction = player.getLocation().getDirection().normalize();
        Vector velocity = direction.multiply(baseVelocity);
        if (velocity.getY() < verticalBoost) {
            velocity.setY(verticalBoost);
        }

        player.setVelocity(velocity);
        riptideCooldowns.put(player.getUniqueId(), now + cooldownMs);
        player.setFallDistance(0f);
        MessageUtil.sendActionBar(player, plugin.getLocaleManager().msg("toggle.diver_riptide"));
        player.playSound(player.getLocation(), Sound.ITEM_TRIDENT_RIPTIDE_3, 1.0f, 1.05f);
        player.getWorld().spawnParticle(Particle.DRIPPING_WATER, player.getLocation().add(0, 1.0, 0), 55, 0.65, 0.55, 0.65, 0.14);
        player.getWorld().spawnParticle(Particle.CLOUD, player.getLocation().add(0, 0.8, 0), 18, 0.45, 0.35, 0.45, 0.06);
    }

    private boolean canUseRiptide(Player player) {
        return player.isInWater() || isExposedToRain(player);
    }

    private boolean isExposedToRain(Player player) {
        World world = player.getWorld();
        if (!world.hasStorm()) return false;
        return player.getLocation().getBlockY() >= world.getHighestBlockYAt(player.getLocation());
    }

    private void applyStaticEffects(Player player) {
        int waterBreathing = plugin.getConfig().getInt("abilities.the_diver.water_breathing_level", 1);
        int dolphinsGrace = plugin.getConfig().getInt("abilities.the_diver.dolphins_grace_level", 4);
        int conduit = plugin.getConfig().getInt("abilities.the_diver.conduit_power_level", 2);
        player.addPotionEffect(new PotionEffect(PotionEffects.WATER_BREATHING,
                Integer.MAX_VALUE, Math.max(0, waterBreathing - 1), false, false, true));
        player.addPotionEffect(new PotionEffect(PotionEffects.DOLPHINS_GRACE,
                Integer.MAX_VALUE, Math.max(0, dolphinsGrace - 1), false, false, true));
        player.addPotionEffect(new PotionEffect(PotionEffects.CONDUIT_POWER,
                Integer.MAX_VALUE, Math.max(0, conduit - 1), false, false, true));
    }

    private void applyWaterAwareCombatEffects(Player player) {
        int strength = plugin.getConfig().getInt("abilities.the_diver.strength_level", 2);
        int resistance = plugin.getConfig().getInt("abilities.the_diver.resistance_level", 1);
        int bonus = player.isInWater() ? plugin.getConfig().getInt("abilities.the_diver.water_bonus_levels", 1) : 0;
        player.addPotionEffect(new PotionEffect(PotionEffects.STRENGTH,
                Integer.MAX_VALUE, Math.max(0, strength + bonus - 1), false, false, true));
        player.addPotionEffect(new PotionEffect(PotionEffects.RESISTANCE,
                Integer.MAX_VALUE, Math.max(0, resistance + bonus - 1), false, false, true));
    }

    private void refreshSonar(Player player) {
        double radius = plugin.getConfig().getDouble("abilities.the_diver.sonar_radius", 45.0);
        Team team = sonarTeam();
        for (Entity entity : player.getNearbyEntities(radius, radius, radius)) {
            if (!(entity instanceof LivingEntity living) || entity.equals(player)) continue;
            living.addPotionEffect(new PotionEffect(PotionEffects.GLOWING,
                    45, 0, false, false, false));
            team.addEntry(entity.getUniqueId().toString());
        }
    }

    private void clearSonar(Player player) {
        double radius = plugin.getConfig().getDouble("abilities.the_diver.sonar_radius", 45.0) + 15.0;
        Team team = sonarTeam();
        for (Entity entity : player.getNearbyEntities(radius, radius, radius)) {
            if (entity instanceof LivingEntity living) living.removePotionEffect(PotionEffects.GLOWING);
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
