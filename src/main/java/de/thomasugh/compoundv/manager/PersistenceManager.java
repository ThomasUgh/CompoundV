package de.thomasugh.compoundv.manager;
import de.thomasugh.compoundv.CompoundV;
import de.thomasugh.compoundv.data.CompoundPotion;
import de.thomasugh.compoundv.data.PlayerAbilityData;
import de.thomasugh.compoundv.util.AbilityAliases;
import org.bukkit.configuration.file.YamlConfiguration;
import java.io.File;
import java.io.IOException;
import java.util.*;
public class PersistenceManager {
    private final CompoundV plugin;
    private final File dataDir;
    public PersistenceManager(CompoundV plugin) {
        this.plugin  = plugin;
        this.dataDir = new File(plugin.getDataFolder(), "playerdata");
        dataDir.mkdirs();
    }
    public void save(UUID uuid, PlayerAbilityData data) {
        YamlConfiguration cfg = new YamlConfiguration();
        cfg.set("ability",    data.abilityId());
        cfg.set("potion",     data.potionType().name());
        cfg.set("expires_at", data.expiresAt());
        try { cfg.save(file(uuid)); } catch (IOException e) { plugin.getLogger().warning("Save failed: " + e.getMessage()); }
    }
    public Optional<PlayerAbilityData> load(UUID uuid) {
        File f = file(uuid);
        if (!f.exists()) return Optional.empty();
        try {
            YamlConfiguration cfg = YamlConfiguration.loadConfiguration(f);
            String ability = AbilityAliases.normalize(cfg.getString("ability"));
            if (ability == null) return Optional.empty();
            CompoundPotion potion = CompoundPotion.valueOf(cfg.getString("potion", "COMPOUND_V"));
            PlayerAbilityData data = new PlayerAbilityData(ability, potion, cfg.getLong("expires_at", 0));
            if (data.isExpired()) { delete(uuid); return Optional.empty(); }
            return Optional.of(data);
        } catch (Exception e) { return Optional.empty(); }
    }
    public void delete(UUID uuid) { file(uuid).delete(); }
    private File file(UUID uuid) { return new File(dataDir, uuid + ".yml"); }
}
