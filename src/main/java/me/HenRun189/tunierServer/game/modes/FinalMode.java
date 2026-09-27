package me.HenRun189.tunierServer.game.modes;

import me.HenRun189.tunierServer.TunierServer;
import me.HenRun189.tunierServer.score.ScoreManager;
import me.HenRun189.tunierServer.team.TeamData;
import me.HenRun189.tunierServer.team.TeamManager;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.title.Title;

import org.bukkit.*;
import org.bukkit.attribute.Attribute;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.EnderDragon;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.player.PlayerItemHeldEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.*;

/**
 * Final-Modus:
 *  - Top 2 Teams kämpfen parallel gegen je einen Ender Dragon in zwei
 *    identischen End-Welten (gleicher Seed → faire Bedingungen).
 *  - Wer den Drachen zuerst killt, gewinnt.
 *  - Alle anderen sind Spectator, können per Hotbar zwischen beiden Welten wechseln.
 *  - Caster (Permission "tunier.caster") bekommen zusätzlich OP-Equipment im Spectator-Modus.
 *  - Am Ende: Gewinner auf die Bühne (Locations aus config.yml).
 */
public class FinalMode extends AbstractGameMode implements Listener {

    private final TeamManager teamManager;
    private final ScoreManager scoreManager;

    private TeamData teamA;
    private TeamData teamB;

    private World endA;
    private World endB;

    // Spectator-Lobby (kann gleich der Stage sein)
    private World spectatorWorld;

    private long sharedSeed;

    private boolean finished = false;
    private TeamData winner = null;

    private BossBar bossBarA;
    private BossBar bossBarB;

    // Wer war wo bevor das Finale gestartet wurde (für sauberes /final stop)
    private final Map<UUID, Location> previousLocations = new HashMap<>();
    private final Map<UUID, GameMode> previousGameModes = new HashMap<>();

    public FinalMode(TeamManager teamManager, ScoreManager scoreManager,
                     TeamData teamA, TeamData teamB) {
        super(-1, teamManager); // Kein Zeitlimit
        this.teamManager  = teamManager;
        this.scoreManager = scoreManager;
        this.teamA = teamA;
        this.teamB = teamB;
    }

    // =========================================================
    //  START
    // =========================================================

    @Override
    public void start() {
        finished = false;
        winner   = null;

        // 1) Beide Welten mit demselben Seed generieren -> identisch
        sharedSeed = new Random().nextLong();
        endA = createEndWorld("final_end_a", sharedSeed);
        endB = createEndWorld("final_end_b", sharedSeed);

        if (endA == null || endB == null) {
            Bukkit.broadcast(Component.text("§c[Final] Konnte End-Welten nicht erstellen!"));
            return;
        }

        // 2) Spectator-Lobby (Stage-Welt aus Config, sonst Default-Spawn)
        spectatorWorld = resolveSpectatorWorld();

        // 3) BossBars
        bossBarA = Bukkit.createBossBar(
                "§e" + teamA.getName() + " §8| §cDrache: §f???",
                BarColor.PURPLE, BarStyle.SEGMENTED_10);
        bossBarB = Bukkit.createBossBar(
                "§e" + teamB.getName() + " §8| §cDrache: §f???",
                BarColor.PURPLE, BarStyle.SEGMENTED_10);

        // 4) Listener registrieren
        Bukkit.getPluginManager().registerEvents(this, TunierServer.getInstance());

        // 5) AbstractGameMode start (Tick-Loop)
        super.start();
    }

