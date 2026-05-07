package me.HenRun189.tunierServer.cast;

import me.HenRun189.tunierServer.TunierServer;
import me.HenRun189.tunierServer.game.GameManager;
import me.HenRun189.tunierServer.team.TeamData;
import me.HenRun189.tunierServer.team.TeamManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.title.Title;
import org.bukkit.*;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.*;
import org.bukkit.event.*;
import org.bukkit.event.inventory.*;
import org.bukkit.inventory.*;
import org.bukkit.inventory.meta.*;

import java.util.*;
import java.util.stream.Collectors;

@SuppressWarnings("deprecation")
public class CastGUI implements Listener {

    // ── Titel-Konstanten ───────────────────────────────────────────
    private static final String T_MAIN    = "§6§lCast Menu";
    private static final String T_GAMES   = "§6§lSpiel starten";
    private static final String T_PLAYERS = "§6§lSpieler Übersicht";
    private static final String T_DETAIL  = "§d§l";       // Prefix: §d§l{playerName}
    private static final String T_INV     = "§d§lInv: ";  // Prefix: §d§lInv: {playerName}

    private final CastManager castManager;
    private final GameManager gameManager;
    private final TeamManager teamManager;

    // Merkt welchen Spieler ein Cast-Mitglied gerade anschaut (für "Zurück")
    private final Map<UUID, String> viewingTarget = new HashMap<>();

    public CastGUI(CastManager castManager, GameManager gameManager, TeamManager teamManager) {
        this.castManager = castManager;
        this.gameManager = gameManager;
        this.teamManager = teamManager;
    }

    // ══════════════════════════════════════════════════════════════
    //  HAUPT-MENÜ
    // ══════════════════════════════════════════════════════════════

    public void openMain(Player caster) {
        Inventory inv = Bukkit.createInventory(null, 27, T_MAIN);

        boolean running = gameManager.isGameRunning();
        boolean paused  = gameManager.isPaused();

        // Spiel starten
        inv.setItem(10, running
            ? item(Material.ORANGE_CONCRETE, "§e§lSpiel läuft",
                   "§7Stoppe zuerst das aktuelle Spiel")
            : item(Material.EMERALD_BLOCK, "§a§lSpiel starten",
                   "§7Wähle einen Spielmodus", "§eKlick"));

        // Spieler-Übersicht
        inv.setItem(12, item(Material.PLAYER_HEAD, "§e§lSpieler Übersicht",
                "§7Alle Online-Spieler anzeigen",
                "§7TP · Inventar · Health",
                "§eKlick"));

        // Pause / Resume
        if (running) {
            inv.setItem(14, paused
                ? item(Material.LIME_DYE,    "§a§lGame Fortsetzen", "§eKlick")
                : item(Material.CLOCK,        "§e§lGame Pausieren",  "§eKlick"));
        } else {
            inv.setItem(14, item(Material.GRAY_DYE, "§7Pause / Resume", "§8Kein Spiel aktiv"));
        }

        // Spiel stoppen
        inv.setItem(16, running
            ? item(Material.RED_CONCRETE,  "§c§lSpiel stoppen", "§7Beendet das Spiel", "§eKlick")
            : item(Material.GRAY_CONCRETE, "§7Spiel stoppen",   "§8Kein Spiel aktiv"));

        // Health-Anzeige Toggle
        boolean hOn = castManager.isHealthDisplayActive();
        inv.setItem(22, item(
            hOn ? Material.REDSTONE : Material.GRAY_DYE,
            hOn ? "§c§lHealth Anzeige: §a AN" : "§7Health Anzeige: §c AUS",
            "§7Zeigt Herzen unter Spielernamen",
            "§7(für alle sichtbar)",
            "§eKlick zum Umschalten"
        ));

        fill(inv);
        caster.openInventory(inv);
    }

    // ══════════════════════════════════════════════════════════════
    //  SPIEL-AUSWAHL
    // ══════════════════════════════════════════════════════════════

