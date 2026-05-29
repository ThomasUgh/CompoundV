package de.thomasugh.compoundv.listener;

import de.thomasugh.compoundv.CompoundV;
import de.thomasugh.compoundv.manager.AbilityManager;
import de.thomasugh.compoundv.server.SchedulerAdapter;
import de.thomasugh.compoundv.util.AbilityKillTracker;
import org.bukkit.entity.Player;

import java.util.LinkedHashMap;
import java.util.Map;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerRespawnEvent;

public final class DeathRespawnListener implements Listener {

    private final CompoundV plugin;
    private final AbilityManager manager;

    public DeathRespawnListener(CompoundV plugin, AbilityManager manager) {
        this.plugin = plugin;
        this.manager = manager;
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onDeath(PlayerDeathEvent event) {
        Player player = event.getEntity();
        applyAbilityDeathMessage(event, player);
        plugin.getSideEffectManager().resetTempVLifeCycle(player);
        manager.handleDeath(player);
    }

    private void applyAbilityDeathMessage(PlayerDeathEvent event, Player victim) {
        if (!plugin.getConfig().getBoolean("combat.custom_kill_messages.enabled", true)) return;
        AbilityKillTracker.KillMarker marker = AbilityKillTracker.consume(victim);
        if (marker == null) return;

        Map<String, String> values = new LinkedHashMap<>();
        values.put("victim", victim.getName());
        values.put("killer", marker.killerName());
        String message = plugin.getLocaleManager().msg(marker.messageKey(), values);
        if (message == null || message.isBlank() || message.equals(marker.messageKey())) return;
        event.setDeathMessage(message);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onRespawn(PlayerRespawnEvent event) {
        Player player = event.getPlayer();
        SchedulerAdapter.runLater(plugin, player, () -> manager.handleRespawn(player), 5L);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        SchedulerAdapter.runLater(plugin, player, () -> manager.handleJoin(player), 10L);
    }
}
