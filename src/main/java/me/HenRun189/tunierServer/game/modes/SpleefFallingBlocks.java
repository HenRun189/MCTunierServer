

package me.HenRun189.tunierServer.game.modes;

import me.HenRun189.tunierServer.team.TeamData;
import me.HenRun189.tunierServer.team.TeamManager;
import me.HenRun189.tunierServer.score.ScoreManager;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;

import java.util.Collection;

import java.util.Map;
import java.util.UUID;
import java.util.ArrayList;



public class SpleefFallingBlocks extends AbstractGameMode implements Listener {

    private World world = Bukkit.getWorld("windchargeworld");

    private int fallTime = 10;
    private Material fallingBlockType = Material.SANDSTONE;  // noch bestimmen !!!!!!!!!!!!
    private Material fallActiveBlockType = Material.RED_SANDSTONE; // auch noch bestimmen !!!!!!!!!!
    private long disqualifyHight;
    private Location spawnLoc = new Location(world, 0, 128, -91); // auch noch setzten etc
    private double higthDiffernce = 8; //// 117, 112, 107...
    private int layerAmount = 6;
    private Location loc1 = new Location(world, 14, 118, -109);
    private  Location loc2 = new Location(world, -13, 118, -78);

    private TeamManager teamManager;
    private ScoreManager scoreManager;
    private Map<UUID, Player> data = new Map<UUID, Player>();
    private ArrayList<UUID> activePlayers = new ArrayList<UUID>();
    private ArrayList<FallingBlock> fallingBlocks = new ArrayList<FallingBlock>();


    public SpleefWindChargeMode(TeamManager arg_teamManager, ScoreManager arg_scoreManager) {
        super(300, arg_teamManager);
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
                disqualify(player.getUniqueID());
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

    @Override
    public void handleEvent(Event event) {
        // wird nicht benutzt
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
    public void onBlockBreak(org.bukkit.event.block.BlockBreakEvent e) {
        e.setCancelled(true); // niemand kann Blöcke abbauen
    }

    @org.bukkit.event.EventHandler
    public void onBlockPlace(org.bukkit.event.block.BlockPlaceEvent e) {
        e.setCancelled(true); // niemand kann Blöcke setzen
    }

    @org.bukkit.event.EventHandler
    public void onDamage(org.bukkit.event.entity.EntityDamageEvent e) {
        if (!(e.getEntity() instanceof Player p)) return;
        if (!activePlayers.contains(p.getUniqueId())) return;
        e.setCancelled(true); // kein Damage, Knockback vom Wind Charge bleibt
    }
}

