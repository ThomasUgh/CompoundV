package de.thomasugh.compoundv.ability.compoundv;

import de.thomasugh.compoundv.CompoundV;
import de.thomasugh.compoundv.ability.Ability;
import de.thomasugh.compoundv.util.MessageUtil;
import de.thomasugh.compoundv.util.PotionEffects;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public class TheGhostAbility implements Ability {

    private final CompoundV plugin;
    private final Set<UUID> invisible = new HashSet<>();
    private final Map<UUID, Integer> targetClearTicker = new HashMap<>();
    private final Map<UUID, Long> toggleCooldown = new HashMap<>();

    public TheGhostAbility(CompoundV plugin) {
        this.plugin = plugin;
    }

    @Override public String getId() { return "the_ghost"; }
    @Override public String getDisplayName() { return "The Ghost"; }
    @Override public int getColor() { return 0xAAAAAA; }
    @Override public boolean hasToggle() { return true; }
    @Override public boolean needsTick() { return true; }

    @Override
    public void apply(Player player) {
        int resistance = plugin.getConfig().getInt("abilities.the_ghost.resistance_level", 2);
        int strength = plugin.getConfig().getInt("abilities.the_ghost.strength_level", 1);
        boolean fireResistance = plugin.getConfig().getBoolean("abilities.the_ghost.fire_resistance", true);

        if (resistance > 0) {
            player.addPotionEffect(new PotionEffect(PotionEffects.RESISTANCE,
                    Integer.MAX_VALUE, resistance - 1, false, false, true));
        }
        if (strength > 0) {
            player.addPotionEffect(new PotionEffect(PotionEffects.STRENGTH,
                    Integer.MAX_VALUE, strength - 1, false, false, true));
        }
        if (fireResistance) {
            player.addPotionEffect(new PotionEffect(PotionEffects.FIRE_RESISTANCE,
                    Integer.MAX_VALUE, 0, false, false, true));
        }
    }

    @Override
    public void remove(Player player) {
        UUID uuid = player.getUniqueId();
        invisible.remove(uuid);
        targetClearTicker.remove(uuid);
        toggleCooldown.remove(uuid);
        player.removePotionEffect(PotionEffects.INVISIBILITY);
        player.removePotionEffect(PotionEffects.RESISTANCE);
        player.removePotionEffect(PotionEffects.STRENGTH);
        player.removePotionEffect(PotionEffects.FIRE_RESISTANCE);
    }

    @Override
    public void onTick(Player player) {
        if (!hidesFromMobs() || !isInvisible(player)) return;
        int tick = targetClearTicker.merge(player.getUniqueId(), 1, Integer::sum);
        int period = Math.max(1, plugin.getConfig().getInt("abilities.the_ghost.mob_target_clear_period_ticks", 10));
        if (tick % period == 0) clearMobTargets(player);
    }

    @Override
    public void onToggle(Player player) {
        toggleGhostMode(player);
    }

    @Override
    public String getDescriptionKey() {
        return "ability.the_ghost.description";
    }

    public boolean isInvisible(Player player) {
        return invisible.contains(player.getUniqueId()) && player.hasPotionEffect(PotionEffects.INVISIBILITY);
    }

    public boolean hidesFromMobs() {
        return plugin.getConfig().getBoolean("abilities.the_ghost.hide_from_mobs", true);
    }

    public void toggleGhostMode(Player player) {
        UUID uuid = player.getUniqueId();
        long now = System.currentTimeMillis();
        long readyAt = toggleCooldown.getOrDefault(uuid, 0L);
        if (readyAt > now) {
            long seconds = Math.max(1L, (long) Math.ceil((readyAt - now) / 1000.0));
            MessageUtil.sendActionBar(player, plugin.getLocaleManager().msg("toggle.ghost_cooldown", "seconds", Long.toString(seconds)));
            return;
        }

        boolean next = !invisible.contains(uuid);
        long cooldownMs = plugin.getConfig().getLong("abilities.the_ghost.toggle_cooldown_ms", 5000L);
        toggleCooldown.put(uuid, now + Math.max(0L, cooldownMs));
        if (next) {
            invisible.add(uuid);
            targetClearTicker.put(uuid, 0);
            player.addPotionEffect(new PotionEffect(PotionEffects.INVISIBILITY,
                    Integer.MAX_VALUE, 1, false, false, false));
            clearMobTargets(player);
            player.getWorld().spawnParticle(Particle.LARGE_SMOKE, player.getLocation().add(0, 1, 0),
                    25, 0.35, 0.55, 0.35, 0.035);
            player.playSound(player.getLocation(), Sound.ENTITY_ENDERMAN_TELEPORT, 0.6f, 1.7f);
            MessageUtil.sendActionBar(player, plugin.getLocaleManager().msg("toggle.ghost_on"));
        } else {
            invisible.remove(uuid);
            targetClearTicker.remove(uuid);
            player.removePotionEffect(PotionEffects.INVISIBILITY);
            player.getWorld().spawnParticle(Particle.POOF, player.getLocation().add(0, 1, 0),
                    25, 0.35, 0.55, 0.35, 0.03);
            player.playSound(player.getLocation(), Sound.BLOCK_BEACON_DEACTIVATE, 0.5f, 1.5f);
            MessageUtil.sendActionBar(player, plugin.getLocaleManager().msg("toggle.ghost_off"));
        }
    }

    private void clearMobTargets(Player player) {
        double radius = plugin.getConfig().getDouble("abilities.the_ghost.mob_target_clear_radius", 48.0);
        for (Entity entity : player.getNearbyEntities(radius, radius, radius)) {
            if (entity instanceof Mob mob && player.equals(mob.getTarget())) {
                mob.setTarget(null);
            }
        }
    }
}