    public void openGames(Player caster) {
        Inventory inv = Bukkit.createInventory(null, 27, T_GAMES);

        inv.setItem(10, item(Material.DIAMOND_SWORD, "§b§lPvP",
                "§7Hunger Games", "§eKlick zum Starten"));
        inv.setItem(11, item(Material.FEATHER, "§a§lJump & Run",
                "§7Parcours", "§eKlick zum Starten"));
        inv.setItem(12, item(Material.FIRE_CHARGE, "§6§lSpleef WindCharge",
                "§7Wind-Spleef", "§eKlick zum Starten"));
        inv.setItem(13, item(Material.SAND, "§e§lSpleef FallingBlocks",
                "§7Falling-Blocks-Spleef", "§eKlick zum Starten"));
        inv.setItem(14, item(Material.IRON_SHOVEL, "§d§lSpleef Shovel",
                "§7Schaufel-Spleef", "§eKlick zum Starten"));
        inv.setItem(15, item(Material.CHEST, "§3§lItem Collector",
                "§7Items sammeln", "§eKlick zum Starten"));
        inv.setItem(16, item(Material.WRITABLE_BOOK, "§5§lAchievement Battle",
                "§7Advancements", "§eKlick zum Starten"));

        inv.setItem(18, item(Material.ARROW, "§7◀ Zurück"));

        fill(inv);
        caster.openInventory(inv);
    }

    // ══════════════════════════════════════════════════════════════
    //  SPIELER-LISTE
    // ══════════════════════════════════════════════════════════════

    public void openPlayers(Player caster) {
        List<Player> players = sortedPlayers();

        // Mindestens 2 Reihen, max 6 Reihen (54 Slots)
        int contentRows = (int) Math.ceil(players.size() / 9.0);
        int rows = Math.max(2, Math.min(6, contentRows + 1));
        int size = rows * 9;

        Inventory inv = Bukkit.createInventory(null, size, T_PLAYERS);

        for (int i = 0; i < Math.min(players.size(), size - 9); i++) {
            inv.setItem(i, skull(players.get(i)));
        }

        // Letzte Reihe: Zurück-Button in der Mitte
        inv.setItem(size - 5, item(Material.ARROW, "§7◀ Zurück"));

        // Leerstellen der letzten Reihe füllen
        for (int i = size - 9; i < size; i++) {
            if (inv.getItem(i) == null) inv.setItem(i, glass());
        }

        caster.openInventory(inv);
    }

    // ══════════════════════════════════════════════════════════════
    //  SPIELER-DETAIL
    // ══════════════════════════════════════════════════════════════

    public void openPlayerDetail(Player caster, Player target) {
        viewingTarget.put(caster.getUniqueId(), target.getName());
        Inventory inv = Bukkit.createInventory(null, 27, T_DETAIL + target.getName());

        // Großes Profil-Item oben mittig
        inv.setItem(4, buildProfileSkull(target));

        // TP
        inv.setItem(10, item(Material.ENDER_PEARL, "§b§lTeleportieren",
                "§7TP zu §f" + target.getName(), "§eKlick"));

        // Inventar ansehen
        inv.setItem(12, item(Material.CHEST, "§6§lInventar ansehen",
                "§7Öffnet das Inventar von §f" + target.getName(), "§eKlick"));

        // Spectator Toggle
        boolean isSpec = caster.getGameMode() == GameMode.SPECTATOR;
        inv.setItem(14, isSpec
            ? item(Material.LIME_STAINED_GLASS, "§a§lSpectator: AN",
                   "§7Klick → Normal-Modus")
            : item(Material.ENDER_EYE,           "§e§lSpectator Modus",
                   "§7Klick → Spectator + TP zu " + target.getName()));

        // Zurück
        inv.setItem(22, item(Material.ARROW, "§7◀ Zurück zur Liste"));

        fill(inv);
        caster.openInventory(inv);
    }

    // ══════════════════════════════════════════════════════════════
    //  INVENTAR-ANSICHT
    // ══════════════════════════════════════════════════════════════

