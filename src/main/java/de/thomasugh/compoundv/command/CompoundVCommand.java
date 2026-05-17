package de.thomasugh.compoundv.command;

import de.thomasugh.compoundv.CompoundV;
import de.thomasugh.compoundv.ability.Ability;
import de.thomasugh.compoundv.ability.AbilityRegistry;
import de.thomasugh.compoundv.data.CompoundPotion;
import de.thomasugh.compoundv.data.PlayerAbilityData;
import de.thomasugh.compoundv.locale.LocaleManager;
import de.thomasugh.compoundv.manager.AbilityManager;
import de.thomasugh.compoundv.manager.PotionRollManager;
import de.thomasugh.compoundv.util.ItemUtil;
import de.thomasugh.compoundv.util.AbilityAliases;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class CompoundVCommand implements CommandExecutor, TabCompleter {

    private final CompoundV   plugin;
    private final AbilityManager    manager;
    private final AbilityRegistry   registry;
    private final PotionRollManager roller;

    public CompoundVCommand(CompoundV plugin, AbilityManager manager,
                            AbilityRegistry registry, PotionRollManager roller) {
        this.plugin = plugin;
        this.manager = manager;
        this.registry = registry;
        this.roller = roller;
    }

    private LocaleManager loc() { return plugin.getLocaleManager(); }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (args.length == 0) { help(sender); return true; }
        switch (args[0].toLowerCase(Locale.ROOT)) {
            case "give"   -> handleGive(sender, args);
            case "remove" -> handleRemove(sender, args);
            case "info"   -> handleInfo(sender, args);
            case "reload" -> handleReload(sender);
            case "help"   -> help(sender);
            default       -> help(sender);
        }
        return true;
    }

    private void handleGive(CommandSender sender, String[] args) {
        if (!sender.hasPermission("compoundv.admin")) {
            sender.sendMessage(loc().msg("general.no_permission"));
            return;
        }
        if (args.length < 3) {
            sender.sendMessage(loc().msg("command.usage_give"));
            return;
        }

        Player t = Bukkit.getPlayer(args[1]);
        if (t == null) {
            sender.sendMessage(loc().msg("general.player_not_found"));
            return;
        }

        CompoundPotion pt = args.length >= 4 ? parsePotion(args[3]) : CompoundPotion.COMPOUND_V;
        if (pt == null) pt = CompoundPotion.COMPOUND_V;

        if (args[2].equalsIgnoreCase("bottle")) {
            t.getInventory().addItem(ItemUtil.createBottle(plugin, pt));
            Map<String, String> ph = new HashMap<>();
            ph.put("bottle", pt.getDisplayName());
            ph.put("player", t.getName());
            sender.sendMessage(loc().msg("command.give_bottle_ok", ph));
            return;
        }

        String abilityId = resolveAbilityId(args[2]);
        if (abilityId == null) {
            sender.sendMessage(loc().msg("command.unknown_ability", "ability", args[2]));
            return;
        }

        Ability ab = registry.get(abilityId).orElse(null);
        if (ab == null) {
            sender.sendMessage(loc().msg("command.unknown_ability", "ability", args[2]));
            return;
        }

        manager.giveAbility(t, abilityId, pt, 0L);

        Map<String, String> ph = new HashMap<>();
        ph.put("name",   ab.getDisplayName());
        ph.put("color",  "#" + AbilityManager.colorHex(ab.getColor()));
        ph.put("player", t.getName());
        sender.sendMessage(loc().msg("command.give_ability_ok", ph));
    }

    private String resolveAbilityId(String input) {
        String id = AbilityAliases.normalize(input);
        return registry.contains(id) ? id : null;
    }

    private void handleRemove(CommandSender sender, String[] args) {
        if (!sender.hasPermission("compoundv.admin")) {
            sender.sendMessage(loc().msg("general.no_permission"));
            return;
        }
        if (args.length < 2) {
            sender.sendMessage(loc().msg("command.usage_remove"));
            return;
        }
        Player t = Bukkit.getPlayer(args[1]);
        if (t == null) {
            sender.sendMessage(loc().msg("general.player_not_found"));
            return;
        }
        manager.removeAndForget(t);
        t.sendMessage(loc().msg("ability.removed_admin"));
        sender.sendMessage(loc().msg("command.remove_ok", "player", t.getName()));
    }

    private void handleInfo(CommandSender sender, String[] args) {
        Player target = null;
        if (args.length >= 2) {
            if (!sender.hasPermission("compoundv.admin")) {
                sender.sendMessage(loc().msg("general.no_permission"));
                return;
            }
            target = Bukkit.getPlayer(args[1]);
            if (target == null) {
                sender.sendMessage(loc().msg("general.player_not_found"));
                return;
            }
        } else if (sender instanceof Player p) {
            target = p;
        }

        sender.sendMessage(loc().msg("info.plugin_header"));
        sender.sendMessage(loc().msg("info.plugin_version",
                "version", plugin.getDescription().getVersion()));
        sender.sendMessage(loc().msg("info.plugin_ability_count",
                "count", Integer.toString(manager.abilityCount())));
        sender.sendMessage(loc().msg("info.plugin_language",
                "language", loc().getLanguage()));
        sender.sendMessage(loc().msg("info.help_hint"));

        if (target != null) {
            sender.sendMessage(loc().msg("info.player_header"));
            sender.sendMessage(loc().msg("info.player_target",
                    "player", target.getName()));

            PlayerAbilityData d = manager.getData(target);
            if (d == null) {
                sender.sendMessage(loc().msg("info.player_none",
                        "player", target.getName()));
            } else {
                Ability ab = registry.get(d.abilityId()).orElse(null);
                String name = d.abilityId();
                if (ab != null) {
                    name = ab.getDisplayName();
                }
                Map<String, String> ph = new HashMap<>();
                ph.put("name", name);
                sender.sendMessage(loc().msg("info.player_ability", ph));
                sender.sendMessage(loc().msg("info.player_potion",
                        "potion", d.potionType().getDisplayName()));

                if (d.isTemporary()) {
                    sender.sendMessage(loc().msg("info.player_status_temporary"));
                    long s = d.remainingSec();
                    Map<String, String> dh = new HashMap<>();
                    dh.put("minutes", Long.toString(s / 60));
                    dh.put("seconds", Long.toString(s % 60));
                    sender.sendMessage(loc().msg("info.player_expires", dh));
                } else {
                    sender.sendMessage(loc().msg("info.player_status_permanent"));
                }

                if (ab != null) {
                    sender.sendMessage(loc().msg("info.player_controls_header"));
                    for (var line : loc().msgList(ab.getDescriptionKey())) {
                        sender.sendMessage(line);
                    }
                }
            }
        } else {
            sender.sendMessage(loc().msg("info.player_no_target_hint"));
        }
        sender.sendMessage(loc().msg("info.plugin_footer"));
    }

    private void handleReload(CommandSender sender) {
        if (!sender.hasPermission("compoundv.admin")) {
            sender.sendMessage(loc().msg("general.no_permission"));
            return;
        }
        plugin.reloadConfig();
        plugin.getLocaleManager().reload();
        sender.sendMessage(loc().msg("general.reloaded"));
    }

    private void help(CommandSender s) {
        s.sendMessage(loc().msg("command.header"));
        s.sendMessage(loc().msg("command.give_bottle"));
        s.sendMessage(loc().msg("command.give_ability"));
        s.sendMessage(loc().msg("command.remove"));
        s.sendMessage(loc().msg("command.info_self"));
        s.sendMessage(loc().msg("command.reload"));
        s.sendMessage(loc().msg("command.abilities_list",
                "list", String.join(", ", registry.ids())));
        s.sendMessage(loc().msg("command.footer"));
    }

    private CompoundPotion parsePotion(String s) {
        return switch (s.toLowerCase(Locale.ROOT)) {
            case "compoundv","compound_v","cv" -> CompoundPotion.COMPOUND_V;
            case "tempv","temp_v","tv"         -> CompoundPotion.TEMP_V;
            case "vone","v_one","v1"           -> CompoundPotion.V_ONE;
            case "antiv","anti_v","av"         -> CompoundPotion.ANTI_V;
            default                            -> null;
        };
    }

    @Override
    public List<String> onTabComplete(CommandSender s, Command c, String a, String[] args) {
        List<String> out = new ArrayList<>();
        if (args.length == 1) {
            out.addAll(List.of("give", "remove", "info", "reload", "help"));
        } else if (args.length == 2) {
            Bukkit.getOnlinePlayers().stream().map(Player::getName).forEach(out::add);
        } else if (args.length == 3 && args[0].equalsIgnoreCase("give")) {
            out.add("bottle");
            out.addAll(registry.ids());
            out.addAll(List.of("the_patriot", "the_patriot_v_one", "the_veteran"));
        } else if (args.length == 4 && args[0].equalsIgnoreCase("give")) {
            out.addAll(List.of("compoundv", "tempv", "vone", "antiv"));
        }
        String prefix = args[args.length - 1].toLowerCase(Locale.ROOT);
        out.removeIf(x -> !x.toLowerCase(Locale.ROOT).startsWith(prefix));
        return out;
    }
}
