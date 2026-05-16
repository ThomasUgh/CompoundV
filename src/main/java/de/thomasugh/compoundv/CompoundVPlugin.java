package de.thomasugh.compoundv;

import de.thomasugh.compoundv.ability.AbilityRegistry;
import de.thomasugh.compoundv.ability.compoundv.FireAbility;
import de.thomasugh.compoundv.ability.compoundv.FlyAbility;
import de.thomasugh.compoundv.ability.compoundv.HeatVisionAbility;
import de.thomasugh.compoundv.ability.compoundv.TheDiverAbility;
import de.thomasugh.compoundv.ability.compoundv.InvisibilityAbility;
import de.thomasugh.compoundv.ability.compoundv.SpeedsterAbility;
import de.thomasugh.compoundv.ability.compoundv.StrengthAbility;
import de.thomasugh.compoundv.ability.compoundv.VisionAbility;
import de.thomasugh.compoundv.ability.shared.ThePatriotAbility;
import de.thomasugh.compoundv.ability.vone.TheVeteranAbility;
import de.thomasugh.compoundv.command.CompoundVCommand;
import de.thomasugh.compoundv.listener.DeathRespawnListener;
import de.thomasugh.compoundv.listener.PlayerActionListener;
import de.thomasugh.compoundv.locale.LocaleManager;
import de.thomasugh.compoundv.manager.AbilityManager;
import de.thomasugh.compoundv.manager.PersistenceManager;
import de.thomasugh.compoundv.manager.PotionRollManager;
import net.kyori.adventure.text.format.TextColor;
import org.bukkit.NamespacedKey;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.Map;

public class CompoundVPlugin extends JavaPlugin {

    private static CompoundVPlugin instance;
    public static NamespacedKey BOTTLE_KEY;
    public static NamespacedKey ACTIVATOR_KEY;

    private AbilityRegistry    registry;
    private AbilityManager     abilityManager;
    private PotionRollManager  rollManager;
    private PersistenceManager persistence;
    private LocaleManager      localeManager;

    @Override
    public void onEnable() {
        instance = this;
        saveDefaultConfig();
        mergeConfigDefaults();
        migrateConfigAliases();
        saveBundledLanguageFiles();

        BOTTLE_KEY    = new NamespacedKey(this, "compound_v_bottle");
        ACTIVATOR_KEY = new NamespacedKey(this, "cv_activator");

        localeManager = new LocaleManager(this);

        registry = new AbilityRegistry();
        registry.register(new ThePatriotAbility(this, "the_patriot",       "compound_v", TextColor.color(0xE53935)));
        registry.register(new FlyAbility());
        registry.register(new HeatVisionAbility(this));
        registry.register(new SpeedsterAbility(this));
        registry.register(new StrengthAbility(this));
        registry.register(new InvisibilityAbility(this));
        registry.register(new FireAbility(this));
        registry.register(new TheDiverAbility(this));
        registry.register(new VisionAbility(this));
        registry.register(new ThePatriotAbility(this, "the_patriot_v_one", "v_one",      TextColor.color(0xFF5252)));
        registry.register(new TheVeteranAbility(this));

        persistence    = new PersistenceManager(this);
        abilityManager = new AbilityManager(this, registry, persistence);
        rollManager    = new PotionRollManager(this, abilityManager);

        var pm = getServer().getPluginManager();
        pm.registerEvents(new PlayerActionListener(this, abilityManager, rollManager), this);
        pm.registerEvents(new DeathRespawnListener(abilityManager), this);

        var cmd = getCommand("compoundv");
        if (cmd != null) {
            var h = new CompoundVCommand(this, abilityManager, registry, rollManager);
            cmd.setExecutor(h);
            cmd.setTabCompleter(h);
        }

        getLogger().info("CompoundV v" + getPluginMeta().getVersion()
                + " loaded with " + registry.ids().size() + " abilities (language: "
                + localeManager.getLanguage() + ")");
    }

