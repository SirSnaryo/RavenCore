# SMP Network — Technical Briefing11

This document summarizes the full architecture of the SMP network for
onboarding a developer or AI coding agent. It covers infrastructure,
networking quirks specific to this host, and the custom plugin systems that
need to be built.

---

## 1. High-Level Architecture

A cross-server Minecraft SMP network, fronted by its own Velocity proxy
(`SMP-Proxy`), separate from — but linked to — a pre-existing "Main" network
that already runs five other games. Players can join the SMP either directly
or via a transfer from the Main network's hub.

```
Player -> Main Proxy -> Main Login -> Hub -> [pick "SMP"] -> transferred to SMP-Proxy
                                                                    |
Player -> SMP-Proxy (direct join) -----------------------------> SMP-Login
                                                                    |
                                                              SMP-Spawn-1/2
                                                              /          \
                                                    SMP-World-NA      SMP-World-EU
                                                    (Overworld+Nether) (Overworld+Nether)
                                                              \          /
                                                              SMP-End (shared)
```

Both networks' login servers run **nLogin**, intended to share session state
via Redis so a player authenticated on one network isn't re-prompted on the
other (this cross-proxy session sync is a known open issue — see §7).

---

## 2. Infrastructure

**Host**: a single Pterodactyl node, hostname `raven`, reachable via a
Tailscale address (`100.104.31.88` for most services, `100.88.7.85`
specifically for SMP-Proxy). All SMP game servers currently run as Docker
containers on this same host, managed through Pterodactyl.

**Total RAM budget**: 192GB, split as:

| Server | Port | RAM |
|---|---|---|
| SMP-Proxy | 28860 | 6 GB |
| SMP-Login | 28861 | 4 GB |
| SMP-Spawn-1 | 28862 | 10 GB |
| SMP-Spawn-2 | 28863 | 10 GB |
| SMP-World-NA | 28864 | 68 GB |
| SMP-World-EU | 28865 | 68 GB |
| SMP-End | 28866 | 12 GB |
| Redis (SMP) | 28867 | 8 GB |
| Buffer | — | 6 GB |

Expected concurrent load: **1,000+ players**.

**Server software**: Folia/Canvas (region-threaded fork of Paper) on all
backend game servers. This has real implications for any custom plugin work
— anything touching entities, chunks, or scheduling must use Folia's
region-aware schedulers/async APIs, not the legacy single-threaded Bukkit
scheduler. Movement listeners in particular must run on the correct region's
scheduler, and any logic that inspects coordinates outside a player's current
loaded region needs an async chunk-load call.

---

## 3. Server Roles (RavenCore `config.yml`)

Every backend server runs a custom **RavenCore** plugin with a `server-role`
field:

- `survival` — normal Overworld + local Nether (default). SMP-World-NA and
  SMP-World-EU.
- `end` — dedicated End server; all non-`THE_END` worlds unloaded. SMP-End.
- `spawn` — lobby, no gameplay modules loaded. SMP-Spawn-1/2.

RavenCore's config also carries: Redis/MySQL credentials, and (newly added
for this project) a `network.cluster` field (`na`/`eu`), a `grid` section
defining this server's slice of the shared virtual coordinate space, a
`border` section for the visual particle-wall/build-deny system, and a
`location-library` section for the RTP location pool engine. Full current
schema is in the project's `config.yml`.

---

## 4. Border System (Seamless Cross-Server World)

**Goal**: NA and EU (and eventually multiple servers per cluster, arranged in
a grid — Donut-SMP style) should feel like one continuous world. Walking
across a server's edge silently transfers the player to the neighboring
server, landing them at the equivalent coordinate rather than a fresh spawn.
This must work for **both Overworld and Nether**, with the Nether grid scaled
1:8 against the Overworld (vanilla ratio), and must catch **any** out-of-
bounds movement — walking, portals, teleport commands, elytra, ender pearls
— not just tick-based position checks.

Current test scope: **1 server per cluster** (just NA↔EU), to validate the
transfer mechanism itself before scaling into a full multi-server grid.

Core components (see `seamless-border-system-design.md` for full detail):
1. **Region registry** — maps (cluster, dimension, cell x, cell z) → server
   ID. Currently expressed per-server via the `grid` block in `config.yml`
   rather than a separate central file.
