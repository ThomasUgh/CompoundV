package de.thomasugh.compoundv;

import de.thomasugh.compoundv.ability.AbilityRegistry;
import de.thomasugh.compoundv.ability.compoundv.FireAbility;
import de.thomasugh.compoundv.ability.compoundv.FlyAbility;
import de.thomasugh.compoundv.ability.compoundv.HeatVisionAbility;
import de.thomasugh.compoundv.ability.compoundv.JumperAbility;
import de.thomasugh.compoundv.ability.compoundv.ShockwaveAbility;
import de.thomasugh.compoundv.ability.compoundv.InvisibilityAbility;
import de.thomasugh.compoundv.ability.compoundv.SpeedsterAbility;
import de.thomasugh.compoundv.ability.compoundv.StrengthAbility;
import de.thomasugh.compoundv.ability.compoundv.TeleporterAbility;
import de.thomasugh.compoundv.ability.compoundv.TheRunnerAbility;
import de.thomasugh.compoundv.ability.compoundv.TheDiverAbility;
import de.thomasugh.compoundv.ability.compoundv.VisionAbility;
import de.thomasugh.compoundv.ability.shared.ThePatriotAbility;
import de.thomasugh.compoundv.ability.vone.SizeChangerAbility;
import de.thomasugh.compoundv.ability.vone.SonicBoomAbility;
import de.thomasugh.compoundv.ability.vone.TheVeteranAbility;
import de.thomasugh.compoundv.command.CompoundVCommand;
import de.thomasugh.compoundv.config.ConfigMigrationService;
import de.thomasugh.compoundv.listener.DeathRespawnListener;
import de.thomasugh.compoundv.listener.WorkbenchRecipeListener;
import de.thomasugh.compoundv.listener.PlayerActionListener;
import de.thomasugh.compoundv.locale.LanguageResourceInstaller;
import de.thomasugh.compoundv.locale.LocaleManager;
import de.thomasugh.compoundv.manager.AbilityManager;
import de.thomasugh.compoundv.manager.PersistenceManager;
import de.thomasugh.compoundv.manager.PotionRollManager;
import de.thomasugh.compoundv.manager.SideEffectManager;
import de.thomasugh.compoundv.server.ServerCompatibility;
import org.bukkit.NamespacedKey;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.java.JavaPlugin;

public final class CompoundV extends JavaPlugin {

    private static CompoundV instance;

    public static NamespacedKey BOTTLE_KEY;
    public static NamespacedKey SERUM_KEY;
    public static NamespacedKey ACTIVATOR_KEY;

    private AbilityRegistry registry;
    private AbilityManager abilityManager;
    private PotionRollManager rollManager;
    private SideEffectManager sideEffectManager;
    private PersistenceManager persistence;
    private LocaleManager localeManager;
    private ServerCompatibility compatibility;

    @Override
    public void onEnable() {
        instance = this;

        prepareConfiguration();
        initializeKeys();
        initializeServices();
        registerAbilities();
        registerListeners();
        registerCommands();
        logStartup();
    }

    @Override
    public void onDisable() {
        if (abilityManager != null) {
            abilityManager.cleanup();
        }
        instance = null;
    }

    private void prepareConfiguration() {
        saveDefaultConfig();
        new ConfigMigrationService(this).migrate();
        new LanguageResourceInstaller(this).installOrUpdate();
    }

    private void initializeKeys() {
        BOTTLE_KEY = new NamespacedKey(this, "compound_v_bottle");
        SERUM_KEY = new NamespacedKey(this, "compound_v_serum");
        ACTIVATOR_KEY = new NamespacedKey(this, "cv_activator");
    }

    private void initializeServices() {
        compatibility = new ServerCompatibility(this);
        localeManager = new LocaleManager(this);
        registry = new AbilityRegistry();
        persistence = new PersistenceManager(this);
        sideEffectManager = new SideEffectManager(this);
        abilityManager = new AbilityManager(this, registry, persistence);
        rollManager = new PotionRollManager(this, abilityManager);
    }

    private void registerAbilities() {
        registry.register(new ThePatriotAbility(this, "the_patriot", "compound_v", 0xE53935));
        registry.register(new FlyAbility(this));
        registry.register(new HeatVisionAbility(this, "heat_vision", "Heat Vision I", 1, 0x2DD2FF));
        registry.register(new HeatVisionAbility(this, "heat_vision_2", "Heat Vision II", 2, 0x2DFF69));
        registry.register(new HeatVisionAbility(this, "heat_vision_3", "Heat Vision III", 3, 0xFF8C1A));
        registry.register(new HeatVisionAbility(this, "heat_vision_4", "Heat Vision IV", 4, 0xFF2A1A));
        registry.register(new SpeedsterAbility(this));
        registry.register(new StrengthAbility(this));
        registry.register(new InvisibilityAbility(this));
        registry.register(new FireAbility(this));
        registry.register(new TheDiverAbility(this));
        registry.register(new TheRunnerAbility(this));
        registry.register(new JumperAbility(this));
        registry.register(new ShockwaveAbility(this));
        registry.register(new VisionAbility(this));
        registry.register(new TeleporterAbility(this));
        registry.register(new ThePatriotAbility(this, "the_patriot_v_one", "v_one", 0xFF5252));
        registry.register(new TheVeteranAbility(this));
        registry.register(new SonicBoomAbility(this));
        registry.register(new SizeChangerAbility(this));
    }

    private void registerListeners() {
        PluginManager pluginManager = getServer().getPluginManager();
        pluginManager.registerEvents(new PlayerActionListener(this, abilityManager, rollManager), this);
        pluginManager.registerEvents(new WorkbenchRecipeListener(this), this);
        pluginManager.registerEvents(new DeathRespawnListener(this, abilityManager), this);
    }

    private void registerCommands() {
        PluginCommand command = getCommand("compoundv");
        if (command == null) {
            getLogger().warning("Command 'compoundv' is missing in plugin.yml.");
            return;
        }

        CompoundVCommand handler = new CompoundVCommand(this, abilityManager, registry, rollManager);
        command.setExecutor(handler);
        command.setTabCompleter(handler);
    }

    private void logStartup() {
        getLogger().info("CompoundV v" + getDescription().getVersion()
                + " loaded with " + registry.ids().size()
                + " abilities (language: " + localeManager.getLanguage()
                + ", server: " + compatibility.serverName()
                + ", folia: " + compatibility.isFolia() + ")");
    }

    public static CompoundV getInstance() {
        return instance;
    }

    public AbilityRegistry getRegistry() {
        return registry;
    }

    public AbilityManager getAbilityManager() {
        return abilityManager;
    }

    public PotionRollManager getRollManager() {
        return rollManager;
    }

    public SideEffectManager getSideEffectManager() {
        return sideEffectManager;
    }

    public LocaleManager getLocaleManager() {
        return localeManager;
    }

    public ServerCompatibility getCompatibility() {
        return compatibility;
    }
}
