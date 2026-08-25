# RavenCore Permissions

Full inventory of permission nodes across the Bukkit and Velocity modules, split into
**player** (self-scoped — cannot touch another player's state, inventory, balance, or
location) and **admin** (affects other players, server state, or bypasses a restriction).

Sources scanned: `bukkit/src/main/resources/plugin.yml`, every `hasPermission()` call site,
all `KCommand` constructor declarations, and permission keys in module YAML configs.

## Player permissions

| Node | Where | Default |
|---|---|---|
| `raven.settings` | `/settings` | `true` |
| `raven.chat` | send network chat, `/chattoggle`, `/broadcasttoggle` | `true` |
| `raven.chat.link` | clickable links in own messages | `true` |
| `raven.scoreboard` | `/scoreboard toggle\|select\|list\|current` | `true` |
| `raven.spawn` | `/spawn` | `true` |
| `raven.afk` | `/afk` | `true` |
| `raven.rtp` | `/rtp` | `true` |
| `raven.rtp.queue` | `/rtpq` | `true` |
| `raven.home` | `/home`, `/homes` | `true` |
| `raven.home.set` | `/sethome` | `true` |
| `raven.home.delete` | `/delhome` | `true` |
| `raven.home.max.<1-20>` | home limit ladder (`HomeService.java:176`) | only `.1` declared |
| `raven.team` | `/team` | `true` |
| `raven.teleport.tpa` | `/tpa`, `/tpahere`, `/tpaccept`, `/tpadeny`, `/tpacancel`, `/tpatoggle`, `/tpaheretoggle`, `/tpauto` | `true` |
| `raven.economy.balance` | `/balance` (self) | `true` |
| `raven.economy.pay` | `/pay`, `/paytoggle`, `/paymsg` | `true` |
| `raven.shop` | `/shop` | `true` |
| `raven.billford` | `/billford` | `true` |
| `raven.stats` | `/stats` (self) | `true` |
| `raven.stats.others` | `/stats <player>` — read-only | `true` |
| `raven.leaderboard` | `/leaderboard`, `/lb`, `/top` | `true` |
| `raven.crates` | `/crates` root gate | `true` |
| `raven.nightvision` | `/nv` | `op` (see issue 3) |
| `raven.msg` *(Velocity)* | `/msg`, `/r` — `privatemessage/config.yml` | not declared |

## Admin permissions

| Node | Where | Default |
|---|---|---|
| `raven.core.admin` | `/ravencore reload` (`RavenCoreCommand.java:38`) | `op` |
| `raven.velocity.admin` | `/ravencore` on proxy (`RavenCoreVelocityCommand.java:41`) | not declared |
| `raven.mythictools.admin` | `/mythictools` | `op` |
| `raven.teleport` | `/tp`, `/tphere` — moves other players | `op` |
| `raven.clearinventory.self` | `/clear` (self) — gates the whole command | `op` |
| `raven.clearinventory.others` | `/clear <player>` | `op` |
| `raven.economy.balance.others` | `/balance <player>` | `op` |
| `raven.economy.admin` | `/eco give\|take\|set\|reset\|log` | `op` |
| `raven.economy.confirm.bypass` | skip large-payment confirm — inert unless `economy/config.yml: bypass-permission` is set | `op` |
| `raven.economy.paytoggle.bypass` | pay players who disabled payments | `op` |
| `raven.setspawn` | set this server's spawn point | `op` |
| `raven.setafk` | set this server's AFK point | `op` |
| `raven.combat.admin` | `/combatmanager tag\|untag` | `op` |
| `raven.shop.admin` | `/shop reload` | `op` |
| `raven.spawner.admin` | `/spawner`, bypass spawner ownership | `op` |
| `raven.spawner.silktouch` | break placed spawners without Silk Touch | `op` |
| `raven.scoreboard.admin` | `/scoreboard reload\|set\|clear`, assign boards to others; also grants the admin board (`scoreboard/config.yml:115`) | `op` |
| `raven.stats.admin` | `/statsmanager` | `op` |
| `raven.leaderboard.admin` | `/leaderboard refresh` | `op` |
| `raven.crates.admin` | **every** `/crates` subcommand + crate-block placement listener | `op` |
| `raven.billford.admin` | `/billfordpreset` | `op` |
| `raven.chat.color` | color codes in chat | `op` |
| `raven.chat.format.admin` | admin chat format — inert, `chat-formats: []` by default | `op` |
| `raven.chat.bypass.length` | max message length | `op` |
| `raven.chat.bypass.cooldown` | chat cooldown | `op` |
| `raven.chat.bypass.same` | duplicate-message check | `op` |
| `raven.chat.bypass.caps` | caps check | `op` |
| `raven.chat.bypass.flood` | flood / repeated-character check | `op` |
| `raven.chat.bypass.filter` | advertising + profanity + blocked-patterns | `op` |
| `raven.chat.bypass.toggle` | see chat others hid — inert unless `chat/config.yml: bypass-toggle-permission` is set | `false` |
| `raven.proxycommandfilter.bypass` *(Velocity)* | use blocked `/server`, `/queue` | not declared |
| `*` *(Velocity)* | hard-coded wildcard bypass at `ProxyCommandFilterModule.java:105` | — |

## Not server permissions

- `TeamPermission` (`EDIT_HOME`, `MANAGE_MEMBERS`, `TOGGLE_PVP`, `VISIT_HOME`, `USE_TEAM_CHAT`)
  is a DB-stored per-member bitmask, not a Bukkit node.
- `MenuItem.permission` and `ScoreboardRequirement` `type: permission` are config-driven hooks.
  The only node used in shipped configs is `raven.scoreboard.admin`.

## Known issues

1. **`/baltop` has no permission at all.** No `permission:` in `plugin.yml:254`, and
   `BalTopCommand.java:16` passes `""`. It opens the same leaderboard menu that
   `/leaderboard` gates behind `raven.leaderboard`.
2. **`raven.crates` is a dead gate.** `default: true`, but every subcommand and the block
   listener require `raven.crates.admin`. A normal player passes the gate and gets a silent
   no-op.
3. **`raven.nightvision` is `default: op`** despite being a self-only cosmetic toggle. If
   `/nv` is meant to be a donor/player perk it belongs at `true` or unset.
4. **`raven.home.max.2` … `.20` are undeclared.** `HomeService.java:176` loops 1→20, but
   `plugin.yml` only declares `.1`. They work through LuckPerms, but do not appear in `/lp`
   suggestions or permission listings.
5. **`raven.stats.others` is `default: true`** — harmless (read-only), but it means anyone can
   view any player's stats. Flag if unintended.

## Resolved

- **`admin.clear` stray node** — `ClearInventoryCommand.java:33` declared
  `super("clear", "admin.clear", false)`, which `KCommand.onCommand` enforced *on top of* the
  `raven.clearinventory.self` from `plugin.yml`, so nobody without a hand-made `admin.clear`
  node could run `/clear`. Now uses `raven.clearinventory.self`, and that node's default was
  flipped from `true` to `op` — `/clear` is an admin command in both forms.