    public void openPlayerInventory(Player caster, Player target) {
        viewingTarget.put(caster.getUniqueId(), target.getName());
        Inventory inv = Bukkit.createInventory(null, 54, T_INV + target.getName());

        // Haupt-Inventar (Slots 0-35)
        ItemStack[] contents = target.getInventory().getContents();
        for (int i = 0; i < 36 && i < contents.length; i++) {
            if (contents[i] != null && contents[i].getType() != Material.AIR)
                inv.setItem(i, contents[i].clone());
        }

        // Rüstung (Slots 36-39: Boots, Leggings, Chestplate, Helmet)
        ItemStack[] armor = target.getInventory().getArmorContents();
        String[] armorLabel = {"§7Boots", "§7Leggings", "§7Chestplate", "§7Helm"};
        for (int i = 0; i < 4; i++) {
            if (armor[i] != null && armor[i].getType() != Material.AIR)
                inv.setItem(36 + i, armor[i].clone());
            else
                inv.setItem(36 + i, item(Material.GRAY_STAINED_GLASS_PANE, armorLabel[i]));
        }

        // Nebenhand (Slot 40)
        ItemStack off = target.getInventory().getItemInOffHand();
        if (off.getType() != Material.AIR)
            inv.setItem(40, off.clone());
        else
            inv.setItem(40, item(Material.GRAY_STAINED_GLASS_PANE, "§7Nebenhand"));

        // Info-Block + Zurück
        inv.setItem(45, buildProfileSkull(target));
        inv.setItem(53, item(Material.ARROW, "§7◀ Zurück zum Spieler"));

        for (int i = 41; i <= 52; i++)
            if (inv.getItem(i) == null) inv.setItem(i, glass());

        caster.openInventory(inv);
    }

    // ══════════════════════════════════════════════════════════════
    //  EVENT HANDLER
    // ══════════════════════════════════════════════════════════════

    @EventHandler
    public void onInventoryClick(InventoryClickEvent e) {
        if (!(e.getWhoClicked() instanceof Player caster)) return;
        if (!castManager.isCast(caster.getUniqueId())) return;

        // Nur obere Inventory (nicht den eigenen Spieler-Inventar-Teil)
        if (e.getClickedInventory() == null) return;
        if (!e.getClickedInventory().equals(e.getView().getTopInventory())) return;

        String title = e.getView().getTitle();

        boolean isCastMenu = title.equals(T_MAIN) || title.equals(T_GAMES)
                || title.equals(T_PLAYERS) || title.startsWith(T_DETAIL);

        if (!isCastMenu) return;

        e.setCancelled(true);

        ItemStack clicked = e.getCurrentItem();
        if (clicked == null || clicked.getType() == Material.AIR
                || clicked.getType() == Material.GRAY_STAINED_GLASS_PANE) return;

        int slot = e.getSlot();

        // 1 Tick warten damit close/open sauber ablaufen
        Bukkit.getScheduler().runTask(TunierServer.getInstance(), () -> {
            if      (title.equals(T_MAIN))           handleMainClick(caster, slot);
            else if (title.equals(T_GAMES))          handleGamesClick(caster, slot);
            else if (title.equals(T_PLAYERS))        handlePlayersClick(caster, slot, e.getView().getTopInventory().getSize());
            else if (title.startsWith(T_INV))        handleInvViewClick(caster, slot);
            else if (title.startsWith(T_DETAIL))     handleDetailClick(caster, title, slot);
        });
    }

    // ── Haupt-Menü ─────────────────────────────────────────────────

    private void handleMainClick(Player caster, int slot) {
        switch (slot) {
            case 10 -> { if (!gameManager.isGameRunning()) openGames(caster); }
            case 12 -> openPlayers(caster);
            case 14 -> togglePause(caster);
            case 16 -> {
                if (gameManager.isGameRunning()) {
                    caster.closeInventory();
                    gameManager.stopGame();
                }
            }
            case 22 -> {
                castManager.toggleHealthDisplay();
                openMain(caster);
            }
        }
    }

