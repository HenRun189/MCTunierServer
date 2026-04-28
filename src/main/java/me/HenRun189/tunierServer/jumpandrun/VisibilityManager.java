package me.HenRun189.tunierServer.jumpandrun;

import me.HenRun189.tunierServer.team.TeamManager;
import me.HenRun189.tunierServer.team.TeamData;
import me.HenRun189.tunierServer.TunierServer;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

public class VisibilityManager {

    private final TeamManager teamManager;

    public VisibilityManager(TeamManager teamManager) {
        this.teamManager = teamManager;
    }

    public enum Mode {
        ALL, TEAM, NONE
    }

    public void apply(Player player, Mode mode) {

        TeamData playerTeam = teamManager.getTeamByPlayer(player.getUniqueId());

        for (Player other : Bukkit.getOnlinePlayers()) {

            if (other.equals(player)) continue;

            switch (mode) {
                case ALL  -> player.showPlayer(TunierServer.getInstance(), other);
                case NONE -> player.hidePlayer(TunierServer.getInstance(), other);
                case TEAM -> {
                    TeamData otherTeam = teamManager.getTeamByPlayer(other.getUniqueId());
                    if (playerTeam != null && playerTeam.equals(otherTeam)) {
                        player.showPlayer(TunierServer.getInstance(), other);
                    } else {
                        player.hidePlayer(TunierServer.getInstance(), other);
                    }
                }
            }
        }
    }
}
