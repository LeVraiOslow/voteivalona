package fr.azurium.vote;

import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Logique métier : votes, récompenses en attente, paliers, VoteParty. */
public final class VoteService {

    private final AzuriumVote plugin;

    public VoteService(AzuriumVote plugin) {
        this.plugin = plugin;
    }

    private YamlConfiguration cfg() {
        return plugin.configs().get(Configs.CONFIG);
    }

    private DataManager data() {
        return plugin.data();
    }

    public void sound(Player p, String key) {
        Text.playSound(p, cfg().getString("sounds." + key, ""));
    }

    public int partyRequired() {
        return Math.max(1, cfg().getInt("voteparty.votes-required", 200));
    }

    // ------------------------------------------------------------------ paliers

    public List<Integer> palierList() {
        ConfigurationSection s = plugin.configs().get(Configs.R_PALIERS).getConfigurationSection("paliers");
        List<Integer> out = new ArrayList<>();
        if (s != null) {
            for (String k : s.getKeys(false)) {
                try {
                    out.add(Integer.parseInt(k));
                } catch (NumberFormatException ignored) {
                }
            }
        }
        Collections.sort(out);
        return out;
    }

    public List<String> palierDescription(int palier) {
        return plugin.configs().get(Configs.R_PALIERS).getStringList("paliers." + palier + ".description");
    }

    public String state(DataManager.PlayerData d, int palier) {
        if (d.claimed.contains(palier)) return "claimed";
        return d.monthVotes >= palier ? "claimable" : "locked";
    }

    public int claimableCount(DataManager.PlayerData d) {
        int n = 0;
        for (int p : palierList()) if (state(d, p).equals("claimable")) n++;
        return n;
    }

    // ------------------------------------------------------------------ placeholders

    public Map<String, String> placeholders(OfflinePlayer p) {
        Map<String, String> m = new HashMap<>();
        m.put("%prefix%", plugin.messages().prefix());
        m.put("%url%", cfg().getString("vote.url", "https://ivalona.fr/vote"));

        int cur = data().partyCurrent();
        int req = partyRequired();
        m.put("%current%", String.valueOf(cur));
        m.put("%required%", String.valueOf(req));
        m.put("%remaining%", String.valueOf(Math.max(0, req - cur)));
        m.put("%percent%", String.valueOf(Math.min(100, cur * 100 / req)));
        m.put("%bar%", bar(cur, req));
        m.put("%server_total%", String.valueOf(data().serverTotal()));
        m.put("%party_count%", String.valueOf(data().partyCount()));
        m.put("%month%", monthLabel());

        if (p != null) {
            DataManager.PlayerData d = data().get(p.getUniqueId(), p.getName());
            m.put("%player%", d.name.isEmpty() ? String.valueOf(p.getName()) : d.name);
            m.put("%votes%", String.valueOf(d.monthVotes));
            m.put("%total_votes%", String.valueOf(d.totalVotes));
            m.put("%pending%", String.valueOf(d.pending));
            m.put("%claimable%", String.valueOf(claimableCount(d)));
        }
        return m;
    }

    private String monthLabel() {
        try {
            Locale loc = Locale.forLanguageTag(cfg().getString("locale", "fr"));
            DateTimeFormatter f = DateTimeFormatter.ofPattern(cfg().getString("month-format", "MMMM yyyy"), loc);
            return YearMonth.now(plugin.zone()).format(f);
        } catch (Exception e) {
            return data().monthKey();
        }
    }

    private String bar(int cur, int req) {
        int len = Math.max(1, cfg().getInt("voteparty.bar.length", 20));
        int filled = (int) Math.min(len, Math.round((double) cur / req * len));
        String fc = cfg().getString("voteparty.bar.filled-color", "&a");
        String ec = cfg().getString("voteparty.bar.empty-color", "&7");
        String fch = cfg().getString("voteparty.bar.filled-char", "█");
        String ech = cfg().getString("voteparty.bar.empty-char", "█");
        StringBuilder sb = new StringBuilder(fc);
        sb.append(fch.repeat(filled));
        sb.append(ec);
        sb.append(ech.repeat(len - filled));
        return sb.toString();
    }

    // ------------------------------------------------------------------ votes

    /** Enregistre un ou plusieurs votes (appelé par AzLink via /vote addvote <joueur>). */
    public void registerVote(OfflinePlayer target, int amount) {
        DataManager.PlayerData d = data().get(target.getUniqueId(), target.getName());
        plugin.getLogger().info("Vote enregistré : +" + amount + " pour " + target.getName()
                + " (" + (target.isOnline() ? "connecté" : "déconnecté") + ")");
        int before = d.monthVotes;
        d.monthVotes += amount;
        d.totalVotes += amount;
        data().addServerVotes(amount);

        Player online = target.getPlayer();
        if (online != null && cfg().getBoolean("vote.reward-online", true)) {
            Map<String, String> ph = placeholders(online);
            ph.put("%amount%", String.valueOf(amount));
            Rewards.give(plugin, online, plugin.configs().get(Configs.R_VOTE), ph, amount);
        } else {
            d.pending += amount;
        }

        if (cfg().getBoolean("vote.broadcast", true)) {
            Map<String, String> ph = placeholders(target);
            ph.put("%amount%", String.valueOf(amount));
            plugin.messages().broadcast("vote-broadcast", ph);
        }

        if (online != null) {
            for (int palier : palierList()) {
                if (before < palier && d.monthVotes >= palier && !d.claimed.contains(palier)) {
                    Map<String, String> ph = placeholders(online);
                    ph.put("%palier%", String.valueOf(palier));
                    plugin.messages().send(online, "palier-available", ph);
                }
            }
        }

        data().markDirty();
        notifyVote(target, amount);
        checkParty();
    }

