package me.HenRun189.tunierServer.game.modes;

import me.HenRun189.tunierServer.TunierServer;
import me.HenRun189.tunierServer.team.TeamData;
import me.HenRun189.tunierServer.team.TeamManager;
import me.HenRun189.tunierServer.score.ScoreManager;

import org.bukkit.*;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.util.Vector;
import org.bukkit.event.block.BlockBreakEvent;

import net.kyori.adventure.text.Component;

import java.util.*;


public class SpleefShovel extends AbstractGameMode implements Listener {

    private static final String MODE_DISPLAY_NAME = "§b§lSpleef Shovel";
    private static final String NEXT_MODE = "spleeffallingblocks";

    private World world = Bukkit.getWorld("windchargeworld");

    private Location spawnLoc = new Location(world, 0, 128, -91);
    private double higthDiffernce = 7;
    private int layerAmount = 6;
    private Location loc1 = new Location(world, 16, 118, -110);
    private Location loc2 = new Location(world, -14, 118, -79);

    private TeamManager teamManager;
    private ScoreManager scoreManager;
    private Map<UUID, Player> data = new HashMap<>();
    private ArrayList<UUID> activePlayers = new ArrayList<UUID>();
    private Map<UUID, Integer> playerLayer = new HashMap<>();

    private int layerDepletionStartTime = 30;
    private int depletionPerSecond = 9;
    private ArrayList<Location> fallingBlocks = new ArrayList<>();
    private int currentLayer = 0;
    private int layerTimer = 0;

    private final Random random = new Random();

    // Punktesystem
    private static final int[] PLACEMENT_POINTS = {30, 20, 15, 10, 6, 3, 1};
    private int playersAtStart = 0;
    private int eliminationsCount = 0;
    private List<UUID> eliminationOrder = new ArrayList<>();
    private Map<UUID, Integer> pointsThisGame = new HashMap<>();

    // BossBar + Timer
    private BossBar bossBar;
    private long gameStartTickMillis = 0;
    private boolean gameRunning = false;


