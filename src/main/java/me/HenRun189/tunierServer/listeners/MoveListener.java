package me.HenRun189.tunierServer.listeners;

import me.HenRun189.tunierServer.game.GameManager;
import org.bukkit.event.*;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityTargetEvent;

public class MoveListener implements Listener {

    private final GameManager gameManager;

    public MoveListener(GameManager gameManager) {
        this.gameManager = gameManager;
    }

    @EventHandler
    public void onMove(PlayerMoveEvent e) {

        // ❄️ Freeze (Countdown etc.)
        if (gameManager.isFrozen(e.getPlayer())) {
            if (e.getFrom().distance(e.getTo()) > 0) {
                e.setTo(e.getFrom());
            }
            return;
        }

        // ⏸ Pause
        if (gameManager.isPaused()) {
            if (e.getFrom().distance(e.getTo()) > 0) {
                e.setTo(e.getFrom());
            }
        }
    }

    // ❌ Kein Damage während Pause
    @EventHandler
    public void onEntityDamage(EntityDamageEvent e) {
        if (gameManager.isPaused()) {
            e.setCancelled(true);
        }
    }

    // ❌ Mobs greifen nicht an während Pause
    @EventHandler
    public void onEntityTarget(EntityTargetEvent e) {
        if (gameManager.isPaused()) {
            e.setCancelled(true);
        }
    }
}

//GPT-12