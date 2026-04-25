package me.HenRun189.tunierServer.game.modes;

import me.HenRun189.tunierServer.jumpandrun.PlayerData;
import me.HenRun189.tunierServer.score.ScoreManager;
import me.HenRun189.tunierServer.team.*;
import me.HenRun189.tunierServer.TunierServer;

import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.*;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.Team;
import org.bukkit.util.Vector;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.event.block.Action;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.event.EventHandler;

import net.kyori.adventure.text.Component;

import java.util.*;

import static java.lang.Math.abs;

public class JumpAndRunMode extends AbstractGameMode implements Listener {

    private final TeamManager teamManager;
    private final ScoreManager scoreManager;

    private final Map<UUID, PlayerData> data = new HashMap<>();

    private final String WORLD_NAME = "voidworld2";

    private final double MIN_X = -2;
    private final double MAX_X = 3;

    private final double MIN_Z = -1;
    private final double MAX_Z = 2;

    private final double MIN_Y = 64;
    private final double MAX_Y = 66;
    private boolean gameStarted = false;

    private final Map<UUID, Long> lastUse = new HashMap<>();
    private final Map<UUID, BukkitRunnable> cooldownTasks = new HashMap<>();

    public JumpAndRunMode(TeamManager teamManager, ScoreManager scoreManager) {
        super(600, teamManager); // 10 Minuten
        this.teamManager = teamManager;
        this.scoreManager = scoreManager;
    }

    @Override
    public void start() {

        data.clear();
        gameStarted = false;
        lastUse.clear();
        cooldownTasks.clear();

        for (TeamData team : teamManager.getTeams().values()) {
            for (UUID uuid : team.getPlayers()) {
                Player p = Bukkit.getPlayer(uuid);
                if (p == null) continue;

                data.put(p.getUniqueId(), new PlayerData(p.getUniqueId()));
            }
        }

        Bukkit.getPluginManager().registerEvents(this, TunierServer.getInstance());
        super.start();
    }

    @Override
    protected void onGameStart() {

        Random r = new Random();

        for (Player p : Bukkit.getOnlinePlayers()) {

            if (!p.getWorld().getName().equals(WORLD_NAME)) continue;

            double x = MIN_X + (Math.random() * (MAX_X - MIN_X));
            double z = MIN_Z + (Math.random() * (MAX_Z - MIN_Z));

            Location spawn = new Location(p.getWorld(), x, 65, z);

            p.teleport(spawn);
        }

        gameStarted = true; // 🔥 GANZ WICHTIG

        for (Player p : Bukkit.getOnlinePlayers()) {

            if (!p.getWorld().getName().equals(WORLD_NAME)) continue;

            p.getInventory().clear();

            ItemStack resetItem = new ItemStack(Material.BLAZE_ROD);
            ItemMeta meta = resetItem.getItemMeta();
            meta.setDisplayName("§cZum Checkpoint teleportieren");
            resetItem.setItemMeta(meta);

            p.getInventory().setItem(0, resetItem);

            //p.sendTitle("§aGO!", "§7Jump and Run", 10, 40, 10);
        }

        Scoreboard board = Bukkit.getScoreboardManager().getMainScoreboard();
        Team collisionTeam = board.getTeam("no_collision");

        if (collisionTeam == null) {
            collisionTeam = board.registerNewTeam("no_collision");
            collisionTeam.setOption(Team.Option.COLLISION_RULE, Team.OptionStatus.NEVER);
        }

        for (Player p : Bukkit.getOnlinePlayers()) {
            collisionTeam.addEntry(p.getName());
        }
    }

    @Override
    protected void onGameTick() {

        for (Player p : Bukkit.getOnlinePlayers()) {

            if (!p.getWorld().getName().equals(WORLD_NAME)) continue;

            PlayerData pd = data.get(p.getUniqueId());
            if (pd == null) continue;

            // ⬇️ FALL SYSTEM
            if (p.getLocation().getY() < 57) {
                resetPlayer(p, pd);
                p.playSound(p.getLocation(), Sound.ENTITY_ENDERMAN_TELEPORT, 1f, 1f);
            }

            // 📊 ACTIONBAR
            int place = getPlayerPlace(p);

            p.sendActionBar(Component.text(
                    "§e#" + place + " §8| §cFalls: " + pd.getFalls()
            ));
        }
    }

    private void resetPlayer(Player p, PlayerData pd) {

        World world = Bukkit.getWorld(WORLD_NAME);
        if (world == null) return;

        if (pd.getLastCheckpoint() != null) {
            p.setVelocity(new Vector(0, 0, 0));
            p.teleport(pd.getLastCheckpoint());
        } else {
            Location start = new Location(world, 0.5, 65, 0.5);
            p.setVelocity(new Vector(0, 0, 0));
            p.teleport(start);
        }

        pd.addFall();
    }

