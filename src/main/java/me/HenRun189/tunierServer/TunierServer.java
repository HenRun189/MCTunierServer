package me.HenRun189.tunierServer;

import me.HenRun189.tunierServer.game.GameManager;
import me.HenRun189.tunierServer.team.TeamManager;
import me.HenRun189.tunierServer.score.ScoreManager;
import me.HenRun189.tunierServer.achievement.AchievementManager;
import me.HenRun189.tunierServer.commands.*;
import me.HenRun189.tunierServer.backpack.BackpackManager;
import me.HenRun189.tunierServer.listeners.MoveListener;
import me.HenRun189.tunierServer.listeners.ChatListener;
import me.HenRun189.tunierServer.listeners.LobbyListener;
import me.HenRun189.tunierServer.listeners.GUIListener;
import me.HenRun189.tunierServer.listeners.VisibilityToggleItem;

import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.command.PluginCommand;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.EventHandler;
import org.bukkit.GameMode;
import org.bukkit.Bukkit;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import org.bukkit.Sound;
import org.bukkit.entity.Player;

import org.bukkit.World;
import org.bukkit.Location;
import org.bukkit.event.EventHandler;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.entity.Player;

public class TunierServer extends JavaPlugin implements Listener {

    private static TunierServer instance;

    private GameManager gameManager;
    private TeamManager teamManager;
    private ScoreManager scoreManager;
    private AchievementManager achievementManager;
    private BackpackManager backpackManager;

    @Override
    public void onEnable() {
        instance = this;

// 1️⃣ ScoreManager erstellen + Board bauen
        scoreManager = new ScoreManager(null);
        scoreManager.setupScoreboard();

// 2️⃣ Board holen
        var board = scoreManager.getBoard();

// 3️⃣ TeamManager bekommt Board
        teamManager = new TeamManager(board);

// 4️⃣ ScoreManager NEU mit TeamManager (wichtig!)
        scoreManager = new ScoreManager(teamManager);
        scoreManager.setupScoreboard(); // 🔥 WICHTIG WIEDER DRIN!

// 5️⃣ Anzeige setzen
        scoreManager.setCurrentGame("Warten auf Spiel");
        scoreManager.updateScoreboard();


        achievementManager = new AchievementManager();
        backpackManager = new BackpackManager();

        gameManager = new GameManager(teamManager, scoreManager, achievementManager, backpackManager);

        VisibilityToggleItem.init(); // initialises the PersistentDataContainer key

        // Final update
        scoreManager.updateScoreboard();

        // Commands
        registerCommand("team", new TeamCommand(teamManager));
        registerCommand("game", new GameCommand(gameManager));
        registerCommand("skip", new SkipCommand(gameManager, teamManager));
        registerCommand("help", new HelpCommand(gameManager));
        registerCommand("score", new ScoreCommand(scoreManager));
        registerCommand("pause", new PauseCommand(gameManager));
        registerCommand("resume", new ResumeCommand(gameManager));
        registerCommand("stats", new InfoCommand(scoreManager, teamManager));
        registerCommand("allowmove", new AllowMoveCommand(gameManager));
        registerCommand("gameinfo", new GameInfoCommand());
        registerCommand("statsgui", new StatsGUICommand(scoreManager, teamManager));

        registerCommand("bp", new BackpackCommand(backpackManager, teamManager, gameManager));
        registerCommand("backpack", new BackpackCommand(backpackManager, teamManager, gameManager));
        getServer().getPluginManager().registerEvents(new LobbyListener(), this);

        // Listener
        Bukkit.getPluginManager().registerEvents(this, this);
        getServer().getPluginManager().registerEvents(new JoinListener(scoreManager, teamManager), this);
        getServer().getPluginManager().registerEvents(new MoveListener(gameManager), this);
        getServer().getPluginManager().registerEvents(new ChatListener(teamManager), this);
        getCommand("gameinfo").setTabCompleter(new GameInfoCommand());
        Bukkit.getPluginManager().registerEvents(gameManager, this);
        getServer().getPluginManager().registerEvents(new GUIListener(), this);

        getLogger().info("TunierServer gestartet!");
    }

    private void registerCommand(String name, Object command) {
        PluginCommand cmd = getCommand(name);

        if (cmd == null) {
            getLogger().warning("Command '" + name + "' fehlt in plugin.yml!");
            return;
        }

        if (command instanceof org.bukkit.command.CommandExecutor executor) {
            cmd.setExecutor(executor);
        }

        if (command instanceof org.bukkit.command.TabCompleter completer) {
            cmd.setTabCompleter(completer);
        }
    }

    public static TunierServer getInstance() {
        return instance;
    }

    @Override
    public void onDisable() {
        if (teamManager != null) {
            teamManager.saveTeams();
        }
        instance = null;
    }

    private class JoinListener implements Listener {

        private final ScoreManager scoreManager;
        private final TeamManager teamManager;

        public JoinListener(ScoreManager scoreManager, TeamManager teamManager) {
            this.scoreManager = scoreManager;
            this.teamManager = teamManager;
        }

        @EventHandler
        public void onJoin(PlayerJoinEvent e) {
            e.setJoinMessage(null);
            Player p = e.getPlayer();

            // Scoreboard + Team
            scoreManager.applyToPlayer(p);
            teamManager.applyTeamToPlayer(p);

            // 🔥 LOBBY SETTINGS (HIER REIN!)
            if (p.getWorld().getName().equalsIgnoreCase("lobby")) {
                p.setGameMode(GameMode.ADVENTURE);
                p.setAllowFlight(false);
                p.setFlying(false);
                p.setInvulnerable(true);
                p.setFoodLevel(20);
                p.setHealth(20);
            }

            // Nachrichten
            p.sendMessage(Component.text("§6§lWillkommen auf dem Minecraft Turnier Server!"));

            Component twitch = Component.text("§5von HenRun189")
                    .clickEvent(ClickEvent.openUrl("https://twitch.tv/henrun189"))
                    .hoverEvent(HoverEvent.showText(Component.text("§dKlick um auf Twitch zu gehen!")));

            p.sendMessage(twitch);

            p.playSound(p.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 0.5f, 1f);

            World lobby = Bukkit.getWorld("lobby");

            if (lobby != null) {
                p.teleport(new Location(lobby, 2, 38, -5));
            }
        }
    }

    @EventHandler
    public void onMove(PlayerMoveEvent e) {

        Player p = e.getPlayer();

        if (!p.getWorld().getName().equals("lobby")) return;

        if (!p.hasPotionEffect(PotionEffectType.SATURATION)) {
            p.addPotionEffect(new PotionEffect(PotionEffectType.SATURATION, 200, 255, true, false));
        }
    }



    public ScoreManager getScoreManager() {
        return scoreManager;
    }

    public GameManager getGameManager() {
        return gameManager;
    }
}


//GPT 00