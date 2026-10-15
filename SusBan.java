package com.susban;

import org.bukkit.BanList;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.ConsoleCommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public class SusBan extends JavaPlugin implements TabExecutor {
    private static final Pattern TIME = Pattern.compile("(\\d+)([smhdw])");

    @Override
    public void onEnable() {
        saveDefaultConfig();
        getCommand("sus").setExecutor(this);
        getCommand("ban").setExecutor(this);
        if ("YourMinecraftName".equals(getConfig().getString("owner"))) {
            getLogger().warning("Set 'owner' in plugins/SusBan/config.yml to your username, or nobody can use /sus and /ban.");
        }
    }

    private boolean isOwner(CommandSender s) {
        if (s instanceof ConsoleCommandSender) return true;
        if (!(s instanceof Player p)) return false;
        String owner = getConfig().getString("owner", "");
        if (owner.isEmpty() || owner.equals("YourMinecraftName")) return false;
        return p.getName().equalsIgnoreCase(owner) || p.getUniqueId().toString().equalsIgnoreCase(owner);
    }

    /** Returns milliseconds, 0 for permanent, -1 if invalid. */
    private static long parseTime(String t) {
        if (t.equalsIgnoreCase("perm") || t.equalsIgnoreCase("permanent")) return 0;
        Matcher m = TIME.matcher(t.toLowerCase(Locale.ROOT));
        long total = 0;
        int end = 0;
        while (m.find()) {
            if (m.start() != end) return -1;
            end = m.end();
            long n = Long.parseLong(m.group(1));
            total += n * switch (m.group(2)) {
                case "s" -> 1000L;
                case "m" -> 60_000L;
                case "h" -> 3_600_000L;
                case "d" -> 86_400_000L;
                default -> 604_800_000L;
            };
        }
        return end == t.length() && total > 0 ? total : -1;
    }

    @Override
    @SuppressWarnings({"rawtypes", "unchecked", "deprecation"})
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (!isOwner(sender)) {
            // Look like the command doesn't exist for everyone else.
            sender.sendMessage(ChatColor.RED + "Unknown command. Type \"/help\" for help.");
            return true;
        }

        if (cmd.getName().equalsIgnoreCase("sus")) {
            List<String> names = Bukkit.getOnlinePlayers().stream().map(Player::getName)
                    .sorted(String.CASE_INSENSITIVE_ORDER).collect(Collectors.toList());
            sender.sendMessage(ChatColor.GOLD + "Players online (" + names.size() + "):");
            sender.sendMessage(ChatColor.YELLOW + String.join(", ", names));
            return true;
        }

        // /ban <player> <time|perm> <reason...>
        if (args.length < 3) {
            sender.sendMessage(ChatColor.RED + "Usage: /ban <player> <time|perm> <reason...>   e.g. /ban Steve 7d griefing");
            return true;
        }
        long ms = parseTime(args[1]);
        if (ms < 0) {
            sender.sendMessage(ChatColor.RED + "Bad time. Use e.g. 30m, 12h, 7d, 2w, 1d12h, or perm.");
            return true;
        }
        String reason = String.join(" ", Arrays.copyOfRange(args, 2, args.length));
        OfflinePlayer target = Bukkit.getOfflinePlayer(args[0]);
        Date expires = ms == 0 ? null : new Date(System.currentTimeMillis() + ms);

        BanList list = Bukkit.getBanList(BanList.Type.PROFILE);
        list.addBan(target.getPlayerProfile(), reason, expires, sender.getName());

        Player online = Bukkit.getPlayerExact(args[0]);
        if (online != null) {
            online.kickPlayer(ChatColor.RED + "You are banned.\n" + ChatColor.WHITE + "Reason: " + reason
                    + "\n" + (expires == null ? "Permanent" : "Until: " + expires));
        }
        sender.sendMessage(ChatColor.GREEN + "Banned " + args[0] + " (" + (ms == 0 ? "permanent" : args[1]) + "): " + reason);
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command cmd, String alias, String[] args) {
        if (!isOwner(sender) || !cmd.getName().equalsIgnoreCase("ban")) return List.of();
        if (args.length == 1) {
            String p = args[0].toLowerCase(Locale.ROOT);
            return Bukkit.getOnlinePlayers().stream().map(Player::getName)
                    .filter(n -> n.toLowerCase(Locale.ROOT).startsWith(p)).sorted(Comparator.naturalOrder()).collect(Collectors.toList());
        }
        if (args.length == 2) return new ArrayList<>(List.of("30m", "1h", "1d", "7d", "30d", "perm"));
        return List.of();
    }
}
