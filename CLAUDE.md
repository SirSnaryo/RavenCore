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
