package fr.azurium.vote;

import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Déclenché à chaque vote enregistré (vote reçu du site via AzLink, ou /vote addvote).
 * Les autres plugins (tutoriel, quêtes...) écoutent cet event pour savoir qu'un joueur a voté.
 *
 * Exemple :
 * <pre>
 * &#64;EventHandler
 * public void onVote(fr.azurium.vote.AzuriumVoteEvent e) {
 *     Player p = e.getOnlinePlayer();
 *     if (p != null) { ... valider la quête ... }
 * }
 * </pre>
 */
public class AzuriumVoteEvent extends Event {
    private static final HandlerList HANDLERS = new HandlerList();

    private final OfflinePlayer player;
    private final int amount;

    public AzuriumVoteEvent(OfflinePlayer player, int amount) {
        this.player = player;
        this.amount = amount;
    }

    /** Le joueur qui a voté (peut être déconnecté). */
    public OfflinePlayer getPlayer() { return player; }

    /** Le joueur s'il est connecté, sinon null. */
    public @Nullable Player getOnlinePlayer() { return player.getPlayer(); }

    /** Nombre de votes ajoutés (1 pour un vote normal). */
    public int getAmount() { return amount; }

    @Override
    public @NotNull HandlerList getHandlers() { return HANDLERS; }

    public static HandlerList getHandlerList() { return HANDLERS; }
}