    // ── Spiel-Auswahl ──────────────────────────────────────────────

    private void handleGamesClick(Player caster, int slot) {
        String mode = switch (slot) {
            case 10 -> "pvp";
            case 11 -> "jumpandrun";
            case 12 -> "spleefwindcharge";
            case 13 -> "spleeffallingblocks";
            case 14 -> "spleefshovel";
            case 15 -> "itemcollector";
            case 16 -> "achievement";
            case 18 -> null; // Zurück
            default -> null;
        };

        if (slot == 18) { openMain(caster); return; }
        if (mode == null) return;

        caster.closeInventory();
        caster.sendMessage(Component.text("§7Starte §e" + mode + "§7..."));
        gameManager.startGameFlow(mode);
    }

    // ── Spieler-Liste ──────────────────────────────────────────────

    private void handlePlayersClick(Player caster, int slot, int invSize) {
        int navRowStart = invSize - 9;

        // Zurück-Button (Mitte der letzten Reihe)
        if (slot >= navRowStart) {
            openMain(caster);
            return;
        }

        List<Player> players = sortedPlayers();
        if (slot < players.size()) {
            openPlayerDetail(caster, players.get(slot));
        }
    }

    // ── Spieler-Detail ─────────────────────────────────────────────

    private void handleDetailClick(Player caster, String title, int slot) {
        String targetName = title.substring(T_DETAIL.length());
        Player target = Bukkit.getPlayerExact(targetName);

        switch (slot) {
            case 10 -> { // TP
                caster.closeInventory();
                if (target != null) {
                    caster.teleport(target.getLocation());
                    caster.sendMessage(Component.text(
                        "§7Teleportiert zu §e" + targetName));
                } else {
                    caster.sendMessage(Component.text("§c" + targetName + " ist offline!"));
                }
            }
            case 12 -> { // Inventar
                if (target != null) openPlayerInventory(caster, target);
                else caster.sendMessage(Component.text("§c" + targetName + " ist offline!"));
            }
            case 14 -> { // Spectator Toggle
                caster.closeInventory();
                if (caster.getGameMode() == GameMode.SPECTATOR) {
                    caster.setGameMode(GameMode.ADVENTURE);
                    caster.sendMessage(Component.text("§7Spectator §cdeaktiviert"));
                } else {
                    caster.setGameMode(GameMode.SPECTATOR);
                    if (target != null) caster.teleport(target.getLocation());
                    caster.sendMessage(Component.text("§7Spectator §aaktiviert"));
                }
            }
            case 22 -> openPlayers(caster); // Zurück
        }
    }

    // ── Inventar-Ansicht ───────────────────────────────────────────

    private void handleInvViewClick(Player caster, int slot) {
        if (slot == 53) {
            // Zurück zum Spieler-Detail
            String targetName = viewingTarget.get(caster.getUniqueId());
            if (targetName != null) {
                Player target = Bukkit.getPlayerExact(targetName);
                if (target != null) { openPlayerDetail(caster, target); return; }
            }
            openPlayers(caster);
        }
        // Alle anderen Klicks → nur anschauen, nichts nehmen (bereits gecancelt)
    }

    // ── Pause-Logik ────────────────────────────────────────────────

    private void togglePause(Player caster) {
        if (!gameManager.isGameRunning()) return;
        caster.closeInventory();

        if (gameManager.isPaused()) {
            gameManager.setPaused(false);
            Bukkit.broadcast(Component.text("§a▶ Game fortgesetzt! §8(Cast)"));
            for (World w : Bukkit.getWorlds())
                for (Entity e : w.getEntities())
                    if (e instanceof LivingEntity le) le.setAI(true);
            for (Player p : Bukkit.getOnlinePlayers())
                p.showTitle(Title.title(
                    Component.text("§aGO!"),
                    Component.text("§7Cast hat fortgesetzt")
                ));
        } else {
            gameManager.setPaused(true);
            Bukkit.broadcast(Component.text("§c⏸ Game pausiert! §8(Cast)"));
            for (World w : Bukkit.getWorlds())
                for (Entity e : w.getEntities())
                    if (e instanceof LivingEntity le) le.setAI(false);
            for (Player p : Bukkit.getOnlinePlayers())
                p.showTitle(Title.title(
                    Component.text("§cPAUSIERT"),
                    Component.text("§7Cast hat pausiert")
                ));
        }
    }

