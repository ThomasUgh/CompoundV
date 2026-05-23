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
        changed |= migrateVersion103Defaults();
        changed |= migrateVersion104HotfixDefaults();
        changed |= migrateVersion110Defaults();

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
                "heat_vision", 9,
                "heat_vision_2", 7,
                "heat_vision_3", 6,
                "heat_vision_4", 5,
                "speedster", 10,
                "strength", 18,
                "invisibility", 7,
                "fire", 20,
                "the_diver", 7,
                "the_runner", 4,
                "jumper", 17,
                "shockwave", 9,
                "vision", 7,
                "teleporter", 2
        ));
        changed |= ensureChanceSection("temp_v.chances", orderedMap(
                "fly", 10,
                "heat_vision", 9,
                "heat_vision_2", 7,
                "heat_vision_3", 6,
                "heat_vision_4", 5,
                "speedster", 20,
                "strength", 25,
                "invisibility", 15,
                "fire", 10
        ));
        changed |= ensureChanceSection("v_one.chances", orderedMap(
                "the_patriot_v_one", 1,
                "the_veteran", 3,
                "sonic_boom", 3,
                "size_changer", 6,
                "teleporter", 5
        ));
        return changed;
    }

    private boolean migrateVeteranBalance() {
        boolean changed = false;

        NumberMigration[] migrations = {
                new NumberMigration("abilities.the_veteran.beam_duration_ticks", 60.0, 100),
                new NumberMigration("abilities.the_veteran.beam_damage_interval_ticks", 6.0, 2),
                new NumberMigration("abilities.the_veteran.beam_block_affect_interval_ticks", 5.0, 4),
                new NumberMigration("abilities.the_veteran.beam_block_affect_interval_ticks", 4.0, 4),
                new NumberMigration("abilities.the_veteran.beam_max_blocks_per_pulse", 4.0, 5),
                new NumberMigration("abilities.the_veteran.beam_max_blocks_per_pulse", 6.0, 5),
                new NumberMigration("abilities.the_veteran.beam_block_hits_to_break", 3.0, 5),
                new NumberMigration("abilities.the_veteran.ground_zero_radius", 8.0, 14.0),
                new NumberMigration("abilities.the_veteran.ground_zero_damage", 240.0, 500.0),
                new NumberMigration("abilities.the_veteran.ground_zero_knockback", 4.0, 0.12),
                new NumberMigration("abilities.the_veteran.ground_zero_knockback", 7.5, 0.12),
                new NumberMigration("abilities.the_veteran.ground_zero_knockback", 0.75, 0.12),
                new NumberMigration("abilities.the_veteran.mushroom_cloud_duration_ticks", 900.0, 1200),
                new NumberMigration("abilities.the_veteran.mushroom_cloud_period_ticks", 10.0, 12),
                new NumberMigration("abilities.the_veteran.mushroom_cloud_height", 26.0, 32.0),
                new NumberMigration("abilities.the_veteran.mushroom_cloud_radius", 13.0, 15.5)
        };

        for (NumberMigration migration : migrations) {
            changed |= replaceIfNumericEquals(migration.path(), migration.oldValue(), migration.newValue());
        }

        changed |= setIfMissing("abilities.the_veteran.charge_duration_ticks", 100);
        changed |= setIfMissing("abilities.the_veteran.charge_period_ticks", 5);
        changed |= setIfMissing("abilities.the_veteran.melee_knockback_horizontal", 1.35);
        changed |= setIfMissing("abilities.the_veteran.melee_knockback_vertical", 0.28);
        changed |= replaceIfNumericEquals("abilities.the_veteran.beam_hit_knockback", 0.45, 0.025);
        changed |= replaceIfNumericEquals("abilities.the_veteran.beam_hit_knockback", 0.08, 0.025);
        changed |= replaceIfNumericEquals("abilities.the_veteran.beam_hit_vertical_knockback", 0.04, 0.0);
        changed |= replaceIfNumericEquals("abilities.the_veteran.ground_zero_vertical_knockback", 0.22, 0.03);
        changed |= setIfMissing("abilities.the_veteran.ground_zero_vertical_knockback", 0.03);
        changed |= setIfMissing("abilities.the_veteran.ground_zero_max_horizontal_velocity", 0.18);
        changed |= setIfMissing("abilities.the_veteran.ground_zero_max_vertical_velocity", 0.04);
        changed |= setIfMissing("abilities.the_veteran.beam_hit_knockback", 0.025);
        changed |= setIfMissing("abilities.the_veteran.beam_hit_vertical_knockback", 0.0);
        changed |= setIfMissing("abilities.the_veteran.beam_hit_max_horizontal_velocity", 0.16);
        changed |= setIfMissing("abilities.the_veteran.beam_hit_max_vertical_velocity", 0.02);
        changed |= setIfMissing("abilities.the_patriot.compound_v.heat_vision_damage_hearts", 4.5);
        changed |= replaceIfNumericEquals("abilities.the_patriot.compound_v.heat_vision_damage_hearts", 5.0, 4.5);
        changed |= setIfMissing("abilities.the_patriot.v_one.heat_vision_damage_hearts", 4.725);

        changed |= migrateLegacyVeteranBeamDamage();

        return changed;
    }

    private boolean migrateVersion102Defaults() {
        boolean changed = false;

        changed |= replaceIfNumericEquals("abilities.the_patriot.compound_v.heat_vision_damage_hearts", 5.0, 4.5);
        changed |= setIfMissing("abilities.the_patriot.v_one.heat_vision_damage_hearts", 4.725);

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
        changed |= replaceIfNumericEquals("abilities.the_diver.dolphins_grace_level", 3.0, 4);
        changed |= setIfMissing("abilities.the_diver.dolphins_grace_level", 4);
        changed |= setIfMissing("abilities.the_diver.conduit_power_level", 2);
        changed |= setIfMissing("abilities.the_diver.strength_level", 2);
        changed |= setIfMissing("abilities.the_diver.resistance_level", 1);
        changed |= setIfMissing("abilities.the_diver.water_bonus_levels", 1);
        changed |= setIfMissing("abilities.the_diver.sonar_radius", 45.0);
        changed |= setIfMissing("abilities.the_diver.riptide_level", 4);
        changed |= setIfMissing("abilities.the_diver.riptide_velocity", 3.85);
        changed |= setIfMissing("abilities.the_diver.riptide_vertical_boost", 0.22);
        changed |= setIfMissing("abilities.the_diver.riptide_cooldown_ms", 1800);
        changed |= setIfMissing("abilities.vision.xray_radius", 35.0);

        return changed;
    }


    private boolean migrateVersion103Defaults() {
        boolean changed = false;

        changed |= replaceIfNumericEquals("abilities.the_veteran.burst_cooldown_ms", 60000.0, 300000);
        changed |= replaceIfNumericEquals("abilities.the_veteran.beam_duration_ticks", 80.0, 100);
        changed |= replaceIfNumericEquals("abilities.the_veteran.mushroom_cloud_duration_ticks", 1200.0, 2400);
        changed |= setIfMissing("abilities.the_veteran.post_burst_strength_multiplier", 0.5);
        changed |= setIfMissing("abilities.the_veteran.post_burst_strength_reduction_ticks",
                plugin.getConfig().getInt("abilities.the_veteran.mushroom_cloud_duration_ticks", 2400));

        changed |= replaceIfNumericEquals("abilities.the_patriot.shared.fall_impact_particle_height", 20.0, 15);
        changed |= replaceIfNumericEquals("abilities.the_patriot.shared.fall_impact_block_height", 50.0, 40);
        if (!plugin.getConfig().contains("abilities.the_patriot.shared.fall_impact_particle_height")) {
            plugin.getConfig().set("abilities.the_patriot.shared.fall_impact_particle_height", 15);
            changed = true;
        }
        if (!plugin.getConfig().contains("abilities.the_patriot.shared.fall_impact_block_height")) {
            plugin.getConfig().set("abilities.the_patriot.shared.fall_impact_block_height", 40);
            changed = true;
        }
        changed |= replaceIfNumericEquals("abilities.the_patriot.shared.fall_impact_height", 30.0, 40);
        changed |= replaceIfNumericEquals("abilities.the_patriot.shared.fall_impact_height", 50.0, 40);

        changed |= replaceIfNumericEquals("abilities.the_veteran.mushroom_cloud_period_ticks", 8.0, 12);
        changed |= replaceIfNumericEquals("abilities.the_veteran.mushroom_cloud_particle_multiplier", 0.65, 0.78);
        changed |= replaceIfNumericEquals("abilities.the_veteran.ground_zero_particle_multiplier", 0.65, 0.75);
        changed |= setIfMissing("abilities.the_veteran.mushroom_cloud_particle_multiplier", 0.78);
        changed |= setIfMissing("abilities.the_veteran.ground_zero_particle_multiplier", 0.75);
        changed |= setIfMissing("abilities.the_veteran.mushroom_cloud_sound_duration_ticks", 80);
        changed |= setIfMissing("abilities.the_veteran.beam_sound_duration_ticks", 40);
        changed |= setIfMissing("abilities.the_veteran.beam_sound_period_ticks", 20);
        changed |= setIfMissing("abilities.the_veteran.beam_particle_step", 0.55);
        changed |= setIfMissing("abilities.the_veteran.beam_particle_density_multiplier", 1.15);
        changed |= setIfMissing("abilities.the_veteran.beam_entity_damage_multiplier", 1.045);
        changed |= setIfMissing("abilities.the_veteran.beam_player_damage_multiplier", 0.0);
        changed |= setIfMissing("abilities.the_veteran.beam_block_damage_multiplier", 1.21);

        changed |= setIfMissing("abilities.the_runner.resistance_level", 1);
        changed |= setIfMissing("abilities.the_runner.strength_level", 2);
        changed |= setIfMissing("abilities.the_runner.extra_hearts", 5);
        changed |= setIfMissing("abilities.the_runner.attack_speed_bonus", 1024.0);

        changed |= setIfMissing("abilities.teleporter.range", 50.0);
        changed |= setIfMissing("abilities.teleporter.cooldown_ms", 2000);
        changed |= setIfMissing("abilities.teleporter.resistance_level", 2);

        return changed;
    }

    private boolean migrateVersion104HotfixDefaults() {
        boolean changed = false;

        if (plugin.getConfig().contains("abilities.the_veteran.activation_double_click_ms")) {
            plugin.getConfig().set("abilities.the_veteran.activation_double_click_ms", null);
            changed = true;
        }
        if (plugin.getConfig().contains("abilities.the_veteran.armed_window_ticks")) {
            plugin.getConfig().set("abilities.the_veteran.armed_window_ticks", null);
            changed = true;
        }
        if (plugin.getConfig().contains("abilities.the_veteran.left_click_hold_grace_ms")) {
            plugin.getConfig().set("abilities.the_veteran.left_click_hold_grace_ms", null);
            changed = true;
        }
        if (plugin.getConfig().contains("abilities.the_veteran.pre_charge_hold_ticks")) {
            plugin.getConfig().set("abilities.the_veteran.pre_charge_hold_ticks", null);
            changed = true;
        }


        changed |= replaceIfNumericEquals("abilities.the_veteran.beam_entity_damage_multiplier", 0.95, 1.045);
        changed |= replaceIfNumericEquals("abilities.the_veteran.beam_block_damage_multiplier", 1.10, 1.21);
        changed |= replaceIfNumericEquals("abilities.the_veteran.ground_zero_damage", 500.0, 525.0);
        changed |= setIfMissing("abilities.the_veteran.beam_player_damage_multiplier", 0.0);
        changed |= replaceIfNumericEquals("abilities.the_patriot.v_one.resistance_level", 4.0, 3);

        if (!plugin.getConfig().contains("abilities.the_runner.speed_levels")) {
            plugin.getConfig().set("abilities.the_runner.speed_levels", java.util.List.of(10, 11, 12, 15));
            changed = true;
        }
        if (plugin.getConfig().getInt("abilities.the_runner.default_speed_level", 9) < 10) {
            plugin.getConfig().set("abilities.the_runner.default_speed_level", 10);
            changed = true;
        }
        changed |= setIfMissing("abilities.the_runner.impact_min_speed_level", 10);
        changed |= setIfMissing("abilities.the_runner.impact_base_damage_hearts", 6.0);
        changed |= setIfMissing("abilities.the_runner.impact_damage_per_speed_level_hearts", 1.0);
        changed |= replaceIfNumericEquals("abilities.the_runner.impact_radius", 1.15, 0.65);
        changed |= replaceIfNumericEquals("abilities.the_runner.impact_vertical_radius", 1.35, 0.55);
        changed |= setIfMissing("abilities.the_runner.impact_radius", 0.65);
        changed |= setIfMissing("abilities.the_runner.impact_vertical_radius", 0.55);
        changed |= setIfMissing("abilities.the_runner.impact_min_move_delta", 0.08);
        changed |= setIfMissing("abilities.the_runner.impact_min_horizontal_speed", 0.32);
        changed |= setIfMissing("abilities.the_runner.impact_path_step", 0.28);
        changed |= setIfMissing("abilities.the_runner.impact_cooldown_ms", 750);
        changed |= setIfMissing("abilities.the_runner.impact_knockback", 1.15);
        if (plugin.getConfig().contains("abilities.the_runner.water_walk")) {
            plugin.getConfig().set("abilities.the_runner.water_walk", null);
            changed = true;
        }
        if (plugin.getConfig().contains("abilities.the_runner.water_walk_radius")) {
            plugin.getConfig().set("abilities.the_runner.water_walk_radius", null);
            changed = true;
        }
        if (plugin.getConfig().contains("abilities.the_runner.water_walk_ice_ticks")) {
            plugin.getConfig().set("abilities.the_runner.water_walk_ice_ticks", null);
            changed = true;
        }

        return changed;
    }

    private boolean migrateVersion110Defaults() {
        boolean changed = false;

        changed |= setIfMissing("heat_vision.stages.stage_1.damage_hearts", 1.35);
        changed |= setIfMissing("heat_vision.stages.stage_1.range", 30.0);
        changed |= setIfMissing("heat_vision.stages.stage_2.damage_hearts", 2.0);
        changed |= setIfMissing("heat_vision.stages.stage_2.range", 35.0);
        changed |= setIfMissing("heat_vision.stages.stage_3.damage_hearts", 2.25);
        changed |= setIfMissing("heat_vision.stages.stage_3.range", 37.0);
        changed |= setIfMissing("heat_vision.stages.stage_4.damage_hearts", 2.7);
        changed |= setIfMissing("heat_vision.stages.stage_4.range", 40.0);
        changed |= replaceIfNumericEquals("heat_vision.stages.stage_1.damage_hearts", 1.5, 1.35);
        changed |= replaceIfNumericEquals("heat_vision.stages.stage_2.damage_hearts", 2.5, 2.0);
        changed |= replaceIfNumericEquals("heat_vision.stages.stage_2.damage_hearts", 2.25, 2.0);
        changed |= replaceIfNumericEquals("heat_vision.stages.stage_3.damage_hearts", 3.0, 2.25);
        changed |= replaceIfNumericEquals("heat_vision.stages.stage_3.damage_hearts", 2.7, 2.25);
        changed |= replaceIfNumericEquals("heat_vision.stages.stage_3.range", 40.0, 37.0);
        changed |= setIfMissing("heat_vision.max_continuous_ticks", 400);
        changed |= replaceIfNumericEquals("heat_vision.overheat_cooldown_ms", 5000.0, 10000);
        changed |= setIfMissing("heat_vision.overheat_cooldown_ms", 10000);

        changed |= replaceIfNumericEquals("abilities.the_patriot.compound_v.heat_vision_range", 43.0, 44.0);
        changed |= replaceIfNumericEquals("abilities.the_patriot.v_one.heat_vision_range", 48.0, 50.0);
        changed |= setIfMissing("abilities.the_patriot.compound_v.heat_vision_range", 44.0);
        changed |= setIfMissing("abilities.the_patriot.v_one.heat_vision_range", 50.0);
        changed |= setIfMissing("abilities.the_patriot.compound_v.heat_vision_damage_hearts", 4.5);
        changed |= replaceIfNumericEquals("abilities.the_patriot.compound_v.heat_vision_damage_hearts", 5.0, 4.5);
        changed |= setIfMissing("abilities.the_patriot.v_one.heat_vision_damage_hearts", 4.725);
        changed |= replaceIfNumericEquals("abilities.the_patriot.v_one.heat_vision_damage_hearts", 5.25, 4.725);
        changed |= replaceIfNumericEquals("abilities.the_patriot.v_one.heat_vision_damage_multiplier", 1.33, 1.0);
        changed |= setIfMissing("abilities.the_patriot.compound_v.heat_vision_damage_multiplier", 1.0);
        changed |= setIfMissing("abilities.the_patriot.v_one.heat_vision_damage_multiplier", 1.0);
        changed |= setIfMissing("abilities.the_patriot.compound_v.heat_vision_max_continuous_ticks", 500);
        changed |= setIfMissing("abilities.the_patriot.compound_v.heat_vision_overheat_cooldown_ms", 5000);
        changed |= setIfMissing("abilities.the_patriot.v_one.heat_vision_max_continuous_ticks", 600);
        changed |= setIfMissing("abilities.the_patriot.v_one.heat_vision_overheat_cooldown_ms", 5000);
        changed |= replaceIfNumericEquals("abilities.the_patriot.shared.fall_impact_particle_height", 15.0, 10);
        changed |= setIfMissing("abilities.the_patriot.shared.fall_impact_particle_height", 10);
        changed |= setIfMissing("abilities.the_patriot.shared.fall_impact_cooldown_ms", 60000);
        changed |= replaceIfNumericEquals("abilities.the_veteran.beam_range", 48.0, 53.0);
        changed |= setIfMissing("abilities.the_veteran.beam_range", 53.0);
        changed |= replaceIfNumericEquals("abilities.the_veteran.pve_damage_multiplier", 1.2, 0.9);
        changed |= setIfMissing("abilities.the_veteran.pve_damage_multiplier", 0.9);
        changed |= setIfMissing("abilities.the_veteran.ground_zero_player_damage_multiplier", 0.0);
        changed |= setIfMissing("abilities.the_veteran.ground_zero_player_damage_cap_fraction", 0.25);
        changed |= setIfMissing("abilities.the_veteran.beam_player_damage_cap_fraction", 0.20);
        changed |= replaceIfNumericEquals("abilities.the_veteran.beam_particle_step", 0.55, 0.65);
        changed |= replaceIfNumericEquals("abilities.the_veteran.beam_particle_density_multiplier", 1.15, 0.70);
        changed |= setIfMissing("abilities.the_veteran.beam_particle_start_distance", 1.8);
        changed |= setIfMissing("abilities.the_veteran.mushroom_cloud_delay_after_beam_ticks", 30);

        changed |= setIfMissing("abilities.invisibility.resistance_level", 2);
        changed |= setIfMissing("abilities.invisibility.strength_level", 1);
        changed |= setIfMissing("abilities.invisibility.fire_resistance", true);
        changed |= setIfMissing("abilities.invisibility.hide_from_mobs", true);
        changed |= setIfMissing("abilities.invisibility.mob_target_clear_radius", 48.0);
        changed |= setIfMissing("abilities.invisibility.mob_target_clear_period_ticks", 10);
        changed |= setIfMissing("abilities.invisibility.toggle_cooldown_ms", 5000);

        changed |= replaceIfNumericEquals("abilities.the_diver.dolphins_grace_level", 4.0, 8);
        changed |= replaceIfNumericEquals("abilities.the_diver.dolphins_grace_level", 12.0, 8);
        changed |= setIfMissing("abilities.the_diver.dolphins_grace_level", 8);
        changed |= setIfMissing("abilities.the_diver.water_speed_level", 2);

        changed |= setIfMissing("abilities.fly.launch_velocity", 1.75);
        changed |= setIfMissing("abilities.fly.launch_peak_ticks", 14);
        changed |= setIfMissing("abilities.fly.launch_cooldown_ms", 10000);
        changed |= replaceIfNumericEquals("abilities.fly.launch_fly_speed", 0.2, 0.15);
        changed |= setIfMissing("abilities.fly.launch_fly_speed", 0.15);

        changed |= setIfMissing("abilities.sonic_boom.strength_level", 4);
        changed |= setIfMissing("abilities.sonic_boom.resistance_level", 4);
        changed |= replaceIfNumericEquals("abilities.sonic_boom.launch_velocity", 3.5, 3.05);
        changed |= setIfMissing("abilities.sonic_boom.launch_velocity", 3.05);
        changed |= replaceIfNumericEquals("abilities.sonic_boom.launch_peak_ticks", 28.0, 24);
        changed |= setIfMissing("abilities.sonic_boom.launch_peak_ticks", 24);
        changed |= setIfMissing("abilities.sonic_boom.launch_cooldown_ms", 10000);
        changed |= replaceIfNumericEquals("abilities.sonic_boom.launch_fly_speed", 0.375, 0.30);
        changed |= replaceIfNumericEquals("abilities.sonic_boom.launch_fly_speed", 0.325, 0.30);
        changed |= setIfMissing("abilities.sonic_boom.launch_fly_speed", 0.30);
        changed |= setIfMissing("abilities.sonic_boom.launch_block_damage", true);
        changed |= setIfMissing("abilities.sonic_boom.launch_block_damage_power", 1.15);
        changed |= setIfMissing("abilities.sonic_boom.launch_entity_radius", 3.5);
        changed |= setIfMissing("abilities.sonic_boom.launch_entity_damage_hearts", 4.0);
        changed |= setIfMissing("abilities.sonic_boom.launch_entity_knockback", 1.4);
        changed |= setIfMissing("abilities.sonic_boom.fall_impact_particle_height", 10);
        changed |= setIfMissing("abilities.sonic_boom.fall_impact_block_height", 35);
        changed |= setIfMissing("abilities.sonic_boom.fall_impact_cooldown_ms", 60000);
        changed |= setIfMissing("abilities.sonic_boom.fall_impact_power", 10.0);
        changed |= setIfMissing("abilities.sonic_boom.fall_impact_block_damage", true);
        changed |= setIfMissing("abilities.sonic_boom.fall_impact_entity_radius", 9.0);
        changed |= setIfMissing("abilities.sonic_boom.fall_impact_entity_damage_hearts", 16.0);
        changed |= setIfMissing("abilities.sonic_boom.fall_impact_player_damage_multiplier", 0.8);
        changed |= setIfMissing("abilities.sonic_boom.fall_impact_knockback", 2.4);
        changed |= setIfMissing("abilities.sonic_boom.sonic_beam_cooldown_ms", 5000);
        changed |= replaceIfNumericEquals("abilities.sonic_boom.sonic_beam_range", 37.5, 30.0);
        changed |= replaceIfNumericEquals("abilities.sonic_boom.sonic_beam_damage_hearts", 7.5, 5.0);
        changed |= replaceIfNumericEquals("abilities.sonic_boom.sonic_beam_impact_power", 0.85, 0.65);
        changed |= replaceIfNumericEquals("abilities.sonic_boom.sonic_ring_damage_hearts", 14.0, 11.2);
        changed |= replaceIfNumericEquals("abilities.sonic_boom.sonic_ring_knockback", 3.35, 2.68);
        changed |= replaceIfNumericEquals("abilities.sonic_boom.sonic_ring_vertical_knockback", 0.75, 0.60);
        changed |= replaceIfNumericEquals("abilities.the_veteran.beam_player_damage_multiplier", 0.76, 0.0);
        changed |= replaceIfNumericEquals("abilities.the_veteran.beam_player_damage_multiplier", 0.38, 0.0);
        changed |= setIfMissing("abilities.the_veteran.beam_player_damage_multiplier", 0.0);
        changed |= setIfMissing("abilities.the_veteran.beam_player_damage_cap_fraction", 0.20);
        changed |= replaceIfNumericEquals("abilities.sonic_boom.sonic_beam_range", 15.0, 30.0);
        changed |= setIfMissing("abilities.sonic_boom.sonic_beam_range", 30.0);
        changed |= replaceIfNumericEquals("abilities.sonic_boom.sonic_beam_radius", 1.45, 1.85);
        changed |= setIfMissing("abilities.sonic_boom.sonic_beam_radius", 1.85);
        changed |= replaceIfNumericEquals("abilities.sonic_boom.sonic_beam_damage_hearts", 6.0, 5.0);
        changed |= setIfMissing("abilities.sonic_boom.sonic_beam_damage_hearts", 5.0);
        changed |= setIfMissing("abilities.sonic_boom.sonic_beam_knockback", 1.85);
        changed |= setIfMissing("abilities.sonic_boom.sonic_beam_vertical_knockback", 0.35);
        changed |= setIfMissing("abilities.sonic_boom.sonic_beam_pve_damage_multiplier", 2.0);
        changed |= replaceIfNumericEquals("abilities.sonic_boom.sonic_beam_impact_power", 1.35, 0.65);
        changed |= setIfMissing("abilities.sonic_boom.sonic_beam_impact_power", 0.65);
        changed |= setIfMissing("abilities.sonic_boom.sonic_beam_impact_block_damage", true);
        changed |= setIfMissing("abilities.sonic_boom.sonic_beam_impact_radius", 2.5);
        changed |= setIfMissing("abilities.sonic_boom.sonic_beam_impact_damage_hearts", 2.0);
        changed |= setIfMissing("abilities.sonic_boom.sonic_ring_cooldown_ms", 60000);
        changed |= setIfMissing("abilities.sonic_boom.sonic_ring_radius", 5.0);
        changed |= setIfMissing("abilities.sonic_boom.sonic_ring_damage_hearts", 11.2);
        changed |= setIfMissing("abilities.sonic_boom.sonic_ring_knockback", 2.68);
        changed |= setIfMissing("abilities.sonic_boom.sonic_ring_vertical_knockback", 0.60);
        changed |= setIfMissing("abilities.sonic_boom.extra_hearts", 10.0);
        changed |= setIfMissing("abilities.sonic_boom.melee_explosion_damage_multiplier", 1.15);
        changed |= setIfMissing("abilities.sonic_boom.melee_critical_damage_multiplier", 1.20);

        changed |= setIfMissing("abilities.size_changer.strength_level", 2);
        changed |= setIfMissing("abilities.size_changer.resistance_level", 2);
        changed |= setIfMissing("abilities.size_changer.cooldown_ms", 60000);
        changed |= setIfMissing("abilities.size_changer.big_duration_ticks", 1200);
        changed |= setIfMissing("abilities.size_changer.small_duration_ticks", 2400);
        changed |= setIfMissing("abilities.size_changer.big_scale_bonus", 1.0);
        changed |= replaceIfNumericEquals("abilities.size_changer.small_scale_bonus", -0.5, -0.7142857143);
        changed |= setIfMissing("abilities.size_changer.small_scale_bonus", -0.7142857143);
        changed |= setIfMissing("abilities.size_changer.big_extra_hearts", 10.0);
        changed |= setIfMissing("abilities.size_changer.big_damage_multiplier", 2.0);
        changed |= setIfMissing("abilities.size_changer.big_jump_boost_level", 2);

        changed |= setIfMissing("abilities.jumper.strength_level", 1);
        changed |= setIfMissing("abilities.jumper.jump_boost_level", 5);
        changed |= setIfMissing("abilities.jumper.active_ticks", 200);
        changed |= setIfMissing("abilities.jumper.cooldown_ms", 15000);

        changed |= setIfMissing("abilities.shockwave.strength_level", 2);
        changed |= setIfMissing("abilities.shockwave.resistance_level", 1);
        changed |= setIfMissing("abilities.shockwave.cooldown_ms", 120000);
        changed |= setIfMissing("abilities.shockwave.radius", 10.0);
        changed |= setIfMissing("abilities.shockwave.damage_hearts", 3.0);
        changed |= setIfMissing("abilities.shockwave.pve_damage_hearts", 6.0);
        changed |= setIfMissing("abilities.shockwave.knockback", 3.25);
        changed |= setIfMissing("abilities.shockwave.vertical_knockback", 0.75);
        changed |= setIfMissing("abilities.shockwave.hit_band", 1.65);
        changed |= setIfMissing("abilities.shockwave.animation_step", 1.0);
        changed |= setIfMissing("abilities.shockwave.animation_period_ticks", 2);

        changed |= replaceIfNumericEquals("compound_v.chances.heat_vision", 8.0, 9);
        changed |= replaceIfNumericEquals("compound_v.chances.heat_vision_2", 4.0, 7);
        changed |= replaceIfNumericEquals("compound_v.chances.heat_vision_3", 2.0, 6);
        changed |= replaceIfNumericEquals("compound_v.chances.heat_vision_3", 5.0, 6);
        changed |= setIfMissing("compound_v.chances.heat_vision_4", 5);
        changed |= replaceIfNumericEquals("temp_v.chances.heat_vision", 5.0, 9);
        changed |= replaceIfNumericEquals("temp_v.chances.heat_vision_2", 3.0, 7);
        changed |= replaceIfNumericEquals("temp_v.chances.heat_vision_3", 1.0, 6);
        changed |= replaceIfNumericEquals("temp_v.chances.heat_vision_3", 5.0, 6);
        changed |= setIfMissing("temp_v.chances.heat_vision_4", 5);

        changed |= ensureChanceSection("compound_v.chances", orderedMap(
                "heat_vision", 9,
                "heat_vision_2", 7,
                "heat_vision_3", 6,
                "heat_vision_4", 5,
                "jumper", 17,
                "shockwave", 9
        ));
        changed |= ensureChanceSection("temp_v.chances", orderedMap(
                "heat_vision", 9,
                "heat_vision_2", 7,
                "heat_vision_3", 6,
                "heat_vision_4", 5
        ));
        changed |= ensureChanceSection("v_one.chances", orderedMap(
                "sonic_boom", 3,
                "size_changer", 6
        ));

        changed |= setIfMissing("side_effects.temp_v.lethal_use_count", 5);
        changed |= replaceIfNumericEquals("v_null.unpowered.effect_seconds", 30.0, 0);
        changed |= replaceIfNumericEquals("v_null.mobs.effect_seconds", 120.0, 0);
        changed |= setIfMissing("v_null.unpowered.effect_seconds", 0);
        changed |= setIfMissing("v_null.mobs.effect_seconds", 0);

        return changed;
    }

    private boolean migrateLegacyVeteranBeamDamage() {
        if (!plugin.getConfig().contains("abilities.the_veteran.beam_damage")) {
            return setIfMissingVeteranBeamAmount();
        }

        double oldDamage = plugin.getConfig().getDouble("abilities.the_veteran.beam_damage", 12.0);
        double patriotDamage = Math.max(0.1,
                plugin.getConfig().getDouble("abilities.the_patriot.v_one.heat_vision_damage_hearts", 4.725) * 2.0);
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
                plugin.getConfig().getDouble("abilities.the_patriot.v_one.heat_vision_damage_hearts", 4.725) * 2.0);
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