    @EventHandler
    public void onMove(PlayerMoveEvent e) {

        if (e.getTo() == null) return;

        // nur reagieren, wenn der Spieler wirklich den Block wechselt
        if (!e.hasChangedBlock()) return;

        Player p = e.getPlayer();

        PlayerData pd = data.get(p.getUniqueId());
        if (pd == null) return;

        if (!p.getWorld().getName().equals(WORLD_NAME)) return;

        // =========================
        // 🚧 PRE-LOBBY WALL
        // =========================

        if (!gameStarted) {

            Location to = e.getTo();

            if (to.getX() < MIN_X || to.getX() > MAX_X
                    || to.getZ() < MIN_Z || to.getZ() > MAX_Z
                    || to.getY() < MIN_Y || to.getY() > MAX_Y) {

                e.setTo(e.getFrom());
                return;
            }

        }

        // =========================
        // 🏁 CHECKPOINT SYSTEM
        // =========================
        Location to = e.getTo();
        Location from = e.getFrom();

        if (to == null) return;

// Blöcke unter dem Spieler
        Block fromBelow = from.clone().subtract(0, 1, 0).getBlock();
        Block toBelow = to.clone().subtract(0, 1, 0).getBlock();

        // nur wenn man NEU auf eine Checkpoint-Plate draufläuft
        if (toBelow.getType() != Material.GOLD_BLOCK) return;
        if (fromBelow.getType() == Material.GOLD_BLOCK) return;

        Location cpLoc = toBelow.getLocation();
        Location checkLoc = cpLoc.clone().add(0.5, 1, 0.5);

        // Anti-Farm Check (dein bestehender) bleibt, aber erhöhe den Radius!
        if (pd.getLastCheckpoint() != null) {
            Location last = pd.getLastCheckpoint();

            if (last.getWorld() != null
                    && checkLoc.getWorld() != null
                    && last.getWorld().equals(checkLoc.getWorld())
                    && last.distanceSquared(checkLoc) < 100) { // <-- war 2.25, jetzt 100 (= 10 Blöcke Radius)
                return;
            }
        }

        // Checkpoint setzen
        int next = pd.getCheckpoint() + 1;
        Location checkpointTeleport = cpLoc.clone().add(0.5, 1, 0.5);

        pd.setCheckpoint(next);
        pd.setLastCheckpoint(checkpointTeleport);

// Punkte
        TeamData team = teamManager.getTeamByPlayer(p.getUniqueId());
        if (team != null) {
            scoreManager.addPoints(team.getName(), 10);
        }

// Feedback
        p.spawnParticle(Particle.HAPPY_VILLAGER, checkpointTeleport, 30, 0.3, 0.3, 0.3, 0);
        p.playSound(p.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1f, 1.2f);
        p.sendActionBar(Component.text("§aCheckpoint §e#" + next));

    }


    @EventHandler
    public void onInteract(PlayerInteractEvent e) {

        // Nur Rechtsklick + Mainhand
        if (e.getHand() != EquipmentSlot.HAND) return; // import org.bukkit.inventory.EquipmentSlot;
        Action action = e.getAction();                  // import org.bukkit.event.block.Action;
        if (action != Action.RIGHT_CLICK_AIR && action != Action.RIGHT_CLICK_BLOCK) return;

        Player p = e.getPlayer();

        // Item sicher prüfen
        ItemStack item = e.getItem();
        if (item == null) return;
        if (item.getType() != Material.BLAZE_ROD) return;
        if (!item.hasItemMeta()) return;

        ItemMeta meta = item.getItemMeta();
        if (meta == null) return;
        if (!"§cZum Checkpoint teleportieren".equals(meta.getDisplayName())) return;

        PlayerData pd = data.get(p.getUniqueId());
        if (pd == null) return;

        long now = System.currentTimeMillis();

        // Cooldown check
        if (lastUse.containsKey(p.getUniqueId())) {
            long diff = now - lastUse.get(p.getUniqueId());

            if (diff < 2000) {
                p.sendActionBar(Component.text("§cCooldown..."));
                return;
            }
        }

        lastUse.put(p.getUniqueId(), now);

        // teleport
        World world = Bukkit.getWorld(WORLD_NAME);
        if (world == null) return;

        if (pd.getLastCheckpoint() != null) {
            p.teleport(pd.getLastCheckpoint());
        } else {
            p.teleport(new Location(world, 0.5, 65, 0.5));
        }

        pd.addFall();

        p.playSound(p.getLocation(), Sound.ENTITY_ENDERMAN_TELEPORT, 1f, 1f);

        // XP BAR COOLDOWN ANIMATION
        startCooldownBar(p);
    }

    private int getPlayerPlace(Player player) {

        List<PlayerData> list = new ArrayList<>(data.values());

        list.sort((a, b) -> {
            int cmp = Integer.compare(b.getCheckpoint(), a.getCheckpoint());
            if (cmp != 0) return cmp;

            return Integer.compare(a.getFalls(), b.getFalls());
        });

        for (int i = 0; i < list.size(); i++) {
            if (list.get(i).getUuid().equals(player.getUniqueId())) {
                return i + 1;
            }
        }

        return list.size();
    }