    @Override
    protected void onGameStart() {

        // --- Alle Spieler verteilen ---
        for (Player p : Bukkit.getOnlinePlayers()) {

            // Aktuellen Zustand merken (für stop / restore)
            previousLocations.put(p.getUniqueId(), p.getLocation());
            previousGameModes.put(p.getUniqueId(), p.getGameMode());

            // Status zurücksetzen
            p.getInventory().clear();
            p.setHealth(p.getAttribute(Attribute.MAX_HEALTH).getValue());
            p.setFoodLevel(20);
            p.setSaturation(20f);
            p.setFireTicks(0);
            for (PotionEffect effect : p.getActivePotionEffects()) {
                p.removePotionEffect(effect.getType());
            }

            TeamData team = teamManager.getTeamByPlayer(p.getUniqueId());

            if (team != null && team.getName().equals(teamA.getName())) {
                setupFinalist(p, endA, bossBarA);
            } else if (team != null && team.getName().equals(teamB.getName())) {
                setupFinalist(p, endB, bossBarB);
            } else {
                setupSpectator(p);
            }
        }

        // --- Title ---
        for (Player p : Bukkit.getOnlinePlayers()) {
            p.showTitle(Title.title(
                    Component.text("§6§lFINALE"),
                    Component.text("§e" + teamA.getName() + " §7vs §e" + teamB.getName())
            ));
            p.playSound(p.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1f, 1f);
        }

        Bukkit.broadcast(Component.text("§8§m═══════════════════════════════"));
        Bukkit.broadcast(Component.text("       §6§l⚔ FINALE ⚔"));
        Bukkit.broadcast(Component.text("  §e" + teamA.getName() + " §7vs §e" + teamB.getName()));
        Bukkit.broadcast(Component.text("§7Wer den Drachen zuerst besiegt, gewinnt!"));
        Bukkit.broadcast(Component.text("§8§m═══════════════════════════════"));
    }

    // =========================================================
    //  TICK / SECOND
    // =========================================================

    @Override
    protected void onGameTick() {
        // Nichts pro Tick nötig
    }

    @Override
    protected void onSecond() {
        // Vanilla Drachenbar bei jedem Tick verstecken (manche Server resetten sie)
        hideVanillaDragonBar(endA);
        hideVanillaDragonBar(endB);

        updateDragonBossBar(endA, bossBarA, teamA);
        updateDragonBossBar(endB, bossBarB, teamB);
    }

    private void updateDragonBossBar(World world, BossBar bar, TeamData team) {
        if (world == null || bar == null) return;

        EnderDragon dragon = findDragon(world);
        if (dragon == null) {
            bar.setTitle("§e" + team.getName() + " §8| §cDrache: §7nicht gefunden");
            bar.setProgress(0);
            return;
        }

        double max = dragon.getAttribute(Attribute.MAX_HEALTH).getValue();
        double hp  = dragon.getHealth();
        double pct = Math.max(0, Math.min(1, hp / max));

        bar.setProgress(pct);
        bar.setTitle("§e" + team.getName() + " §8| §c❤ " + (int) hp + " §7/ " + (int) max);
    }

    private EnderDragon findDragon(World world) {
        for (org.bukkit.entity.Entity e : world.getEntities()) {
            if (e instanceof EnderDragon dragon) return dragon;
        }
        return null;
    }

    // =========================================================
    //  EVENTS
    // =========================================================

    @EventHandler
    public void onDragonDeath(EntityDeathEvent e) {
        if (finished) return;
        if (!(e.getEntity() instanceof EnderDragon dragon)) return;

        World world = dragon.getWorld();
        TeamData killerTeam = null;

        if (world.equals(endA))      killerTeam = teamA;
        else if (world.equals(endB)) killerTeam = teamB;

        if (killerTeam == null) return;

        finished = true;
        winner   = killerTeam;

        // Punkte vergeben (Sieger des Tuniers!)
        scoreManager.addPoints(winner.getName(), 100);

        for (Player p : Bukkit.getOnlinePlayers()) {
            p.showTitle(Title.title(
                    Component.text("§6§l🏆 SIEGER 🏆"),
                    Component.text("§e" + winner.getName())
            ));
            p.playSound(p.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1f, 1f);
            p.playSound(p.getLocation(), Sound.ENTITY_ENDER_DRAGON_DEATH, 1f, 1f);
        }

        Bukkit.broadcast(Component.text(""));
        Bukkit.broadcast(Component.text("§8§m═══════════════════════════════"));
        Bukkit.broadcast(Component.text("    §6§l🏆 §e" + winner.getName() + " §6§lGEWINNT! 🏆"));
        Bukkit.broadcast(Component.text("§8§m═══════════════════════════════"));

        // Bühne nach 8 Sekunden -> Showtime
        Bukkit.getScheduler().runTaskLater(TunierServer.getInstance(), this::teleportToStage, 20L * 8);

        // Spiel offiziell beenden nach 30 Sekunden
        Bukkit.getScheduler().runTaskLater(TunierServer.getInstance(),
                () -> TunierServer.getInstance().getGameManager().stopGame(), 20L * 30);
    }

