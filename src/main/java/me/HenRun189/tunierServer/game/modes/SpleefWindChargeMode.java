package me.HenRun189.tunierServer.game.modes;

import me.HenRun189.tunierServer.score.ScoreManager;
import me.HenRun189.tunierServer.team.TeamData;
import me.HenRun189.tunierServer.team.TeamManager;
import me.HenRun189.tunierServer.TunierServer;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.ArrayList;
import java.util.Random;
import java.util.List;


import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.data.type.TrapDoor;
import org.bukkit.event.HandlerList;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.Vector;

import net.kyori.adventure.text.Component;

public class SpleefWindChargeMode extends AbstractGameMode implements Listener {


    private long startTime = 0;

    private World world = Bukkit.getWorld("windchargeworld");


    private Location spawnLoc = new Location(world, -1, 128, 1);
    private Location loc1 = new Location(world, -14, 117, -16);
    private Location loc2 = new Location(world, 14, 117, 15);
    private double higthDiffernce = 8;
    private int layerAmount = 6;
    private double currentDepletionExp = 0.8;
    private double startDegradeSpeed = 1;
    private double layerDepletionTime = 55 * 20;
    private double depletionExp = 3 * 20;
    private long degradeTime = 2 * 20;
    private long deleteTime = 1 * 20;
    private int extraWindchargeCooldown = 2 * 20;


    private Map<UUID, Player> data = new HashMap<>();
    private Map<UUID, Integer> windchargeCooldown = new HashMap<>();
    private ArrayList<UUID> activePlayers = new ArrayList<>();
    private Map<UUID, Integer> playerLayer = new HashMap<>();
    private double degradeCoefficient;
    private double prevDegrade = 0;
    private double trapDoorArea;
    private TeamManager teamManager;
    private ScoreManager scoreManager;
    private final Material[] trapDoorTypes = {Material.BAMBOO_TRAPDOOR, Material.ACACIA_TRAPDOOR, Material.MANGROVE_TRAPDOOR};

    private int currentLayer = 0;
    private ArrayList<TrapdoorLayer> TDLayers = new ArrayList<>();
    private int currDepletingL = 0;

    private final Random random = new Random();

    private ArrayList<degradingTrapdoor> nextLayerTrapdoors = new ArrayList<>();
    private boolean nextLayerStarted = false;

    // Punktesystem
    private static final int[] PLACEMENT_POINTS = {30, 20, 15, 10, 6, 3, 1};
    private int playersAtStart = 0;
    private int eliminationsCount = 0;
    private List<UUID> eliminationOrder = new ArrayList<>();
    private Map<UUID, Integer> pointsThisGame = new HashMap<>();



    public SpleefWindChargeMode(TeamManager arg_teamManager, ScoreManager arg_scoreManager) {
        super(-1, arg_teamManager);
        teamManager = arg_teamManager;
        scoreManager = arg_scoreManager;
    }

