package de.thomasugh.compoundv.locale;

import de.thomasugh.compoundv.CompoundV;
import org.bukkit.ChatColor;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class LocaleManager {

    private static final String DEFAULT_LANG = "en";

    private final CompoundV plugin;
    private YamlConfiguration active;
    private YamlConfiguration fallback;
    private String activeLanguage = DEFAULT_LANG;
    private String prefix = "";

    public LocaleManager(CompoundV plugin) {
        this.plugin = plugin;
        reload();
    }

    public String getLanguage() {
        return activeLanguage;
    }

    public void reload() {
        String lang = plugin.getConfig().getString("language", DEFAULT_LANG);
        if (lang == null || lang.isBlank()) {
            lang = DEFAULT_LANG;
        }
        lang = lang.toLowerCase(Locale.ROOT);

        fallback = loadBundle(DEFAULT_LANG);
        active = lang.equals(DEFAULT_LANG) ? fallback : loadBundle(lang);
        if (active == null) {
            active = fallback;
        }
        activeLanguage = (active == fallback) ? DEFAULT_LANG : lang;

        prefix = active != null ? active.getString("prefix", null) : null;
        if (prefix == null && fallback != null) {
            prefix = fallback.getString("prefix", "");
        }
        if (prefix == null) {
            prefix = "";
        }
        prefix = colorize(prefix);
    }

    private YamlConfiguration loadBundle(String lang) {
        String resource = "lang/messages_" + lang + ".yml";
        File diskFile = new File(plugin.getDataFolder(), resource);
        if (diskFile.exists()) {
            try {
                return YamlConfiguration.loadConfiguration(diskFile);
            } catch (Exception e) {
                plugin.getLogger().warning("Failed to load language file '" + diskFile.getPath() + "': " + e.getMessage());
            }
        }

        try (InputStream in = plugin.getResource(resource)) {
            if (in == null) {
                plugin.getLogger().warning("Missing language bundle in JAR: " + resource);
                return null;
            }
            return YamlConfiguration.loadConfiguration(new InputStreamReader(in, StandardCharsets.UTF_8));
        } catch (Exception e) {
            plugin.getLogger().warning("Failed to load language bundle '" + resource + "': " + e.getMessage());
            return null;
        }
    }

    public String raw(String key) {
        String value = active != null ? active.getString(key) : null;
        if (value == null && fallback != null) {
            value = fallback.getString(key);
        }
        return value != null ? value : key;
    }

    public String raw(String key, Map<String, String> placeholders) {
        String value = raw(key);
        if (placeholders != null) {
            for (Map.Entry<String, String> entry : placeholders.entrySet()) {
                value = value.replace("{" + entry.getKey() + "}", entry.getValue());
            }
        }
        return value;
    }

    public String msg(String key) {
        return format(raw(key));
    }

    public String msg(String key, Map<String, String> placeholders) {
        return format(raw(key, placeholders));
    }

    public String msg(String key, String placeholder, String value) {
        Map<String, String> placeholders = new HashMap<>(1);
        placeholders.put(placeholder, value);
        return msg(key, placeholders);
    }

    public List<String> msgList(String key) {
        return msgList(key, null);
    }

    public List<String> msgList(String key, Map<String, String> placeholders) {
        List<String> list = active != null ? active.getStringList(key) : null;
        if ((list == null || list.isEmpty()) && fallback != null) {
            list = fallback.getStringList(key);
        }
        if (list == null) {
            return List.of();
        }

        List<String> output = new ArrayList<>(list.size());
        for (String line : list) {
            String filled = line;
            if (placeholders != null) {
                for (Map.Entry<String, String> entry : placeholders.entrySet()) {
                    filled = filled.replace("{" + entry.getKey() + "}", entry.getValue());
                }
            }
            output.add(format(filled));
        }
        return output;
    }

    private String format(String input) {
        if (input == null) {
            return "";
        }
        return colorize(input.replace("<prefix>", prefix));
    }

    private String colorize(String input) {
        return ChatColor.translateAlternateColorCodes('&', input == null ? "" : input);
    }
}
