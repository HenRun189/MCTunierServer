package me.HenRun189.tunierServer.game.modes;

import me.HenRun189.tunierServer.TunierServer;
import me.HenRun189.tunierServer.game.GameManager;
import me.HenRun189.tunierServer.score.ScoreManager;
import me.HenRun189.tunierServer.team.TeamData;
import me.HenRun189.tunierServer.team.TeamManager;

import org.bukkit.*;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.entity.ArmorStand;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.FoodLevelChangeEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.scoreboard.Team;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.title.Title;

import java.util.*;

/**
 * Hide and Seek – teamfähig (1 Spieler/Team oder mehrere).
 * Ganze Teams sind Sucher oder Verstecker. Ein Treffer nimmt nur den getroffenen
 * Spieler raus; das Team spielt weiter, solange noch jemand versteckt ist.
 */
public class HideAndSeekMode extends AbstractGameMode implements Listener {

    public static final String WORLD_NAME = "hideandseek";
    public static final String FALLBACK_WORLD = "pvp_map";

    private static final int DEFAULT_GAME_TIME = 600;
    private static final int HIDE_PHASE_SECONDS = 45;
    private static final int POINTS_PER_CATCH = 10;
    private static final int POINTS_PER_SURVIVOR = 12;
    private static final int POINTS_FULL_TEAM_SURVIVE = 8;
    private static final int POINTS_SEEKER_SWEEP = 20;
    private static final int SEEKERS_PER_PLAYERS = 5;

    private static final int COMPASS_COOLDOWN = 60;
    private static final int COMPASS_MARKER_SECONDS = 25;
    private static final int SPEED_COOLDOWN = 120;
    private static final int SPEED_DURATION = 30;
    private static final int REVEAL_COOLDOWN = 180;
    private static final int REVEAL_GLOW_SECONDS = 15;
    private static final int HIDER_GLOW_SEEKERS_SECONDS = 12;
    private static final int HIDER_INVIS_SECONDS = 8;
    private static final int HIDER_SPEED_SECONDS = 12;

    private static final int HIDER_BUFF_DURATION = HIDER_GLOW_SEEKERS_SECONDS;
    private static final int HIDER_SPEED_AMPLIFIER = 1;

    public enum Ability {
        COMPASS("compass", COMPASS_COOLDOWN),
        SPEED_BOOST("speed", SPEED_COOLDOWN),
        REVEAL_HIDERS("reveal", REVEAL_COOLDOWN),
        HIDER_GLOW_SEEKERS("hider_glow", 0),
        HIDER_INVIS("hider_invis", 0),
        HIDER_SPEED("hider_speed", 0);

        public final String id;
        public final int cooldownSeconds;
        Ability(String id, int cooldownSeconds) {
            this.id = id;
            this.cooldownSeconds = cooldownSeconds;
        }

        static Ability fromId(String id) {
            for (Ability a : values()) {
                if (a.id.equals(id)) return a;
            }
            return null;
        }
    }

    private final ScoreManager scoreManager;
    private final NamespacedKey abilityKey;

    private final Set<UUID> seekers = new HashSet<>();
    private final Set<UUID> hiders = new HashSet<>();
    private final Set<UUID> caughtHiders = new HashSet<>();
    private final Set<String> seekerTeams = new HashSet<>();
    private final Set<String> hiderTeams = new HashSet<>();
    private final Map<UUID, Integer> catchCount = new HashMap<>();
    private final Map<UUID, Map<Ability, Integer>> cooldowns = new HashMap<>();
    private final List<BukkitTask> markerTasks = new ArrayList<>();
    private final List<ArmorStand> pingMarkers = new ArrayList<>();

    private boolean hidePhase = true;
    private int hidePhaseTimer = HIDE_PHASE_SECONDS;
    private boolean seekerWon = false;
    private boolean hidersWon = false;
    private boolean pointsAwarded = false;
    private boolean ending = false;

    private BossBar bossBar;
    private World world;

    public HideAndSeekMode(TeamManager teamManager, ScoreManager scoreManager) {
        super(DEFAULT_GAME_TIME, teamManager);
        this.scoreManager = scoreManager;
        this.abilityKey = new NamespacedKey(TunierServer.getInstance(), "has_ability");
    }

    @Override
    public void start() {
        seekers.clear();
        hiders.clear();
        caughtHiders.clear();
        seekerTeams.clear();
        hiderTeams.clear();
        catchCount.clear();
        cooldowns.clear();
        hidePhase = true;
        hidePhaseTimer = HIDE_PHASE_SECONDS;
        clearMarkers();
        seekerWon = false;
        hidersWon = false;
        pointsAwarded = false;
        ending = false;

        world = resolveWorld();
        assignRolesByTeam();

        bossBar = Bukkit.createBossBar("§6Hide and Seek", BarColor.YELLOW, BarStyle.SEGMENTED_10);
        for (Player p : Bukkit.getOnlinePlayers()) bossBar.addPlayer(p);

        hideNametagsFromOtherTeams(true);
        Bukkit.getPluginManager().registerEvents(this, TunierServer.getInstance());
        super.start();
    }

