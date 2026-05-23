package de.thomasugh.compoundv.listener;

import de.thomasugh.compoundv.CompoundV;
import de.thomasugh.compoundv.manager.AbilityManager;
import de.thomasugh.compoundv.server.SchedulerAdapter;
import org.bukkit.entity.Player;
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
        plugin.getSideEffectManager().resetTempVLifeCycle(player);
        manager.handleDeath(player);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onRespawn(PlayerRespawnEvent event) {
        Player player = event.getPlayer();
        SchedulerAdapter.runLater(plugin, () -> manager.handleRespawn(player), 5L);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        SchedulerAdapter.runLater(plugin, () -> manager.handleJoin(player), 10L);
    }
}
