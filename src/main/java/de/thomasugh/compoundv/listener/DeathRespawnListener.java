package de.thomasugh.compoundv.listener;
import de.thomasugh.compoundv.manager.AbilityManager;
import org.bukkit.entity.Player;
import org.bukkit.event.*;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.*;
public class DeathRespawnListener implements Listener {
    private final AbilityManager manager;
    public DeathRespawnListener(AbilityManager manager) { this.manager = manager; }
    @EventHandler(priority = EventPriority.HIGH)
    public void onDeath(PlayerDeathEvent e) { manager.handleDeath(e.getEntity()); }
    @EventHandler(priority = EventPriority.MONITOR)
    public void onRespawn(PlayerRespawnEvent e) {
        Player p = e.getPlayer();
        p.getServer().getScheduler().runTaskLater(p.getServer().getPluginManager().getPlugin("CompoundV"), () -> manager.handleRespawn(p), 5L);
    }
    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent e) {
        Player p = e.getPlayer();
        p.getServer().getScheduler().runTaskLater(p.getServer().getPluginManager().getPlugin("CompoundV"), () -> manager.handleJoin(p), 10L);
    }
}