    public SpleefShovel(TeamManager arg_teamManager, ScoreManager arg_scoreManager) {
        super(-1, arg_teamManager);
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
                p.teleport(spawnLoc);
                p.setInvulnerable(true);
                p.setHealth(20.0);
                p.setFoodLevel(20);
                p.getInventory().clear();
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
            fill(lLoc1, lLoc2, Material.SNOW_BLOCK);
        }
    }

    @Override
    protected void onGameStart() {
        playersAtStart = activePlayers.size();
        eliminationsCount = 0;
        eliminationOrder.clear();
        pointsThisGame.clear();
        gameStartTickMillis = System.currentTimeMillis();
        gameRunning = true;

        bossBar = Bukkit.createBossBar(MODE_DISPLAY_NAME + " §7- §e00:00", BarColor.WHITE, BarStyle.SOLID);
        bossBar.setProgress(1.0);

        Component tabHeader = Component.text(MODE_DISPLAY_NAME);

        for (UUID uuid : activePlayers) {
            Player p = data.get(uuid);
            if (p == null) continue;

            Location drop = p.getLocation().clone().subtract(0, 5, 0);
            p.teleport(drop);
            p.setVelocity(new Vector(0, 0, 0));
            p.setGameMode(GameMode.SURVIVAL);
            p.setInvulnerable(true);

            ItemStack shovel = new ItemStack(Material.IRON_SHOVEL, 1);
            ItemMeta meta = shovel.getItemMeta();
            if (meta != null) {
                meta.setUnbreakable(true);
                shovel.setItemMeta(meta);
            }
            p.getInventory().addItem(shovel);

            bossBar.addPlayer(p);
            p.sendTitle(MODE_DISPLAY_NAME, "§7Schaufel die Schneeblöcke!", 10, 60, 20);
            p.playSound(p.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1f, 1f);
            p.sendPlayerListHeaderAndFooter(tabHeader, Component.text("§7Modus läuft..."));
        }
    }


    @EventHandler
    public void onMove(PlayerMoveEvent e) {
        // wird nicht benutzt
    }

    @Override
    protected void onGameTick() {

        // BossBar Timer
        if (bossBar != null && gameRunning) {
            long elapsed = (System.currentTimeMillis() - gameStartTickMillis) / 1000;
            long minutes = elapsed / 60;
            long seconds = elapsed % 60;
            bossBar.setTitle(MODE_DISPLAY_NAME + " §7- §e" + String.format("%02d:%02d", minutes, seconds));
        }

        double disqualifyHight = loc1.getY() - higthDiffernce * layerAmount - 2;

        List<UUID> toDisqualify = new ArrayList<>();

        for (UUID uuid : activePlayers) {
            Player p = data.get(uuid);
            if (p == null) continue;

            double yPos = p.getLocation().getY();
            int currPlayerLayer = (int) ((loc1.getY() - yPos) / higthDiffernce + 0.5);
            playerLayer.put(uuid, currPlayerLayer);

            if (p.getGameMode() != GameMode.CREATIVE && p.getGameMode() != GameMode.SPECTATOR) {
                if (yPos < disqualifyHight) {
                    toDisqualify.add(uuid);
                }
            }
        }

        for (UUID uuid : toDisqualify) {
            disqualify(uuid);
        }

        // Win-Check: nur noch 1 Team übrig?
        if (checkTeamWin()) {
            return;
        }

        if (activePlayers.size() <= 1) {
            if (activePlayers.size() == 1) {
                UUID winner = activePlayers.get(0);
                eliminationOrder.add(winner);
                awardPoints(winner, 0);
            }
            endGame();
            return;
        }

        // Neuen Layer zum Verschwinden hinzufügen
        if (fallingBlocks.size() == 0 && layerTimer > layerDepletionStartTime) {
            if (currentLayer < layerAmount) {
                int smallX = (int) Math.floor(Math.min(loc1.getX(), loc2.getX()));
                int bigX = (int) Math.floor(Math.max(loc1.getX(), loc2.getX()));
                int smallZ = (int) Math.floor(Math.min(loc1.getZ(), loc2.getZ()));
                int bigZ = (int) Math.floor(Math.max(loc1.getZ(), loc2.getZ()));

                for (int x = smallX; x <= bigX; x++) {
                    for (int z = smallZ; z <= bigZ; z++) {
                        fallingBlocks.add(new Location(world, x, loc1.getY() - higthDiffernce * currentLayer, z));
                    }
                }

                currentLayer++;
                layerTimer = 0;
            }
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
    protected void onSecond() {
        if (fallingBlocks.size() != 0) {
            for (int i = 0; i < depletionPerSecond; i++) {
                if (fallingBlocks.size() == 0) break;
                int indexBlock = random.nextInt(fallingBlocks.size());
                Location dloc = fallingBlocks.get(indexBlock);
                dloc.getBlock().setType(Material.AIR);
                fallingBlocks.remove(indexBlock);
            }
        }
        layerTimer++;
    }

    private boolean checkTeamWin() {
        if (activePlayers.size() <= 1) return false;

        Set<String> remainingTeams = new HashSet<>();
        for (UUID uuid : activePlayers) {
            TeamData t = teamManager.getTeamByPlayer(uuid);
            if (t != null) remainingTeams.add(t.getName());
        }

        if (remainingTeams.size() == 1) {
            List<UUID> winnersCopy = new ArrayList<>(activePlayers);
            for (UUID winner : winnersCopy) {
                eliminationOrder.add(winner);
                awardPoints(winner, 0);
            }
            activePlayers.clear();
            endGame();
            return true;
        }
        return false;
    }

    @Override
    protected void updateActionbar() {
        // Layer-Info wird direkt in onGameTick() gesetzt
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


    public void disqualify(UUID uuid) {
        if (!activePlayers.contains(uuid)) return;

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
        Bukkit.broadcast(Component.text(MODE_DISPLAY_NAME + " §7- §6§lRanking"));
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

    private void endGame() {
        gameRunning = false;
        if (NEXT_MODE != null) {
            TunierServer.getInstance().getGameManager().transitionToNextMode(NEXT_MODE);
        } else {
            TunierServer.getInstance().getGameManager().stopGame();
        }
    }


    @EventHandler
    public void onDamage(org.bukkit.event.entity.EntityDamageEvent e) {
        if (!(e.getEntity() instanceof Player p)) return;
        if (!activePlayers.contains(p.getUniqueId())) return;
        e.setCancelled(true);
    }

    @EventHandler
    public void onBlockBreak(BlockBreakEvent e) {
        e.setDropItems(false);
        if (!activePlayers.contains(e.getPlayer().getUniqueId())) return;
        if (!TunierServer.getInstance().getGameManager().isGameActive()) return;
        if (e.getBlock().getType() == Material.SNOW_BLOCK) {
            e.setCancelled(false);
        } else {
            e.setCancelled(true);
        }
    }

    @EventHandler
    public void onBlockPlace(org.bukkit.event.block.BlockPlaceEvent e) {
        if (!activePlayers.contains(e.getPlayer().getUniqueId())) return;
        if (!TunierServer.getInstance().getGameManager().isGameActive()) return;
        e.setCancelled(true);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        UUID uuid = e.getPlayer().getUniqueId();
        if (activePlayers.contains(uuid)) {
            disqualify(uuid);
        }
        if (bossBar != null) {
            bossBar.removePlayer(e.getPlayer());
        }
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent e) {
        if (bossBar != null && gameRunning) {
            bossBar.addPlayer(e.getPlayer());
        }
        if (eliminationOrder.contains(e.getPlayer().getUniqueId())) {
            e.getPlayer().setGameMode(GameMode.SPECTATOR);
        }
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
        gameRunning = false;

        if (bossBar != null) {
            bossBar.removeAll();
            bossBar = null;
        }

        for (Player p : Bukkit.getOnlinePlayers()) {
            p.sendPlayerListHeaderAndFooter(Component.empty(), Component.empty());
        }

        broadcastRanking();

        super.stop();
        HandlerList.unregisterAll(this);

        activePlayers.clear();
        data.clear();
        playerLayer.clear();
        fallingBlocks.clear();
    }
}