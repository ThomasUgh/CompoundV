package de.thomasugh.compoundv.ability.compoundv;

import de.thomasugh.compoundv.CompoundV;
import de.thomasugh.compoundv.ability.Ability;
import de.thomasugh.compoundv.server.SchedulerAdapter;
import de.thomasugh.compoundv.util.MessageUtil;
import de.thomasugh.compoundv.util.PotionEffects;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class JumperAbility implements Ability {

    private final CompoundV plugin;
    private final Map<UUID, Long> cooldownUntil = new HashMap<>();
    private final Map<UUID, Long> activeUntil = new HashMap<>();

    public JumperAbility(CompoundV plugin) {
        this.plugin = plugin;
    }

    @Override public String getId() { return "jumper"; }
    @Override public String getDisplayName() { return "Jumper"; }
    @Override public int getColor() { return 0x7DFF6A; }
    @Override public boolean hasToggle() { return true; }

    @Override
    public void apply(Player player) {
        int strength = plugin.getConfig().getInt("abilities.jumper.strength_level", 1);
        if (strength > 0) {
            player.addPotionEffect(new PotionEffect(PotionEffects.STRENGTH,
                    Integer.MAX_VALUE, Math.max(0, strength - 1), false, false, true));
        }
    }

    @Override
    public void remove(Player player) {
        cooldownUntil.remove(player.getUniqueId());
        activeUntil.remove(player.getUniqueId());
        player.removePotionEffect(PotionEffects.STRENGTH);
        player.removePotionEffect(PotionEffects.JUMP_BOOST);
    }

    @Override
    public void onToggle(Player player) {
        UUID uuid = player.getUniqueId();
        long now = System.currentTimeMillis();
        long activeReadyAt = activeUntil.getOrDefault(uuid, 0L);
        if (activeReadyAt > now) {
            long seconds = Math.max(1L, (long) Math.ceil((activeReadyAt - now) / 1000.0));
            MessageUtil.sendActionBar(player, plugin.getLocaleManager().msg("jumper.active", "seconds", Long.toString(seconds)));
            return;
        }

        long readyAt = cooldownUntil.getOrDefault(uuid, 0L);
        if (readyAt > now) {
            long seconds = Math.max(1L, (long) Math.ceil((readyAt - now) / 1000.0));
            MessageUtil.sendActionBar(player, plugin.getLocaleManager().msg("jumper.cooldown", "seconds", Long.toString(seconds)));
            return;
        }

        long durationTicks = plugin.getConfig().getLong("abilities.jumper.active_ticks", 200L);
        long durationMs = Math.max(1L, durationTicks) * 50L;
        long cooldownMs = plugin.getConfig().getLong("abilities.jumper.cooldown_ms", 15000L);
        activeUntil.put(uuid, now + durationMs);
        cooldownUntil.put(uuid, now + Math.max(durationMs, cooldownMs));

        int jumpBoost = plugin.getConfig().getInt("abilities.jumper.jump_boost_level", 5);
        if (jumpBoost > 0) {
            player.addPotionEffect(new PotionEffect(PotionEffects.JUMP_BOOST,
                    (int) Math.min(Integer.MAX_VALUE, durationTicks), Math.max(0, jumpBoost - 1), false, false, true));
        }

        player.getWorld().playSound(player.getLocation(), Sound.ENTITY_SLIME_JUMP, 0.75f, 0.8f);
        player.getWorld().playSound(player.getLocation(), Sound.BLOCK_AMETHYST_BLOCK_CHIME, 0.45f, 1.8f);
        player.getWorld().spawnParticle(Particle.CLOUD, player.getLocation().add(0, 0.15, 0), 26, 0.6, 0.08, 0.6, 0.08);
        MessageUtil.sendActionBar(player, plugin.getLocaleManager().msg("jumper.activated"));

        SchedulerAdapter.runLater(plugin, () -> {
            if (player.isOnline()) {
                activeUntil.remove(uuid);
                player.removePotionEffect(PotionEffects.JUMP_BOOST);
            }
        }, durationTicks);
    }

    public boolean preventsFallDamage(Player player) {
        return activeUntil.getOrDefault(player.getUniqueId(), 0L) > System.currentTimeMillis();
    }
}
