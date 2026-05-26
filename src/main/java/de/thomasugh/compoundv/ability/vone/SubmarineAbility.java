package de.thomasugh.compoundv.ability.vone;

import de.thomasugh.compoundv.CompoundV;
import de.thomasugh.compoundv.ability.Ability;
import de.thomasugh.compoundv.util.MessageUtil;
import de.thomasugh.compoundv.util.PotionEffects;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.scoreboard.Team;
import org.bukkit.util.Vector;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public class SubmarineAbility implements Ability {

    private final CompoundV plugin;
    private final Map<UUID, Boolean> sonarActive = new HashMap<>();
    private final Map<UUID, Integer> ticker = new HashMap<>();
    private final Map<UUID, Long> dashCooldown = new HashMap<>();
    private final Map<UUID, Set<UUID>> visibleTargets = new HashMap<>();

    public SubmarineAbility(CompoundV plugin) {
        this.plugin = plugin;
    }

    @Override public String getId() { return "submarine"; }
    @Override public String getDisplayName() { return "Submarine"; }
    @Override public int getColor() { return 0x0077C8; }
    @Override public boolean hasToggle() { return true; }
    @Override public boolean needsTick() { return true; }

    @Override
    public void apply(Player player) {
        applyStaticEffects(player);
        applyWaterCombatEffects(player);
    }

    @Override
    public void onToggle(Player player) {
        useRiptide(player);
    }

    @Override
    public void remove(Player player) {
        clearSonar(player);
        sonarActive.remove(player.getUniqueId());
        ticker.remove(player.getUniqueId());
        dashCooldown.remove(player.getUniqueId());
        player.removePotionEffect(PotionEffects.WATER_BREATHING);
        player.removePotionEffect(PotionEffects.DOLPHINS_GRACE);
        player.removePotionEffect(PotionEffects.CONDUIT_POWER);
        player.removePotionEffect(PotionEffects.STRENGTH);
        player.removePotionEffect(PotionEffects.RESISTANCE);
        player.removePotionEffect(PotionEffects.REGENERATION);
        player.removePotionEffect(PotionEffects.NIGHT_VISION);
    }

    @Override
    public void onTick(Player player) {
        int tick = ticker.merge(player.getUniqueId(), 1, Integer::sum);
        if (tick % 20 == 0) {
            applyStaticEffects(player);
            applyWaterCombatEffects(player);
        }

        if (!sonarActive.getOrDefault(player.getUniqueId(), false)) return;
        if (!isWaterActive(player)) {
            sonarActive.put(player.getUniqueId(), false);
            clearSonar(player);
            player.removePotionEffect(PotionEffects.NIGHT_VISION);
            MessageUtil.sendActionBar(player, plugin.getLocaleManager().msg("submarine.sonar_water_only"));
            return;
        }
        if (tick % 10 == 0) refreshSonar(player);
    }

    public void toggleSonar(Player player) {
        if (!isWaterActive(player)) {
            MessageUtil.sendActionBar(player, plugin.getLocaleManager().msg("submarine.sonar_water_only"));
            player.playSound(player.getLocation(), Sound.BLOCK_BUBBLE_COLUMN_WHIRLPOOL_INSIDE, 0.55f, 0.55f);
            return;
        }

        boolean next = !sonarActive.getOrDefault(player.getUniqueId(), false);
        sonarActive.put(player.getUniqueId(), next);
        if (next) {
            refreshSonar(player);
            player.addPotionEffect(new PotionEffect(PotionEffects.NIGHT_VISION, Integer.MAX_VALUE, 0, false, false, false));
            MessageUtil.sendActionBar(player, plugin.getLocaleManager().msg("submarine.sonar_on"));
            player.playSound(player.getLocation(), Sound.ITEM_TRIDENT_THUNDER, 0.75f, 1.35f);
        } else {
            clearSonar(player);
            player.removePotionEffect(PotionEffects.NIGHT_VISION);
            MessageUtil.sendActionBar(player, plugin.getLocaleManager().msg("submarine.sonar_off"));
            player.playSound(player.getLocation(), Sound.ENTITY_DOLPHIN_SPLASH, 0.5f, 0.8f);
        }
    }

    public void useRiptide(Player player) {
        if (!canUseDash(player)) {
            MessageUtil.sendActionBar(player, plugin.getLocaleManager().msg("submarine.dash_water_or_rain"));
            player.playSound(player.getLocation(), Sound.BLOCK_BUBBLE_COLUMN_WHIRLPOOL_INSIDE, 0.45f, 0.6f);
            return;
        }

        long now = System.currentTimeMillis();
        long cooldownMs = plugin.getConfig().getLong("abilities.submarine.riptide_cooldown_ms", 1000L);
        long readyAt = dashCooldown.getOrDefault(player.getUniqueId(), 0L);
        if (readyAt > now) {
            long seconds = Math.max(1L, (long) Math.ceil((readyAt - now) / 1000.0));
            MessageUtil.sendActionBar(player, plugin.getLocaleManager().msg("submarine.dash_cooldown", "seconds", Long.toString(seconds)));
            return;
        }

        double velocity = plugin.getConfig().getDouble("abilities.submarine.riptide_velocity", 10.0);
        double verticalBoost = plugin.getConfig().getDouble("abilities.submarine.riptide_vertical_boost", 0.35);
        Vector direction = player.getLocation().getDirection().normalize().multiply(velocity);
        if (direction.getY() < verticalBoost) direction.setY(verticalBoost);
        player.setVelocity(direction);
        player.setFallDistance(0f);
        dashCooldown.put(player.getUniqueId(), now + Math.max(0L, cooldownMs));
        player.getWorld().playSound(player.getLocation(), Sound.ITEM_TRIDENT_RIPTIDE_3, 1.0f, 0.9f);
        player.getWorld().spawnParticle(resolveParticle("SPLASH", "WATER_SPLASH", "CLOUD"), player.getLocation().add(0, 1.0, 0), 80, 0.75, 0.65, 0.75, 0.18);
        MessageUtil.sendActionBar(player, plugin.getLocaleManager().msg("submarine.dash"));
    }

    private void applyStaticEffects(Player player) {
        player.addPotionEffect(new PotionEffect(PotionEffects.WATER_BREATHING, Integer.MAX_VALUE, 0, false, false, true));
        player.addPotionEffect(new PotionEffect(PotionEffects.DOLPHINS_GRACE, Integer.MAX_VALUE,
                Math.max(0, plugin.getConfig().getInt("abilities.submarine.dolphins_grace_level", 10) - 1), false, false, true));
        player.addPotionEffect(new PotionEffect(PotionEffects.CONDUIT_POWER, Integer.MAX_VALUE,
                Math.max(0, plugin.getConfig().getInt("abilities.submarine.conduit_power_level", 2) - 1), false, false, true));
        int resistance = plugin.getConfig().getInt("abilities.submarine.resistance_level", 2);
        player.addPotionEffect(new PotionEffect(PotionEffects.RESISTANCE, Integer.MAX_VALUE, Math.max(0, resistance - 1), false, false, true));
    }

    private void applyWaterCombatEffects(Player player) {
        int strength = isWaterActive(player)
                ? plugin.getConfig().getInt("abilities.submarine.water_strength_level", 5)
                : plugin.getConfig().getInt("abilities.submarine.strength_level", 2);
        player.addPotionEffect(new PotionEffect(PotionEffects.STRENGTH, Integer.MAX_VALUE, Math.max(0, strength - 1), false, false, true));
        if (isWaterActive(player)) {
            int regeneration = plugin.getConfig().getInt("abilities.submarine.water_regeneration_level", 2);
            player.addPotionEffect(new PotionEffect(PotionEffects.REGENERATION, 45, Math.max(0, regeneration - 1), false, false, true));
        } else {
            player.removePotionEffect(PotionEffects.REGENERATION);
        }
    }

    private void refreshSonar(Player player) {
        double radius = plugin.getConfig().getDouble("abilities.submarine.sonar_radius", 65.0);
        Team team = sonarTeam();
        Set<UUID> currentTargets = new HashSet<>();
        Particle.DustOptions dust = new Particle.DustOptions(Color.fromRGB(0, 190, 255), 1.05f);
        for (Entity entity : player.getNearbyEntities(radius, radius, radius)) {
            if (!(entity instanceof LivingEntity living) || entity.equals(player)) continue;
            currentTargets.add(living.getUniqueId());
            living.addPotionEffect(new PotionEffect(PotionEffects.GLOWING, 45, 0, false, false, false));
            team.addEntry(entity.getUniqueId().toString());
            Location loc = living.getLocation().add(0, Math.min(1.3, Math.max(0.7, living.getEyeHeight())), 0);
            player.spawnParticle(Particle.DUST, loc, 8, 0.28, 0.42, 0.28, 0, dust);
            player.spawnParticle(Particle.BUBBLE_POP, loc, 6, 0.20, 0.24, 0.20, 0.01);
        }
        clearStaleSonarGlow(player, currentTargets);
        visibleTargets.put(player.getUniqueId(), currentTargets);
    }

    private void clearSonar(Player player) {
        Set<UUID> oldTargets = visibleTargets.remove(player.getUniqueId());
        if (oldTargets == null) return;
        Team team = sonarTeam();
        for (UUID targetId : oldTargets) {
            Entity entity = Bukkit.getEntity(targetId);
            if (entity instanceof LivingEntity living) living.removePotionEffect(PotionEffects.GLOWING);
            team.removeEntry(targetId.toString());
        }
    }

    private void clearStaleSonarGlow(Player player, Set<UUID> currentTargets) {
        Set<UUID> oldTargets = visibleTargets.get(player.getUniqueId());
        if (oldTargets == null) return;
        Team team = sonarTeam();
        for (UUID targetId : oldTargets) {
            if (currentTargets.contains(targetId)) continue;
            Entity entity = Bukkit.getEntity(targetId);
            if (entity instanceof LivingEntity living) living.removePotionEffect(PotionEffects.GLOWING);
            team.removeEntry(targetId.toString());
        }
    }

    private boolean canUseDash(Player player) {
        return isWaterActive(player) || isExposedToRain(player);
    }

    private boolean isWaterActive(Player player) {
        Material feet = player.getLocation().getBlock().getType();
        Material eyes = player.getEyeLocation().getBlock().getType();
        return player.isInWater()
                || player.isSwimming()
                || feet == Material.WATER
                || eyes == Material.WATER;
    }

    private boolean isExposedToRain(Player player) {
        World world = player.getWorld();
        if (!world.hasStorm()) return false;
        return player.getLocation().getBlockY() >= world.getHighestBlockYAt(player.getLocation());
    }

    private Particle resolveParticle(String... names) {
        for (String name : names) {
            try { return Particle.valueOf(name); } catch (IllegalArgumentException ignored) { }
        }
        return Particle.CLOUD;
    }

    @SuppressWarnings("deprecation")
    private Team sonarTeam() {
        var sb = Bukkit.getScoreboardManager().getMainScoreboard();
        Team team = sb.getTeam("cv_submarine_sonar");
        if (team == null) {
            team = sb.registerNewTeam("cv_submarine_sonar");
            team.setColor(ChatColor.AQUA);
        }
        return team;
    }
}
