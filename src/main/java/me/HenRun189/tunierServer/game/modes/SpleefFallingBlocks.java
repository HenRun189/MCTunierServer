package me.HenRun189.tunierServer.game.modes;

import me.HenRun189.tunierServer.TunierServer;
import me.HenRun189.tunierServer.team.TeamData;
import me.HenRun189.tunierServer.team.TeamManager;
import me.HenRun189.tunierServer.score.ScoreManager;

import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.util.Vector;
import org.bukkit.event.block.BlockBreakEvent;

import net.kyori.adventure.text.Component;

import java.util.*;


public class SpleefFallingBlocks extends AbstractGameMode implements Listener {

    private World world = Bukkit.getWorld("windchargeworld");

    private int fallTime = 30;
    private Material fallingBlockType = Material.SANDSTONE;
    private Material fallActiveBlockType = Material.RED_SANDSTONE;
    private Location spawnLoc = new Location(world, 0, 128, -91);
    private double higthDiffernce = 5;
    private int layerAmount = 6;
    private Location loc1 = new Location(world, 16, 118, -110);
    private Location loc2 = new Location(world, -14, 118, -79);
    private int cheatDetectTimer = 60;

    private TeamManager teamManager;
    private ScoreManager scoreManager;
    private Map<UUID, Player> data = new HashMap<>();
    private ArrayList<UUID> activePlayers = new ArrayList<UUID>();
    private ArrayList<FallingBlock> fallingBlocks = new ArrayList<FallingBlock>();
    private Map<UUID, Integer> fallTimer = new HashMap<>();
    private Map<UUID, Integer> playerLayer = new HashMap<>();

    // Punktesystem
    private static final int[] PLACEMENT_POINTS = {30, 20, 15, 10, 6, 3, 1};
    private int playersAtStart = 0;
    private int eliminationsCount = 0;
    private List<UUID> eliminationOrder = new ArrayList<>();
    private Map<UUID, Integer> pointsThisGame = new HashMap<>();


