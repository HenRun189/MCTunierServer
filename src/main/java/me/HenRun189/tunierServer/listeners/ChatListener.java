package me.HenRun189.tunierServer.listeners;

import me.HenRun189.tunierServer.team.TeamManager;
import me.HenRun189.tunierServer.team.TeamData;

import org.bukkit.event.*;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.entity.Player;

public class ChatListener implements Listener {

    private final TeamManager teamManager;

    public ChatListener(TeamManager teamManager) {
        this.teamManager = teamManager;
    }

    @EventHandler
    public void onChat(AsyncPlayerChatEvent e) {

        Player p = e.getPlayer();
        TeamData team = teamManager.getTeamByPlayer(p.getUniqueId());

        if (team == null) return;

        e.setFormat(
                team.getColor() + "[" + team.getPrefix() + "] "
                        + team.getColor() + p.getName()
                        + " §7» §f" + e.getMessage()
        );
    }
}