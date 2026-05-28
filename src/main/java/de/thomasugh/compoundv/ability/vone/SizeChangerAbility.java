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
    private final String id;
    private final String displayName;
    private final String configPath;
    private final int color;
    private final NamespacedKey scaleKey;
    private final NamespacedKey healthKey;
    private final Map<UUID, Mode> activeMode = new HashMap<>();
    private final Map<UUID, Long> cooldownUntil = new HashMap<>();
    private final Map<UUID, Long> lastHandledAt = new HashMap<>();
    private final Map<UUID, TaskHandle> revertTasks = new HashMap<>();

    public SizeChangerAbility(CompoundV plugin) {
        this(plugin, "size_changer", "SizeChanger", "abilities.size_changer", 0x9C64FF);
    }

    public SizeChangerAbility(CompoundV plugin, String id, String displayName, String configPath, int color) {
        this.plugin = plugin;
        this.id = id;
        this.displayName = displayName;
        this.configPath = configPath;
        this.color = color;
        this.scaleKey = new NamespacedKey(plugin, id + "_scale");
        this.healthKey = new NamespacedKey(plugin, id + "_hearts");
    }

    @Override public String getId() { return id; }
    @Override public String getDisplayName() { return displayName; }
    @Override public int getColor() { return color; }
    @Override public String getDescriptionKey() { return "ability." + id + ".description"; }

    @Override
    public void apply(Player player) {
        int strength = plugin.getConfig().getInt(path("strength_level"), 1);
        int resistance = plugin.getConfig().getInt(path("resistance_level"), 1);

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
        lastHandledAt.remove(player.getUniqueId());
        player.removePotionEffect(PotionEffects.STRENGTH);
        player.removePotionEffect(PotionEffects.RESISTANCE);
    }

    public void handleSneakLeftClick(Player player) {
        UUID uuid = player.getUniqueId();
        long handledNow = System.currentTimeMillis();
        long lastHandled = lastHandledAt.getOrDefault(uuid, 0L);
        if (handledNow - lastHandled < 250L) return;
        lastHandledAt.put(uuid, handledNow);
        float pitch = player.getLocation().getPitch();
        Mode mode = getMode(player);

        if (mode != Mode.NORMAL) {
            if (pitch <= -35f || pitch >= 35f) {
                revertToNormal(player, true, true);
                return;
            }
            MessageUtil.sendActionBar(player, plugin.getLocaleManager().msg("size_changer.look_up_down"));
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

    public double bigDamageMultiplier() {
        return plugin.getConfig().getDouble(path("big_damage_multiplier"), id.equalsIgnoreCase("size_changer_v_one") ? 1.75 : 1.25);
    }

    public Mode getMode(Player player) {
        return activeMode.getOrDefault(player.getUniqueId(), Mode.NORMAL);
    }

    private void activateBig(Player player) {
        UUID uuid = player.getUniqueId();
        activeMode.put(uuid, Mode.BIG);
        cancelRevertTask(uuid);

        double scaleBonus = plugin.getConfig().getDouble(path("big_scale_bonus"), 1.0);
        double extraHearts = plugin.getConfig().getDouble(path("big_extra_hearts"), 0.0);
        AttributeUtil.setScaleBonus(player, scaleKey, scaleBonus);
        AttributeUtil.setMaxHealthBonus(player, healthKey, extraHearts * 2.0);

        long durationTicks = plugin.getConfig().getLong(path("big_duration_ticks"), 1200L);
        int jumpBoostLevel = plugin.getConfig().getInt(path("big_jump_boost_level"), 0);
        if (jumpBoostLevel > 0) {
            player.addPotionEffect(new PotionEffect(PotionEffects.JUMP_BOOST,
                    (int) Math.min(Integer.MAX_VALUE, durationTicks + 20L),
                    Math.max(0, jumpBoostLevel - 1), false, false, true));
        }
        revertTasks.put(uuid, SchedulerAdapter.runLater(plugin, player, () -> {
            if (player.isOnline() && getMode(player) == Mode.BIG) {
                revertToNormal(player, true, true);
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
        cancelRevertTask(uuid);

        double scaleBonus = plugin.getConfig().getDouble(path("small_scale_bonus"), -0.7142857143);
        AttributeUtil.setScaleBonus(player, scaleKey, scaleBonus);
        AttributeUtil.setMaxHealthBonus(player, healthKey, 0);

        long durationTicks = plugin.getConfig().getLong(path("small_duration_ticks"), 2400L);
        revertTasks.put(uuid, SchedulerAdapter.runLater(plugin, player, () -> {
            if (player.isOnline() && getMode(player) == Mode.SMALL) {
                revertToNormal(player, true, true);
            }
        }, durationTicks));

        player.getWorld().spawnParticle(Particle.POOF, player.getLocation().add(0, 0.6, 0), 35, 0.35, 0.45, 0.35, 0.03);
        player.playSound(player.getLocation(), Sound.ENTITY_ENDERMAN_TELEPORT, 0.55f, 1.65f);
        MessageUtil.sendActionBar(player, plugin.getLocaleManager().msg("size_changer.small_on"));
    }

    private void revertToNormal(Player player, boolean notify) {
        revertToNormal(player, notify, false);
    }

    private void revertToNormal(Player player, boolean notify, boolean beginCooldown) {
        UUID uuid = player.getUniqueId();
        cancelRevertTask(uuid);
        activeMode.put(uuid, Mode.NORMAL);
        AttributeUtil.setScaleBonus(player, scaleKey, 0);
        AttributeUtil.setMaxHealthBonus(player, healthKey, 0);
        player.removePotionEffect(PotionEffects.JUMP_BOOST);
        if (beginCooldown) {
            startCooldown(player);
        }
        if (notify) {
            player.getWorld().spawnParticle(Particle.POOF, player.getLocation().add(0, 0.9, 0), 25, 0.35, 0.5, 0.35, 0.03);
            player.playSound(player.getLocation(), Sound.BLOCK_BEACON_DEACTIVATE, 0.45f, 1.3f);
            MessageUtil.sendActionBar(player, plugin.getLocaleManager().msg("size_changer.normal"));
        }
    }

    private void startCooldown(Player player) {
        long cooldownMs = plugin.getConfig().getLong(path("cooldown_ms"), 60000L);
        cooldownUntil.put(player.getUniqueId(), System.currentTimeMillis() + Math.max(0L, cooldownMs));
    }

    private void cancelRevertTask(UUID uuid) {
        TaskHandle task = revertTasks.remove(uuid);
        if (task != null) task.cancel();
    }

    private String path(String key) {
        return configPath + "." + key;
    }
}
