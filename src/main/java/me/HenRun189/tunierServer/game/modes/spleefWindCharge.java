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


public class SpleefWindChargeMode extends AbstractGameMode implements Listener {

    private long startTime = 0;

    private Location loc1 = new Location(windchargeworld, -13.7, 117, -15.7); // Noch einen Wert hinzufügen !!!!!!!!!!!!!!!!!!
    private Location loc2 = new Location(windchargeworld, 14.7, 117, 15.7); // Noch einen Wert hinzufügen !!!!!!!!!!!!!!!!!!
    private long higthDiffernce = 5; // Noch einen Wert hinzufügen !!!!!!!!!!!!!!!!!!
    private int layerAmount = 6; // Noch einen Wert hinzufügen !!!!!!!!!!!!!!!!!!
    private double startDegradeSpeed = 2;  // Pro Sekunde
    private double layerDepletionTime = 60 * 20;  // In ticks (oder halt *20 für Sekunden)
    private double depletionExp = 1;
    private World world = Bukkit.getWorld(worldName);


    private map<UUID, Player> data = new map<UUID, Player>();
    private ArrayList<UUID> activePlayers = new ArrayList<UUID>();
    private map<UUID, int> playerLayer = map<UUID, int>();
    private double degradeCoefficient;
    private double prevDegrade = 0;
    private double totalTick = 0;
    private double trapDoorArea;
    TeamManager teamManager;
    ScoreManager scoreManager;
    private const Material[] trapDoorTypes = {Material.BAMBOO_TRAPDOOR, Material.ACACIA_TRAPDOOR, Material.MANGROVE_TRAPDOOR};

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
                data.put(p.getUniqueId(), new PlayerData(p.getUniqueId()));
                activePlayers.add(p.getUniqueId());
                playerLayer.put(p.getUniqueId(), 0);

            }
        }

        trapDoorArea = abs(loc1.getX() - loc2.getX()) * abs(loc1.getZ() - loc2.getZ());

        degradeCoefficient = (depletionExp + 1) * (trapDoorArea / expn(layerDepletionTime, depletionExp + 1) - startDegradeSpeed / expn(layerDepletionTime, depletionExp));

        for (int i = 0; i < layerAmount; i++) {

            Location lLoc1 = loc1.substract(0, higthDiffernce * i, 0);
            Location lLoc2 = loc2.substract(0, higthDiffernce * i, 0);
            fill(lLoc1, lLoc2, Material.BAMBOO_TRAPDOOR);
        }
    }

    public void start() {

        for (UUID uuid : activePlayers) {

            Player p = data.get(uuid);
            p.setVelocity(new Vector(0, 0, 0));
            double rndX = randomBetween(loc1.getX(), loc2.getX());
            double rndZ = randomBetween(loc1.getZ(), loc2.getZ());
            Location loc = new Location(world, rndX, loc.getY(), rndZ);
            p.teleport(start);
        }
    }

    protected void onGameTick() {

        for (UUID uuid : activePlayers) {
            Player p = data.get(uuid);
            if (p.getGameMode() != GameMode.CREATIVE && p.getGameMode() != GameMode.SPECTATOR) {
                long yPos = p.getLocation().getY();
                int currPlayerLayer = (int) ((loc1.getY() - yPos) / higthDiffernce + 0.5);
                if (currPlayerLayer > (layerAmount + 1)) {
                    // noch eine Funktion (sowas wie disqualify)
                } else {
                    playerLayer.put(uuid, currPlayerLayer);
                }
            }
        }

        if (!player.getInventory().contains(Material.WIND_CHARGE) && p.getGameMode() != GameMode.CREATIVE && p.getGameMode() != GameMode.SPECTATOR) {
            ItemStack blazeRod = new ItemStack(Material.WIND_CHARGE, 1);
        }

        if (trapdoors.length == 0) {
            Location lLoc1 = loc1.substract(0, higthDiffernce * currentLayer, 0);
            Location lLoc2 = loc2.substract(0, higthDiffernce * currentLayer, 0);
            fillTrapDoorsArr(trapdoors, lLoc1, lLoc2, world);
            currentLayer++;
        }

        totalTick += 1.0;

        double currDegrade = ((degradeCoefficient / (depletionExp + 1)) * expn(totalTick, (depletionExp + 1)) + startDegradeSpeed * totalTick;
        int newDegrade = ((int)currDegrade) - ((int)prevDegrade);
        prevDegrade = currDegrade;

        for (int i = 0; i < newDegrade; i++) {
            Random random = new Random();
            int indexTP = random.nextInt(trapdoors.length);

            if (trapdoors.get(indexTP).degrade()) {
                trapdoors.remove(indexTP);
            }

        }
    }


    public disqualify(UUID uuid) {
        playerLayer.remove(uuid);
        p.setGameMode(GameMode.SPECTATOR);
        // noch Punkte vergeben und so und evtl. noch Nachricht
    }

}

