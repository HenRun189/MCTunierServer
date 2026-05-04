

package me.HenRun189.tunierServer.game.modes;

import me.HenRun189.tunierServer.TunierServer;
import me.HenRun189.tunierServer.team.TeamData;
import me.HenRun189.tunierServer.team.TeamManager;
import me.HenRun189.tunierServer.score.ScoreManager;
import me.HenRun189.tunierServer.jumpandrun.PlayerData;

import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.util.Vector;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;

import java.util.*;


public class SpleefFallingBlocks extends AbstractGameMode implements Listener {

    private World world = Bukkit.getWorld("windchargeworld");

    private int fallTime = 30;
    private Material fallingBlockType = Material.SANDSTONE;
    private Material fallActiveBlockType = Material.RED_SANDSTONE;
    private long disqualifyHight;
    private Location spawnLoc = new Location(world, 0, 128, -91);
    private double higthDiffernce = 8; //// 117, 112, 107...
    private int layerAmount = 6;
    private Location loc1 = new Location(world, 16, 118, -110);
    private  Location loc2 = new Location(world, -14, 118, -79);

    private TeamManager teamManager;
    private ScoreManager scoreManager;
    private Map<UUID, Player> data = new HashMap<>();
    private ArrayList<UUID> activePlayers = new ArrayList<UUID>();
    private ArrayList<FallingBlock> fallingBlocks = new ArrayList<FallingBlock>();


    public SpleefFallingBlocks(TeamManager arg_teamManager, ScoreManager arg_scoreManager) {
        super(300, arg_teamManager);
        teamManager = arg_teamManager;
        scoreManager = arg_scoreManager;
    }


    public void preGame(Collection<Player> players) {

        for (TeamData team : teamManager.getTeams().values()) {
            for (UUID uuid : team.getPlayers()) {
                Player p = Bukkit.getPlayer(uuid);
                if (p == null) continue;
                data.put(p.getUniqueId(), p);
                activePlayers.add(p.getUniqueId());
                p.teleport(spawnLoc);          // ← Spawn
                p.setInvulnerable(true);       // ← kein Damage
                p.setHealth(20.0); // 10 Herzen
                p.setFoodLevel(20);
            }
        }

        for (int i = 0; i < layerAmount; i++) {

            Location lLoc1 = loc1.clone().subtract(0, higthDiffernce * i, 0);
            Location lLoc2 = loc2.clone().subtract(0, higthDiffernce * i, 0);
            fill(lLoc1, lLoc2, fallingBlockType);
        }
    }

    @Override
    protected void onGameStart() {
        for (UUID uuid : activePlayers) {
            Player p = data.get(uuid);
            if (p == null) continue;

            // Einfach 5 Blöcke runter von aktueller Position
            Location drop = p.getLocation().clone().subtract(0, 5, 0);
            p.teleport(drop);

            p.setVelocity(new Vector(0, 0, 0));
        }
    }


    @EventHandler
    public void onMove(PlayerMoveEvent e) {
        if (e.getTo() == null) return;
        if (!e.hasChangedBlock()) return;

        Player p = e.getPlayer();

        Location loc = e.getTo();
        Location bLoc = loc.clone().subtract(0, 0.6, 0);
        Block block = bLoc.getBlock();

        if (block.getType() == fallingBlockType) {
            FallingBlock newFB = new FallingBlock(bLoc, fallTime);
            fallingBlocks.add(newFB);
        }
    }

    protected void onGameTick() {

        for (int i = fallingBlocks.size() - 1; i >= 0; i--) {
            if (fallingBlocks.get(i).newTick()) {
                fallingBlocks.remove(i);
            }
        }

        for (Player player : data.values()) {
            if (player.getLocation().getY() < disqualifyHight && player.getGameMode() != GameMode.SPECTATOR) {
                disqualify(player.getUniqueId());
            }
        }

    }


    public void fill(Location loc1, Location loc2, Material m) {

        int smallX = (int) Math.floor(Math.min(loc1.getX(), loc2.getX()));
        int bigX = (int) Math.floor(Math.max(loc1.getX(), loc2.getX()));

        int smallY = (int) Math.floor(Math.min(loc1.getY(), loc2.getY()));
        int bigY = (int) Math.floor(Math.max(loc1.getY(), loc2.getY()));

        int smallZ = (int) Math.floor(Math.min(loc1.getZ(), loc2.getZ()));
        int bigZ = (int) Math.floor(Math.max(loc1.getZ(), loc2.getZ()));

        for (int x = smallX; x <= bigX; x++) {
            for (int y = smallY; y <= bigY; y++) {
                for (int z = smallZ; z <= bigZ; z++) {
                    world.getBlockAt(x,y,z).setType(m);
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

    //Muss noch gemacht werden (man sollte noch am Ende noch Punkte hinzufügen aber wie muss man halt noch schauen
    public void disqualify(UUID uuid) {
        Player p = Bukkit.getPlayer(uuid);
        if (p != null) {
            p.setGameMode(GameMode.SPECTATOR);
        }

    }


    @org.bukkit.event.EventHandler
    public void onDamage(org.bukkit.event.entity.EntityDamageEvent e) {
        if (!(e.getEntity() instanceof Player p)) return;
        if (!activePlayers.contains(p.getUniqueId())) return;
        e.setCancelled(true); // kein Damage, Knockback vom Wind Charge bleibt
    }

    @EventHandler
    public void onBlockBreak(BlockBreakEvent e) {
        if (!activePlayers.contains(e.getPlayer().getUniqueId())) return;
        if (!TunierServer.getInstance().getGameManager().isGameActive()) return;
        e.setCancelled(true);
    }

    @org.bukkit.event.EventHandler
    public void onBlockPlace(org.bukkit.event.block.BlockPlaceEvent e) {
        if (!activePlayers.contains(e.getPlayer().getUniqueId())) return;
        if (!TunierServer.getInstance().getGameManager().isGameActive()) return;
        e.setCancelled(true);
    }

    @Override
    public List<TeamData> getRanking() {
        return new ArrayList<>(teamManager.getTeams().values());
    }

    @Override
    public int getPoints(String team) {
        return 0; // oder deine Logik
    }

    @Override
    public void handleEvent(org.bukkit.event.Event event) {
        // wird nicht benutzt
    }

    @Override
    public void stop() {
        HandlerList.unregisterAll(this);

        activePlayers.clear();
        data.clear();
        fallingBlocks.clear();
    }

}