    /** Prévient les autres plugins (event AzuriumVoteEvent) et exécute vote.on-vote-commands. */
    private void notifyVote(OfflinePlayer target, int amount) {
        Runnable r = () -> {
            Bukkit.getPluginManager().callEvent(new AzuriumVoteEvent(target, amount));
            String name = target.getName();
            if (name == null) return;
            boolean onlyOnline = cfg().getBoolean("vote.on-vote-commands-only-online", true);
            if (onlyOnline && !target.isOnline()) return;
            for (String cmd : cfg().getStringList("vote.on-vote-commands")) {
                if (cmd == null || cmd.isBlank()) continue;
                Bukkit.dispatchCommand(Bukkit.getConsoleSender(), cmd.replaceFirst("^/", "")
                        .replace("%player%", name).replace("%amount%", String.valueOf(amount)));
            }
        };
        if (Bukkit.isPrimaryThread()) r.run();
        else Bukkit.getScheduler().runTask(plugin, r);
    }

    public void claimPending(Player p) {
        DataManager.PlayerData d = data().get(p.getUniqueId(), p.getName());
        if (d.pending <= 0) {
            plugin.messages().send(p, "no-pending", placeholders(p));
            sound(p, "error");
            return;
        }
        int n = d.pending;
        d.pending = 0;
        data().markDirty();

        Map<String, String> ph = placeholders(p);
        ph.put("%amount%", String.valueOf(n));
        Rewards.give(plugin, p, plugin.configs().get(Configs.R_VOTE), ph, n);
        plugin.messages().send(p, "pending-claimed", ph);
        sound(p, "claim");
    }

    public void claimPalier(Player p, int palier) {
        ConfigurationSection reward = plugin.configs().get(Configs.R_PALIERS).getConfigurationSection("paliers." + palier);
        if (reward == null) return;
        DataManager.PlayerData d = data().get(p.getUniqueId(), p.getName());

        Map<String, String> ph = placeholders(p);
        ph.put("%palier%", String.valueOf(palier));
        ph.put("%palier_remaining%", String.valueOf(Math.max(0, palier - d.monthVotes)));

        if (d.claimed.contains(palier)) {
            plugin.messages().send(p, "palier-already", ph);
            sound(p, "error");
            return;
        }
        if (d.monthVotes < palier) {
            plugin.messages().send(p, "palier-locked", ph);
            sound(p, "error");
            return;
        }
        d.claimed.add(palier);
        data().markDirty();
        Rewards.give(plugin, p, reward, ph, 1);
        plugin.messages().send(p, "palier-claimed", ph);
        sound(p, "claim");
    }

    public void onJoin(Player p) {
        int queued = data().takeUnresolved(p.getName());
        if (queued > 0) registerVote(p, queued);
        DataManager.PlayerData d = data().get(p.getUniqueId(), p.getName());
        if (d.pending <= 0) return;
        if (cfg().getBoolean("vote.auto-claim-on-join", false)) {
            claimPending(p);
        } else {
            plugin.messages().send(p, "pending-join", placeholders(p));
        }
    }

    // ------------------------------------------------------------------ VoteParty

    public void checkParty() {
        int req = partyRequired();
        boolean carry = cfg().getBoolean("voteparty.carry-over", false);
        int guard = 0;
        while (data().partyCurrent() >= req && guard++ < 50) {
            data().setPartyCurrent(carry ? data().partyCurrent() - req : 0);
            data().incrementPartyCount();
            runParty();
            if (!carry) break;
        }
    }

    /** Déclenche la récompense VoteParty pour tous les joueurs connectés. */
    public void runParty() {
        plugin.messages().broadcast("party-start", placeholders(null));
        ConfigurationSection reward = plugin.configs().get(Configs.R_PARTY);
        for (Player p : new ArrayList<>(Bukkit.getOnlinePlayers())) {
            Rewards.give(plugin, p, reward, placeholders(p), 1);
        }
        data().markDirty();
    }

    // ------------------------------------------------------------------ mois

    public void tickMonth() {
        String now = data().monthKey();
        if (now.equals(data().lastMonth())) return;
        boolean first = data().lastMonth().isEmpty();
        data().setLastMonth(now);
        if (!first) plugin.messages().broadcast("month-reset", placeholders(null));
    }
}