2. **Boundary/movement listener** — per-tick check (Folia region scheduler),
   comparing player position against this server's cell bounds.
3. **Portal/teleport interceptor** — hooks `PlayerPortalEvent` and
   `PlayerTeleportEvent` to catch instant, non-tick-based movement before it
   completes, redirecting to the correct server if the destination resolves
   outside the current one.
4. **Cross-server transfer** — via the Minecraft 1.20.5+ Transfer packet
   (Velocity), not a raw `/server` switch, for a smoother handoff. Exact
   destination coordinates are passed through and applied on arrival, not
   world spawn.
5. **Chunk pre-loading near borders** — a buffer strip kept loaded along each
   server's own edge to avoid a stutter/fall-through at the exact moment of
   crossing.

Velocity-side: `[proxy-addresses]` + `master-proxy-ids` are configured so
that if SMP-Proxy fully goes down, players connecting through Main Proxy get
rerouted rather than stranded (dynamic-proxy-filter = "first_found").

---

## 5. LocationPool (Shared Random-Location Engine)

A standalone core library — not tied to any single feature — that generates,
stores, and hands out pre-validated safe coordinates. Three modules consume
it: **RTP**, **RTP-Zone**, and **Respawn**. Full spec in `LocationPool.md`;
summary:

- **Storage**: Redis lists, one per server per dimension
  (`locationpool:<server-id>:overworld` / `:nether`), plus per-zone variants
  for RTP-Zone. Atomic pops (`RPOP`) prevent duplicate hand-outs.
- **Generation rules**: candidate must be strictly inside the server's own
  grid cell (pulled in by `border-safety-margin` on every side — never
  allowed to be close enough to trigger a border transfer, or to fall inside
  a neighboring server's territory), not a blacklisted block
  (`location-library.blacklisted-blocks`) or biome
  (`location-library.blacklisted-biomes`), not claimed, with enough vertical
  clearance to avoid trapping the player.
- **Lifecycle**: pool filled to `pool-target-size` (default 50) on startup;
  a repeating task refills it every `refill-interval-seconds` (default 45s),
  capped at `max-refill-per-cycle` (default 10) new locations per cycle to
  avoid bursty chunk-loading load.
- **Public API**:
  ```java
  RtpLocation getRandomLocation(String serverId, Dimension dimension);
  RtpLocation getRandomLocationInCluster(String cluster, Dimension dimension);
  RtpLocation getRandomLocationInZone(String zoneId, Dimension dimension);
  int getPoolSize(String serverId, Dimension dimension);
  ```
  No cooldowns/costs/permissions live here — that's each consumer module's
  job.

### RTP module (thin wrapper)
Cooldown, warmup (cancel on move/damage), economy cost, dimension allow-list,
and cluster-wide server selection (uniform random pick across the player's
current cluster, not weighted by pool size) — layered on top of
`getRandomLocationInCluster()`. Also carries its own `sounds` and `messages`
config blocks. See `rtp-config.yml`.

### Respawn module
Calls into LocationPool with no gates (should resolve instantly on death).
Open decision: stay on the player's current server (`getRandomLocation()`)
or roam the cluster (`getRandomLocationInCluster()`) — not yet finalized.

### RTP-Zone module
Same mechanism, scoped to smaller named zones (e.g. event arenas) with their
own independent cooldown/cost rules, via `getRandomLocationInZone()`.

---

## 6. End Portal Linking

SMP-End is a single shared server, not part of the NA/EU grid. Every
survival server's local End portal is intercepted (`PlayerPortalEvent` with
`TeleportCause.END_PORTAL`) and replaced with a cross-server transfer to
SMP-End's fixed spawn point.

**Return trip is intentionally simple**: no origin tracking. On exit portal
use, the player is sent to a **random safe location** via
`LocationPool.getRandomLocationInCluster()` — either a fixed default cluster,
a uniformly random cluster pick, or a player-facing cluster-choice GUI (open
decision, see `end-portal-linking.md`). This avoids any Redis TTL/state-
tracking complexity that a "remember where they came from" approach would
require.
