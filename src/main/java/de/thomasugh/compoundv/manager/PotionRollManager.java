package de.thomasugh.compoundv.manager;

import de.thomasugh.compoundv.CompoundV;
import de.thomasugh.compoundv.data.CompoundPotion;
import de.thomasugh.compoundv.util.AbilityAliases;
import de.thomasugh.compoundv.data.PlayerAbilityData;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;
import java.util.logging.Level;

public class PotionRollManager {

    private final CompoundV plugin;
    private final AbilityManager  abilityManager;
    private final Map<String, Deque<String>> recentRolls = new ConcurrentHashMap<>();

    public PotionRollManager(CompoundV plugin, AbilityManager am) {
        this.plugin = plugin;
        this.abilityManager = am;
    }

    public boolean roll(Player player, CompoundPotion type) {
        try {
            Map<String, Integer> chances = getChances(type);
            if (chances.isEmpty()) {
                plugin.getLogger().warning("No valid chances configured for potion '"
                        + type.getConfigKey() + "'. Player: " + player.getName());
                player.sendMessage(plugin.getLocaleManager().msg("potion.no_chances"));
                return false;
            }

            Map<String, Integer> pool = applyAntiRepeat(player, type, chances);

            int total = pool.values().stream().mapToInt(Integer::intValue).sum();
            if (total <= 0) {
                plugin.getLogger().warning("Total weight is zero for '" + type.getConfigKey() + "'.");
                player.sendMessage(plugin.getLocaleManager().msg("potion.no_chances"));
                return false;
            }

            int roll = ThreadLocalRandom.current().nextInt(total);
            String chosen = null;
            for (Map.Entry<String, Integer> e : pool.entrySet()) {
                roll -= e.getValue();
                if (roll < 0) {
                    chosen = e.getKey();
                    break;
                }
            }
            if (chosen == null) chosen = pool.keySet().iterator().next();
            recordRecent(player, type, chosen);

            long expiresAt = type.isTemporary() ? System.currentTimeMillis() + randomDurationMillis(type) : 0L;
            abilityManager.giveAbility(player, chosen, type, expiresAt);
            return true;
        } catch (Exception ex) {
            plugin.getLogger().log(Level.SEVERE,
                    "Error while rolling potion '" + type + "' for " + player.getName(), ex);
            player.sendMessage(plugin.getLocaleManager().msg("potion.roll_failed"));
            return false;
        }
    }



    public boolean extendTemporaryAbility(Player player, CompoundPotion type) {
        if (type == null || !type.isTemporary()) return false;
        PlayerAbilityData data = abilityManager.getData(player);
        if (data == null || data.potionType() != type || data.isExpired()) return false;

        long base = Math.max(System.currentTimeMillis(), data.expiresAt());
        long newExpiresAt = base + randomDurationMillis(type);
        abilityManager.updateData(player, new PlayerAbilityData(data.abilityId(), data.potionType(), newExpiresAt));
        return true;
    }

    private long randomDurationMillis(CompoundPotion type) {
        if (type != CompoundPotion.TEMP_V) return 0L;
        int min = plugin.getConfig().getInt("temp_v.min_duration_minutes", 15);
        int max = plugin.getConfig().getInt("temp_v.max_duration_minutes", 120);
        if (max < min) max = min;
        int span = Math.max(1, max - min + 1);
        return (long) (min + ThreadLocalRandom.current().nextInt(span)) * 60_000L;
    }

    private boolean isBlockedDirectVOneRoll(CompoundPotion type, String abilityId) {
        if (type != CompoundPotion.V_ONE) return false;
        return "the_patriot_v_one".equalsIgnoreCase(abilityId)
                || "teleporter".equalsIgnoreCase(abilityId)
                || "teleporter_v_one".equalsIgnoreCase(abilityId)
                || "size_changer".equalsIgnoreCase(abilityId)
                || "size_changer_v_one".equalsIgnoreCase(abilityId)
                || "bloodweaver_v_one".equalsIgnoreCase(abilityId);
    }

    private Map<String, Integer> applyAntiRepeat(Player player, CompoundPotion type, Map<String, Integer> chances) {
        if (!plugin.getConfig().getBoolean("randomization.anti_repeat", true)) return chances;
        int avoid = plugin.getConfig().getInt("randomization.avoid_recent_count", 2);
        if (avoid <= 0 || chances.size() <= 1) return chances;
        Deque<String> recent = recentRolls.get(recentKey(player, type));
        if (recent == null || recent.isEmpty()) return chances;
        Map<String, Integer> filtered = new LinkedHashMap<>();
        for (Map.Entry<String, Integer> e : chances.entrySet()) {
            if (!recent.contains(e.getKey())) filtered.put(e.getKey(), e.getValue());
        }
        return filtered.isEmpty() ? chances : filtered;
    }

    private void recordRecent(Player player, CompoundPotion type, String chosen) {
        if (!plugin.getConfig().getBoolean("randomization.anti_repeat", true)) return;
        int avoid = Math.max(0, plugin.getConfig().getInt("randomization.avoid_recent_count", 2));
        if (avoid <= 0) return;
        Deque<String> recent = recentRolls.computeIfAbsent(recentKey(player, type), k -> new ArrayDeque<>());
        recent.remove(chosen);
        recent.addFirst(chosen);
        while (recent.size() > avoid) recent.removeLast();
    }

    private String recentKey(Player player, CompoundPotion type) {
        return player.getUniqueId() + "|" + type.name();
    }

    private Map<String, Integer> getChances(CompoundPotion type) {
        ConfigurationSection section =
                plugin.getConfig().getConfigurationSection(type.getConfigKey() + ".chances");
        if (section == null) return Map.of();

        Map<String, Integer> map = new LinkedHashMap<>();
        for (String key : section.getKeys(false)) {
            int weight = section.getInt(key, 0);
            if (weight <= 0) continue;
            String abilityId = AbilityAliases.normalize(key);
            if (!plugin.getRegistry().contains(abilityId)) {
                plugin.getLogger().warning("Ignoring unknown ability '" + key
                        + "' in " + type.getConfigKey() + ".chances");
                continue;
            }
            if (isBlockedDirectVOneRoll(type, abilityId)) {
                plugin.getLogger().warning("Ignoring upgrade-only ability '" + key
                        + "' in " + type.getConfigKey() + ".chances. Use V One as an upgrade instead.");
                continue;
            }
            map.merge(abilityId, weight, Integer::sum);
        }
        return map;
    }
}
