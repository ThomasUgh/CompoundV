package de.thomasugh.compoundv.gui;

import de.thomasugh.compoundv.CompoundV;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;

public final class AbilityShowcaseListener implements Listener {

    private final AbilityShowcaseGui gui;

    public AbilityShowcaseListener(CompoundV plugin) {
        this.gui = new AbilityShowcaseGui(plugin);
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onClick(InventoryClickEvent event) {
        if (!(event.getInventory().getHolder() instanceof AbilityShowcaseGui.ShowcaseHolder holder)) {
            return;
        }

        event.setCancelled(true);

        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }

        if (event.getClickedInventory() == null
                || !event.getClickedInventory().equals(event.getView().getTopInventory())) {
            return;
        }

        int slot = event.getSlot();
        if (slot == AbilityShowcaseGui.SLOT_PREV) {
            gui.open(player, holder.page() - 1);
        } else if (slot == AbilityShowcaseGui.SLOT_NEXT) {
            gui.open(player, holder.page() + 1);
        } else if (slot == AbilityShowcaseGui.SLOT_INFO) {
            player.closeInventory();
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onDrag(InventoryDragEvent event) {
        if (event.getInventory().getHolder() instanceof AbilityShowcaseGui.ShowcaseHolder) {
            event.setCancelled(true);
        }
    }
}
