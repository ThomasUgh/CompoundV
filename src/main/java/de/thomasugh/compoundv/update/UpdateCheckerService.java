package de.thomasugh.compoundv.update;

import de.thomasugh.compoundv.CompoundV;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class UpdateCheckerService implements Listener {

    private static final String MODRINTH_PROJECT_SLUG = "compoundv";
    private static final String MODRINTH_PROJECT_URL = "https://modrinth.com/plugin/compoundv";
    private static final String MODRINTH_VERSIONS_ENDPOINT =
            "https://api.modrinth.com/v2/project/" + MODRINTH_PROJECT_SLUG + "/version?include_changelog=false";
    private static final Pattern VERSION_NUMBER_PATTERN = Pattern.compile("\\\"version_number\\\"\\s*:\\s*\\\"([^\\\"]+)\\\"");

    private final CompoundV plugin;
    private volatile UpdateResult lastResult;

    public UpdateCheckerService(CompoundV plugin) {
        this.plugin = plugin;
    }

    public void start() {
        if (!isEnabled()) return;

        Bukkit.getPluginManager().registerEvents(this, plugin);
        String currentVersion = plugin.getDescription().getVersion();

        CompletableFuture
                .supplyAsync(() -> fetchUpdateResult(currentVersion))
                .thenAccept(result -> {
                    if (result == null) return;
                    lastResult = result;
                    if (!result.updateAvailable()) return;
                    if (notifyConsole()) {
                        plugin.getLogger().info("New update available: " + result.latestVersion()
                                + " (current: " + result.currentVersion() + ") - " + MODRINTH_PROJECT_URL);
                    }
                })
                .exceptionally(throwable -> {
                    if (notifyConsole()) {
                        plugin.getLogger().warning("Update check failed: " + throwable.getMessage());
                    }
                    return null;
                });
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        if (!isEnabled() || !notifyOpsInChat()) return;

        Player player = event.getPlayer();
        if (!player.isOp()) return;

        UpdateResult result = lastResult;
        if (result == null || !result.updateAvailable()) return;

        player.sendMessage(ChatColor.DARK_GRAY + "[" + ChatColor.AQUA + "CompoundV" + ChatColor.DARK_GRAY + "] "
                + ChatColor.YELLOW + "Update available: " + ChatColor.WHITE + result.latestVersion());
        player.sendMessage(ChatColor.GRAY + "Current: " + ChatColor.WHITE + result.currentVersion()
                + ChatColor.DARK_GRAY + " | " + ChatColor.GRAY + "Modrinth: " + ChatColor.AQUA + MODRINTH_PROJECT_URL);
    }

    private UpdateResult fetchUpdateResult(String currentVersion) {
        List<String> versions = fetchVersionNumbers();
        if (versions.isEmpty()) return null;

        String latest = versions.get(0);
        for (String version : versions) {
            if (compareVersions(version, latest) > 0) latest = version;
        }

        return new UpdateResult(currentVersion, latest, compareVersions(latest, currentVersion) > 0);
    }

    private List<String> fetchVersionNumbers() {
        HttpURLConnection connection = null;
        try {
            URL url = new URL(MODRINTH_VERSIONS_ENDPOINT);
            connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("GET");
            connection.setConnectTimeout(5000);
            connection.setReadTimeout(5000);
            connection.setRequestProperty("Accept", "application/json");
            connection.setRequestProperty("User-Agent", "CompoundV/" + plugin.getDescription().getVersion()
                    + " (" + MODRINTH_PROJECT_URL + ")");

            int status = connection.getResponseCode();
            if (status < 200 || status >= 300) {
                throw new IllegalStateException("Modrinth responded with HTTP " + status);
            }

            StringBuilder body = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(
                    connection.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    body.append(line);
                }
            }

            Matcher matcher = VERSION_NUMBER_PATTERN.matcher(body);
            List<String> versions = new ArrayList<>();
            while (matcher.find()) {
                String version = matcher.group(1).trim();
                if (!version.isBlank()) versions.add(version);
            }
            return versions;
        } catch (Exception ex) {
            throw new IllegalStateException(ex.getMessage(), ex);
        } finally {
            if (connection != null) connection.disconnect();
        }
    }

    private boolean isEnabled() {
        return plugin.getConfig().getBoolean("updates.enabled", true);
    }

    private boolean notifyConsole() {
        return plugin.getConfig().getBoolean("updates.console", true);
    }

    private boolean notifyOpsInChat() {
        return plugin.getConfig().getBoolean("updates.op_chat", true);
    }

    private int compareVersions(String left, String right) {
        if (Objects.equals(left, right)) return 0;

        List<Token> a = tokenizeVersion(left);
        List<Token> b = tokenizeVersion(right);
        int max = Math.max(a.size(), b.size());

        for (int i = 0; i < max; i++) {
            Token ta = i < a.size() ? a.get(i) : Token.ZERO;
            Token tb = i < b.size() ? b.get(i) : Token.ZERO;
            int cmp = ta.compareTo(tb);
            if (cmp != 0) return cmp;
        }

        return left.compareToIgnoreCase(right);
    }

    private List<Token> tokenizeVersion(String version) {
        String normalized = version == null ? "" : version.toLowerCase(Locale.ROOT).replaceFirst("^v", "");
        Matcher matcher = Pattern.compile("\\d+|[a-zA-Z]+").matcher(normalized);
        List<Token> tokens = new ArrayList<>();
        while (matcher.find()) {
            String token = matcher.group();
            if (token.chars().allMatch(Character::isDigit)) {
                tokens.add(Token.numeric(token));
            } else {
                tokens.add(Token.text(token));
            }
        }
        if (tokens.isEmpty()) tokens.add(Token.ZERO);
        return tokens;
    }

    private record UpdateResult(String currentVersion, String latestVersion, boolean updateAvailable) { }

    private record Token(boolean numeric, long number, String text) implements Comparable<Token> {
        static final Token ZERO = new Token(true, 0L, "");

        static Token numeric(String value) {
            try {
                return new Token(true, Long.parseLong(value), "");
            } catch (NumberFormatException ex) {
                return new Token(true, Long.MAX_VALUE, "");
            }
        }

        static Token text(String value) {
            return new Token(false, 0L, value == null ? "" : value);
        }

        @Override
        public int compareTo(Token other) {
            if (numeric && other.numeric) return Long.compare(number, other.number);
            if (numeric != other.numeric) return numeric ? 1 : -1;
            return text.compareToIgnoreCase(other.text);
        }
    }
}
