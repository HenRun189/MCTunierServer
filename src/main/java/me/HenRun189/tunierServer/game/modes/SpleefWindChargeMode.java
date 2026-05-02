package me.HenRun189.tunierServer.game.modes;

import me.HenRun189.tunierServer.score.ScoreManager;
import me.HenRun189.tunierServer.team.TeamData;
import me.HenRun189.tunierServer.team.TeamManager;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
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

import org.bukkit.inventory.ItemStack;
import org.bukkit.util.Vector;

public class SpleefWindChargeMode extends AbstractGameMode implements Listener {


    private long startTime = 0;

    private World world = Bukkit.getWorld("windchargeworld");


    private Location loc1 = new Location(world, -13.7, 117, -15.7); // Noch einen Wert hinzufügen !!!!!!!!!!!!!!!!!!
    private Location loc2 = new Location(world, 14.7, 117, 15.7); // Noch einen Wert hinzufügen !!!!!!!!!!!!!!!!!!
    private double higthDiffernce = 5; // Noch einen Wert hinzufügen !!!!!!!!!!!!!!!!!!
    private int layerAmount = 6; // Noch einen Wert hinzufügen !!!!!!!!!!!!!!!!!!
    private double startDegradeSpeed = 2;  // Pro Sekunde
    private double layerDepletionTime = 60 * 20;  // In ticks (oder halt *20 für Sekunden)
    private double depletionExp = 2;
    private double degradeTime = 60;
    private int extraWindchargeCooldown = 10;


    private Map<UUID, Player> data = new HashMap<>();
    private ArrayList<degradingTrapdoor> currDegradingTD = new ArrayList<>();
    private Map<UUID, Integer> windchargeCooldown = new HashMap<>();
    private ArrayList<UUID> activePlayers = new ArrayList<>();
    private Map<UUID, Integer> playerLayer = new HashMap<>();
    private double degradeCoefficient;
    private double prevDegrade = 0;
    private double totalTick = 0;
    private double trapDoorArea;
    private TeamManager teamManager;
    private ScoreManager scoreManager;
    private final Material[] trapDoorTypes = {Material.BAMBOO_TRAPDOOR, Material.ACACIA_TRAPDOOR, Material.MANGROVE_TRAPDOOR};

