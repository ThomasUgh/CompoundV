package de.thomasugh.compoundv.manager;

import de.thomasugh.compoundv.CompoundV;
import de.thomasugh.compoundv.data.CompoundPotion;
import de.thomasugh.compoundv.util.AbilityAliases;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;
import java.util.logging.Level;

public class PotionRollManager {

    private final CompoundV plugin;
    private final AbilityManager  abilityManager;

    public PotionRollManager(CompoundV plugin, AbilityManager am) {
        this.plugin = plugin;
        this.abilityManager = am;
    }

    public void roll(Player player, CompoundPotion type) {
        try {
            Map<String, Integer> chances = getChances(type);
            if (chances.isEmpty()) {
                plugin.getLogger().warning("No valid chances configured for potion '"
                        + type.getConfigKey() + "'. Player: " + player.getName());
                player.sendMessage(plugin.getLocaleManager().msg("potion.no_chances"));
                return;
            }

            int total = chances.values().stream().mapToInt(Integer::intValue).sum();
            if (total <= 0) {
                plugin.getLogger().warning("Total weight is zero for '" + type.getConfigKey() + "'.");
                player.sendMessage(plugin.getLocaleManager().msg("potion.no_chances"));
                return;
            }

            int roll = ThreadLocalRandom.current().nextInt(total);
            String chosen = null;
            for (Map.Entry<String, Integer> e : chances.entrySet()) {
                roll -= e.getValue();
                if (roll < 0) {
                    chosen = e.getKey();
                    break;
                }
            }
            if (chosen == null) chosen = chances.keySet().iterator().next();

            long expiresAt = 0L;
            if (type.isTemporary()) {
                int min = plugin.getConfig().getInt("temp_v.min_duration_minutes", 15);
                int max = plugin.getConfig().getInt("temp_v.max_duration_minutes", 120);
                if (max < min) max = min;
                int span = Math.max(1, max - min + 1);
                expiresAt = System.currentTimeMillis()
                        + (long) (min + ThreadLocalRandom.current().nextInt(span)) * 60_000L;
            }
            abilityManager.giveAbility(player, chosen, type, expiresAt);
        } catch (Exception ex) {
            plugin.getLogger().log(Level.SEVERE,
                    "Error while rolling potion '" + type + "' for " + player.getName(), ex);
            player.sendMessage(plugin.getLocaleManager().msg("potion.roll_failed"));
        }
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
            map.merge(abilityId, weight, Integer::sum);
        }
        return map;
    }
}