    public void preGame(Collection<Player> players) {
        currDepletingL = 0;
        for (TeamData team : teamManager.getTeams().values()) {
            for (UUID uuid : team.getPlayers()) {
                Player p = Bukkit.getPlayer(uuid);
                if (p == null) continue;
                data.put(p.getUniqueId(), p);
                activePlayers.add(p.getUniqueId());
                playerLayer.put(p.getUniqueId(), 0);
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


        trapDoorArea = (Math.abs(loc1.getX() - loc2.getX()) + 1) * (Math.abs(loc1.getZ() - loc2.getZ()) + 1);

        degradeCoefficient = (currentDepletionExp + 1) * (trapDoorArea / expn(layerDepletionTime, currentDepletionExp + 1) - startDegradeSpeed / expn(layerDepletionTime, currentDepletionExp));

        for (int i = 0; i < layerAmount; i++) {
            Location lLoc1 = loc1.clone().subtract(0, higthDiffernce * i, 0);
            Location lLoc2 = loc2.clone().subtract(0, higthDiffernce * i, 0);
            fill(lLoc1, lLoc2, Material.BAMBOO_TRAPDOOR);
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
            windchargeCooldown.put(uuid, 0);
        }

        TDLayers.add(new TrapdoorLayer(fillTrapDoorsArr(new ArrayList<>(), loc1.clone(), loc2.clone(), world), new ArrayList<>()));
        currentLayer++;
    }


    @Override
    protected void onGameTick() {

        List<UUID> toDisqualify = new ArrayList<>();

        for (UUID uuid : activePlayers) {
            Player p = data.get(uuid);
            if (p == null) continue;

            double yPos = p.getLocation().getY();
            int currPlayerLayer = (int) ((loc1.getY() - yPos) / higthDiffernce + 0.5);
            playerLayer.put(uuid, currPlayerLayer);

            if (p.getGameMode() != GameMode.CREATIVE && p.getGameMode() != GameMode.SPECTATOR) {
                if (yPos <= 71) {
                    toDisqualify.add(uuid);
                }
            }
        }

        for (UUID uuid : toDisqualify) {
            disqualify(uuid);
        }

        if (activePlayers.size() <= 1) {
            if (activePlayers.size() == 1) {
                UUID winner = activePlayers.get(0);
                eliminationOrder.add(winner);
                awardPoints(winner, 0);
            }
            TunierServer.getInstance().getGameManager().stopGame();
            return;
        }

        // Windcharge Cooldown
        for (UUID uuid : activePlayers) {
            Player p = data.get(uuid);
            if (p == null) continue;
            if (p.getGameMode() == GameMode.CREATIVE || p.getGameMode() == GameMode.SPECTATOR) continue;

            int prev = windchargeCooldown.getOrDefault(uuid, 0) + 1;
            boolean hasCharge = p.getInventory().contains(Material.WIND_CHARGE);

            if (!hasCharge) {
                float progress = (float) prev / extraWindchargeCooldown;
                p.setExp(Math.min(progress, 1.0f));
                p.setLevel(Math.max(0, extraWindchargeCooldown - prev));
            } else {
                p.setExp(0f);
                p.setLevel(0);
            }

            if (prev >= extraWindchargeCooldown) {
                if (!hasCharge) {
                    p.getInventory().addItem(new ItemStack(Material.WIND_CHARGE));
                }
                prev = 0;
            }

            windchargeCooldown.put(uuid, prev);
        }

        if (TDLayers.isEmpty()) return;
        if (currDepletingL < 0 || currDepletingL >= TDLayers.size()) return;

        if (TDLayers.get(currDepletingL).leftTD() < (0.4 * trapDoorArea)) {
            if ((currentLayer - 1) < layerAmount) {
                Location olLoc1 = loc1.clone().subtract(0, higthDiffernce * currentLayer, 0);
                Location olLoc2 = loc2.clone().subtract(0, higthDiffernce * currentLayer, 0);
                TDLayers.add(new TrapdoorLayer(
                        fillTrapDoorsArr(new ArrayList<>(), olLoc1, olLoc2, world),
                        new ArrayList<>()
                ));
                currentLayer++;
                currDepletingL++;
            }
        }

        for (int i = TDLayers.size() - 1; i >= 0; i--) {
            TrapdoorLayer tdl = TDLayers.get(i);
            if (tdl.leftTD() == 0) {
                TDLayers.remove(i);
                if (currDepletingL > i) {
                    currDepletingL--;
                }
            } else {
                tdl.degrade();
            }
        }

        if (!TDLayers.isEmpty() && currDepletingL >= TDLayers.size()) {
            currDepletingL = TDLayers.size() - 1;
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
        // Layer-Info wird direkt in onGameTick() gesetzt
    }

    @Override
    public List<TeamData> getRanking() {
        return new ArrayList<>(teamManager.getTeams().values());
    }

    @Override
    public int getPoints(String team) {
        return 0;
    }


    public void disqualify(UUID uuid) {
        int placeFromBottom = eliminationsCount;
        int placeFromTop = playersAtStart - 1 - placeFromBottom;

        eliminationOrder.add(uuid);
        awardPoints(uuid, placeFromTop);
        eliminationsCount++;

        playerLayer.remove(uuid);
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
        Bukkit.broadcast(Component.text("§b§lSpleef Windcharge §7- §6§lRanking"));
        Bukkit.broadcast(Component.text(" "));

        // eliminationOrder: index 0 = erster raus = letzter Platz
        // letzter Index = Sieger
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

    public ArrayList<degradingTrapdoor> fillTrapDoorsArr(ArrayList<degradingTrapdoor> arg_trapdoors, Location loc1, Location loc2, World world) {
        int smallX = (int) Math.floor(Math.min(loc1.getX(), loc2.getX()));
        int bigX = (int) Math.floor(Math.max(loc1.getX(), loc2.getX()));
        int smallZ = (int) Math.floor(Math.min(loc1.getZ(), loc2.getZ()));
        int bigZ = (int) Math.floor(Math.max(loc1.getZ(), loc2.getZ()));

        for (int x = smallX; x <= bigX; x++) {
            for (int z = smallZ; z <= bigZ; z++) {
                Location loc = new Location(world, x, loc1.getY(), z);
                degradingTrapdoor dTP = new degradingTrapdoor(loc, 0);
                arg_trapdoors.add(dTP);
            }
        }
        return arg_trapdoors;
    }



    private class TrapdoorLayer {
        private ArrayList<degradingTrapdoor> trapdoors = new ArrayList<degradingTrapdoor>();
        private ArrayList<degradingTrapdoor> currDegradingTD = new ArrayList<>();
        private double currTick;
        private double prevDegrade;

        TrapdoorLayer(ArrayList<degradingTrapdoor> arg_trapdoors, ArrayList<degradingTrapdoor> arg_currDegradingTD) {
            trapdoors = arg_trapdoors;
            currDegradingTD = arg_currDegradingTD;
            currTick += 1.0;
            prevDegrade = 0;
        }

        public void degrade() {
            currTick++;

            for (int i = currDegradingTD.size() - 1; i >= 0; i--) {
                if (currDegradingTD.get(i).degrade()) {
                    currDegradingTD.remove(i);
                }
            }

            double currDegrade = ((degradeCoefficient / (currentDepletionExp + 1)) * expn(currTick, (currentDepletionExp + 1)) + startDegradeSpeed * currTick);
            int newDegrade = ((int) currDegrade) - ((int) prevDegrade);
            prevDegrade = currDegrade;

            for (int i = 0; i < newDegrade; i++) {
                if (trapdoors.size() > 0) {
                    int indexTP = random.nextInt(trapdoors.size());
                    currDegradingTD.add(trapdoors.get(indexTP));
                    trapdoors.remove(indexTP);
                }
            }
        }

        public int leftTD() {
            return (trapdoors.size() + currDegradingTD.size());
        }
    }



    private class degradingTrapdoor {
        private int ticks;
        private int status;

        private Location pos;

        public degradingTrapdoor(Location arg_pos, int arg_ticks) {
            ticks = arg_ticks;
            pos = arg_pos;
        }

        public boolean degrade() {
            ticks++;

            long currentLimit = (status >= trapDoorTypes.length - 1) ? deleteTime : degradeTime;

            if (ticks >= currentLimit) {
                status++;
                ticks = 0;

                if (status >= trapDoorTypes.length) {
                    Block trapDoorB = pos.getBlock();
                    trapDoorB.setType(Material.AIR);
                    return true;
                }

                replace();
                return false;
            }

            return false;
        }

        public void replace() {
            Block trapDoorB = pos.getBlock();
            TrapDoor trapDoorTD = (TrapDoor) trapDoorB.getBlockData();
            boolean open = trapDoorTD.isOpen();
            trapDoorB.setType(trapDoorTypes[status]);
            TrapDoor newData = (TrapDoor) trapDoorB.getBlockData();
            newData.setOpen(open);
            trapDoorB.setBlockData(newData);
        }

    }

    public double expn(double base, double exponent) {
        if (base <= 0) return 0;
        return Math.exp(exponent * Math.log(base));
    }

    public double randomBetween(double val0, double val1) {
        double smallVal = val0 > val1 ? val1 : val0;
        double bigVal = val0 > val1 ? val0 : val1;
        return smallVal + (bigVal - smallVal) * random.nextDouble();
    }

    @Override
    public void handleEvent(org.bukkit.event.Event event) {
        // wird nicht benutzt
    }

    @org.bukkit.event.EventHandler
    public void onDamage(org.bukkit.event.entity.EntityDamageEvent e) {
        if (!(e.getEntity() instanceof Player p)) return;
        if (!activePlayers.contains(p.getUniqueId())) return;
        e.setCancelled(true);
    }

    @EventHandler
    public void onBlockBreak(BlockBreakEvent e) {
        if (!activePlayers.contains(e.getPlayer().getUniqueId())) return;
        e.setCancelled(true);
    }

    @org.bukkit.event.EventHandler
    public void onBlockPlace(org.bukkit.event.block.BlockPlaceEvent e) {
        if (!activePlayers.contains(e.getPlayer().getUniqueId())) return;
        e.setCancelled(true);
    }

    @Override
    public void stop() {
        // Ranking VOR super.stop() broadcasten
        broadcastRanking();

        super.stop();
        HandlerList.unregisterAll(this);

        activePlayers.clear();
        data.clear();
        playerLayer.clear();
        windchargeCooldown.clear();
        nextLayerTrapdoors.clear();
        TDLayers.clear();
    }
}