    @Override
    protected List<TeamData> getRanking() {

        List<TeamData> ranking = new ArrayList<>(teamManager.getTeams().values());

        ranking.sort((a, b) -> Integer.compare(
                getPoints(b.getName()),
                getPoints(a.getName())
        ));

        return ranking;
    }

    @Override
    protected int getPoints(String teamName) {

        TeamData team = teamManager.getTeam(teamName);
        if (team == null) return 0;

        int sum = 0;

        for (UUID uuid : team.getPlayers()) {
            PlayerData pd = data.get(uuid);
            if (pd != null) {
                sum += pd.getCheckpoint() * 10;
            }
        }

        return sum;
    }

    @Override
    public void handleEvent(Event event) {}

    @Override
    public void stop() {

        // alle Cooldown-Tasks stoppen
        for (BukkitRunnable task : cooldownTasks.values()) {
            if (task != null) {
                task.cancel();
            }
        }
        cooldownTasks.clear();
        lastUse.clear();

        // XP-Bar bei allen betroffenen Spielern zurücksetzen
        for (UUID uuid : data.keySet()) {
            Player player = Bukkit.getPlayer(uuid);
            if (player != null && player.isOnline()) {
                player.setExp(0f);
                player.setLevel(0);
            }
        }

        super.stop();

        HandlerList.unregisterAll(this);

        Scoreboard board = Bukkit.getScoreboardManager().getMainScoreboard();
        Team team = board.getTeam("no_collision");

        if (team != null) {
            for (String entry : new HashSet<>(team.getEntries())) {
                team.removeEntry(entry);
            }
        }

        data.clear();
    }

    @EventHandler
    public void onDrop(PlayerDropItemEvent e) {
        if (data.containsKey(e.getPlayer().getUniqueId())) {
            e.setCancelled(true);
        }
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent e) {
        if (e.getWhoClicked() instanceof Player p) {
            if (data.containsKey(p.getUniqueId())) {
                e.setCancelled(true);
            }
        }
    }

    @EventHandler
    public void onSwap(PlayerSwapHandItemsEvent e) {
        if (data.containsKey(e.getPlayer().getUniqueId())) {
            e.setCancelled(true);
        }
    }

    @EventHandler
    public void onAdvancement(org.bukkit.event.player.PlayerAdvancementDoneEvent e) {

        if (!data.containsKey(e.getPlayer().getUniqueId())) return;

        // ❌ Chat Nachricht weg
        e.message(Component.empty());

        // ❌ Fortschritt direkt wieder löschen (kein Toast)
        var adv = e.getAdvancement();
        var progress = e.getPlayer().getAdvancementProgress(adv);

        for (String crit : new HashSet<>(progress.getAwardedCriteria())) {
            progress.revokeCriteria(crit);
        }
    }

    private void startCooldownBar(Player p) {

        // alten Task killen
        if (cooldownTasks.containsKey(p.getUniqueId())) {
            cooldownTasks.get(p.getUniqueId()).cancel();
        }

        BukkitRunnable task = new BukkitRunnable() {

            double time = 2.0;

            @Override
            public void run() {

                time -= 0.05;

                float progress = (float) (time / 2.0);

                p.setExp(Math.max(0, progress));
                p.setLevel((int) Math.ceil(time));

                if (time <= 0) {
                    p.setExp(0);
                    p.setLevel(0);
                    p.sendActionBar(Component.text("§eTeleport bereit"));
                    cancel();
                    cooldownTasks.remove(p.getUniqueId()); // 🔥 wichtig
                }
            }

        };

        cooldownTasks.put(p.getUniqueId(), task);
        task.runTaskTimer(TunierServer.getInstance(), 0L, 1L);
    }

    @EventHandler
    public void onBreak(org.bukkit.event.block.BlockBreakEvent e) {

        Player p = e.getPlayer();

        // ❌ nur wenn im Game
        if (!data.containsKey(p.getUniqueId())) return;

        // ❌ Creative darf alles
        if (p.getGameMode() == GameMode.CREATIVE) return;

        Material type = e.getBlock().getType();

        // 🔒 Trapdoors + Bookshelf blocken
        if (type.name().contains("TRAPDOOR") || type == Material.BOOKSHELF) {
            e.setCancelled(true);
        }
    }

    @EventHandler
    public void onInteractBlock(PlayerInteractEvent e) {

        if (e.getClickedBlock() == null) return;

        Player p = e.getPlayer();

        if (!data.containsKey(p.getUniqueId())) return;
        if (p.getGameMode() == GameMode.CREATIVE) return;

        Material type = e.getClickedBlock().getType();

        // 🔒 Trapdoors nicht klickbar
        if (Tag.TRAPDOORS.isTagged(type)) {
            e.setCancelled(true);
        }
    }

}