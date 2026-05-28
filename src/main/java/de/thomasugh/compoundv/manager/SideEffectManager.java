package de.thomasugh.compoundv.manager;

import de.thomasugh.compoundv.CompoundV;
import de.thomasugh.compoundv.data.CompoundPotion;
import de.thomasugh.compoundv.data.PlayerAbilityData;
import de.thomasugh.compoundv.server.SchedulerAdapter;
import de.thomasugh.compoundv.util.PotionEffects;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

public class SideEffectManager {

    private final CompoundV plugin;
    private final Map<UUID, Deque<Long>> tempVUses = new HashMap<>();

    public SideEffectManager(CompoundV plugin) {
        this.plugin = plugin;
    }

    public boolean shouldBlockRoll(Player player, CompoundPotion type) {
        return false;
    }

    public void resetTempVLifeCycle(UUID uuid) {
        if (uuid != null) {
            tempVUses.remove(uuid);
        }
    }

    public void resetTempVLifeCycle(Player player) {
        if (player != null) {
            resetTempVLifeCycle(player.getUniqueId());
        }
    }

    public void afterSuccessfulRoll(Player player, CompoundPotion type) {
        if (!plugin.getConfig().getBoolean("side_effects.enabled", true)) return;

        if (type == CompoundPotion.TEMP_V) {
            int uses = recordTempUse(player.getUniqueId());
            int lethalUseCount = plugin.getConfig().getInt("side_effects.temp_v.lethal_use_count", 5);
            if (lethalUseCount > 0 && uses >= lethalUseCount) {
                applyTempVLethalCollapse(player);
                return;
            }
            rollTempVImmediate(player, uses);
            return;
        }

        if (type == CompoundPotion.COMPOUND_V) {
            if (rollChance("side_effects.compound_v.failed_transformation.chance", 4.0)) {
                applyFailedTransformation(player);
                player.sendMessage(plugin.getLocaleManager().msg("side_effect.failed_transformation"));
            }
            if (rollChance("side_effects.compound_v.overload_sickness.chance", 2.0)) {
                applyOverloadSickness(player, "side_effects.compound_v.overload_sickness");
                player.sendMessage(plugin.getLocaleManager().msg("side_effect.overload_sickness"));
            }
            if (rollChance("side_effects.compound_v.muscle_failure.chance", 1.0)) {
                applyMuscleFailure(player, "side_effects.compound_v.muscle_failure");
                player.sendMessage(plugin.getLocaleManager().msg("side_effect.muscle_failure"));
            }
            return;
        }

        if (type == CompoundPotion.V_ONE) {
            if (rollChance("side_effects.v_one.heat_spike.chance", 20.0)) {
                double hearts = randomDouble("side_effects.v_one.heat_spike.damage_hearts_min", 1.0,
                        "side_effects.v_one.heat_spike.damage_hearts_max", 2.0);
                player.damage(Math.max(0.0, hearts) * 2.0);
                player.getWorld().spawnParticle(Particle.FLAME, player.getLocation().add(0, 1, 0), 18, 0.35, 0.55, 0.35, 0.02);
                player.playSound(player.getLocation(), Sound.ENTITY_BLAZE_HURT, 0.7f, 0.8f);
                player.sendMessage(plugin.getLocaleManager().msg("side_effect.heat_spike"));
            }
            if (rollChance("side_effects.v_one.body_rejection.chance", 15.0)) {
                int seconds = randomInt("side_effects.v_one.body_rejection.min_seconds", 60,
                        "side_effects.v_one.body_rejection.max_seconds", 600);
                add(player, PotionEffects.WEAKNESS, seconds * 20, 0);
                add(player, PotionEffects.SLOWNESS, seconds * 20, 0);
                player.sendMessage(plugin.getLocaleManager().msg("side_effect.body_rejection"));
            }
            if (rollChance("side_effects.v_one.critical_instability.chance", 5.0)) {
                applyCriticalInstability(player);
                player.sendMessage(plugin.getLocaleManager().msg("side_effect.critical_instability"));
            }
            if (rollChance("side_effects.v_one.no_power.chance", 10.0)) {
                player.sendMessage(plugin.getLocaleManager().msg("side_effect.no_power"));
                playFailure(player);
            }
            if (rollChance("side_effects.v_one.muscle_failure.chance", 2.0)) {
                applyMuscleFailure(player, "side_effects.v_one.muscle_failure");
                player.sendMessage(plugin.getLocaleManager().msg("side_effect.muscle_failure"));
            }
        }
    }