    private int currentLayer = 0;
    private ArrayList<degradingTrapdoor> trapdoors = new ArrayList<degradingTrapdoor>();




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
                data.put(p.getUniqueId(), p);
                activePlayers.add(p.getUniqueId());
                playerLayer.put(p.getUniqueId(), 0);

            }
        }

        trapDoorArea = Math.abs(loc1.getX() - loc2.getX()) * Math.abs(loc1.getZ() - loc2.getZ());

        degradeCoefficient = (depletionExp + 1) * (trapDoorArea / expn(layerDepletionTime, depletionExp + 1) - startDegradeSpeed / expn(layerDepletionTime, depletionExp));

        for (int i = 0; i < layerAmount; i++) {

            Location lLoc1 = loc1.subtract(0, higthDiffernce * i, 0);
            Location lLoc2 = loc2.subtract(0, higthDiffernce * i, 0);
            fill(lLoc1, lLoc2, Material.BAMBOO_TRAPDOOR);
        }
    }

    @Override
    protected void onGameStart() {

        for (UUID uuid : activePlayers) {

            Player p = data.get(uuid);
            p.setVelocity(new Vector(0, 0, 0));
            double rndX = randomBetween(loc1.getX(), loc2.getX());
            double rndZ = randomBetween(loc1.getZ(), loc2.getZ());
            Location loc = new Location(world, rndX, 117, rndZ);
            p.teleport(loc);

            windchargeCooldown.put(uuid, 0);
        }
    }

    @Override
    protected void onGameTick() {

        for (UUID uuid : activePlayers) {
            Player p = data.get(uuid);
            if (p.getGameMode() != GameMode.CREATIVE && p.getGameMode() != GameMode.SPECTATOR) {
                double yPos = p.getLocation().getY();
                int currPlayerLayer = (int) ((loc1.getY() - yPos) / higthDiffernce + 0.5);
                if (currPlayerLayer > (layerAmount + 1)) {
                    // noch eine Funktion (sowas wie disqualify)
                } else {
                    playerLayer.put(uuid, currPlayerLayer);
                }
            }
        }

        for (UUID uuid : activePlayers) {
            Player p = data.get(uuid);

            if (p == null) continue;

            if (p.getGameMode() == GameMode.CREATIVE || p.getGameMode() == GameMode.SPECTATOR) continue;

            if (!p.getInventory().contains(Material.WIND_CHARGE)) {
                Integer prev = windchargeCooldown.get(uuid);

                if (prev + 1 >= extraWindchargeCooldown) {
                    p.getInventory().addItem(new ItemStack(Material.WIND_CHARGE));
                    windchargeCooldown.put(uuid, 0);
                }
                else {
                    windchargeCooldown.put(uuid, prev + 1);
                }
            }
        }

        if (trapdoors.size() == 0) {
            Location olLoc1 = loc1.clone().subtract(0, higthDiffernce * currentLayer, 0);
            Location olLoc2 = loc2.clone().subtract(0, higthDiffernce * currentLayer, 0);
            trapdoors = fillTrapDoorsArr(trapdoors, olLoc1, olLoc2, world);
            currentLayer++;
        }

        totalTick += 1.0;

        for (Integer i = 0; i < currDegradingTD.size(); i++) {
            if (currDegradingTD.get(i).degrade()) {
                currDegradingTD.remove(i);
            }
        }

        double currDegrade = ((degradeCoefficient / (depletionExp + 1)) * expn(totalTick, (depletionExp + 1)) + startDegradeSpeed * totalTick);
        int newDegrade = ((int)currDegrade) - ((int)prevDegrade);
        prevDegrade = currDegrade;

        for (int i = 0; i < newDegrade; i++) {
            Random random = new Random();
            int indexTP = random.nextInt(trapdoors.size());

            currDegradingTD.add(trapdoors.get(indexTP));

            trapdoors.remove(indexTP);
        }
    }

    @Override
    public List<TeamData> getRanking() {
        return new ArrayList<>(teamManager.getTeams().values());
    }

    @Override
    public int getPoints(String team) {
        return 0; // oder deine Logik
    }


    public void disqualify(UUID uuid) {
        playerLayer.remove(uuid);
        Player p = Bukkit.getPlayer(uuid);
        if (p != null) {
            p.setGameMode(GameMode.SPECTATOR);
        }
    }

    public void fill(Location loc1, Location loc2, Material m) {

        int smallX = (int) Math.min(loc1.getX(), loc2.getX());
        int bigX = (int) Math.max(loc1.getX(), loc2.getX());

        int smallY = (int) Math.min(loc1.getY(), loc2.getY());
        int bigY = (int) Math.max(loc1.getY(), loc2.getY());

        int smallZ = (int) Math.min(loc1.getZ(), loc2.getZ());
        int bigZ = (int) Math.max(loc1.getZ(), loc2.getZ());

        for (int x = smallX; x <= bigX; x++) {
            for (int y = smallY; y <= bigY; y++) {
                for (int z = smallZ; z <= bigZ; z++) {
                    world.getBlockAt(x,y,z).setType(m);
                }
            }
        }

    }

    public ArrayList<degradingTrapdoor> fillTrapDoorsArr(ArrayList<degradingTrapdoor> arg_trapdoors, Location loc1, Location loc2, World world) {

        int smallX = (int) Math.min(loc1.getX(), loc2.getX());
        int bigX = (int) Math.max(loc1.getX(), loc2.getX());

        int smallZ = (int) Math.min(loc1.getZ(), loc2.getZ());
        int bigZ = (int) Math.max(loc1.getZ(), loc2.getZ());

        for (int x = smallX; x <= bigX; x++) {
            for (int z = smallZ; z <= bigZ; z++) {
                Location loc = new Location(world, x, loc1.getY(), z);
                degradingTrapdoor dTP = new degradingTrapdoor(loc, 0);
                arg_trapdoors.add(dTP);
            }
        }
        return arg_trapdoors;
    }

    private class degradingTrapdoor {
        private int ticks;
        private Location pos;

        public degradingTrapdoor(Location arg_pos, int arg_ticks) {
            ticks = arg_ticks;
            pos = arg_pos;
        }

        public boolean degrade() {
            Block trapDoorB = pos.getBlock();
            ticks++;
            double percentage = (double)ticks;
            percentage = percentage / degradeTime;
            percentage *= 3;
            int status = (int)percentage;

            if (status != trapDoorTypes.length) {

                TrapDoor trapDoorTD = (TrapDoor) trapDoorB.getBlockData();

                boolean open = trapDoorTD.isOpen();

                trapDoorB.setType(trapDoorTypes[status]);

                TrapDoor newData = (TrapDoor) trapDoorB.getBlockData();
                newData.setOpen(open);
                trapDoorB.setBlockData(newData);

                return false;
            }
            else {
                trapDoorB.setType(Material.AIR);
                return true;
            }
        }

    }

    public double expn(double base, double exponent) {
        return Math.exp(exponent * Math.log(base));
    }

    public double randomBetween(double val0, double val1) {
        Random random = new Random();
        double smallVal = val0 > val1 ? val1 : val0;
        double bigVal = val0 > val1 ? val0 : val1;
        return smallVal + (bigVal - smallVal) * random.nextDouble();
    }

    @Override
    public void handleEvent(org.bukkit.event.Event event) {
        // erstmal leer lassen
    }


}