    // ══════════════════════════════════════════════════════════════
    //  HILFSMETHODEN
    // ══════════════════════════════════════════════════════════════

    private ItemStack buildProfileSkull(Player target) {
        ItemStack skull = new ItemStack(Material.PLAYER_HEAD);
        SkullMeta meta = (SkullMeta) skull.getItemMeta();
        meta.setOwningPlayer(target);

        double hp = target.getHealth();
        var attr  = target.getAttribute(Attribute.MAX_HEALTH);
        int maxHp = attr != null ? (int) attr.getValue() : 20;

        TeamData team = teamManager.getTeamByPlayer(target.getUniqueId());
        String teamStr = team != null ? "§7Team: §f" + team.getName() : "§7Kein Team";

        meta.displayName(Component.text("§e§l" + target.getName()));
        meta.lore(List.of(
            Component.text(buildHearts((int) hp, maxHp)),
            Component.text(teamStr),
            Component.text("§7World: §f" + target.getWorld().getName()),
            Component.text("§7GameMode: §f" + target.getGameMode().name())
        ));
        skull.setItemMeta(meta);
        return skull;
    }

    private ItemStack skull(Player target) {
        ItemStack skull = new ItemStack(Material.PLAYER_HEAD);
        SkullMeta meta = (SkullMeta) skull.getItemMeta();
        meta.setOwningPlayer(target);

        double hp = target.getHealth();
        var attr  = target.getAttribute(Attribute.MAX_HEALTH);
        int maxHp = attr != null ? (int) attr.getValue() : 20;
        TeamData team = teamManager.getTeamByPlayer(target.getUniqueId());

        meta.displayName(Component.text("§e§l" + target.getName()));
        meta.lore(List.of(
            Component.text(buildHearts((int) hp, maxHp)),
            Component.text(team != null ? "§7Team: §f" + team.getName() : "§7Kein Team"),
            Component.text("§7§oKlick für Details")
        ));
        skull.setItemMeta(meta);
        return skull;
    }

    private String buildHearts(int hp, int maxHp) {
        maxHp = Math.min(maxHp, 20);
        hp    = Math.max(0, Math.min(hp, maxHp));

        int fullHearts  = hp / 2;
        boolean half    = (hp % 2) == 1;
        int emptyHearts = (maxHp / 2) - fullHearts - (half ? 1 : 0);

        StringBuilder sb = new StringBuilder("§c");
        sb.append("❤".repeat(fullHearts));
        if (half) sb.append("§6❤");
        sb.append("§8");
        sb.append("❤".repeat(Math.max(0, emptyHearts)));
        sb.append("  §f").append(hp).append("§7/§f").append(maxHp).append(" HP");
        return sb.toString();
    }

    private ItemStack item(Material mat, String name, String... lore) {
        ItemStack stack = new ItemStack(mat);
        ItemMeta meta  = stack.getItemMeta();
        meta.displayName(Component.text(name));
        if (lore.length > 0) {
            List<Component> list = new ArrayList<>();
            for (String l : lore) list.add(Component.text(l));
            meta.lore(list);
        }
        stack.setItemMeta(meta);
        return stack;
    }

    private ItemStack glass() {
        return item(Material.GRAY_STAINED_GLASS_PANE, " ");
    }

    private void fill(Inventory inv) {
        ItemStack g = glass();
        for (int i = 0; i < inv.getSize(); i++)
            if (inv.getItem(i) == null) inv.setItem(i, g);
    }

    private List<Player> sortedPlayers() {
        return Bukkit.getOnlinePlayers().stream()
            .sorted(Comparator.comparing(Player::getName))
            .collect(Collectors.toList());
    }
}
