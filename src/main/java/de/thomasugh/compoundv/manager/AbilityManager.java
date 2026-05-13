package de.thomasugh.compoundv.manager;

import de.thomasugh.compoundv.CompoundVPlugin;
import de.thomasugh.compoundv.ability.Ability;
import de.thomasugh.compoundv.ability.AbilityRegistry;
import de.thomasugh.compoundv.ability.shared.ThePatriotAbility;
import de.thomasugh.compoundv.util.AbilityAliases;
import de.thomasugh.compoundv.data.CompoundPotion;
import de.thomasugh.compoundv.data.PlayerAbilityData;
import de.thomasugh.compoundv.locale.LocaleManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextColor;
import org.bukkit.Bukkit;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

public class AbilityManager {

    private final CompoundVPlugin    plugin;
    private final AbilityRegistry    registry;
    private final PersistenceManager persistence;

    private final Map<UUID, Ability>           activeAbility = new HashMap<>();
    private final Map<UUID, PlayerAbilityData> playerData    = new HashMap<>();
    private BukkitRunnable tickTask;

    public AbilityManager(CompoundVPlugin plugin, AbilityRegistry registry, PersistenceManager persistence) {
        this.plugin = plugin;
        this.registry = registry;
        this.persistence = persistence;
        startTicker();
    }

    public void giveAbility(Player player, String id, CompoundPotion type, long expiresAt) {
        String abilityId = AbilityAliases.normalize(id);
        Ability ability = registry.get(abilityId).orElse(null);
        if (ability == null) {
            plugin.getLogger().warning("Unknown ability id: " + id);
            return;
        }

        removeAbility(player);
        UUID uuid = player.getUniqueId();
        PlayerAbilityData data = new PlayerAbilityData(abilityId, type, expiresAt);
        activeAbility.put(uuid, ability);
        playerData.put(uuid, data);
        ability.apply(player);
        persistence.save(uuid, data);

        LocaleManager loc = plugin.getLocaleManager();
        player.sendMessage(loc.msg("ability.granted_header"));

        Map<String, String> ph = new HashMap<>();
        ph.put("name",   ability.getDisplayName());
        ph.put("color",  "#" + colorHex(ability.getColor()));
        ph.put("potion", type.getDisplayName());
        player.sendMessage(loc.msg("ability.granted_name", ph));

        List<Component> desc = loc.msgList(ability.getDescriptionKey());
        if (!desc.isEmpty()) {
            player.sendMessage(loc.msg("ability.controls_header"));
            for (Component c : desc) player.sendMessage(c);
        }

        if (data.isTemporary()) {
            long s = data.remainingSec();
            Map<String, String> dh = new HashMap<>();
            dh.put("minutes", Long.toString(s / 60));
            dh.put("seconds", Long.toString(s % 60));
            player.sendMessage(loc.msg("ability.granted_expires", dh));
        }
        player.sendMessage(loc.msg("ability.granted_footer"));

        player.getWorld().playSound(player.getLocation(),
                Sound.ENTITY_LIGHTNING_BOLT_THUNDER, 0.8f, 1.4f);
        player.getWorld().spawnParticle(Particle.TOTEM_OF_UNDYING,
                player.getLocation().add(0, 1, 0), 40, 0.5, 0.6, 0.5, 0.15);
    }

    public void removeAbility(Player player) {
        Ability a = activeAbility.remove(player.getUniqueId());
        if (a != null) a.remove(player);
        playerData.remove(player.getUniqueId());
    }

    public void removeAndForget(Player player) {
        removeAbility(player);
        persistence.delete(player.getUniqueId());
    }

    public void handleDeath(Player player) {
        if (!playerData.containsKey(player.getUniqueId())) return;
        Ability a = activeAbility.remove(player.getUniqueId());
        if (a != null) a.remove(player);
        playerData.remove(player.getUniqueId());
        persistence.delete(player.getUniqueId());
        player.sendMessage(plugin.getLocaleManager().msg("ability.lost_on_death"));
    }

    public void handleRespawn(Player player) {
        PlayerAbilityData data = playerData.get(player.getUniqueId());
        if (data == null || data.isExpired()) {
            playerData.remove(player.getUniqueId());
            persistence.delete(player.getUniqueId());
            return;
        }
        Ability ability = registry.get(data.abilityId()).orElse(null);
        if (ability == null) return;
        activeAbility.put(player.getUniqueId(), ability);
        ability.apply(player);
    }

    public void handleJoin(Player player) {
        persistence.load(player.getUniqueId()).ifPresent(data -> {
            if (data.isExpired()) { persistence.delete(player.getUniqueId()); return; }
            playerData.put(player.getUniqueId(), data);
            handleRespawn(player);
        });
    }

    public Ability           getAbility(Player p)   { return activeAbility.get(p.getUniqueId()); }
    public PlayerAbilityData getData(Player p)      { return playerData.get(p.getUniqueId()); }
    public boolean           hasAbility(Player p)   { return activeAbility.containsKey(p.getUniqueId()); }
    public boolean           isThePatriot(Player p) { return getAbility(p) instanceof ThePatriotAbility; }

    public int abilityCount() { return registry.ids().size(); }

    public List<String> abilityList() { return new ArrayList<>(registry.ids()); }

    private void startTicker() {
        tickTask = new BukkitRunnable() {
            @Override
            public void run() {
                List<UUID> expired = new ArrayList<>();
                for (Map.Entry<UUID, Ability> e : activeAbility.entrySet()) {
                    Player p = Bukkit.getPlayer(e.getKey());
                    if (p == null || !p.isOnline()) continue;
                    if (e.getValue().needsTick()) e.getValue().onTick(p);
                    PlayerAbilityData d = playerData.get(e.getKey());
                    if (d != null && d.isExpired()) expired.add(e.getKey());
                }
                for (UUID u : expired) {
                    Player p = Bukkit.getPlayer(u);
                    if (p != null) {
                        removeAndForget(p);
                        p.sendMessage(plugin.getLocaleManager().msg("ability.expired"));
                    }
                }
            }
        };
        tickTask.runTaskTimer(plugin, 1L, 1L);
    }

    public void cleanup() {
        if (tickTask != null) tickTask.cancel();
        Bukkit.getOnlinePlayers().forEach(p -> {
            Ability a = activeAbility.remove(p.getUniqueId());
            if (a != null) a.remove(p);
        });
        playerData.clear();
    }

    public static String colorHex(TextColor color) {
        if (color == null) return "FFFFFF";
        return String.format(Locale.ROOT, "%06X", color.value() & 0xFFFFFF);
    }
}