    /**
     * Spectator switcht per Hotbar zwischen den beiden Welten.
     * Slot 1 = Welt A | Slot 2 = Welt B
     */
    @EventHandler
    public void onHotbarSwitch(PlayerItemHeldEvent e) {
        Player p = e.getPlayer();
        if (p.getGameMode() != GameMode.SPECTATOR) return;

        int slot = e.getNewSlot();

        if (slot == 0) {
            teleportToWorldSpawn(p, endA);
            p.sendActionBar(Component.text("§7Watching: §e" + teamA.getName()));
        } else if (slot == 1) {
            teleportToWorldSpawn(p, endB);
            p.sendActionBar(Component.text("§7Watching: §e" + teamB.getName()));
        }
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent e) {
        Player p = e.getPlayer();
        TeamData team = teamManager.getTeamByPlayer(p.getUniqueId());

        if (team != null && team.getName().equals(teamA.getName())) {
            setupFinalist(p, endA, bossBarA);
        } else if (team != null && team.getName().equals(teamB.getName())) {
            setupFinalist(p, endB, bossBarB);
        } else {
            setupSpectator(p);
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        if (bossBarA != null) bossBarA.removePlayer(e.getPlayer());
        if (bossBarB != null) bossBarB.removePlayer(e.getPlayer());
    }

    // =========================================================
    //  PLAYER SETUP
    // =========================================================

    private void setupFinalist(Player p, World world, BossBar bar) {
        p.setGameMode(GameMode.SURVIVAL);
        p.teleport(world.getSpawnLocation());
        giveOpEquipment(p);
        bar.addPlayer(p);
    }

    private void setupSpectator(Player p) {
        p.setGameMode(GameMode.SPECTATOR);
        // Caster (Hauptcast) bekommen sichtbares Inventar fürs Switchen
        // (Spectator zeigt Inventar zwar nicht, aber Hotbar-Slot-Wechsel funktioniert trotzdem)
        teleportToWorldSpawn(p, endA);

        // Beide BossBars sehen (für Übersicht)
        if (bossBarA != null) bossBarA.addPlayer(p);
        if (bossBarB != null) bossBarB.addPlayer(p);

        // Hauptcaster bekommen Hinweis-Items im Inventar
        if (p.hasPermission("tunier.caster")) {
            giveCasterItems(p);
            p.sendMessage("§a[Caster] §7Du bist Hauptcaster. Slot 1/2 wechselt die Welt.");
        } else {
            p.sendMessage("§7[Spectator] §fDu kannst den beiden Teams zuschauen.");
            p.sendMessage("§7Drücke §eSlot 1 §7oder §eSlot 2 §7um die Welt zu wechseln.");
        }
    }

    private void teleportToWorldSpawn(Player p, World world) {
        if (world == null) return;
        Location target = world.getSpawnLocation();
        // Etwas hoch für Übersicht
        p.teleport(target.clone().add(0, 30, 0));
    }

    // =========================================================
    //  EQUIPMENT
    // =========================================================

    private void giveOpEquipment(Player p) {
        PlayerInventory inv = p.getInventory();
        inv.clear();

        // Rüstung — Netherite + Schutz
        inv.setHelmet(enchanted(new ItemStack(Material.NETHERITE_HELMET),
                Enchantment.PROTECTION, 4));
        inv.setChestplate(enchanted(new ItemStack(Material.NETHERITE_CHESTPLATE),
                Enchantment.PROTECTION, 4));
        inv.setLeggings(enchanted(new ItemStack(Material.NETHERITE_LEGGINGS),
                Enchantment.PROTECTION, 4));
        inv.setBoots(enchant(new ItemStack(Material.NETHERITE_BOOTS),
                new Enchantment[]{Enchantment.PROTECTION, Enchantment.FEATHER_FALLING},
                new int[]{4, 4}));

        // Waffen
        ItemStack sword = new ItemStack(Material.NETHERITE_SWORD);
        sword.addUnsafeEnchantment(Enchantment.SHARPNESS, 5);
        sword.addUnsafeEnchantment(Enchantment.SWEEPING_EDGE, 3);
        sword.addUnsafeEnchantment(Enchantment.FIRE_ASPECT, 2);
        inv.setItem(0, sword);

        ItemStack bow = new ItemStack(Material.BOW);
        bow.addUnsafeEnchantment(Enchantment.POWER, 5);
        bow.addUnsafeEnchantment(Enchantment.INFINITY, 1);
        bow.addUnsafeEnchantment(Enchantment.FLAME, 1);
        inv.setItem(1, bow);

        // Pickaxe & Block-Tools
        ItemStack pick = new ItemStack(Material.NETHERITE_PICKAXE);
        pick.addUnsafeEnchantment(Enchantment.EFFICIENCY, 5);
        pick.addUnsafeEnchantment(Enchantment.UNBREAKING, 3);
        inv.setItem(2, pick);

        // Verbrauchsmaterial
        inv.setItem(3, new ItemStack(Material.GOLDEN_APPLE, 16));
        inv.setItem(4, new ItemStack(Material.ENDER_PEARL, 16));
        inv.setItem(5, new ItemStack(Material.COBBLESTONE, 64));
        inv.setItem(6, new ItemStack(Material.OAK_PLANKS, 64));
        inv.setItem(7, new ItemStack(Material.WATER_BUCKET));
        inv.setItem(8, new ItemStack(Material.ARROW, 1)); // Bogen + Infinity → 1 Arrow reicht

        // Ein Pfeil zum Mitnehmen
        inv.addItem(new ItemStack(Material.COOKED_BEEF, 32));
    }

    private void giveCasterItems(Player p) {
        PlayerInventory inv = p.getInventory();
        inv.clear();

        ItemStack a = new ItemStack(Material.RED_WOOL);
        ItemMeta am = a.getItemMeta();
        if (am != null) {
            am.displayName(Component.text("§c§lWelt A: §e" + teamA.getName()));
            a.setItemMeta(am);
        }
        inv.setItem(0, a);

        ItemStack b = new ItemStack(Material.BLUE_WOOL);
        ItemMeta bm = b.getItemMeta();
        if (bm != null) {
            bm.displayName(Component.text("§9§lWelt B: §e" + teamB.getName()));
            b.setItemMeta(bm);
        }
        inv.setItem(1, b);
    }

    private ItemStack enchanted(ItemStack item, Enchantment ench, int level) {
        item.addUnsafeEnchantment(ench, level);
        return item;
    }

    private ItemStack enchant(ItemStack item, Enchantment[] enchs, int[] lvls) {
        for (int i = 0; i < enchs.length; i++) item.addUnsafeEnchantment(enchs[i], lvls[i]);
        return item;
    }

    // =========================================================
    //  WORLD CREATION
    // =========================================================

    private World createEndWorld(String name, long seed) {

        // Schritt 1: Falls die Welt noch geladen ist, alle Spieler raus + entladen
        World existing = Bukkit.getWorld(name);
        if (existing != null) {
            World fallback = Bukkit.getWorlds().get(0);
            for (Player p : new ArrayList<>(existing.getPlayers())) {
                p.teleport(fallback.getSpawnLocation());
            }
            // save=false ist wichtig - sonst speichert er den Stand wieder!
            boolean unloaded = Bukkit.unloadWorld(existing, false);
            if (!unloaded) {
                Bukkit.getLogger().warning("[Final] Konnte Welt '" + name + "' nicht entladen!");
            }
        }

        // Schritt 2: Ordner brutal löschen (Bukkit hält manchmal noch Locks)
        java.io.File container = Bukkit.getWorldContainer();
        java.io.File worldFolder = new java.io.File(container, name);

        if (worldFolder.exists()) {
            // session.lock zuerst löschen damit der Folder nicht mehr "in Benutzung" ist
            java.io.File lockFile = new java.io.File(worldFolder, "session.lock");
            if (lockFile.exists()) lockFile.delete();

            boolean ok = deleteRecursive(worldFolder);
            if (!ok || worldFolder.exists()) {
                // Fallback: Nochmal versuchen nach kurzem Sleep (Windows hält oft Locks)
                try { Thread.sleep(200); } catch (InterruptedException ignored) {}
                deleteRecursive(worldFolder);
            }

            if (worldFolder.exists()) {
                Bukkit.broadcast(Component.text(
                        "§c[Final] Konnte alte Welt '" + name + "' nicht löschen! Server-Restart nötig."));
                return null;
            }
        }

        // Schritt 3: Frische Welt generieren MIT Strukturen (sonst kein Drache!)
        WorldCreator wc = new WorldCreator(name);
        wc.environment(World.Environment.THE_END);
        wc.seed(seed);
        wc.type(WorldType.NORMAL);
        wc.generateStructures(true);   // <— WICHTIG: sonst spawnt kein Drache
        World world = wc.createWorld();

        if (world != null) {
            world.setDifficulty(Difficulty.HARD);
            world.setGameRule(GameRules.ADVANCE_TIME,              false);
            world.setGameRule(GameRules.ADVANCE_WEATHER,           false);
            world.setGameRule(GameRules.KEEP_INVENTORY,            false);
            world.setGameRule(GameRules.SPAWN_MOBS,                true);
            world.setGameRule(GameRules.SHOW_ADVANCEMENT_MESSAGES, false);
            world.setAutoSave(false); // verhindert dass die fresh world überschrieben wird

            // Vanilla-Drachenbar verstecken (wir haben unsere eigene)
            org.bukkit.boss.DragonBattle battle = world.getEnderDragonBattle();
            if (battle != null) {
                battle.getBossBar().setVisible(false);
            }

            // Spawn-Chunk vorgenerieren damit der Drache da ist wenn TP passiert
            Location spawn = world.getSpawnLocation();
            for (int x = -3; x <= 3; x++) {
                for (int z = -3; z <= 3; z++) {
                    world.getChunkAt((spawn.getBlockX() >> 4) + x,
                            (spawn.getBlockZ() >> 4) + z).load(true);
                }
            }
        }
        return world;
    }

    private boolean deleteRecursive(java.io.File f) {
        if (f == null || !f.exists()) return true;
        boolean ok = true;
        if (f.isDirectory()) {
            java.io.File[] children = f.listFiles();
            if (children != null) {
                for (java.io.File c : children) {
                    if (!deleteRecursive(c)) ok = false;
                }
            }
        }
        if (!f.delete()) ok = false;
        return ok;
    }

    private World resolveSpectatorWorld() {
        String name = TunierServer.getInstance().getConfig().getString("final.stage.world", "world");
        World w = Bukkit.getWorld(name);
        return w != null ? w : Bukkit.getWorlds().get(0);
    }

    // =========================================================
    //  STAGE / END
    // =========================================================

    private void teleportToStage() {
        var cfg = TunierServer.getInstance().getConfig();

        Location stage = readLocation(cfg, "final.stage.winner");
        Location castA = readLocation(cfg, "final.stage.caster1");
        Location castB = readLocation(cfg, "final.stage.caster2");
        Location crowd = readLocation(cfg, "final.stage.crowd");

        if (stage == null) {
            Bukkit.broadcast(Component.text("§c[Final] §7Stage-Locations fehlen in config.yml — Spieler bleiben wo sie sind."));
            return;
        }

        // Caster ermitteln (max. 2 mit Permission)
        List<Player> casters = new ArrayList<>();
        for (Player p : Bukkit.getOnlinePlayers())
            if (p.hasPermission("tunier.caster")) casters.add(p);

        for (Player p : Bukkit.getOnlinePlayers()) {
            // Spielzustand aufräumen
            p.getInventory().clear();
            p.setGameMode(GameMode.ADVENTURE);

            TeamData team = teamManager.getTeamByPlayer(p.getUniqueId());

            if (winner != null && team != null && team.getName().equals(winner.getName())) {
                p.teleport(stage);
                p.showTitle(Title.title(
                        Component.text("§6§l🏆 SIEGER 🏆"),
                        Component.text("§e" + winner.getName())
                ));
                spawnFireworks(stage);
            } else if (casters.contains(p) && casters.indexOf(p) == 0 && castA != null) {
                p.teleport(castA);
            } else if (casters.contains(p) && casters.indexOf(p) == 1 && castB != null) {
                p.teleport(castB);
            } else if (crowd != null) {
                p.teleport(crowd);
            }
        }
    }

    private void spawnFireworks(Location loc) {
        for (int i = 0; i < 5; i++) {
            Bukkit.getScheduler().runTaskLater(TunierServer.getInstance(), () -> {
                org.bukkit.entity.Firework fw = loc.getWorld().spawn(loc, org.bukkit.entity.Firework.class);
                org.bukkit.inventory.meta.FireworkMeta meta = fw.getFireworkMeta();
                meta.addEffect(org.bukkit.FireworkEffect.builder()
                        .with(org.bukkit.FireworkEffect.Type.BALL_LARGE)
                        .withColor(Color.YELLOW, Color.ORANGE, Color.RED)
                        .withFlicker()
                        .withTrail()
                        .build());
                meta.setPower(1);
                fw.setFireworkMeta(meta);
            }, i * 10L);
        }
    }

    private Location readLocation(org.bukkit.configuration.file.FileConfiguration cfg, String path) {
        if (!cfg.contains(path)) return null;
        String worldName = cfg.getString(path + ".world", "world");
        World w = Bukkit.getWorld(worldName);
        if (w == null) return null;
        double x = cfg.getDouble(path + ".x");
        double y = cfg.getDouble(path + ".y");
        double z = cfg.getDouble(path + ".z");
        float yaw   = (float) cfg.getDouble(path + ".yaw", 0);
        float pitch = (float) cfg.getDouble(path + ".pitch", 0);
        return new Location(w, x, y, z, yaw, pitch);
    }

    // =========================================================
    //  STOP
    // =========================================================

    @Override
    public void stop() {
        super.stop();
        HandlerList.unregisterAll(this);

        if (bossBarA != null) bossBarA.removeAll();
        if (bossBarB != null) bossBarB.removeAll();

        // Vanilla-Drachenbar in beiden Welten verstecken (falls neu gespawnt)
        hideVanillaDragonBar(endA);
        hideVanillaDragonBar(endB);

        // Final-Welten entladen damit /final wieder funktioniert
        // (wir löschen sie beim nächsten /final start neu, nicht hier)
        if (endA != null) {
            for (Player p : new ArrayList<>(endA.getPlayers())) {
                p.teleport(Bukkit.getWorlds().get(0).getSpawnLocation());
            }
            Bukkit.unloadWorld(endA, false);
        }
        if (endB != null) {
            for (Player p : new ArrayList<>(endB.getPlayers())) {
                p.teleport(Bukkit.getWorlds().get(0).getSpawnLocation());
            }
            Bukkit.unloadWorld(endB, false);
        }

        previousLocations.clear();
        previousGameModes.clear();
    }

    private void hideVanillaDragonBar(World world) {
        if (world == null) return;
        try {
            org.bukkit.boss.DragonBattle battle = world.getEnderDragonBattle();
            if (battle != null) battle.getBossBar().setVisible(false);
        } catch (Exception ignored) {}
    }

    // =========================================================
    //  RANKING (für AbstractGameMode)
    // =========================================================

    @Override
    protected List<TeamData> getRanking() {
        List<TeamData> list = new ArrayList<>();
        if (winner != null) {
            list.add(winner);
            TeamData loser = winner.getName().equals(teamA.getName()) ? teamB : teamA;
            list.add(loser);
        } else {
            list.add(teamA);
            list.add(teamB);
        }
        return list;
    }

    @Override
    protected int getPoints(String teamName) {
        return scoreManager.getPoints(teamName);
    }

    @Override
    public void handleEvent(org.bukkit.event.Event event) {}
}