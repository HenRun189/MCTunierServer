package me.HenRun189.tunierServer.listeners;

import org.bukkit.event.*;
import org.bukkit.event.inventory.InventoryClickEvent;

public class GUIListener implements Listener {

    @EventHandler
    public void onClick(InventoryClickEvent e) {
        if (e.getView().getTitle().contains("Turnier Stats")) {
            e.setCancelled(true);
        }
    }
}