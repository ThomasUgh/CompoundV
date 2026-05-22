package de.thomasugh.compoundv.ability.vone;

import de.thomasugh.compoundv.CompoundV;
import de.thomasugh.compoundv.ability.Ability;
import de.thomasugh.compoundv.server.SchedulerAdapter;
import de.thomasugh.compoundv.server.TaskHandle;
import de.thomasugh.compoundv.util.AttributeUtil;
import de.thomasugh.compoundv.util.MessageUtil;
import de.thomasugh.compoundv.util.PotionEffects;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class SizeChangerAbility implements Ability {

    public enum Mode {
        NORMAL,
        BIG,
        SMALL
    }

    private final CompoundV plugin;
    private final NamespacedKey scaleKey;
    private final NamespacedKey healthKey;
    private final Map<UUID, Mode> activeMode = new HashMap<>();
    private final Map<UUID, Long> cooldownUntil = new HashMap<>();
    private final Map<UUID, TaskHandle> revertTasks = new HashMap<>();

    public SizeChangerAbility(CompoundV plugin) {
        this.plugin = plugin;
        this.scaleKey = new NamespacedKey(plugin, "size_changer_scale");
        this.healthKey = new NamespacedKey(plugin, "size_changer_hearts");
    }

    @Override public String getId() { return "size_changer"; }
    @Override public String getDisplayName() { return "SizeChanger"; }
    @Override public int getColor() { return 0x9C64FF; }

    @Override
    public void apply(Player player) {
        int strength = plugin.getConfig().getInt("abilities.size_changer.strength_level", 2);
        int resistance = plugin.getConfig().getInt("abilities.size_changer.resistance_level", 2);

        player.addPotionEffect(new PotionEffect(PotionEffects.STRENGTH,
                Integer.MAX_VALUE, Math.max(0, strength - 1), false, false, true));
        player.addPotionEffect(new PotionEffect(PotionEffects.RESISTANCE,
                Integer.MAX_VALUE, Math.max(0, resistance - 1), false, false, true));
        activeMode.put(player.getUniqueId(), Mode.NORMAL);
    }

    @Override
    public void remove(Player player) {
        revertToNormal(player, false);
        cooldownUntil.remove(player.getUniqueId());
        player.removePotionEffect(PotionEffects.STRENGTH);
        player.removePotionEffect(PotionEffects.RESISTANCE);
    }

    public void handleSneakLeftClick(Player player) {
        UUID uuid = player.getUniqueId();
        if (getMode(player) != Mode.NORMAL) {
            MessageUtil.sendActionBar(player, plugin.getLocaleManager().msg("size_changer.already_active"));
            return;
        }

        long now = System.currentTimeMillis();
        long readyAt = cooldownUntil.getOrDefault(uuid, 0L);
        if (readyAt > now) {
            long seconds = Math.max(1L, (long) Math.ceil((readyAt - now) / 1000.0));
            MessageUtil.sendActionBar(player, plugin.getLocaleManager().msg("size_changer.cooldown",
                    "seconds", Long.toString(seconds)));
            return;
        }

        float pitch = player.getLocation().getPitch();
        if (pitch <= -35f) {
            activateBig(player);
        } else if (pitch >= 35f) {
            activateSmall(player);
        } else {
            MessageUtil.sendActionBar(player, plugin.getLocaleManager().msg("size_changer.look_up_down"));
        }
    }

    public boolean isBig(Player player) {
        return getMode(player) == Mode.BIG;
    }

    public Mode getMode(Player player) {
        return activeMode.getOrDefault(player.getUniqueId(), Mode.NORMAL);
    }

    private void activateBig(Player player) {
        UUID uuid = player.getUniqueId();
        activeMode.put(uuid, Mode.BIG);
        startCooldown(player);
        cancelRevertTask(uuid);

        double scaleBonus = plugin.getConfig().getDouble("abilities.size_changer.big_scale_bonus", 1.0);
        double extraHearts = plugin.getConfig().getDouble("abilities.size_changer.big_extra_hearts", 10.0);
        AttributeUtil.setScaleBonus(player, scaleKey, scaleBonus);
        AttributeUtil.setMaxHealthBonus(player, healthKey, extraHearts * 2.0);

        long durationTicks = plugin.getConfig().getLong("abilities.size_changer.big_duration_ticks", 1200L);
        revertTasks.put(uuid, SchedulerAdapter.runLater(plugin, () -> {
            if (player.isOnline() && getMode(player) == Mode.BIG) {
                revertToNormal(player, true);
            }
        }, durationTicks));

        player.getWorld().spawnParticle(Particle.EXPLOSION, player.getLocation().add(0, 1, 0), 2, 0.25, 0.4, 0.25, 0);
        player.getWorld().spawnParticle(Particle.DUST, player.getLocation().add(0, 1, 0), 30, 0.5, 0.8, 0.5, 0,
                new Particle.DustOptions(org.bukkit.Color.fromRGB(155, 100, 255), 1.2f));
        player.playSound(player.getLocation(), Sound.ENTITY_ENDER_DRAGON_GROWL, 0.55f, 1.4f);
        MessageUtil.sendActionBar(player, plugin.getLocaleManager().msg("size_changer.big_on"));
    }

    private void activateSmall(Player player) {
        UUID uuid = player.getUniqueId();
        activeMode.put(uuid, Mode.SMALL);
        startCooldown(player);
        cancelRevertTask(uuid);

        double scaleBonus = plugin.getConfig().getDouble("abilities.size_changer.small_scale_bonus", -0.5);
        AttributeUtil.setScaleBonus(player, scaleKey, scaleBonus);
        AttributeUtil.setMaxHealthBonus(player, healthKey, 0);

        long durationTicks = plugin.getConfig().getLong("abilities.size_changer.small_duration_ticks", 2400L);
        revertTasks.put(uuid, SchedulerAdapter.runLater(plugin, () -> {
            if (player.isOnline() && getMode(player) == Mode.SMALL) {
                revertToNormal(player, true);
            }
        }, durationTicks));

        player.getWorld().spawnParticle(Particle.POOF, player.getLocation().add(0, 0.6, 0), 35, 0.35, 0.45, 0.35, 0.03);
        player.playSound(player.getLocation(), Sound.ENTITY_ENDERMAN_TELEPORT, 0.55f, 1.65f);
        MessageUtil.sendActionBar(player, plugin.getLocaleManager().msg("size_changer.small_on"));
    }

    private void revertToNormal(Player player, boolean notify) {
        UUID uuid = player.getUniqueId();
        cancelRevertTask(uuid);
        activeMode.put(uuid, Mode.NORMAL);
        AttributeUtil.setScaleBonus(player, scaleKey, 0);
        AttributeUtil.setMaxHealthBonus(player, healthKey, 0);
        if (notify) {
            player.getWorld().spawnParticle(Particle.POOF, player.getLocation().add(0, 0.9, 0), 25, 0.35, 0.5, 0.35, 0.03);
            player.playSound(player.getLocation(), Sound.BLOCK_BEACON_DEACTIVATE, 0.45f, 1.3f);
            MessageUtil.sendActionBar(player, plugin.getLocaleManager().msg("size_changer.normal"));
        }
    }

    private void startCooldown(Player player) {
        long cooldownMs = plugin.getConfig().getLong("abilities.size_changer.cooldown_ms", 60000L);
        cooldownUntil.put(player.getUniqueId(), System.currentTimeMillis() + Math.max(0L, cooldownMs));
    }

    private void cancelRevertTask(UUID uuid) {
        TaskHandle task = revertTasks.remove(uuid);
        if (task != null) task.cancel();
    }
}