    public static World resolveWorld() {
        World named = Bukkit.getWorld(WORLD_NAME);
        if (named != null) return named;
        World pvp = Bukkit.getWorld(FALLBACK_WORLD);
        if (pvp != null) return pvp;
        return Bukkit.getWorlds().isEmpty() ? null : Bukkit.getWorlds().get(0);
    }

    /**
     * Rollen pro Team: ganze Teams werden Sucher oder Verstecker.
     * Ziel: ca. 1 Sucher auf SEEKERS_PER_PLAYERS Spieler.
     * Ein-Spieler-Teams und Mehrspieler-Teams funktionieren gleich:
     * alle Online-Mitglieder eines Teams teilen sich die Rolle.
     * Gibt es nur ein Team, werden die Rollen innerhalb des Teams aufgeteilt.
     */
    private void assignRolesByTeam() {
        List<TeamGroup> groups = new ArrayList<>();
        for (TeamData team : teamManager.getTeams().values()) {
            List<Player> members = onlineMembers(team);
            if (members.isEmpty()) continue;
            groups.add(new TeamGroup(team.getName(), members));
        }
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (teamManager.getTeamByPlayer(p.getUniqueId()) == null) {
                groups.add(new TeamGroup(null, List.of(p)));
            }
        }
        Collections.shuffle(groups);

        int totalPlayers = 0;
        for (TeamGroup g : groups) totalPlayers += g.members.size();
        int targetSeekers = Math.max(1, totalPlayers / SEEKERS_PER_PLAYERS);
        int seekerPlayers = 0;

        for (int i = 0; i < groups.size(); i++) {
            TeamGroup group = groups.get(i);
            boolean last = i == groups.size() - 1;
            boolean forceHider = last && !seekers.isEmpty() && hiders.isEmpty();
            boolean makeSeekers = !forceHider && (seekers.isEmpty() || (seekerPlayers < targetSeekers && !last));

            if (makeSeekers) {
                if (group.teamName != null) seekerTeams.add(group.teamName);
                for (Player p : group.members) {
                    seekers.add(p.getUniqueId());
                    seekerPlayers++;
                }
            } else {
                if (group.teamName != null) hiderTeams.add(group.teamName);
                for (Player p : group.members) hiders.add(p.getUniqueId());
            }
        }

