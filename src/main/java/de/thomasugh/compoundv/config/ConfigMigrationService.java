package de.thomasugh.compoundv.config;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;

public final class ConfigMigrationService {

    private final JavaPlugin plugin;

    public ConfigMigrationService(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public void migrate() {
        boolean changed = false;

        changed |= mergeBundledDefaults();
        changed |= migrateLegacyAliases();
        changed |= ensureRollChances();
        changed |= migrateVeteranBalance();
        changed |= migrateVersion102Defaults();

        if (changed) {
            plugin.saveConfig();
        }
    }

    private boolean mergeBundledDefaults() {
        try (InputStream in = plugin.getResource("config.yml")) {
            if (in == null) return false;
            YamlConfiguration defaults = YamlConfiguration.loadConfiguration(
                    new InputStreamReader(in, StandardCharsets.UTF_8));
            plugin.getConfig().setDefaults(defaults);
            plugin.getConfig().options().copyDefaults(true);
            return true;
        } catch (Exception ex) {
            plugin.getLogger().warning("Could not merge config defaults: " + ex.getMessage());
            return false;
        }
    }

    private boolean migrateLegacyAliases() {
        boolean changed = false;
        changed |= moveIfMissing("compound_v.chances.homelander", "compound_v.chances.the_patriot");
        changed |= moveIfMissing("v_one.chances.vone_homelander", "v_one.chances.the_patriot_v_one");
        changed |= moveIfMissing("v_one.chances.soldier_boy", "v_one.chances.the_veteran");
        changed |= moveIfMissing("abilities.homelander", "abilities.the_patriot");
        changed |= moveIfMissing("abilities.soldier_boy", "abilities.the_veteran");
        return changed;
    }

    private boolean ensureRollChances() {
        boolean changed = false;
        changed |= ensureChanceSection("compound_v.chances", orderedMap(
                "the_patriot", 1,
                "fly", 15,
                "heat_vision", 8,
                "speedster", 10,
                "strength", 18,
                "invisibility", 7,
                "fire", 20,
                "the_diver", 7,
                "vision", 7
        ));
        changed |= ensureChanceSection("temp_v.chances", orderedMap(
                "fly", 10,
                "heat_vision", 5,
                "speedster", 20,
                "strength", 25,
                "invisibility", 15,
                "fire", 10
        ));
        changed |= ensureChanceSection("v_one.chances", orderedMap(
                "the_patriot_v_one", 1,
                "the_veteran", 3
        ));
        return changed;
    }

    private boolean migrateVeteranBalance() {
        boolean changed = false;

        NumberMigration[] migrations = {
                new NumberMigration("heat_vision.damage_amount", 8.0, 2.0),
                new NumberMigration("abilities.the_veteran.beam_duration_ticks", 60.0, 80),
                new NumberMigration("abilities.the_veteran.beam_duration_ticks", 100.0, 80),
                new NumberMigration("abilities.the_veteran.beam_damage_interval_ticks", 6.0, 2),
                new NumberMigration("abilities.the_veteran.beam_block_affect_interval_ticks", 5.0, 4),
                new NumberMigration("abilities.the_veteran.beam_block_affect_interval_ticks", 4.0, 4),
                new NumberMigration("abilities.the_veteran.beam_max_blocks_per_pulse", 4.0, 5),
                new NumberMigration("abilities.the_veteran.beam_max_blocks_per_pulse", 6.0, 5),
                new NumberMigration("abilities.the_veteran.beam_block_hits_to_break", 3.0, 5),
                new NumberMigration("abilities.the_veteran.ground_zero_radius", 8.0, 14.0),
                new NumberMigration("abilities.the_veteran.ground_zero_damage", 240.0, 500.0),
                new NumberMigration("abilities.the_veteran.ground_zero_knockback", 4.0, 7.5),
                new NumberMigration("abilities.the_veteran.mushroom_cloud_duration_ticks", 900.0, 1200),
                new NumberMigration("abilities.the_veteran.mushroom_cloud_period_ticks", 10.0, 8),
                new NumberMigration("abilities.the_veteran.mushroom_cloud_height", 26.0, 32.0),
                new NumberMigration("abilities.the_veteran.mushroom_cloud_radius", 13.0, 15.5),
                new NumberMigration("abilities.the_veteran.pre_charge_hold_ticks", 40.0, 20)
        };

        for (NumberMigration migration : migrations) {
            changed |= replaceIfNumericEquals(migration.path(), migration.oldValue(), migration.newValue());
        }

        changed |= setIfMissing("abilities.the_veteran.pre_charge_hold_ticks", 20);
        changed |= setIfMissing("abilities.the_veteran.charge_duration_ticks", 100);
        changed |= setIfMissing("abilities.the_veteran.charge_period_ticks", 5);
        changed |= setIfMissing("abilities.the_veteran.melee_knockback_horizontal", 1.35);
        changed |= setIfMissing("abilities.the_veteran.melee_knockback_vertical", 0.28);
        changed |= setIfMissing("abilities.the_veteran.beam_hit_knockback", 0.45);
        changed |= setIfMissing("abilities.the_patriot.compound_v.heat_vision_damage_amount", 5.2);
        changed |= setIfMissing("abilities.the_patriot.v_one.heat_vision_damage_amount", 5.2);

        changed |= migrateLegacyVeteranBeamDamage();

        return changed;
    }

    private boolean migrateVersion102Defaults() {
        boolean changed = false;

        changed |= replaceIfNumericEquals("abilities.the_patriot.v_one.heat_vision_damage_amount", 5.0, 5.2);
        changed |= replaceIfNumericEquals("abilities.the_patriot.v_one.heat_vision_damage_multiplier", 1.0, 1.33);

        if (!plugin.getConfig().contains("abilities.the_patriot.compound_v.heat_vision_range")) {
            plugin.getConfig().set("abilities.the_patriot.compound_v.heat_vision_range",
                    plugin.getConfig().getDouble("heat_vision.range", 43.0));
            changed = true;
        }

        if (!plugin.getConfig().contains("abilities.the_patriot.v_one.heat_vision_range")) {
            double standardRange = plugin.getConfig().getDouble("abilities.the_patriot.compound_v.heat_vision_range",
                    plugin.getConfig().getDouble("heat_vision.range", 43.0));
            plugin.getConfig().set("abilities.the_patriot.v_one.heat_vision_range", standardRange + 5.0);
            changed = true;
        }

        if (!plugin.getConfig().contains("abilities.the_patriot.v_one.strength_level")) {
            int standardStrength = plugin.getConfig().getInt("abilities.the_patriot.compound_v.strength_level", 3);
            plugin.getConfig().set("abilities.the_patriot.v_one.strength_level", standardStrength + 1);
            changed = true;
        }

        changed |= setIfMissing("abilities.the_diver.water_breathing_level", 1);
        changed |= setIfMissing("abilities.the_diver.dolphins_grace_level", 3);
        changed |= setIfMissing("abilities.the_diver.conduit_power_level", 2);
        changed |= setIfMissing("abilities.the_diver.strength_level", 2);
        changed |= setIfMissing("abilities.the_diver.resistance_level", 1);
        changed |= setIfMissing("abilities.the_diver.water_bonus_levels", 1);
        changed |= setIfMissing("abilities.the_diver.sonar_radius", 45.0);
        changed |= setIfMissing("abilities.vision.xray_radius", 35.0);

        return changed;
    }

    private boolean migrateLegacyVeteranBeamDamage() {
        if (!plugin.getConfig().contains("abilities.the_veteran.beam_damage")) {
            return setIfMissingVeteranBeamAmount();
        }

        double oldDamage = plugin.getConfig().getDouble("abilities.the_veteran.beam_damage", 12.0);
        double patriotDamage = Math.max(0.1,
                plugin.getConfig().getDouble("abilities.the_patriot.v_one.heat_vision_damage_amount", 5.2));
        double multiplier = Math.abs(oldDamage - 12.0) < 0.0001 ? 5.0 : Math.max(0.1, oldDamage / patriotDamage);

        plugin.getConfig().set("abilities.the_veteran.beam_damage_multiplier", multiplier);
        plugin.getConfig().set("abilities.the_veteran.beam_damage_amount", patriotDamage * multiplier);
        plugin.getConfig().set("abilities.the_veteran.beam_damage", null);
        return true;
    }

    private boolean setIfMissingVeteranBeamAmount() {
        if (plugin.getConfig().contains("abilities.the_veteran.beam_damage_amount")) {
            return false;
        }

        double patriotDamage = Math.max(0.1,
                plugin.getConfig().getDouble("abilities.the_patriot.v_one.heat_vision_damage_amount", 5.2));
        double multiplier = plugin.getConfig().getDouble("abilities.the_veteran.beam_damage_multiplier", 5.0);
        plugin.getConfig().set("abilities.the_veteran.beam_damage_amount", patriotDamage * multiplier);
        return true;
    }

    private boolean setIfMissing(String path, Object value) {
        if (plugin.getConfig().contains(path)) return false;
        plugin.getConfig().set(path, value);
        return true;
    }

    private boolean replaceIfNumericEquals(String path, double oldValue, Object newValue) {
        if (!plugin.getConfig().contains(path)) return false;
        Object current = plugin.getConfig().get(path);
        if (!(current instanceof Number n)) return false;
        if (Math.abs(n.doubleValue() - oldValue) > 0.0001) return false;
        plugin.getConfig().set(path, newValue);
        return true;
    }

    private boolean moveIfMissing(String oldPath, String newPath) {
        if (!plugin.getConfig().contains(oldPath)) return false;
        if (plugin.getConfig().contains(newPath)) {
            plugin.getConfig().set(oldPath, null);
            return true;
        }

        ConfigurationSection oldSection = plugin.getConfig().getConfigurationSection(oldPath);
        if (oldSection != null) {
            plugin.getConfig().set(newPath, null);
            ConfigurationSection newSection = plugin.getConfig().createSection(newPath);
            for (String key : oldSection.getKeys(true)) {
                if (!oldSection.isConfigurationSection(key)) {
                    newSection.set(key, oldSection.get(key));
                }
            }
        } else {
            plugin.getConfig().set(newPath, plugin.getConfig().get(oldPath));
        }

        plugin.getConfig().set(oldPath, null);
        return true;
    }

    private boolean ensureChanceSection(String path, Map<String, Integer> defaults) {
        ConfigurationSection section = plugin.getConfig().getConfigurationSection(path);
        boolean changed = false;

        if (section == null) {
            section = plugin.getConfig().createSection(path);
            changed = true;
        }

        for (Map.Entry<String, Integer> entry : defaults.entrySet()) {
            if (!section.contains(entry.getKey()) || section.getInt(entry.getKey(), 0) <= 0) {
                section.set(entry.getKey(), entry.getValue());
                changed = true;
            }
        }

        return changed;
    }

    private Map<String, Integer> orderedMap(Object... values) {
        Map<String, Integer> map = new LinkedHashMap<>();
        for (int i = 0; i < values.length; i += 2) {
            map.put((String) values[i], (Integer) values[i + 1]);
        }
        return map;
    }

    private record NumberMigration(String path, double oldValue, Object newValue) { }
}
