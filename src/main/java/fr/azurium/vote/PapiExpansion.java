package fr.azurium.vote;

import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.OfflinePlayer;

import java.util.Locale;

/**
 * Placeholders PlaceholderAPI (identifiant : azuriumvote).
 * Lecture seule et sans thread principal obligatoire : compatible CustomNameplates.
 */
public final class PapiExpansion extends PlaceholderExpansion {

    private final AzuriumVote plugin;

    public PapiExpansion(AzuriumVote plugin) {
        this.plugin = plugin;
    }

    @Override public String getIdentifier() { return "azuriumvote"; }
    @Override public String getAuthor() { return "Ivalona"; }
    @Override public String getVersion() { return plugin.getPluginMeta().getVersion(); }
    @Override public boolean persist() { return true; }

    @Override
    public String onRequest(OfflinePlayer player, String params) {
        DataManager data = plugin.data();
        int cur = data.partyCurrent();
        int req = plugin.service().partyRequired();

        switch (params.toLowerCase(Locale.ROOT)) {
            // --- VoteParty (global) ---
            case "voteparty":            return cur + "/" + req;
            case "voteparty_current":    return String.valueOf(cur);
            case "voteparty_required":   return String.valueOf(req);
            case "voteparty_remaining":  return String.valueOf(Math.max(0, req - cur));
            case "voteparty_percent":    return String.valueOf(Math.min(100, cur * 100 / req));
            case "voteparty_count":      return String.valueOf(data.partyCount());
            case "server_total":         return String.valueOf(data.serverTotal());
            default: break;
        }

        // --- Joueur ---
        if (player == null) return "";
        DataManager.PlayerData d = data.peek(player.getUniqueId());
        boolean sameMonth = d != null && data.monthKey().equals(d.month);
        switch (params.toLowerCase(Locale.ROOT)) {
            case "votes":       return String.valueOf(sameMonth ? d.monthVotes : 0);
            case "total_votes": return String.valueOf(d == null ? 0 : d.totalVotes);
            case "pending":     return String.valueOf(d == null ? 0 : d.pending);
            case "claimable":   return String.valueOf(sameMonth ? plugin.service().claimableCount(d) : 0);
            default:            return null;
        }
    }
}