    public void applyMinorCompatibility(Player player) {
        if (!plugin.getConfig().getBoolean("side_effects.enabled", true)) return;
        add(player, PotionEffects.NAUSEA, 5 * 20, 0);
        add(player, PotionEffects.HUNGER, 10 * 20, 0);
        player.getWorld().spawnParticle(Particle.SMOKE, player.getLocation().add(0, 1, 0), 8, 0.25, 0.25, 0.25, 0.02);
        player.playSound(player.getLocation(), Sound.ENTITY_GENERIC_DRINK, 0.35f, 0.55f);
    }

    public void handleAbilityExpired(Player player, PlayerAbilityData data) {
        // Temp V side effects are applied directly on drink. Expiry only removes the temporary ability.
    }

    public void applyVNull(Player player) {
        PlayerAbilityData data = plugin.getAbilityManager().getData(player);
        CompoundPotion source = data != null ? data.potionType() : null;

        if (source == CompoundPotion.V_ONE) {
            int seconds = randomInt("v_null.v_one.min_seconds", 60, "v_null.v_one.max_seconds", 120);
            add(player, PotionEffects.WITHER, seconds * 20, 0);
            add(player, PotionEffects.POISON, seconds * 20, 0);
            add(player, PotionEffects.SLOWNESS, plugin.getConfig().getInt("v_null.v_one.slowness_seconds", 30) * 20, 0);
            player.sendMessage(plugin.getLocaleManager().msg("v_null.hit_v_one"));
            playVNull(player);
            return;
        }

        if (source == CompoundPotion.COMPOUND_V || source == CompoundPotion.TEMP_V) {
            int seconds = plugin.getConfig().getInt("v_null.standard.effect_seconds", 900);
            add(player, PotionEffects.WITHER, seconds * 20, 4);
            add(player, PotionEffects.POISON, seconds * 20, 4);
            add(player, PotionEffects.SLOWNESS, seconds * 20, 2);
            player.sendMessage(plugin.getLocaleManager().msg("v_null.hit_powered"));
            SchedulerAdapter.runLater(plugin, player, () -> {
                if (!player.isOnline() || player.isDead()) return;
                PlayerAbilityData current = plugin.getAbilityManager().getData(player);
                if (current != null && (current.potionType() == CompoundPotion.COMPOUND_V || current.potionType() == CompoundPotion.TEMP_V)) {
                    player.damage(10_000.0);
                }
            }, seconds * 20L);
            playVNull(player);
            return;
        }

        player.sendMessage(plugin.getLocaleManager().msg("v_null.no_effect"));
    }

    public void applyVNull(LivingEntity entity) {
        if (entity instanceof Player player) {
            applyVNull(player);
        }
    }

