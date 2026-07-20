# RavenCore

## Code style

- Keep comments minimal. Only add a comment when the *why* is genuinely non-obvious — don't narrate what the code already says, and skip class/method Javadoc unless it earns its place. Match the surrounding file's low comment density.

## Message color palette

Config messages support hex colors as `&#RRGGBB` (parsed by `HexColorCodes` / `ChatFormatter`). Use this palette by role so messages stay consistent across modules:

| Color     | Hex       | Config code | Use for |
|-----------|-----------|-------------|---------|
| Red       | `#D90429` | `&#D90429`  | errors, denied, failing |
| Orange    | `#FF9F1C` | `&#FF9F1C`  | warnings, low stats, caution |
| Gold      | `#FFD447` | `&#FFD447`  | playtime, rewards, streaks |
| Green     | `#33FF66` | `&#33FF66`  | success, money, ping, positive |
| Cyan      | `#4CC9F0` | `&#4CC9F0`  | info, tips, announcements |
| Blue      | `#3A86FF` | `&#3A86FF`  | team / social / faction |
| Purple    | `#A855F7` | `&#A855F7`  | rare / legendary / special events |
| Gray      | `#7A7A7A` | `&#7A7A7A`  | normal / neutral text |
| White     | `#FFFFFF` | `&#FFFFFF`  | labels (paired with any accent) |
| Bold Red  | `#FF2B2B` | `&#FF2B2B`  | server branding / titles only |

## Raven Text, Message, And Sound Rules

Use these rules for every Bukkit/Velocity module unless a module already has a stricter local pattern.

### Color Codes

- Normal config text uses legacy hex format: `&#RRGGBB`.
- MiniMessage surfaces, such as scoreboard text, use `<#RRGGBB>` and may use `<shadow:#000000FF>...</shadow>`.
- GUI menu titles are always dark gray: `&8...`. Do not use colored menu titles.
- Use `&l`/`<bold>` only for brand names, titles, important action buttons, or short status labels.
- Keep labels white and values colored. Example: `&#FFFFFFMoney &#33FF66%raven_money%`.

### Core Palette

- `#FF2B2B` / `&#FF2B2B`: Raven brand red, main titles, server identity.
- `#D90429` / `&#D90429`: errors, denied actions, danger, cancel buttons.
- `#33FF66` / `&#33FF66`: success, enabled, accept/confirm, money, positive state.
- `#FFD447` / `&#FFD447`: warnings, player names, tips, playtime, reward-like values.
- `#FF9F1C` / `&#FF9F1C`: caution, deaths, MSPT, secondary warning values.
- `#4CC9F0` / `&#4CC9F0`: info, server/location, ping, links, announcements.
- `#3A86FF` / `&#3A86FF`: team/social/faction related text.
- `#A855F7` / `&#A855F7`: rare/special/admin metric accents such as CPU.
- `#7A7A7A` / `&#7A7A7A`: neutral text, separators, descriptions, disabled-ish detail.
- `#FFFFFF` / `&#FFFFFF`: readable labels and main body text.
- `#000000FF`: strongest MiniMessage shadow color.

### Message Style

- Error: `&#D90429...`
- Success: `&#33FF66...`
- Warning/tip: `&#FFD447...`
- Info: `&#4CC9F0...`
- Neutral/body: `&#7A7A7A... &#FFFFFF...`
- Player names usually use `&#FFD447%player%`.
- Server/location values usually use `&#4CC9F0%server%`, `%raven_server_display%`, `%world%`.
- Money values use `&#33FF66%raven_money%`.
- Use placeholders with `%name%` style in configs.
- Use click/hover rich text where supported:
  - `<click:run_command:/tpaccept %player%>... </click>`
  - `<hover:show_text:'&#33FF66Accept request'>... </hover>`
- Missing-message fallback should be red: `&#D90429[Missing message: key]`.

### GUI Text

- First lore line in GUI items should be a small-caps category label.
- GUI item names should use small caps when practical.
- Use the same action colors everywhere:
  - Confirm/accept: `&#33FF66`
  - Cancel/deny/delete: `&#D90429`
  - Player/item focus: `&#FFD447`
  - Location/server/info: `&#4CC9F0`
  - Description text: `&#7A7A7A` and `&#FFFFFF`
- Do not show raw proxy IDs in lore when a display name exists. Prefer `network.server-display-name`.

### Chat Module Defaults

- Chat is Redis-backed. Publish raw + formatted message, then render hover/click per receiving server.
- `/chattoggle` hides member/public chat for that player, but the player can still send.
- `/broadcasttoggle` hides auto broadcast messages for that player.
- Both toggle states live in Redis.
- Auto broadcasts default to disabled: `auto-messages.enabled: false`.
- Rich chat should keep these defaults:
  - Sender hover enabled.
  - Sender click action: `SUGGEST_COMMAND`.
  - Sender click value: `/msg %player% `.
  - Links clickable when `links.clickable: true`.
  - Mention self color: `&#FFD447&l%player%`.
  - Mention other color: `&#4CC9F0%player%`.

### Scoreboard Text

- Title default: `<shadow:#000000FF><#FF2B2B><bold>Raven SMP</bold></shadow>`.
- Every visible line should be wrapped with `<shadow:#000000FF>...</shadow>`.
- Use `%raven_server_display%` for the footer/server name, not hard-coded region names.
- Default footer pattern:
  - `<shadow:#000000FF><#7A7A7A>%raven_server_display% <#4CC9F0>(%raven_ping%)</shadow>`
- Money uses `%raven_money%`, not Vault's coin suffix.
- `%raven_balance%` is the full economy format and may include the economy currency name.
- `%raven_money%` is compact: `1$`, `1k`, `1.1k`, `1m`, `1.1m`.

### Sound Palette

Use Bukkit `Sound` enum names in config. Prefer section format when pitch/volume are supported:

```yml
sound-key:
  sound: ENTITY_EXPERIENCE_ORB_PICKUP
  pitch: 1.35
  volume: 1.0
```

- Confirm/accept/success: `ENTITY_EXPERIENCE_ORB_PICKUP`, pitch `1.35`, volume `1.0`.
- Toggle/click: `UI_BUTTON_CLICK`, pitch `1.35`, volume `1.0`.
- Deny/error/ignore/no: `ENTITY_VILLAGER_NO`, pitch `0.85` to `1.0`, volume `1.0`.
- Teleport start/info click: `ENTITY_ENDERMAN_TELEPORT`, pitch `1.75`, volume `0.9`.
- Teleport countdown: `BLOCK_NOTE_BLOCK_PLING`, pitch `1.9`, volume `0.85`.
- Chat mention: `BLOCK_NOTE_BLOCK_PLING`, pitch `1.8`, volume `0.8`.
- Broadcast/info ping: `ENTITY_EXPERIENCE_ORB_PICKUP`, pitch `1.15`, volume `0.8`.

### Current Named Sound Keys

- `toggle`: settings/chat/teleport toggles.
- `error`: validation or failed action.
- `mention`: chat @mention.
- `broadcast`: chat auto message notification.
- `information-click`: teleport information/open/countdown start style.
- `request-cancelled`: teleport deny/cancel.
- `accept-teleport`: teleport accept.
- `count-down`: teleport countdown tick.

### Config Migration Rule

- Bukkit `saveDefaultConfig()` only creates a file once. New keys must be added with a small migration that preserves user YAML comments/order.
- Do not call `config.save()` on large user config files just to add one missing key.
- For existing installs, migrate only the missing key or a tiny exact string replacement.
