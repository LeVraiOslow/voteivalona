# AzuriumVote

Plugin de vote / VoteParty / paliers de vote mensuels pour Paper 26.2, relié au site Azuriom (https://ivalona.fr/vote) via **AzLink**.
Compatible **Nexo** (`nexo:monitem`) partout (menus et récompenses).

## Installation

1. Compiler (GitHub Actions : `.github/workflows/build.yml`, ou `mvn package` avec Java 25).
2. Mettre `AzuriumVote-1.0.0.jar` dans `plugins/` puis redémarrer.
3. Configurer la récompense côté site (voir ci-dessous).

## Liaison avec Azuriom / AzLink

Dans l'admin Azuriom : **Vote → Récompenses**, crée (ou modifie) une récompense avec cette commande :

    azuriumvote:vote addvote {player}

AzLink l'exécute en console à chaque vote. Le plugin s'occupe du reste :
- il compte le vote (paliers du mois, total, VoteParty) ;
- si le joueur est connecté, il lui donne la récompense (`rewards/vote.yml`) ;
- s'il est déconnecté, la récompense est mise en attente (menu **Récompenses** ou message à la connexion).

Le préfixe `azuriumvote:` évite qu'un autre plugin ayant aussi une commande `/vote` intercepte la commande.
Sur la récompense Azuriom, mets une chance de 100 % (sinon un autre lot peut être tiré à la place).

Ne mets donc pas d'autres commandes de récompense côté site (sinon elles s'ajoutent à celles du plugin).
Si la case « uniquement si le joueur est connecté » existe sur la récompense Azuriom, ne la coche pas.

## Arborescence de `plugins/AzuriumVote/`

    config/config.yml        général : lien, VoteParty (votes requis, barre), fuseau, sons
    messages/messages.yml    tous les messages
    menus/main.yml           menu /vote
    menus/paliers.yml        menu des paliers (slots, états locked / claimable / claimed)
    rewards/vote.yml         récompense de chaque vote
    rewards/voteparty.yml    récompense de la VoteParty (tous les joueurs connectés)
    rewards/paliers.yml      récompenses des paliers (10, 20, 30, 50, 75, 100, 150, 200, 300, 500)
    data/votes.yml           données (généré automatiquement, ne pas modifier serveur allumé)

## Fonctionnement

- `/vote` : menu avec **Voter** (lien cliquable), **Récompenses**, **VoteParty**, **Palier de Vote**.
- Paliers : clic gauche sur un palier atteint pour le récupérer (une seule fois). Réinitialisation
  automatique le **1er de chaque mois** (fuseau `timezone`), même si le serveur était éteint à ce moment-là.
- VoteParty : à `voteparty.votes-required` votes, tous les joueurs connectés reçoivent la récompense,
  puis le compteur repart à 0 (ou garde le surplus avec `carry-over: true`).
- Le nombre de votes de la VoteParty se règle à un seul endroit : `config/config.yml`.

## Personnalisation

- Couleurs : `&a`, `&b`, `&#RRGGBB`, `<#RRGGBB>` partout.
- Items : `DIAMOND`, `nexo:monitem`, ou `PLAYER_HEAD` + `texture:` (base64). Options : `amount`, `name`, `lore`, `glow`, `hide-flags`.
- Menus : `size`, `title`, `slot` / `slots: [0, "3-5"]`, `filler`, `commands` au clic, `action`
  (`VOTE`, `REWARDS`, `VOTEPARTY`, `PALIERS`, `MAIN`, `CLOSE`, `NONE`).
- Paliers : ajoute/retire un palier dans `rewards/paliers.yml` puis place-le dans `menus/paliers.yml`.
  Un item différent pour un seul palier est possible (voir l'exemple en commentaire dans `menus/paliers.yml`).
- Récompenses : `commands` (`[console]` par défaut, `[player]`), `items`, `messages`, `title`, `sound`.
- Placeholders : `%player% %votes% %total_votes% %pending% %claimable% %current% %required% %remaining%
  %percent% %bar% %server_total% %party_count% %month% %palier% %palier_remaining%`.

## Placeholders PlaceholderAPI (CustomNameplates, scoreboard, hologrammes...)

| Placeholder | Valeur |
|---|---|
| `%azuriumvote_voteparty%` | `12/200` |
| `%azuriumvote_voteparty_current%` | votes actuels de la VoteParty |
| `%azuriumvote_voteparty_required%` | votes requis |
| `%azuriumvote_voteparty_remaining%` | votes restants |
| `%azuriumvote_voteparty_percent%` | pourcentage (0-100) |
| `%azuriumvote_voteparty_count%` | nombre de VoteParty déjà déclenchées |
| `%azuriumvote_server_total%` | votes totaux du serveur |
| `%azuriumvote_votes%` | votes du joueur ce mois-ci |
| `%azuriumvote_total_votes%` | votes totaux du joueur |
| `%azuriumvote_pending%` | récompenses en attente du joueur |
| `%azuriumvote_claimable%` | paliers récupérables du joueur |

## Prévenir d'autres plugins à chaque vote

- **Event Java** `fr.azurium.vote.AzuriumVoteEvent` : déclenché à chaque vote enregistré
  (`getPlayer()`, `getOnlinePlayer()`, `getAmount()`). Ajoute `AzuriumVote` en `softdepend` du plugin qui l'écoute.
- **Commandes** `vote.on-vote-commands` dans `config/config.yml` : lancées en console à chaque vote
  (`%player%`, `%amount%`), par exemple pour valider une quête.

## Commandes

| Commande | Permission | Description |
|---|---|---|
| `/vote` | `azuriumvote.use` (défaut : tous) | Ouvre le menu |
| `/vote reload` | `azuriumvote.admin` | Recharge la configuration |
| `/vote addvote <joueur> [nombre]` | `azuriumvote.admin` | Ajoute des votes (utilisée par AzLink) |
| `/vote setparty <nombre>` | `azuriumvote.admin` | Fixe le compteur VoteParty |
| `/vote forceparty` | `azuriumvote.admin` | Déclenche la VoteParty |
| `/vote resetmonth <joueur\|all>` | `azuriumvote.admin` | Réinitialise les paliers du mois |
| `/vote info <joueur>` | `azuriumvote.admin` | Affiche les stats d'un joueur |

## Notes

- Les récompenses d'exemple utilisent `eco give` (Essentials/Vault) : adapte-les à ton serveur.
- Un vote pour un joueur jamais vu par le serveur est refusé par défaut (`vote.allow-unknown-players`).
- Version de Paper API : `26.2.build.119-stable` (propriété `<paper.version>` du `pom.xml`).