    private void rollTempVImmediate(Player player, int usesWithinWindow) {
        ConfigurationSection section = plugin.getConfig().getConfigurationSection("side_effects.temp_v");
        if (section == null) return;

        Map<String, Double> effects = new HashMap<>();
        effects.put("adrenaline_crash", section.getDouble("adrenaline_crash.chance", 35.0));
        effects.put("overload_sickness", section.getDouble("overload_sickness.chance", 25.0));
        effects.put("muscle_failure", section.getDouble("muscle_failure.chance", 15.0));
        effects.put("internal_damage", section.getDouble("internal_damage.chance", 8.0));
        effects.put("severe_collapse", section.getDouble("severe_collapse.chance", 5.0));

        double base = effects.values().stream().mapToDouble(Double::doubleValue).sum();
        double bonus = bonusForTempUse(usesWithinWindow);
        double chance = Math.min(plugin.getConfig().getDouble("side_effects.temp_v.max_total_chance", 95.0), base + bonus);
        if (!rollPercent(chance)) return;

        String selected = weightedPick(effects);
        switch (selected) {
            case "adrenaline_crash" -> {
                int seconds = randomInt("side_effects.temp_v.adrenaline_crash.min_seconds", 120,
                        "side_effects.temp_v.adrenaline_crash.max_seconds", 240);
                add(player, PotionEffects.WEAKNESS, seconds * 20, 0);
                player.sendMessage(plugin.getLocaleManager().msg("side_effect.adrenaline_crash"));
            }
            case "overload_sickness" -> {
                applyOverloadSickness(player, "side_effects.temp_v.overload_sickness");
                player.sendMessage(plugin.getLocaleManager().msg("side_effect.overload_sickness"));
            }
            case "muscle_failure" -> {
                applyMuscleFailure(player, "side_effects.temp_v.muscle_failure");
                player.sendMessage(plugin.getLocaleManager().msg("side_effect.muscle_failure"));
            }
            case "internal_damage" -> {
                double hearts = randomDouble("side_effects.temp_v.internal_damage.min_hearts", 1.0,
                        "side_effects.temp_v.internal_damage.max_hearts", 3.0);
                player.damage(Math.max(0.0, hearts) * 2.0);
                player.sendMessage(plugin.getLocaleManager().msg("side_effect.internal_damage"));
            }
            case "severe_collapse" -> {
                int seconds = plugin.getConfig().getInt("side_effects.temp_v.severe_collapse.seconds", 120);
                add(player, PotionEffects.WEAKNESS, seconds * 20, 1);
                add(player, PotionEffects.MINING_FATIGUE, seconds * 20, 0);
                player.sendMessage(plugin.getLocaleManager().msg("side_effect.severe_collapse"));
            }
            default -> { }
        }
        playFailure(player);
    }

    private int recordTempUse(UUID uuid) {
        long now = System.currentTimeMillis();
        long window = plugin.getConfig().getLong("side_effects.temp_v.reuse_window_minutes", 30) * 60_000L;
        Deque<Long> uses = tempVUses.computeIfAbsent(uuid, ignored -> new ArrayDeque<>());
        while (!uses.isEmpty() && now - uses.peekFirst() > window) uses.removeFirst();
        uses.addLast(now);
        return uses.size();
    }

    private double bonusForTempUse(int uses) {
        if (uses <= 1) return plugin.getConfig().getDouble("side_effects.temp_v.reuse_bonus.use_1", 0.0);
        if (uses == 2) return plugin.getConfig().getDouble("side_effects.temp_v.reuse_bonus.use_2", 10.0);
        if (uses == 3) return plugin.getConfig().getDouble("side_effects.temp_v.reuse_bonus.use_3", 25.0);
        return plugin.getConfig().getDouble("side_effects.temp_v.reuse_bonus.use_4_plus", 50.0);
    }

    private void applyTempVLethalCollapse(Player player) {
        player.sendMessage(plugin.getLocaleManager().msg("side_effect.temp_v_lethal"));
        player.getWorld().spawnParticle(Particle.EXPLOSION, player.getLocation().add(0, 1, 0), 3, 0.35, 0.45, 0.35, 0.0);
        player.playSound(player.getLocation(), Sound.ENTITY_WITHER_DEATH, 0.9f, 1.7f);
        player.damage(10_000.0);
    }

