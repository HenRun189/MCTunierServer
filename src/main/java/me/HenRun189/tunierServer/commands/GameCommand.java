package me.HenRun189.tunierServer.commands;

import me.HenRun189.tunierServer.game.GameManager;

import org.bukkit.entity.Player;

import org.bukkit.command.*;

import java.util.*;

public class GameCommand implements CommandExecutor, TabCompleter {

    private final GameManager manager;

    private static final List<String> SUB_COMMANDS = List.of("start", "stop", "reset", "softreset");
    private static final List<String> MODES = List.of("achievement", "jumpandrun", "pvp", "itemcollector", "spleefwindcharge");

    public GameCommand(GameManager manager) {
        this.manager = manager;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {

        if (!(sender instanceof ConsoleCommandSender) && !sender.isOp()) {
            sender.sendMessage("§cKeine Rechte!");
            return true;
        }

        if (args.length == 0) {

            sender.sendMessage("§6§lGame Commands:");

            if (manager.isGameRunning() && sender instanceof Player p) {

                String adv = manager.getCurrentAchievement(p);

                if (adv != null) {
                    sender.sendMessage("§7Aktuelles Ziel:");
                    sender.sendMessage("§e" + manager.getAchievementManager().getDisplayName(adv));
                }
            }

            sender.sendMessage("§e/game start <modus>");
            sender.sendMessage("§e/game stop");
            sender.sendMessage("§e/game reset");

            return true;
        }

        switch (args[0].toLowerCase(Locale.ROOT)) {

            case "start" -> {
                if (args.length < 2) {
                    sender.sendMessage("§cBitte Modus angeben!");
                    return true;
                }

                String mode = args[1].toLowerCase(Locale.ROOT);

                if (!MODES.contains(mode)) {
                    sender.sendMessage("§cUnbekannter Modus!");
                    return true;
                }

                manager.startGameFlow(mode);
                //sender.sendMessage("§aGame gestartet: §e" + mode);
            }

            case "stop" -> {
                manager.stopGame();
                sender.sendMessage("§cGame gestoppt!");
            }

            case "reset" -> {
                manager.resetGame();
                sender.sendMessage("§eAlles wurde zurückgesetzt!");
            }

            case "softreset" -> {
                manager.softReset();
                sender.sendMessage("§eSoft Reset ausgeführt!");
            }

            default -> sender.sendMessage("§cUnbekannter Befehl!");
        }

        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command cmd, String label, String[] args) {

        if (!sender.isOp()) return Collections.emptyList();

        if (args.length == 1) {
            return filter(SUB_COMMANDS, args[0]);
        }

        if (args.length == 2 && args[0].equalsIgnoreCase("start")) {
            return filter(MODES, args[1]);
        }

        return Collections.emptyList();
    }

    // 🔥 bessere TabComplete UX (filtert nach Eingabe)
    private List<String> filter(List<String> list, String input) {
        String lower = input.toLowerCase(Locale.ROOT);
        List<String> result = new ArrayList<>();

        for (String s : list) {
            if (s.toLowerCase(Locale.ROOT).startsWith(lower)) {
                result.add(s);
            }
        }

        return result;
    }
}

//GPT 12:30
//hinzugefügt /help mit gPT 12:45