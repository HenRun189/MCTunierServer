package me.HenRun189.tunierServer.listeners;

import org.bukkit.*;
import org.bukkit.entity.Player;
import org.bukkit.event.*;
import org.bukkit.event.block.*;
import org.bukkit.event.entity.*;
import org.bukkit.event.player.*;

public class LobbyListener implements Listener {

    private boolean isLobby(Player p) {
        return p.getWorld().getName().equalsIgnoreCase("lobby");
    }

    private boolean bypass(Player p) {
        return p.isOp(); // OP darf alles
    }

    @EventHandler
    public void onBlockBreak(BlockBreakEvent e) {
        if (isLobby(e.getPlayer()) && !bypass(e.getPlayer())) {
            e.setCancelled(true);
        }
    }

    @EventHandler
    public void onBlockPlace(BlockPlaceEvent e) {
        if (isLobby(e.getPlayer()) && !bypass(e.getPlayer())) {
            e.setCancelled(true);
        }
    }

    @EventHandler
    public void onDamage(EntityDamageEvent e) {
        if (e.getEntity() instanceof Player p) {
            if (isLobby(p)) {
                e.setCancelled(true);
            }
        }
    }

    @EventHandler
    public void onHunger(FoodLevelChangeEvent e) {
        if (e.getEntity() instanceof Player p) {
            if (isLobby(p)) {
                e.setCancelled(true);
                p.setFoodLevel(20);
            }
        }
    }

    @EventHandler
    public void onDrop(PlayerDropItemEvent e) {
        if (isLobby(e.getPlayer()) && !bypass(e.getPlayer())) {
            e.setCancelled(true);
        }
    }

    @EventHandler
    public void onPickup(EntityPickupItemEvent e) {
        if (e.getEntity() instanceof Player p) {
            if (isLobby(p) && !bypass(p)) {
                e.setCancelled(true);
            }
        }
    }
}