    private void applyFailedTransformation(Player player) {
        int seconds = plugin.getConfig().getInt("side_effects.compound_v.failed_transformation.seconds", 300);
        add(player, PotionEffects.WEAKNESS, seconds * 20, 1);
        add(player, PotionEffects.SLOWNESS, seconds * 20, 0);
        add(player, PotionEffects.MINING_FATIGUE, seconds * 20, 0);
        playFailure(player);
    }

    private void applyCriticalInstability(Player player) {
        int seconds = randomInt("side_effects.v_one.critical_instability.min_seconds", 60,
                "side_effects.v_one.critical_instability.max_seconds", 180);
        add(player, PotionEffects.WEAKNESS, seconds * 20, 1);
        add(player, PotionEffects.SLOWNESS, seconds * 20, 1);
        playFailure(player);
    }

    private void applyOverloadSickness(Player player, String path) {
        int seconds = randomInt(path + ".min_seconds", 30, path + ".max_seconds", 60);
        add(player, PotionEffects.NAUSEA, seconds * 20, 0);
        add(player, PotionEffects.HUNGER, seconds * 20, 0);
    }

    private void applyMuscleFailure(Player player, String path) {
        int seconds = plugin.getConfig().getInt(path + ".seconds", 30);
        int amplifier = plugin.getConfig().getInt(path + ".amplifier", 1);
        add(player, PotionEffects.SLOWNESS, seconds * 20, amplifier);
    }

    private void add(LivingEntity entity, org.bukkit.potion.PotionEffectType type, int ticks, int amplifier) {
        if (type == null || ticks <= 0) return;
        entity.addPotionEffect(new PotionEffect(type, ticks, Math.max(0, amplifier), false, true, true));
    }

    private boolean rollChance(String path, double fallback) {
        return rollPercent(plugin.getConfig().getDouble(path, fallback));
    }

    private boolean rollPercent(double chance) {
        if (chance <= 0.0) return false;
        if (chance >= 100.0) return true;
        return ThreadLocalRandom.current().nextDouble(100.0) < chance;
    }

    private int randomInt(String minPath, int minFallback, String maxPath, int maxFallback) {
        int min = plugin.getConfig().getInt(minPath, minFallback);
        int max = plugin.getConfig().getInt(maxPath, maxFallback);
        if (max < min) max = min;
        return min + ThreadLocalRandom.current().nextInt(max - min + 1);
    }

    private double randomDouble(String minPath, double minFallback, String maxPath, double maxFallback) {
        double min = plugin.getConfig().getDouble(minPath, minFallback);
        double max = plugin.getConfig().getDouble(maxPath, maxFallback);
        if (max < min) max = min;
        return min + ThreadLocalRandom.current().nextDouble(max - min + 0.00001);
    }

    private String weightedPick(Map<String, Double> weights) {
        double total = weights.values().stream().filter(v -> v > 0).mapToDouble(Double::doubleValue).sum();
        if (total <= 0) return "";
        double roll = ThreadLocalRandom.current().nextDouble(total);
        for (Map.Entry<String, Double> entry : weights.entrySet()) {
            if (entry.getValue() <= 0) continue;
            roll -= entry.getValue();
            if (roll <= 0) return entry.getKey();
        }
        return weights.keySet().stream().findFirst().orElse("");
    }

    private void playFailure(Player player) {
        player.playSound(player.getLocation(), Sound.ENTITY_WITHER_HURT, 0.45f, 1.6f);
        player.getWorld().spawnParticle(Particle.SMOKE, player.getLocation().add(0, 1, 0), 20, 0.45, 0.55, 0.45, 0.04);
    }

    private void playVNull(LivingEntity entity) {
        entity.getWorld().spawnParticle(Particle.WITCH, entity.getLocation().add(0, 1, 0), 35, 0.5, 0.7, 0.5, 0.08);
        entity.getWorld().playSound(entity.getLocation(), Sound.ENTITY_WITHER_SPAWN, 0.6f, 1.7f);
    }
}