        // Nur ein Team / alle wurden Sucher → innerhalb aufteilen
        if (hiders.isEmpty() && seekers.size() > 1) {
            int keep = Math.max(1, seekers.size() / SEEKERS_PER_PLAYERS);
            List<UUID> all = new ArrayList<>(seekers);
            Collections.shuffle(all);
            for (int i = keep; i < all.size(); i++) {
                UUID uuid = all.get(i);
                seekers.remove(uuid);
                hiders.add(uuid);
                TeamData team = teamManager.getTeamByPlayer(uuid);
                if (team == null) continue;
                hiderTeams.add(team.getName());
                boolean teammateStillSeeker = false;
                for (UUID mate : team.getPlayers()) {
                    if (seekers.contains(mate)) {
                        teammateStillSeeker = true;
                        break;
                    }
                }
                if (!teammateStillSeeker) seekerTeams.remove(team.getName());
            }
        }
    }

    private record TeamGroup(String teamName, List<Player> members) {}

    private List<Player> onlineMembers(TeamData team) {
        List<Player> list = new ArrayList<>();
        for (UUID uuid : team.getPlayers()) {
            Player p = Bukkit.getPlayer(uuid);
            if (p != null && p.isOnline()) list.add(p);
        }
        return list;
    }

    @Override
    protected void onGameStart() {
        GameManager gm = TunierServer.getInstance().getGameManager();

        for (Player p : Bukkit.getOnlinePlayers()) {
            UUID uuid = p.getUniqueId();
            resetPlayerForRound(p);

            if (seekers.contains(uuid)) {
                setupSeeker(p);
                gm.freezePlayer(p);
            } else if (hiders.contains(uuid)) {
                setupHider(p);
            } else {
                p.setGameMode(GameMode.SPECTATOR);
            }
        }

        scatterHiders();

        broadcast("§8§m════════════════════════════════");
        broadcast("§6§lHide and Seek §7ist gestartet!");
        broadcast("§eVersteckt euch! Die Sucher starten in §c" + HIDE_PHASE_SECONDS + " Sekunden§e.");
        broadcastSeekerTeams();
        broadcast("§8§m════════════════════════════════");
    }

    private void broadcastSeekerTeams() {
        if (!seekerTeams.isEmpty()) {
            broadcast("§cSucher-Team(s): §f" + String.join("§7, §f", seekerTeams));
        } else {
            List<String> names = new ArrayList<>();
            for (UUID uuid : seekers) {
                Player p = Bukkit.getPlayer(uuid);
                if (p != null) names.add(p.getName());
            }
            if (!names.isEmpty()) broadcast("§cSucher: §f" + String.join("§7, §f", names));
        }
        if (!hiderTeams.isEmpty()) {
            broadcast("§aVerstecker-Team(s): §f" + String.join("§7, §f", hiderTeams));
        }
    }

    private void resetPlayerForRound(Player p) {
        p.getInventory().clear();
        p.getInventory().setArmorContents(new ItemStack[4]);
        p.getActivePotionEffects().forEach(eff -> p.removePotionEffect(eff.getType()));
        p.setGameMode(GameMode.ADVENTURE);
        p.setHealth(20.0);
        p.setFoodLevel(20);
        p.setSaturation(20f);
        p.setExhaustion(0f);
        p.setFireTicks(0);
        p.setInvulnerable(false);
        p.setAllowFlight(false);
        p.setFlying(false);
        p.setMaximumNoDamageTicks(20);
    }

    private void setupSeeker(Player p) {
        TeamData team = teamManager.getTeamByPlayer(p.getUniqueId());
        String teamHint = team != null && onlineMembers(team).size() > 1
                ? " §7(mit Team " + team.getName() + ")"
                : "";
        p.sendMessage("§c§lDu bist SUCHER!" + teamHint);
        p.sendMessage("§7Schwert-Treffer = Fund. Abilities per Rechtsklick:");
        p.sendMessage("§8• §bKompass §7– Standort des Nächsten (kein Tracking, 1min CD)");
        p.sendMessage("§8• §aSpeed §7– 30s Boost alle 2min");
        p.sendMessage("§8• §eGlow §7– alle Verstecker 15s sichtbar, alle 3min");
        p.showTitle(Title.title(
                Component.text("§c§lSUCHER"),
                Component.text("§7Warte " + HIDE_PHASE_SECONDS + "s, dann jagen!")
        ));
        p.playSound(p.getLocation(), Sound.ENTITY_ENDER_DRAGON_GROWL, 0.7f, 1.5f);

        p.getInventory().setItem(0, new ItemStack(Material.IRON_SWORD));
        p.getInventory().setItem(1, abilityItem(Ability.COMPASS, Material.COMPASS,
                "§b§lKompass-Ortung §7(Rechtsklick)",
                "§7Zeigt den Standort des nächsten Versteckers",
                "§7zum Zeitpunkt des Klicks – §ckehrt nicht nach.",
                "§8Cooldown: 1 Minute"));
        p.getInventory().setItem(2, abilityItem(Ability.SPEED_BOOST, Material.SUGAR,
                "§a§lSpeed-Boost §7(Rechtsklick)",
                "§7Speed II für §e30 Sekunden",
                "§8Cooldown: 2 Minuten"));
        p.getInventory().setItem(3, abilityItem(Ability.REVEAL_HIDERS, Material.GLOWSTONE_DUST,
                "§e§lVerstecker-Glow §7(Rechtsklick)",
                "§7Alle Verstecker leuchten §e15 Sekunden",
                "§8Cooldown: 3 Minuten"));
        p.addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS, HIDE_PHASE_SECONDS * 20 + 20, 0, false, false, false));

        cooldowns.put(p.getUniqueId(), new HashMap<>());
        catchCount.putIfAbsent(p.getUniqueId(), 0);
    }

    private void setupHider(Player p) {
        TeamData team = teamManager.getTeamByPlayer(p.getUniqueId());
        boolean teamPlay = team != null && onlineMembers(team).size() > 1;
        p.sendMessage("§a§lDu bist VERSTECKER!");
        if (teamPlay) {
            p.sendMessage("§7Dein ganzes Team versteckt sich. Nur wer getroffen wird, fliegt raus.");
        } else {
            p.sendMessage("§7Versteck dich – ein Schwerttreffer und du bist raus.");
        }
        p.sendMessage("§7Du darfst dein Versteck jederzeit wechseln.");
        p.sendMessage("§e3 Power-ups §7(je 1x nutzen): Sucher-Glow, Unsichtbarkeit, Sprint.");
        p.showTitle(Title.title(
                Component.text("§a§lVERSTECKER"),
                Component.text("§7Du hast " + HIDE_PHASE_SECONDS + "s Vorsprung!")
        ));
        p.playSound(p.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1f, 1.2f);
        p.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, HIDE_PHASE_SECONDS * 20, 1, false, false, false));

        p.getInventory().setItem(0, abilityItem(Ability.HIDER_GLOW_SEEKERS, Material.GLOW_INK_SAC,
                "§e§lSucher-Glow §7(1x)",
                "§7Alle Sucher leuchten §e" + HIDER_GLOW_SEEKERS_SECONDS + "s"));
        p.getInventory().setItem(1, abilityItem(Ability.HIDER_INVIS, Material.PHANTOM_MEMBRANE,
                "§d§lUnsichtbarkeit §7(1x)",
                "§7" + HIDER_INVIS_SECONDS + "s unsichtbar – Versteck wechseln"));
        p.getInventory().setItem(2, abilityItem(Ability.HIDER_SPEED, Material.FEATHER,
                "§b§lSprint §7(1x)",
                "§7Speed III für §e" + HIDER_SPEED_SECONDS + "s"));
    }

    private void scatterHiders() {
        if (world == null) return;
        Location spawn = world.getSpawnLocation();
        Random rng = new Random();
        for (UUID uuid : hiders) {
            Player p = Bukkit.getPlayer(uuid);
            if (p == null || !p.isOnline()) continue;
            int dx = rng.nextInt(31) - 15;
            int dz = rng.nextInt(31) - 15;
            if (dx == 0 && dz == 0) dx = 8;
            int x = spawn.getBlockX() + dx;
            int z = spawn.getBlockZ() + dz;
            int y = world.getHighestBlockYAt(x, z) + 1;
            Location loc = new Location(world, x + 0.5, y, z + 0.5, rng.nextFloat() * 360f, 0f);
            p.teleport(loc);
        }
    }

    private ItemStack abilityItem(Ability ability, Material material, String name, String... loreLines) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.text(name));
        List<Component> lore = new ArrayList<>();
        for (String line : loreLines) lore.add(Component.text(line));
        meta.lore(lore);
        meta.getPersistentDataContainer().set(abilityKey, PersistentDataType.STRING, ability.id);
        item.setItemMeta(meta);
        return item;
    }

    @Override
    protected void onGameTick() {}

    @Override
    protected void onSecond() {
        keepPlayersAlive();

        if (hidePhase) {
            hidePhaseTimer--;
            if (hidePhaseTimer <= 0) {
                hidePhase = false;
                releaseSeekers();
            }
            return;
        }

        for (Map<Ability, Integer> map : cooldowns.values()) {
            for (Ability ability : new ArrayList<>(map.keySet())) {
                int left = map.get(ability);
                if (left > 0) map.put(ability, left - 1);
            }
        }

        checkNoHidersLeft();
    }

    private void keepPlayersAlive() {
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (!seekers.contains(p.getUniqueId()) && !hiders.contains(p.getUniqueId())) continue;
            if (p.getGameMode() == GameMode.SPECTATOR) continue;
            p.setFoodLevel(20);
            p.setSaturation(20f);
            p.setExhaustion(0f);
            if (p.getHealth() > 0 && p.getHealth() < 20.0) p.setHealth(20.0);
            p.setFireTicks(0);
            p.setRemainingAir(p.getMaximumAir());
        }
    }

    private void clearMarkers() {
        for (BukkitTask task : markerTasks) {
            if (task != null && !task.isCancelled()) task.cancel();
        }
        markerTasks.clear();

        for (ArmorStand stand : pingMarkers) {
            if (stand != null && !stand.isDead()) stand.remove();
        }
        pingMarkers.clear();
    }

    private String cdLabel(UUID uuid, Ability ability, String shortTag) {
        Map<Ability, Integer> map = cooldowns.get(uuid);
        int left = (map != null) ? map.getOrDefault(ability, 0) : 0;
        if (left <= 0) {
            return "§a" + shortTag + "✓";
        }
        return "§7" + shortTag + ":§c" + left + "s";
    }

    @Override
    protected void updateActionbar() {
        int alive = aliveHiderCount();
        int minutes = Math.max(time, 0) / 60;
        int seconds = Math.max(time, 0) % 60;
        String clock = String.format("%02d:%02d", minutes, seconds);

        if (bossBar != null) {
            if (hidePhase) {
                bossBar.setTitle("§eVerstecken §8| §cSucher in " + hidePhaseTimer + "s §8| §a" + alive + " versteckt");
                bossBar.setColor(BarColor.YELLOW);
                bossBar.setProgress(Math.min(1.0, Math.max(0.0, hidePhaseTimer / (double) HIDE_PHASE_SECONDS)));
            } else {
                bossBar.setTitle("§6Hide and Seek §8| §e" + clock + " §8| §a" + alive + " versteckt");
                bossBar.setColor(alive <= 2 ? BarColor.RED : BarColor.GREEN);
                bossBar.setProgress(Math.min(1.0, Math.max(0.0, time / (double) DEFAULT_GAME_TIME)));
            }
        }

        for (Player p : Bukkit.getOnlinePlayers()) {
            UUID uuid = p.getUniqueId();
            if (hidePhase) {
                if (seekers.contains(uuid)) {
                    p.sendActionBar(Component.text("§c⏳ Jagd startet in " + hidePhaseTimer + "s"));
                } else if (isAliveHider(uuid)) {
                    p.sendActionBar(Component.text("§a⏳ Noch " + hidePhaseTimer + "s zum Verstecken!"));
                }
                continue;
            }

            if (seekers.contains(uuid)) {
                p.sendActionBar(Component.text("§cSucher §8| §e" + clock + " §8| §a" + alive + " übrig"
                        + " §8| " + cdLabel(uuid, Ability.COMPASS, "K")
                        + " " + cdLabel(uuid, Ability.SPEED_BOOST, "S")
                        + " " + cdLabel(uuid, Ability.REVEAL_HIDERS, "G")));
            } else if (isAliveHider(uuid)) {
                p.sendActionBar(Component.text("§aVersteckt §8| §e" + clock + " §8| §a" + alive + " übrig §8| §7Versteck jederzeit wechseln"));
            } else if (caughtHiders.contains(uuid)) {
                p.sendActionBar(Component.text("§7Gefunden §8| §e" + clock + " §8| §a" + alive + " noch versteckt"));
            }
        }
    }

    private void releaseSeekers() {
        GameManager gm = TunierServer.getInstance().getGameManager();
        for (UUID uuid : seekers) {
            Player p = Bukkit.getPlayer(uuid);
            if (p == null || !p.isOnline()) continue;
            gm.unfreezePlayer(p);
            p.removePotionEffect(PotionEffectType.BLINDNESS);
            p.removePotionEffect(PotionEffectType.SLOWNESS);
            p.showTitle(Title.title(
                    Component.text("§c§lLOS!"),
                    Component.text("§7Die Jagd beginnt!")
            ));
            p.playSound(p.getLocation(), Sound.ENTITY_WITHER_SPAWN, 0.6f, 1.6f);
        }
        for (UUID uuid : hiders) {
            if (caughtHiders.contains(uuid)) continue;
            Player p = Bukkit.getPlayer(uuid);
            if (p == null || !p.isOnline()) continue;
            p.removePotionEffect(PotionEffectType.SPEED);
            p.showTitle(Title.title(
                    Component.text("§c§lSIE KOMMEN"),
                    Component.text("§7Die Sucher sind los!")
            ));
        }
        broadcast("§c§lDie Sucher wurden losgelassen!");
    }

    private void applyHiderBuffs() {
        int durationTicks = HIDER_BUFF_DURATION * 20;
        for (UUID uuid : hiders) {
            if (caughtHiders.contains(uuid)) continue;
            Player p = Bukkit.getPlayer(uuid);
            if (p == null || !p.isOnline()) continue;
            p.addPotionEffect(new PotionEffect(PotionEffectType.GLOWING, durationTicks, 0, false, false, true));
            p.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, durationTicks, HIDER_SPEED_AMPLIFIER, false, false, false));
            p.sendMessage("§b✦ Kurz sichtbar! (Glow) – lauf weiter!");
            p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_CHIME, 0.8f, 1.4f);
        }
        for (UUID uuid : seekers) {
            Player p = Bukkit.getPlayer(uuid);
            if (p != null && p.isOnline()) {
                p.sendMessage("§eEin Verstecker leuchtet kurz auf!");
                p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 0.7f, 1.8f);
            }
        }
    }

    @EventHandler
    public void onCompassUse(PlayerInteractEvent e) {
        if (e.getHand() != EquipmentSlot.HAND) return;
        if (e.getAction() != Action.RIGHT_CLICK_AIR && e.getAction() != Action.RIGHT_CLICK_BLOCK) return;

        ItemStack item = e.getItem();
        if (item == null || item.getType() != Material.COMPASS) return;
        if (item.getItemMeta() == null) return;
        String abilityId = item.getItemMeta().getPersistentDataContainer().get(abilityKey, PersistentDataType.STRING);
        if (!Ability.COMPASS.id.equals(abilityId)) return;

        Player p = e.getPlayer();
        if (!seekers.contains(p.getUniqueId())) return;

        e.setCancelled(true);
        useCompassPing(p);
    }

    public boolean useCompassPing(Player seeker) {
        if (hidePhase) {
            seeker.sendMessage("§cNoch nicht! Warte bis die Jagd beginnt.");
            return false;
        }

        Map<Ability, Integer> map = cooldowns.computeIfAbsent(seeker.getUniqueId(), k -> new HashMap<>());
        int left = map.getOrDefault(Ability.COMPASS, 0);
        if (left > 0) {
            seeker.sendMessage("§cOrtung noch §e" + left + "s §caufladen!");
            seeker.playSound(seeker.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 1f, 0.6f);
            return false;
        }

        Player target = findNearestHider(seeker);
        if (target == null) {
            seeker.sendMessage("§7Keine Verstecker mehr zum Orten.");
            return false;
        }

        map.put(Ability.COMPASS, Ability.COMPASS.cooldownSeconds);

        Location from = seeker.getLocation();
        Location to = target.getLocation();
        double dx = to.getX() - from.getX();
        double dz = to.getZ() - from.getZ();
        double dist = Math.sqrt(from.distanceSquared(to));
        double angle = Math.toDegrees(Math.atan2(-dx, dz));
        String direction = angleToCompassDirection(angle);
        String distLabel = dist < 25 ? "§asehr nah" : dist < 60 ? "§enah" : dist < 120 ? "§6mittel" : "§cweit weg";

        seeker.setCompassTarget(to);
        seeker.sendMessage("§b§l🧭 Ortung! §7Richtung §e" + direction + "§7, Distanz: " + distLabel);
        seeker.playSound(seeker.getLocation(), Sound.BLOCK_BEACON_ACTIVATE, 1f, 1.2f);

        target.sendMessage("§c§l⚠ Du wurdest grob geortet!");
        target.showTitle(Title.title(
                Component.text("§c⚠ GEORTET"),
                Component.text("§7Beweg dich, der Sucher hat eine Spur!")
        ));
        target.playSound(target.getLocation(), Sound.ENTITY_ENDERMAN_STARE, 0.8f, 1f);
        return true;
    }

    private Player findNearestHider(Player seeker) {
        Player nearest = null;
        double bestDist = Double.MAX_VALUE;
        for (UUID uuid : hiders) {
            if (caughtHiders.contains(uuid)) continue;
            Player p = Bukkit.getPlayer(uuid);
            if (p == null || !p.isOnline()) continue;
            if (!p.getWorld().equals(seeker.getWorld())) continue;
            double dist = p.getLocation().distanceSquared(seeker.getLocation());
            if (dist < bestDist) {
                bestDist = dist;
                nearest = p;
            }
        }
        return nearest;
    }

    private String angleToCompassDirection(double angle) {
        if (angle < 0) angle += 360;
        String[] dirs = {"Norden", "Nordosten", "Osten", "Südosten", "Süden", "Südwesten", "Westen", "Nordwesten"};
        int index = (int) Math.round(angle / 45.0) % 8;
        return dirs[index];
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onHit(EntityDamageByEntityEvent e) {
        if (!(e.getDamager() instanceof Player damager)) return;
        if (!(e.getEntity() instanceof Player victim)) return;

        if (hidePhase) {
            e.setCancelled(true);
            return;
        }

        boolean damagerSeeker = seekers.contains(damager.getUniqueId());
        boolean victimHider = isAliveHider(victim.getUniqueId());

        if (seekers.contains(damager.getUniqueId()) && seekers.contains(victim.getUniqueId())) {
            e.setCancelled(true);
            return;
        }
        if (hiders.contains(damager.getUniqueId()) && hiders.contains(victim.getUniqueId())) {
            e.setCancelled(true);
            return;
        }

        if (damagerSeeker && victimHider) {
            e.setCancelled(true);
            catchHider(damager, victim);
            return;
        }

        e.setCancelled(true);
    }

    @EventHandler
    public void onAnyDamage(EntityDamageEvent e) {
        if (!(e.getEntity() instanceof Player p)) return;
        if (!seekers.contains(p.getUniqueId()) && !hiders.contains(p.getUniqueId())) return;
        if (e.getCause() == EntityDamageEvent.DamageCause.VOID) return;
        if (e instanceof EntityDamageByEntityEvent) return;
        e.setCancelled(true);
    }

    @EventHandler
    public void onFood(FoodLevelChangeEvent e) {
        if (!(e.getEntity() instanceof Player p)) return;
        if (seekers.contains(p.getUniqueId()) || hiders.contains(p.getUniqueId())) {
            e.setCancelled(true);
            e.setFoodLevel(20);
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        UUID uuid = e.getPlayer().getUniqueId();
        if (isAliveHider(uuid)) {
            caughtHiders.add(uuid);
            broadcast("§c" + e.getPlayer().getName() + " §7hat verlassen und zählt als gefunden.");
            checkNoHidersLeft();
        }
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent e) {
        if (ending) return;
        Player p = e.getPlayer();
        if (seekers.contains(p.getUniqueId()) || isAliveHider(p.getUniqueId())) return;
        p.setGameMode(GameMode.SPECTATOR);
        if (bossBar != null) bossBar.addPlayer(p);
    }

    private void catchHider(Player seeker, Player victim) {
        if (!caughtHiders.add(victim.getUniqueId())) return;

        catchCount.merge(seeker.getUniqueId(), 1, Integer::sum);

        TeamData seekerTeam = teamManager.getTeamByPlayer(seeker.getUniqueId());
        if (seekerTeam != null) {
            scoreManager.addPoints(seekerTeam.getName(), POINTS_PER_CATCH);
        }

        victim.setGameMode(GameMode.SPECTATOR);
        victim.getInventory().clear();
        victim.sendMessage("§c§lDu wurdest gefunden!");
        victim.showTitle(Title.title(
                Component.text("§c§lGEFUNDEN"),
                Component.text("§7Dein Team spielt weiter, falls jemand noch versteckt ist.")
        ));
        victim.playSound(victim.getLocation(), Sound.ENTITY_ELDER_GUARDIAN_CURSE, 0.7f, 1.2f);

        TeamData victimTeam = teamManager.getTeamByPlayer(victim.getUniqueId());
        String teamTag = victimTeam != null ? " §8[" + victimTeam.getName() + "]" : "";
        int left = aliveHiderCount();
        broadcast("§c§l☠ " + victim.getName() + teamTag + " §7wurde von §c" + seeker.getName() + " §7gefunden! §8(§a" + left + " §7übrig)");
        seeker.playSound(seeker.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1f, 1.8f);

        if (victimTeam != null && !teamHasAliveHider(victimTeam)) {
            broadcast("§cTeam §f" + victimTeam.getName() + " §cist komplett gefunden!");
        }

        checkNoHidersLeft();
    }

    private boolean teamHasAliveHider(TeamData team) {
        for (UUID uuid : team.getPlayers()) {
            if (isAliveHider(uuid)) return true;
        }
        return false;
    }

    private boolean isAliveHider(UUID uuid) {
        return hiders.contains(uuid) && !caughtHiders.contains(uuid);
    }

    private int aliveHiderCount() {
        int n = 0;
        for (UUID uuid : hiders) {
            if (!caughtHiders.contains(uuid)) n++;
        }
        return n;
    }

    private void checkNoHidersLeft() {
        if (ending || seekerWon || hidersWon) return;
        if (hiders.isEmpty()) return;
        if (aliveHiderCount() > 0) return;

        seekerWon = true;
        ending = true;
        broadcast("§c§lAlle Verstecker gefunden! Die Sucher gewinnen!");
        TunierServer.getInstance().getGameManager().stopGame();
    }

    @Override
    protected boolean skipDefaultEndTitle() {
        return true;
    }

    @Override
    public void stop() {
        if (!seekerWon) hidersWon = true;
        awardEndPoints();
        showEndScreen();

        GameManager gm = TunierServer.getInstance().getGameManager();
        for (UUID uuid : seekers) {
            Player p = Bukkit.getPlayer(uuid);
            if (p != null) gm.unfreezePlayer(p);
        }

        hideNametagsFromOtherTeams(false);
        if (bossBar != null) {
            bossBar.removeAll();
            bossBar = null;
        }

        super.stop();
        HandlerList.unregisterAll(this);

        seekers.clear();
        hiders.clear();
        caughtHiders.clear();
        cooldowns.clear();
    }

    private void awardEndPoints() {
        if (pointsAwarded) return;
        pointsAwarded = true;

        if (seekerWon) {
            for (String teamName : seekerTeams) {
                scoreManager.addPoints(teamName, POINTS_SEEKER_SWEEP);
            }
        }

        Set<String> awardedFullBonus = new HashSet<>();
        for (UUID uuid : hiders) {
            if (caughtHiders.contains(uuid)) continue;
            TeamData team = teamManager.getTeamByPlayer(uuid);
            if (team == null) continue;
            scoreManager.addPoints(team.getName(), POINTS_PER_SURVIVOR);

            if (awardedFullBonus.add(team.getName()) && allTeamHidersSurvived(team)) {
                scoreManager.addPoints(team.getName(), POINTS_FULL_TEAM_SURVIVE);
            }
        }
    }

    private boolean allTeamHidersSurvived(TeamData team) {
        boolean any = false;
        for (UUID uuid : team.getPlayers()) {
            if (!hiders.contains(uuid)) continue;
            any = true;
            if (caughtHiders.contains(uuid)) return false;
        }
        return any;
    }

    private void showEndScreen() {
        Bukkit.broadcast(Component.text(" "));
        Bukkit.broadcast(Component.text("§8§m════════════════════════════════"));
        Bukkit.broadcast(Component.text("  §6§lHIDE AND SEEK ENDE"));
        Bukkit.broadcast(Component.text("§8§m════════════════════════════════"));

        if (seekerWon) {
            Bukkit.broadcast(Component.text("§c§l🏆 Die Sucher haben gewonnen!"));
        } else {
            Bukkit.broadcast(Component.text("§a§l🏆 Die Verstecker haben gewonnen! §7(Zeit)"));
        }

        Bukkit.broadcast(Component.text(" "));
        Bukkit.broadcast(Component.text("§e§l📊 Team Ranking:"));
        List<TeamData> ranking = getRanking();
        int rank = 1;
        for (TeamData team : ranking) {
            String medal = switch (rank) {
                case 1 -> "§6§l🥇";
                case 2 -> "§7§l🥈";
                case 3 -> "§c§l🥉";
                default -> "§8  #" + rank;
            };
            String role = seekerTeams.contains(team.getName()) ? "§cSucher" : hiderTeams.contains(team.getName()) ? "§aVerstecker" : "§7–";
            Bukkit.broadcast(Component.text(medal + " §f" + team.getName() + " §8- §a" + getPoints(team.getName()) + " Punkte §8| " + role));
            rank++;
        }

        Bukkit.broadcast(Component.text(" "));
        Bukkit.broadcast(Component.text("§c§lSucher:"));
        for (UUID uuid : seekers) {
            Player p = Bukkit.getPlayer(uuid);
            String name = p != null ? p.getName() : "Unbekannt";
            int catches = catchCount.getOrDefault(uuid, 0);
            TeamData t = teamManager.getTeamByPlayer(uuid);
            String tTag = t != null ? " §8[" + t.getName() + "]" : "";
            Bukkit.broadcast(Component.text("§7➤ §c" + name + tTag + " §8| §e" + catches + " Funde"));
        }

        Bukkit.broadcast(Component.text(" "));
        Bukkit.broadcast(Component.text("§a§lVerstecker:"));
        for (UUID uuid : hiders) {
            Player p = Bukkit.getPlayer(uuid);
            String name = p != null ? p.getName() : "Unbekannt";
            boolean caught = caughtHiders.contains(uuid);
            String status = caught ? "§c✘ gefunden" : "§a✔ überlebt";
            TeamData t = teamManager.getTeamByPlayer(uuid);
            String tTag = t != null ? " §8[" + t.getName() + "]" : "";
            Bukkit.broadcast(Component.text("§7➤ §f" + name + tTag + " §8- " + status));
        }

        Bukkit.broadcast(Component.text(" "));
        Bukkit.broadcast(Component.text("§8§m════════════════════════════════"));

        for (Player p : Bukkit.getOnlinePlayers()) {
            boolean playerWon;
            if (seekers.contains(p.getUniqueId())) {
                playerWon = seekerWon;
            } else if (isAliveHider(p.getUniqueId()) || (hiders.contains(p.getUniqueId()) && hidersWon && !caughtHiders.contains(p.getUniqueId()))) {
                playerWon = hidersWon && !caughtHiders.contains(p.getUniqueId());
            } else {
                playerWon = seekerWon && seekers.contains(p.getUniqueId());
            }

            p.showTitle(Title.title(
                    playerWon ? Component.text("§a§lGEWONNEN") : Component.text("§c§lVERLOREN"),
                    seekerWon
                            ? Component.text("§7Die Sucher haben gewonnen!")
                            : Component.text("§7Die Verstecker haben gewonnen!")
            ));
            p.playSound(p.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1f, 1f);
        }
    }

    private void hideNametagsFromOtherTeams(boolean hide) {
        if (teamManager.getBoard() == null) return;
        Team.OptionStatus status = hide ? Team.OptionStatus.FOR_OTHER_TEAMS : Team.OptionStatus.ALWAYS;
        for (Team team : teamManager.getBoard().getTeams()) {
            team.setOption(Team.Option.NAME_TAG_VISIBILITY, status);
        }
    }

    @Override
    protected List<TeamData> getRanking() {
        List<TeamData> ranking = new ArrayList<>(teamManager.getTeams().values());
        ranking.sort((a, b) -> Integer.compare(
                scoreManager.getPoints(b.getName()),
                scoreManager.getPoints(a.getName())
        ));
        return ranking;
    }

    @Override
    protected int getPoints(String teamName) {
        return scoreManager.getPoints(teamName);
    }

    @Override
    public void handleEvent(Event event) {}

    public boolean isSeeker(Player p) {
        return seekers.contains(p.getUniqueId());
    }

    public boolean isHider(Player p) {
        return hiders.contains(p.getUniqueId());
    }

    public boolean isCaught(Player p) {
        return caughtHiders.contains(p.getUniqueId());
    }
}
