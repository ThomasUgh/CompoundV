package de.thomasugh.compoundv.config;

import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class ConfigValidator {

    private static final class Rule {
        final String path;
        final double min;
        final double max;
        Rule(String path, double min, double max) {
            this.path = path;
            this.min = min;
            this.max = max;
        }
    }

    private static final List<Rule> RULES = buildRules();

    private final JavaPlugin plugin;

    public ConfigValidator(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    private static List<Rule> buildRules() {
        List<Rule> r = new ArrayList<>();
        r.add(new Rule("performance.particle_multiplier", 0.1, 3.0));
        r.add(new Rule("performance.min_tps", 5.0, 20.0));
        r.add(new Rule("performance.max_scan_entities", 8, 512));
        r.add(new Rule("performance.max_concurrent_cinematics", 1, 32));
        r.add(new Rule("heat_vision.hit_radius", 0.01, 5.0));
        r.add(new Rule("heat_vision.damage_interval", 1, 200));
        r.add(new Rule("heat_vision.block_effect_interval_ticks", 1, 200));
        r.add(new Rule("heat_vision.max_range", 1.0, 256.0));
        r.add(new Rule("abilities.vision.xray_radius", 1.0, 128.0));
        r.add(new Rule("temp_v.min_duration_minutes", 1, 100000));
        r.add(new Rule("temp_v.max_duration_minutes", 1, 100000));
        r.add(new Rule("brewing.custom_brew_time_ticks", 1, 2000000));
        r.add(new Rule("v_one.drink_effect_seconds", 0, 3600));
        r.add(new Rule("randomization.avoid_recent_count", 0, 64));
        return r;
    }

    public int validateAndClamp() {
        String mode = plugin.getConfig().getString("performance.config_validation", "warn_and_clamp");
        if (mode == null) mode = "warn_and_clamp";
        mode = mode.toLowerCase(Locale.ROOT).trim();
        if (mode.equals("off")) return 0;

        boolean clamp = mode.equals("warn_and_clamp");
        FileConfiguration config = plugin.getConfig();
        int issues = 0;
        boolean dirty = false;

        for (Rule rule : RULES) {
            if (!config.isSet(rule.path)) continue;
            if (!config.isDouble(rule.path) && !config.isInt(rule.path) && !config.isLong(rule.path)) {
                plugin.getLogger().warning("Config value '" + rule.path + "' is not a number. Expected a value between "
                        + trim(rule.min) + " and " + trim(rule.max) + ".");
                issues++;
                continue;
            }
            double value = config.getDouble(rule.path);
            double fixed = value;
            if (value < rule.min) fixed = rule.min;
            else if (value > rule.max) fixed = rule.max;
            if (fixed != value) {
                issues++;
                plugin.getLogger().warning("Config value '" + rule.path + "' = " + trim(value)
                        + " is out of range [" + trim(rule.min) + ", " + trim(rule.max) + "]"
                        + (clamp ? ". Clamped to " + trim(fixed) + "." : "."));
                if (clamp) {
                    config.set(rule.path, isWhole(fixed) ? (Object) (long) fixed : (Object) fixed);
                    dirty = true;
                }
            }
        }

        int min = config.getInt("temp_v.min_duration_minutes", 15);
        int max = config.getInt("temp_v.max_duration_minutes", 120);
        if (max < min) {
            issues++;
            plugin.getLogger().warning("Config value 'temp_v.max_duration_minutes' (" + max
                    + ") is lower than 'temp_v.min_duration_minutes' (" + min + ")"
                    + (clamp ? ". Raised max to " + min + "." : "."));
            if (clamp) {
                config.set("temp_v.max_duration_minutes", min);
                dirty = true;
            }
        }

        if (dirty) {
            plugin.saveConfig();
        }
        if (issues > 0) {
            plugin.getLogger().warning("Config validation finished with " + issues + " issue(s) (mode: " + mode + ").");
        }
        return issues;
    }

    private static boolean isWhole(double v) {
        return v == Math.floor(v) && !Double.isInfinite(v);
    }

    private static String trim(double v) {
        if (isWhole(v)) return Long.toString((long) v);
        return Double.toString(v);
    }
}
