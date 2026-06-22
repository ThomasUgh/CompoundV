package de.thomasugh.compoundv.ability.vone;

import de.thomasugh.compoundv.CompoundV;
import de.thomasugh.compoundv.ability.Ability;
import de.thomasugh.compoundv.server.SchedulerAdapter;
import de.thomasugh.compoundv.server.TaskHandle;
import de.thomasugh.compoundv.util.AbilityKillTracker;
import de.thomasugh.compoundv.util.AttributeUtil;
import de.thomasugh.compoundv.util.MessageUtil;
import de.thomasugh.compoundv.util.PotionEffects;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
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
    private final NamespacedKey stepKey;
    private final Map<UUID, Mode> activeMode = new HashMap<>();
    private final Map<UUID, Long> cooldownUntil = new HashMap<>();
    private final Map<UUID, Long> lastHandledAt = new HashMap<>();
    private final Map<UUID, TaskHandle> revertTasks = new HashMap<>();
    private final Map<UUID, TaskHandle> stompTasks = new HashMap<>();
    private final Map<UUID, Map<UUID, Long>> stompCooldowns = new HashMap<>();
    private final Map<UUID, Long> lastStepSoundAt = new HashMap<>();
    private final Map<UUID, Location> lastStepLocation = new HashMap<>();

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
        this.stepKey = new NamespacedKey(plugin, id + "_step");
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

        double stepBonus = plugin.getConfig().getDouble(path("big_step_height_bonus"),
                id.equalsIgnoreCase("size_changer_v_one") ? 0.9 : 0.4);
        AttributeUtil.setStepHeightBonus(player, stepKey, stepBonus);

        int jumpBoostLevel = plugin.getConfig().getInt(path("big_jump_boost_level"),
                id.equalsIgnoreCase("size_changer_v_one") ? 4 : 2);
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
        startStompTask(player);
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
        cancelStompTask(uuid);
        activeMode.put(uuid, Mode.NORMAL);
        AttributeUtil.setScaleBonus(player, scaleKey, 0);
        AttributeUtil.setMaxHealthBonus(player, healthKey, 0);
        AttributeUtil.setStepHeightBonus(player, stepKey, 0);
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

    private void startStompTask(Player player) {
        UUID uuid = player.getUniqueId();
        cancelStompTask(uuid);
        if (!plugin.getConfig().getBoolean(path("stomp_enabled"), true)) {
            return;
        }
        long period = Math.max(1L, plugin.getConfig().getLong(path("stomp_check_period_ticks"), 2L));
        stompTasks.put(uuid, SchedulerAdapter.runTimer(plugin, player, () -> {
            if (!player.isOnline() || getMode(player) != Mode.BIG) {
                cancelStompTask(uuid);
                return;
            }
            tryWalkStomp(player);
            tryStomp(player);
        }, period, period));
    }

    private void tryStomp(Player player) {
        if (player.isFlying()) {
            return;
        }
        boolean descending = player.getVelocity().getY() < -0.08 || player.getFallDistance() > 0.4f;
        if (!descending) {
            return;
        }

        double minHearts = plugin.getConfig().getDouble(path("stomp_min_hearts"), 0.5);
        double maxHearts = Math.max(minHearts, plugin.getConfig().getDouble(path("stomp_max_hearts"), 1.0));
        double hearts = minHearts + Math.random() * (maxHearts - minHearts);
        double damage = Math.max(0.0, hearts * 2.0);
        if (damage <= 0.0) {
            return;
        }

        double horiz = plugin.getConfig().getDouble(path("stomp_horizontal_range"), 0.9);
        double below = plugin.getConfig().getDouble(path("stomp_below_range"), 1.2);
        long cdMs = Math.max(0L, plugin.getConfig().getLong(path("stomp_cooldown_ms"), 500L));
        double feetY = player.getLocation().getY();
        long now = System.currentTimeMillis();
        Map<UUID, Long> cds = stompCooldowns.computeIfAbsent(player.getUniqueId(), k -> new HashMap<>());

        boolean hitAny = false;
        for (Entity entity : player.getNearbyEntities(horiz, below, horiz)) {
            if (!(entity instanceof LivingEntity living)) {
                continue;
            }
            if (entity.getUniqueId().equals(player.getUniqueId()) || living.isDead() || !living.isValid()) {
                continue;
            }
            if (living.getLocation().getY() > feetY + 0.1) {
                continue;
            }
            Long until = cds.get(entity.getUniqueId());
            if (until != null && until > now) {
                continue;
            }
            cds.put(entity.getUniqueId(), now + cdMs);
            boolean landed = AbilityKillTracker.damage(plugin, living, player, damage,
                    "death_messages.size_changer_stomp", true);
            if (landed) {
                hitAny = true;
                living.getWorld().spawnParticle(Particle.BLOCK, living.getLocation().add(0, 0.1, 0),
                        10, 0.25, 0.05, 0.25, 0.0, Material.DIRT.createBlockData());
            }
        }

        if (hitAny) {
            player.getWorld().playSound(player.getLocation(), Sound.ENTITY_GENERIC_BIG_FALL, 0.55f, 0.8f);
            player.setFallDistance(0f);
        }
    }

    private void tryWalkStomp(Player player) {
        if (!plugin.getConfig().getBoolean(path("stomp_walk_sound_enabled"), true)) {
            return;
        }
        UUID uuid = player.getUniqueId();
        Location current = player.getLocation();
        Location previous = lastStepLocation.put(uuid, current.clone());
        if (current.getWorld() == null) {
            return;
        }
        if (previous == null || previous.getWorld() == null || !previous.getWorld().equals(current.getWorld())) {
            return;
        }

        Material ground = current.clone().subtract(0, 0.2, 0).getBlock().getType();
        if (ground.isAir() || !ground.isSolid()) {
            return;
        }

        double dx = current.getX() - previous.getX();
        double dz = current.getZ() - previous.getZ();
        double horizontal = Math.sqrt(dx * dx + dz * dz);
        if (horizontal < Math.max(0.0, plugin.getConfig().getDouble(path("stomp_walk_min_distance"), 0.06))) {
            return;
        }

        long intervalMs = Math.max(0L, plugin.getConfig().getLong(path("stomp_walk_interval_ms"), 320L));
        long now = System.currentTimeMillis();
        if (now - lastStepSoundAt.getOrDefault(uuid, 0L) < intervalMs) {
            return;
        }
        lastStepSoundAt.put(uuid, now);

        String sound = plugin.getConfig().getString(path("stomp_walk_sound"), "entity.iron_golem.step");
        if (sound == null || sound.isBlank()) {
            return;
        }
        float volume = (float) plugin.getConfig().getDouble(path("stomp_walk_sound_volume"), 0.85);
        float pitch = (float) plugin.getConfig().getDouble(path("stomp_walk_sound_pitch"), 0.8);
        current.getWorld().playSound(current, sound, Math.max(0.0f, volume), Math.max(0.01f, pitch));
        current.getWorld().spawnParticle(Particle.CLOUD, current.clone().add(0, 0.05, 0), 4, 0.22, 0.02, 0.22, 0.01);
    }

    private void cancelStompTask(UUID uuid) {
        TaskHandle task = stompTasks.remove(uuid);
        if (task != null) task.cancel();
        stompCooldowns.remove(uuid);
        lastStepSoundAt.remove(uuid);
        lastStepLocation.remove(uuid);
    }

    private void cancelRevertTask(UUID uuid) {
        TaskHandle task = revertTasks.remove(uuid);
        if (task != null) task.cancel();
    }

    private String path(String key) {
        return configPath + "." + key;
    }
}