    private void mergeConfigDefaults() {
        try (InputStream in = getResource("config.yml")) {
            if (in == null) return;
            YamlConfiguration defaults = YamlConfiguration.loadConfiguration(
                    new InputStreamReader(in, StandardCharsets.UTF_8));
            getConfig().setDefaults(defaults);
            getConfig().options().copyDefaults(true);
            saveConfig();
        } catch (Exception ex) {
            getLogger().warning("Could not merge config defaults: " + ex.getMessage());
        }
    }

    private void migrateConfigAliases() {
        boolean changed = false;

        changed |= moveIfMissing("compound_v.chances.homelander", "compound_v.chances.the_patriot");
        changed |= moveIfMissing("v_one.chances.vone_homelander", "v_one.chances.the_patriot_v_one");
        changed |= moveIfMissing("v_one.chances.soldier_boy", "v_one.chances.the_veteran");
        changed |= moveIfMissing("abilities.homelander", "abilities.the_patriot");
        changed |= moveIfMissing("abilities.soldier_boy", "abilities.the_veteran");

        changed |= ensureChanceSection("compound_v.chances", Map.of(
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
        changed |= ensureChanceSection("temp_v.chances", Map.of(
                "fly", 10,
                "heat_vision", 5,
                "speedster", 20,
                "strength", 25,
                "invisibility", 15,
                "fire", 10
        ));
        changed |= ensureChanceSection("v_one.chances", Map.of(
                "the_patriot_v_one", 1,
                "the_veteran", 3
        ));

        changed |= migrateVeteranBurstDefaults();

        if (changed) saveConfig();
    }

    private boolean migrateVeteranBurstDefaults() {
        boolean changed = false;
        changed |= replaceIfNumericEquals("heat_vision.damage_amount", 8.0, 2.0);
        changed |= replaceIfNumericEquals("abilities.the_veteran.beam_duration_ticks", 60.0, 80);
        changed |= replaceIfNumericEquals("abilities.the_veteran.beam_duration_ticks", 100.0, 80);
        changed |= replaceIfNumericEquals("abilities.the_veteran.beam_damage_interval_ticks", 6.0, 2);
        changed |= replaceIfNumericEquals("abilities.the_veteran.beam_block_affect_interval_ticks", 5.0, 4);
        changed |= replaceIfNumericEquals("abilities.the_veteran.beam_block_affect_interval_ticks", 4.0, 4);
        changed |= replaceIfNumericEquals("abilities.the_veteran.beam_max_blocks_per_pulse", 4.0, 5);
        changed |= replaceIfNumericEquals("abilities.the_veteran.beam_max_blocks_per_pulse", 6.0, 5);
        changed |= replaceIfNumericEquals("abilities.the_veteran.beam_block_hits_to_break", 3.0, 5);
        changed |= replaceIfNumericEquals("abilities.the_veteran.ground_zero_radius", 8.0, 14.0);
        changed |= replaceIfNumericEquals("abilities.the_veteran.ground_zero_damage", 240.0, 500.0);
        changed |= replaceIfNumericEquals("abilities.the_veteran.ground_zero_knockback", 4.0, 7.5);
        changed |= replaceIfNumericEquals("abilities.the_veteran.mushroom_cloud_duration_ticks", 900.0, 1200);
        changed |= replaceIfNumericEquals("abilities.the_veteran.mushroom_cloud_period_ticks", 10.0, 8);
        changed |= replaceIfNumericEquals("abilities.the_veteran.mushroom_cloud_height", 26.0, 32.0);
        changed |= replaceIfNumericEquals("abilities.the_veteran.mushroom_cloud_radius", 13.0, 15.5);

        changed |= replaceIfNumericEquals("abilities.the_veteran.pre_charge_hold_ticks", 40.0, 20);
        if (!getConfig().contains("abilities.the_veteran.pre_charge_hold_ticks")) {
            getConfig().set("abilities.the_veteran.pre_charge_hold_ticks", 20);
            changed = true;
        }
        if (!getConfig().contains("abilities.the_veteran.charge_duration_ticks")) {
            getConfig().set("abilities.the_veteran.charge_duration_ticks", 100);
            changed = true;
        }
        if (!getConfig().contains("abilities.the_veteran.charge_period_ticks")) {
            getConfig().set("abilities.the_veteran.charge_period_ticks", 5);
            changed = true;
        }
        if (!getConfig().contains("abilities.the_veteran.melee_knockback_horizontal")) {
            getConfig().set("abilities.the_veteran.melee_knockback_horizontal", 1.35);
            changed = true;
        }
        if (!getConfig().contains("abilities.the_veteran.melee_knockback_vertical")) {
            getConfig().set("abilities.the_veteran.melee_knockback_vertical", 0.28);
            changed = true;
        }
        if (!getConfig().contains("abilities.the_veteran.beam_hit_knockback")) {
            getConfig().set("abilities.the_veteran.beam_hit_knockback", 0.45);
            changed = true;
        }

        if (!getConfig().contains("abilities.the_patriot.compound_v.heat_vision_damage_amount")) {
            getConfig().set("abilities.the_patriot.compound_v.heat_vision_damage_amount", 5.2);
            changed = true;
        }
        if (!getConfig().contains("abilities.the_patriot.v_one.heat_vision_damage_amount")) {
            getConfig().set("abilities.the_patriot.v_one.heat_vision_damage_amount", 5.2);
            changed = true;
        }

        if (getConfig().contains("abilities.the_veteran.beam_damage")) {
            double oldDamage = getConfig().getDouble("abilities.the_veteran.beam_damage", 12.0);
            double patriotDamage = Math.max(0.1, getConfig().getDouble("abilities.the_patriot.v_one.heat_vision_damage_amount", 5.2));
            double multiplier = Math.abs(oldDamage - 12.0) < 0.0001 ? 5.0 : Math.max(0.1, oldDamage / patriotDamage);
            getConfig().set("abilities.the_veteran.beam_damage_multiplier", multiplier);
            getConfig().set("abilities.the_veteran.beam_damage_amount", patriotDamage * multiplier);
            getConfig().set("abilities.the_veteran.beam_damage", null);
            changed = true;
        }
        if (!getConfig().contains("abilities.the_veteran.beam_damage_amount")) {
            double patriotDamage = Math.max(0.1, getConfig().getDouble("abilities.the_patriot.v_one.heat_vision_damage_amount", 5.2));
            double multiplier = getConfig().getDouble("abilities.the_veteran.beam_damage_multiplier", 5.0);
            getConfig().set("abilities.the_veteran.beam_damage_amount", patriotDamage * multiplier);
            changed = true;
        }

        changed |= migrateVersion102Defaults();

        return changed;
    }


    private boolean migrateVersion102Defaults() {
        boolean changed = false;

        changed |= replaceIfNumericEquals("abilities.the_patriot.v_one.heat_vision_damage_amount", 5.0, 5.2);
        changed |= replaceIfNumericEquals("abilities.the_patriot.v_one.heat_vision_damage_multiplier", 1.0, 1.33);

        if (!getConfig().contains("abilities.the_patriot.compound_v.heat_vision_range")) {
            getConfig().set("abilities.the_patriot.compound_v.heat_vision_range",
                    getConfig().getDouble("heat_vision.range", 43.0));
            changed = true;
        }
        if (!getConfig().contains("abilities.the_patriot.v_one.heat_vision_range")) {
            double standardRange = getConfig().getDouble("abilities.the_patriot.compound_v.heat_vision_range",
                    getConfig().getDouble("heat_vision.range", 43.0));
            getConfig().set("abilities.the_patriot.v_one.heat_vision_range", standardRange + 5.0);
            changed = true;
        }
        if (!getConfig().contains("abilities.the_patriot.v_one.strength_level")) {
            int standardStrength = getConfig().getInt("abilities.the_patriot.compound_v.strength_level", 3);
            getConfig().set("abilities.the_patriot.v_one.strength_level", standardStrength + 1);
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

    private boolean setIfMissing(String path, Object value) {
        if (getConfig().contains(path)) return false;
        getConfig().set(path, value);
        return true;
    }

    private boolean replaceIfNumericEquals(String path, double oldValue, Object newValue) {
        if (!getConfig().contains(path)) return false;
        Object current = getConfig().get(path);
        if (!(current instanceof Number n)) return false;
        if (Math.abs(n.doubleValue() - oldValue) > 0.0001) return false;
        getConfig().set(path, newValue);
        return true;
    }

    private boolean moveIfMissing(String oldPath, String newPath) {
        if (!getConfig().contains(oldPath)) return false;
        ConfigurationSection oldSection = getConfig().getConfigurationSection(oldPath);
        if (oldSection != null) {
            getConfig().set(newPath, null);
            ConfigurationSection newSection = getConfig().createSection(newPath);
            for (String key : oldSection.getKeys(true)) {
                if (!oldSection.isConfigurationSection(key)) {
                    newSection.set(key, oldSection.get(key));
                }
            }
        } else {
            getConfig().set(newPath, getConfig().get(oldPath));
        }
        getConfig().set(oldPath, null);
        return true;
    }

    private boolean ensureChanceSection(String path, Map<String, Integer> defaults) {
        ConfigurationSection section = getConfig().getConfigurationSection(path);
        boolean changed = false;
        if (section == null) {
            section = getConfig().createSection(path);
            changed = true;
        }
        for (Map.Entry<String, Integer> e : defaults.entrySet()) {
            if (!section.contains(e.getKey()) || section.getInt(e.getKey(), 0) <= 0) {
                section.set(e.getKey(), e.getValue());
                changed = true;
            }
        }
        return changed;
    }

    private void saveBundledLanguageFiles() {
        saveBundledLanguageResource("lang/messages_en.yml");
        saveBundledLanguageResource("lang/messages_de.yml");
    }

    private void saveBundledLanguageResource(String resourcePath) {
        File out = new File(getDataFolder(), resourcePath);
        if (!out.exists()) {
            try {
                saveResource(resourcePath, false);
                return;
            } catch (IllegalArgumentException ex) {
                getLogger().warning("Could not save bundled resource '" + resourcePath + "': " + ex.getMessage());
                return;
            }
        }

        try (InputStream in = getResource(resourcePath)) {
            if (in == null) return;
            YamlConfiguration bundled = YamlConfiguration.loadConfiguration(
                    new InputStreamReader(in, StandardCharsets.UTF_8));
            YamlConfiguration disk = YamlConfiguration.loadConfiguration(out);

            int bundledVersion = bundled.getInt("language_file_version", 1);
            int diskVersion = disk.getInt("language_file_version", 0);
            if (diskVersion < bundledVersion) {
                File backup = new File(out.getParentFile(), out.getName() + ".v" + diskVersion + ".bak");
                try {
                    Files.copy(out.toPath(), backup.toPath(), StandardCopyOption.REPLACE_EXISTING);
                } catch (Exception backupEx) {
                    getLogger().warning("Could not create language backup for '" + resourcePath + "': " + backupEx.getMessage());
                }
                saveResource(resourcePath, true);
                return;
            }

            boolean changed = false;
            for (String key : bundled.getKeys(true)) {
                if (!bundled.isConfigurationSection(key) && !disk.contains(key)) {
                    disk.set(key, bundled.get(key));
                    changed = true;
                }
            }
            if (changed) disk.save(out);
        } catch (Exception ex) {
            getLogger().warning("Could not merge language defaults for '" + resourcePath + "': " + ex.getMessage());
        }
    }

    @Override
    public void onDisable() {
        if (abilityManager != null) abilityManager.cleanup();
    }

    public static CompoundVPlugin getInstance() { return instance; }

    public AbilityRegistry   getRegistry()       { return registry; }
    public AbilityManager    getAbilityManager() { return abilityManager; }
    public PotionRollManager getRollManager()    { return rollManager; }
    public LocaleManager     getLocaleManager()  { return localeManager; }
}