    public SpleefFallingBlocks(TeamManager arg_teamManager, ScoreManager arg_scoreManager) {
        super(-1, arg_teamManager); // kein Zeitlimit
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
                playerLayer.put(p.getUniqueId(), 0);
                fallTimer.put(p.getUniqueId(), 0);
                p.teleport(spawnLoc);
                p.setInvulnerable(true);
                p.setHealth(20.0);
                p.setFoodLevel(20);
            }
        }

        // Chunks vorladen
        int chunkMinX = (int) Math.floor(Math.min(loc1.getX(), loc2.getX())) >> 4;
        int chunkMaxX = (int) Math.floor(Math.max(loc1.getX(), loc2.getX())) >> 4;
        int chunkMinZ = (int) Math.floor(Math.min(loc1.getZ(), loc2.getZ())) >> 4;
        int chunkMaxZ = (int) Math.floor(Math.max(loc1.getZ(), loc2.getZ())) >> 4;

        for (int cx = chunkMinX; cx <= chunkMaxX; cx++) {
            for (int cz = chunkMinZ; cz <= chunkMaxZ; cz++) {
                world.getChunkAt(cx, cz).load(true);
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
        playersAtStart = activePlayers.size();
        eliminationsCount = 0;
        eliminationOrder.clear();
        pointsThisGame.clear();

        for (UUID uuid : activePlayers) {
            Player p = data.get(uuid);
            if (p == null) continue;

            Location drop = p.getLocation().clone().subtract(0, 5, 0);
            p.teleport(drop);
            p.setVelocity(new Vector(0, 0, 0));
        }
    }


    @EventHandler
    public void onMove(PlayerMoveEvent e) {
        if (e.getTo() == null) return;
        if (!e.hasChangedBlock()) return;
        if (!activePlayers.contains(e.getPlayer().getUniqueId())) return;

        Location loc = e.getTo();
        Location bLoc = loc.clone().subtract(0, 0.6, 0);
        Block block = bLoc.getBlock();

        if (block.getType() == fallingBlockType) {
            FallingBlock newFB = new FallingBlock(bLoc, fallTime);
            fallingBlocks.add(newFB);
        }
    }

    @Override
    protected void onGameTick() {

        // Disqualify-Höhe = unter dem letzten Layer
        double disqualifyHight = loc1.getY() - higthDiffernce * layerAmount - 2;

        // Falling Blocks Tick
        for (int i = fallingBlocks.size() - 1; i >= 0; i--) {
            if (fallingBlocks.get(i).newTick()) {
                fallingBlocks.remove(i);
            }
        }

        List<UUID> toDisqualify = new ArrayList<>();

        // Layer-Check + Cheat-Detection
        for (UUID uuid : activePlayers) {
            Player player = data.get(uuid);
            if (player == null) continue;

            double yPos = player.getLocation().getY();
            int currPlayerLayer = (int) ((loc1.getY() - yPos) / higthDiffernce + 0.5);
            playerLayer.put(uuid, currPlayerLayer);

            if (player.getGameMode() == GameMode.SPECTATOR) continue;
            if (player.getGameMode() == GameMode.CREATIVE) continue;

            // Cheat-Detection
            Location bLoc = player.getLocation().clone().subtract(0, 0.6, 0);
            Block block = bLoc.getBlock();

            if (block.getType() == Material.AIR) {
                int prevTime = fallTimer.getOrDefault(uuid, 0);
                fallTimer.put(uuid, prevTime + 1);

                if (fallTimer.get(uuid) > cheatDetectTimer) {
                    for (int x = -1; x <= 1; x++)
                        for (int z = -1; z <= 1; z++) {
                            Location nbLoc = player.getLocation().clone().subtract(x, 0.6, z);
                            FallingBlock newFB = new FallingBlock(nbLoc, fallTime);
                            fallingBlocks.add(newFB);
                        }
                }
            } else {
                fallTimer.put(uuid, 0);
            }

            if (yPos < disqualifyHight) {
                toDisqualify.add(uuid);
            }
        }

        for (UUID uuid : toDisqualify) {
            disqualify(uuid);
        }

        if (activePlayers.size() <= 1) {
            // Letzter Überlebender bekommt 1. Platz Punkte
            if (activePlayers.size() == 1) {
                UUID winner = activePlayers.get(0);
                eliminationOrder.add(winner);
                awardPoints(winner, 0);
            }
            TunierServer.getInstance().getGameManager().stopGame();
            return;
        }

        // Action Bar: Layer-Info
        for (UUID uuid : activePlayers) {
            Player p = data.get(uuid);
            if (p == null) continue;
            if (p.getGameMode() == GameMode.CREATIVE || p.getGameMode() == GameMode.SPECTATOR) continue;

            int myLayer = playerLayer.getOrDefault(uuid, 0);

            int aboveMe = 0;
            int belowMe = 0;

            for (UUID other : activePlayers) {
                if (other.equals(uuid)) continue;
                int otherLayer = playerLayer.getOrDefault(other, 0);
                if (otherLayer < myLayer) aboveMe++;
                else if (otherLayer > myLayer) belowMe++;
            }

            p.sendActionBar(Component.text(
                    "§eLayer §f" + (myLayer + 1) + "§7/§f" + layerAmount
                            + " §7| §a↑" + aboveMe + " Spieler"
                            + " §7| §c↓" + belowMe + " Spieler"
            ));
        }
    }

    @Override
    protected void updateActionbar() {
        // Layer-Info wird direkt in onGameTick() per sendActionBar gesetzt
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
            } else {
                return false;
            }
        }
    }


    public void disqualify(UUID uuid) {
        // Punkte vergeben basierend auf Reihenfolge
        int placeFromBottom = eliminationsCount;
        int placeFromTop = playersAtStart - 1 - placeFromBottom;

        eliminationOrder.add(uuid);
        awardPoints(uuid, placeFromTop);
        eliminationsCount++;

        playerLayer.remove(uuid);
        fallTimer.remove(uuid);
        activePlayers.remove(uuid);
        Player p = Bukkit.getPlayer(uuid);
        if (p != null) {
            p.setGameMode(GameMode.SPECTATOR);
        }
    }

    private void awardPoints(UUID uuid, int placeFromTop) {
        TeamData team = teamManager.getTeamByPlayer(uuid);
        if (team == null) return;

        int points;
        if (placeFromTop < PLACEMENT_POINTS.length) {
            points = PLACEMENT_POINTS[placeFromTop];
        } else {
            points = 0;
        }

        pointsThisGame.put(uuid, points);

        if (points > 0) {
            scoreManager.addPoints(team.getName(), points);
            Player p = Bukkit.getPlayer(uuid);
            if (p != null) {
                p.sendMessage(Component.text("§6+ " + points + " Punkte §7(Platz " + (placeFromTop + 1) + ")"));
            }
        }
    }


    private void broadcastRanking() {
        Bukkit.broadcast(Component.text(" "));
        Bukkit.broadcast(Component.text("§8§m-----------------------------"));
        Bukkit.broadcast(Component.text("§b§lSpleef Falling Blocks §7- §6§lRanking"));
        Bukkit.broadcast(Component.text(" "));

        int total = eliminationOrder.size();
        for (int i = total - 1; i >= 0; i--) {
            UUID uuid = eliminationOrder.get(i);
            int place = total - i;
            int points = pointsThisGame.getOrDefault(uuid, 0);

            String playerName;
            Player p = Bukkit.getPlayer(uuid);
            if (p != null) {
                playerName = p.getName();
            } else {
                playerName = Bukkit.getOfflinePlayer(uuid).getName();
                if (playerName == null) playerName = "Unbekannt";
            }

            TeamData team = teamManager.getTeamByPlayer(uuid);
            String teamName = team != null ? team.getName() : "-";

            String medal;
            switch (place) {
                case 1 -> medal = "§6§l#1";
                case 2 -> medal = "§7§l#2";
                case 3 -> medal = "§c§l#3";
                default -> medal = "§8#" + place;
            }

            Bukkit.broadcast(Component.text(
                    medal + " §f" + playerName + " §7(" + teamName + ") §8» §a+" + points + " Punkte"
            ));
        }

        Bukkit.broadcast(Component.text("§8§m-----------------------------"));
        Bukkit.broadcast(Component.text(" "));
    }


    @EventHandler
    public void onDamage(org.bukkit.event.entity.EntityDamageEvent e) {
        if (!(e.getEntity() instanceof Player p)) return;
        if (!activePlayers.contains(p.getUniqueId())) return;
        e.setCancelled(true);
    }

    @EventHandler
    public void onBlockBreak(BlockBreakEvent e) {
        if (!activePlayers.contains(e.getPlayer().getUniqueId())) return;
        if (!TunierServer.getInstance().getGameManager().isGameActive()) return;
        e.setCancelled(true);
    }

    @EventHandler
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
        return 0;
    }

    @Override
    public void handleEvent(org.bukkit.event.Event event) {
        // wird nicht benutzt
    }

    @Override
    public void stop() {
        broadcastRanking();

        super.stop();
        HandlerList.unregisterAll(this);

        activePlayers.clear();
        data.clear();
        fallingBlocks.clear();
        playerLayer.clear();
        fallTimer.clear();

        // Direkt weiter zu SpleefWindCharge
        Bukkit.getScheduler().runTaskLater(TunierServer.getInstance(), () -> {
            TunierServer.getInstance().getGameManager().startGameFlow("spleefwindcharge");
        }, 1L);
    }
}