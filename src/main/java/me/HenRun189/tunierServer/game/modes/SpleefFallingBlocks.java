package me.HenRun189.tunierServer.game.modes;

import me.HenRun189.tunierServer.TunierServer;
import me.HenRun189.tunierServer.listeners.VisibilityToggleItem;
import me.HenRun189.tunierServer.team.TeamData;
import me.HenRun189.tunierServer.team.TeamManager;

import net.kyori.adventure.text.Component;

import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.Team;

import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.UUID;
import java.util.ArrayList;
import java.util.Random;


public class SpleefFallingBlocks extends AbstractGameMode implements Listener {

    private int fallTime = 10;
    private Material fallingBlockType;  // noch bestimmen !!!!!!!!!!!!
    private Material fallActiveBlockType; // auch noch bestimmen !!!!!!!!!!
    private long disqualifyHight;

    private TeamManager teamManager;
    private ScoreManager scoreManager;
    private map<UUID, Player> data = new map<UUID, Player>();
    private ArrayList<UUID> activePlayers = new ArrayList<UUID>();
    private ArrayList<FallingBlock> fallingBlocks = new ArrayList<FallingBlock>();


    public SpleefWindChargeMode(TeamManager arg_teamManager, ScoreManager arg_scoreManager) {
        super(200, arg_teamManager);
        teamManager = arg_teamManager;
        scoreManager = arg_scoreManager;
    }


    public void preGame(Collection<Player> players) {

        for (TeamData team : teamManager.getTeams().values()) {
            for (UUID uuid : team.getPlayers()) {
                Player p = Bukkit.getPlayer(uuid);
                if (p == null) continue;
                data.put(p.getUniqueId(), new PlayerData(p.getUniqueId()));
                activePlayers.add(p.getUniqueId());

            }
        }
    }

    public void start() {

    }


    @EventHandler
    public void onMove(PlayerMoveEvent e) {
        if (e.getTo() == null) return;
        if (!e.hasChangedBlock()) return;

        Player p = e.getPlayer();

        Location loc = e.getTo();
        Block block = loc.clone().subtract(0, 1, 0).getBlock();

        if (block.getType() == fallingBlockType) {
            FallingBlock newFB = new FallingBlock(loc, fallTime);
            fallingBlocks.add(newFB);
        }
    }

    protected void onGameTick() {

        for (int i = 0; i < fallingBlocks.length; i++) {
            if (fallingBlocks.get(i).newTick()) {
               fallingBlocks.remove(i);
            }
        }

        for (Player player : data.values) {
            if (p.getLocation().getY() < disqualifyHight && p.getGameMode() != GameMode.SPECTATOR) {
                disqualify(player);
            }
        }

    }

}

public class FallingBlock {
    private Location loc;
    private int tickTimer;

    public FallingBlock(Location arg_loc, int arg_tickTimer) {
        loc = arg_loc;
        tickTimer = arg_tickTimer;
        loc.getBlock().setType(fallActiveBlockType);
    }

    public boolean newTick() {
        tickTimer--;
        if (tickTimer < 0) {
            loc.getBlock().setType(Material.AIR);
            return true;
        }
        else {
            return false;
        }
    }
}

//Muss noch gemacht werden
public void disqualify(Player p) {

}