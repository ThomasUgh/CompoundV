package de.thomasugh.compoundv.locale;

import de.thomasugh.compoundv.CompoundVPlugin;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
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
    private static final MiniMessage MM = MiniMessage.miniMessage();
    private static final Map<Character, String> LEGACY_CODES = Map.ofEntries(
            Map.entry('0', "<black>"),
            Map.entry('1', "<dark_blue>"),
            Map.entry('2', "<dark_green>"),
            Map.entry('3', "<dark_aqua>"),
            Map.entry('4', "<dark_red>"),
            Map.entry('5', "<dark_purple>"),
            Map.entry('6', "<gold>"),
            Map.entry('7', "<gray>"),
            Map.entry('8', "<dark_gray>"),
            Map.entry('9', "<blue>"),
            Map.entry('a', "<green>"),
            Map.entry('b', "<aqua>"),
            Map.entry('c', "<red>"),
            Map.entry('d', "<light_purple>"),
            Map.entry('e', "<yellow>"),
            Map.entry('f', "<white>"),
            Map.entry('k', "<obfuscated>"),
            Map.entry('l', "<bold>"),
            Map.entry('m', "<strikethrough>"),
            Map.entry('n', "<underlined>"),
            Map.entry('o', "<italic>"),
            Map.entry('r', "<reset>")
    );

    private final CompoundVPlugin plugin;
    private YamlConfiguration active;
    private YamlConfiguration fallback;
    private String activeLanguage = DEFAULT_LANG;
    private TagResolver prefixResolver = TagResolver.empty();

    public LocaleManager(CompoundVPlugin plugin) {
        this.plugin = plugin;
        reload();
    }

    public String getLanguage() { return activeLanguage; }

    public void reload() {
        String lang = plugin.getConfig().getString("language", DEFAULT_LANG);
        if (lang == null || lang.isBlank()) lang = DEFAULT_LANG;
        lang = lang.toLowerCase(Locale.ROOT);

        fallback = loadBundle(DEFAULT_LANG);
        active   = lang.equals(DEFAULT_LANG) ? fallback : loadBundle(lang);
        if (active == null) active = fallback;
        activeLanguage = (active == fallback) ? DEFAULT_LANG : lang;

        String prefix = active != null ? active.getString("prefix", null) : null;
        if (prefix == null && fallback != null) prefix = fallback.getString("prefix", "");
        if (prefix == null) prefix = "";
        prefixResolver = Placeholder.parsed("prefix", legacyToMini(prefix));
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
            return YamlConfiguration.loadConfiguration(
                    new InputStreamReader(in, StandardCharsets.UTF_8));
        } catch (Exception e) {
            plugin.getLogger().warning("Failed to load language bundle '" + resource + "': " + e.getMessage());
            return null;
        }
    }

    public String raw(String key) {
        String v = active != null ? active.getString(key) : null;
        if (v == null && fallback != null) v = fallback.getString(key);
        return v != null ? v : key;
    }

    public String raw(String key, Map<String, String> placeholders) {
        String value = raw(key);
        if (placeholders != null) {
            for (Map.Entry<String, String> e : placeholders.entrySet()) {
                value = value.replace("{" + e.getKey() + "}", e.getValue());
            }
        }
        return value;
    }

    public Component msg(String key) {
        return MM.deserialize(legacyToMini(raw(key)), prefixResolver);
    }

    public Component msg(String key, Map<String, String> placeholders) {
        return MM.deserialize(legacyToMini(raw(key, placeholders)), prefixResolver);
    }

    public Component msg(String key, String placeholder, String value) {
        Map<String, String> m = new HashMap<>(1);
        m.put(placeholder, value);
        return msg(key, m);
    }

    public List<Component> msgList(String key) {
        List<String> list = active != null ? active.getStringList(key) : null;
        if ((list == null || list.isEmpty()) && fallback != null) {
            list = fallback.getStringList(key);
        }
        if (list == null) return List.of();
        List<Component> out = new ArrayList<>(list.size());
        for (String s : list) out.add(MM.deserialize(legacyToMini(s), prefixResolver));
        return out;
    }

    public List<Component> msgList(String key, Map<String, String> placeholders) {
        List<String> list = active != null ? active.getStringList(key) : null;
        if ((list == null || list.isEmpty()) && fallback != null) {
            list = fallback.getStringList(key);
        }
        if (list == null) return List.of();
        List<Component> out = new ArrayList<>(list.size());
        for (String s : list) {
            String filled = s;
            if (placeholders != null) {
                for (Map.Entry<String, String> e : placeholders.entrySet()) {
                    filled = filled.replace("{" + e.getKey() + "}", e.getValue());
                }
            }
            out.add(MM.deserialize(legacyToMini(filled), prefixResolver));
        }
        return out;
    }

    private String legacyToMini(String input) {
        if (input == null || input.indexOf('&') < 0) return input == null ? "" : input;

        StringBuilder out = new StringBuilder(input.length());
        for (int i = 0; i < input.length(); i++) {
            char current = input.charAt(i);
            if (current == '&' && i + 1 < input.length()) {
                char code = Character.toLowerCase(input.charAt(i + 1));
                String replacement = LEGACY_CODES.get(code);
                if (replacement != null) {
                    out.append(replacement);
                    i++;
                    continue;
                }
            }
            out.append(current);
        }
        return out.toString();
    }
}
