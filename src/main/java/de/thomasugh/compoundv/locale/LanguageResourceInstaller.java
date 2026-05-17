package de.thomasugh.compoundv.locale;

import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;

public final class LanguageResourceInstaller {

    private static final String[] LANGUAGE_FILES = {
            "lang/messages_en.yml",
            "lang/messages_de.yml"
    };

    private final JavaPlugin plugin;

    public LanguageResourceInstaller(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public void installOrUpdate() {
        for (String resourcePath : LANGUAGE_FILES) {
            installOrUpdate(resourcePath);
        }
    }

    private void installOrUpdate(String resourcePath) {
        File output = new File(plugin.getDataFolder(), resourcePath);
        if (!output.exists()) {
            saveResource(resourcePath, false);
            return;
        }

        mergeExistingFile(resourcePath, output);
    }

    private void mergeExistingFile(String resourcePath, File output) {
        try (InputStream in = plugin.getResource(resourcePath)) {
            if (in == null) return;

            YamlConfiguration bundled = YamlConfiguration.loadConfiguration(
                    new InputStreamReader(in, StandardCharsets.UTF_8));
            YamlConfiguration disk = YamlConfiguration.loadConfiguration(output);

            int bundledVersion = bundled.getInt("language_file_version", 1);
            int diskVersion = disk.getInt("language_file_version", 0);

            if (diskVersion < bundledVersion) {
                backup(output, diskVersion);
                saveResource(resourcePath, true);
                return;
            }

            if (copyMissingKeys(bundled, disk)) {
                disk.save(output);
            }
        } catch (Exception ex) {
            plugin.getLogger().warning("Could not merge language defaults for '" + resourcePath + "': " + ex.getMessage());
        }
    }

    private boolean copyMissingKeys(YamlConfiguration bundled, YamlConfiguration disk) {
        boolean changed = false;
        for (String key : bundled.getKeys(true)) {
            if (!bundled.isConfigurationSection(key) && !disk.contains(key)) {
                disk.set(key, bundled.get(key));
                changed = true;
            }
        }
        return changed;
    }

    private void backup(File file, int diskVersion) {
        File backup = new File(file.getParentFile(), file.getName() + ".v" + diskVersion + ".bak");
        try {
            Files.copy(file.toPath(), backup.toPath(), StandardCopyOption.REPLACE_EXISTING);
        } catch (Exception ex) {
            plugin.getLogger().warning("Could not create language backup for '" + file.getName() + "': " + ex.getMessage());
        }
    }

    private void saveResource(String resourcePath, boolean replace) {
        try {
            plugin.saveResource(resourcePath, replace);
        } catch (IllegalArgumentException ex) {
            plugin.getLogger().warning("Could not save bundled resource '" + resourcePath + "': " + ex.getMessage());
        }
    }
}
