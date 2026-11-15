package fr.azurium.vote;

import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

public final class VoteCommand implements CommandExecutor, TabCompleter {

    private static final List<String> SUBS = List.of("reload", "addvote", "setparty", "forceparty", "resetmonth", "info");

    private final AzuriumVote plugin;

    public VoteCommand(AzuriumVote plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        Messages msg = plugin.messages();

        if (args.length == 0 || !SUBS.contains(args[0].toLowerCase(Locale.ROOT))) {
            if (!(sender instanceof Player p)) {
                msg.send(sender, "player-only", null);
                return true;
            }
            if (!p.hasPermission("azuriumvote.use")) {
                msg.send(p, "no-permission", null);
                return true;
            }
            plugin.menus().open(p, "main");
            return true;
        }

        if (!sender.hasPermission("azuriumvote.admin")) {
            msg.send(sender, "no-permission", null);
            return true;
        }

        String sub = args[0].toLowerCase(Locale.ROOT);
        VoteService svc = plugin.service();
        DataManager data = plugin.data();

        switch (sub) {
            case "reload": {
                plugin.reloadAll();
                msg.send(sender, "reload", null);
                return true;
            }
            case "addvote": {
                if (args.length < 2) {
                    msg.send(sender, "usage-admin", null);
                    return true;
                }
                OfflinePlayer target = resolve(args[1]);
                if (target == null) {
                    msg.send(sender, "unknown-player", Text.ph("%target%", args[1]));
                    plugin.getLogger().warning("Vote ignoré : joueur inconnu '" + args[1] + "'");
                    return true;
                }
                int amount = 1;
                if (args.length >= 3) {
                    try {
                        amount = Integer.parseInt(args[2]);
                    } catch (NumberFormatException e) {
                        msg.send(sender, "usage-admin", null);
                        return true;
                    }
                }
                if (amount < 1 || amount > 1000) {
                    msg.send(sender, "usage-admin", null);
                    return true;
                }
                svc.registerVote(target, amount);
                msg.send(sender, "admin-vote-added", Text.ph(
                        "%target%", String.valueOf(target.getName()), "%amount%", String.valueOf(amount)));
                return true;
            }
            case "setparty": {
                if (args.length < 2) {
                    msg.send(sender, "usage-admin", null);
                    return true;
                }
                try {
                    data.setPartyCurrent(Integer.parseInt(args[1]));
                } catch (NumberFormatException e) {
                    msg.send(sender, "usage-admin", null);
                    return true;
                }
                svc.checkParty();
                msg.send(sender, "admin-party-set", svc.placeholders(null));
                return true;
            }
            case "forceparty": {
                svc.runParty();
                msg.send(sender, "admin-party-forced", svc.placeholders(null));
                return true;
            }
            case "resetmonth": {
                if (args.length < 2) {
                    msg.send(sender, "usage-admin", null);
                    return true;
                }
                if (args[1].equalsIgnoreCase("all")) {
                    for (DataManager.PlayerData d : data.all()) {
                        d.monthVotes = 0;
                        d.claimed.clear();
                    }
                    data.markDirty();
                    msg.send(sender, "admin-reset-all", null);
                    return true;
                }
                OfflinePlayer t = resolve(args[1]);
                if (t == null) {
                    msg.send(sender, "unknown-player", Text.ph("%target%", args[1]));
                    return true;
                }
                DataManager.PlayerData d = data.get(t.getUniqueId(), t.getName());
                d.monthVotes = 0;
                d.claimed.clear();
                data.markDirty();
                msg.send(sender, "admin-reset-player", Text.ph("%target%", String.valueOf(t.getName())));
                return true;
            }
            case "info": {
                if (args.length < 2) {
                    msg.send(sender, "usage-admin", null);
                    return true;
                }
                OfflinePlayer t = resolve(args[1]);
                if (t == null) {
                    msg.send(sender, "unknown-player", Text.ph("%target%", args[1]));
                    return true;
                }
                Map<String, String> ph = svc.placeholders(t);
                ph.put("%target%", String.valueOf(t.getName()));
                msg.send(sender, "admin-info", ph);
                return true;
            }
            default:
                return true;
        }
    }

    /** Joueur connecté > joueur connu du plugin > UUID > cache du serveur > (option) tout nom. */
    private OfflinePlayer resolve(String arg) {
        Player on = Bukkit.getPlayerExact(arg);
        if (on != null) return on;

        UUID known = plugin.data().findByName(arg);
        if (known != null) return Bukkit.getOfflinePlayer(known);

        try {
            return Bukkit.getOfflinePlayer(UUID.fromString(arg));
        } catch (IllegalArgumentException ignored) {
        }

        OfflinePlayer cached = Bukkit.getOfflinePlayerIfCached(arg);
        if (cached != null) return cached;

        if (plugin.configs().get(Configs.CONFIG).getBoolean("vote.allow-unknown-players", false)) {
            return Bukkit.getOfflinePlayer(arg);
        }
        return null;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> out = new ArrayList<>();
        if (!sender.hasPermission("azuriumvote.admin")) return out;
        if (args.length == 1) {
            for (String s : SUBS) if (s.startsWith(args[0].toLowerCase(Locale.ROOT))) out.add(s);
        } else if (args.length == 2) {
            String sub = args[0].toLowerCase(Locale.ROOT);
            if (sub.equals("addvote") || sub.equals("info") || sub.equals("resetmonth")) {
                for (Player p : Bukkit.getOnlinePlayers()) {
                    if (p.getName().toLowerCase(Locale.ROOT).startsWith(args[1].toLowerCase(Locale.ROOT))) out.add(p.getName());
                }
                if (sub.equals("resetmonth") && "all".startsWith(args[1].toLowerCase(Locale.ROOT))) out.add("all");
            }
        }
        return out;
    }
}