public void fill(Location loc1, Location loc2, Material m) {

    long smallX = long(loc1.getX > loc2.getX ? loc2.getX : loc1.getX);
    long bigX = long(loc1.getX > loc2.getX ? loc1.getX : loc2.getX);

    long smallY = long(loc1.getY > loc2.getY ? loc2.getY : loc1.getY);
    long bigY = long(loc1.getY > loc2.getY ? loc1.getY : loc2.getY);

    long smallZ long(smallZ = loc1.getZ > loc2.getZ ? loc2.getZ : loc1.getZ);
    long bigZ = long(bigZ = loc1.getZ > loc2.getZ ? loc1.getZ : loc2.getZ);

    for (int x = smallX; x <= bigX; x++) {
        for (int y = smallY; y <= bigY; y++) {
            for (int z = smallZ; z <= bigZ; z++) {
                world.getBlockAt(x,y,z).setType(m);
            }
        }
    }

}

public void fillTrapDoorsArr(ArrayList<degradingTrapdoor> trapdoors, Location loc1, Location loc2, World world) {

    long smallX = long(loc1.getX > loc2.getX ? loc2.getX : loc1.getX);
    long bigX = long(loc1.getX > loc2.getX ? loc1.getX : loc2.getX);

    long smallZ long(smallZ = loc1.getZ > loc2.getZ ? loc2.getZ : loc1.getZ);
    long bigZ = long(bigZ = loc1.getZ > loc2.getZ ? loc1.getZ : loc2.getZ);

    for (int x = smallX; x <= bigX; x++) {
        for (int z = smallZ; z <= bigZ; z++) {
            Location loc = new Location(world, x, loc1.getY(), z);
            degradingTrapdoor dTP = degradingTrapdoor(loc, 0);
            trapdoors.add(dTP);
        }
    }
}

private class degradingTrapdoor {
    private int status;
    private Location pos;

    public degradingTrapdoor(Location arg_pos, int arg_status) {
        status = arg_status;
        pos = arg_pos;
    }

    public boolean degrade() {
        Block trapDoorB = pos.getBlock();
        status++;

        if (status != trapDoorTypes.length) {

            TrapDoor trapDoorTD = (TrapDoor) trapDoorB;
            boolean open = trapDoorTD.isOpen;
            Material mTD = trapDoorTypes[status];
            trapDoor.setType(mTD);

            if (open) {
                trapdoorTD.setOpen(true);
            } else {
                trapdoorTD.setOpen(false);
            }
            return false;
        }
        else {
            trapDoorB.setType(Material.AIR);
            return true;
        }
    }
}


public expn(double base, exponent) {
    return exp(exponent * log(base));
}

public double randomBetween(double val0, double val1) {
    Random random = new Random();
    double smallVal = val0 > val1 ? val1 : val0;
    double bigVal = val0 > val1 ? val0 : val1;
    return (random.nextInt(bigVal - smallVal) + smallVal);
}