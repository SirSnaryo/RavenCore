# Pre-Generated RTP Location Pool — Design

## 1. Concept

Instead of scanning for a safe location live on every `/rtp` request, each
survival server maintains a **pool of pre-validated coordinates** in Redis.
When a player requests RTP, the server pops one location from its own pool
(never another server's — RTP stays within the requesting player's current
cluster/cell, consistent with the border system).

This avoids:
- Live safe-location scan lag (especially costly on Folia, where scanning
  chunks outside a player's own loaded region needs cross-region async work)
- Players landing right at a cell edge and immediately triggering a border
  transfer (the pool is pre-filtered to respect `border-safety-margin`)

## 2. Storage

**Redis, per server, populated once at startup.** On boot, each survival
server scans and validates **50 random locations** within its own cell bounds
(respecting `min-radius`, `max-radius`, `border-safety-margin`) and pushes
them into a Redis list scoped to that server:

```
rtp:pool:smp-world-na   -> ["120,64,-2301", "890,71,-4102", ...]
rtp:pool:smp-world-eu   -> [...]
```

Simple `x,y,z` strings are enough; parse on pop. Using Redis (already in your
stack) means:
- The pool is visible **network-wide** — any server can pop from any other
  server's pool directly (important for cluster-wide RTP, see §4), no plugin
  message round-trip needed.
- Pops are atomic (`RPOP`/`LPOP`) — no two players can grab the same
  coordinate even if requests land on different servers simultaneously.
- You (or an admin command) can **flush a pool instantly** with a single
  `DEL rtp:pool:<server>` if something's wrong with it — fast to clear and
  regenerate.

Since this is a **one-time startup scan** (not a continuously refilling
worker), once a server's 50 locations are used up, the pool is empty until
the server restarts — unless you add an on-demand top-up trigger later.

## 3. Pre-generation (Startup Scan + Ongoing Refill)

### Startup scan
Runs once, when the server boots, before it accepts RTP requests — fills the
pool from empty up to 50.

### Ongoing refill
A lightweight repeating task (e.g., every 30-60 seconds) checks the current
pool size in Redis (`LLEN rtp:pool:<this-server>`) and tops it back up to 50
if it's dropped below that, generating only as many new locations as needed
to close the gap — not a full re-scan each time.

```
Every 30-60s:
  current = LLEN rtp:pool:<this-server>
  needed = 50 - current
  if needed > 0:
      generate `needed` new valid locations (same validation logic as
      startup) and RPUSH them into the pool
```

This keeps the pool self-sustaining without ever fully emptying under normal
play — as players RTP and pop locations out, the refill task quietly replaces
them a little behind in real time.

### Generation logic (shared by both startup and refill)
1. Picks a random `(x, z)` **strictly inside this server's cell bounds**, as
   defined by the region registry (§1) — not just a spawn-relative radius.
   Concretely: `cell_min_x < x < cell_max_x` and same for `z`, then further
   pulled in by `border-safety-margin` on all sides so a generated location
   can never land close enough to the true border to trigger the border
   listener on arrival. A location is only valid if it's inside this
   tightened box — there is no fallback that allows crossing into a
   neighboring cell, even partially.
2. Validates safety:
    - Highest solid block isn't lava, water (unless allowed), or void
    - No overhang trapping the player in a 2-block-high gap
    - Not inside a claimed/protected region if you're using a claims plugin
    - Optional: minimum distance from other players' claims/builds, if you
      want RTP to avoid dropping players in the middle of someone's base
3. If valid → push `x,y,z` into this server's Redis pool (`rtp:pool:<this
   server>`).
4. If invalid → discard and try again.

**Why this matters**: since RTP can transfer a player to a different server
in the cluster (§4), a generated location that's technically inside the
target server's world file but too close to (or past) its cell boundary would
either immediately re-trigger the border-crossing transfer on arrival, or —
worse — place the player in territory that's actually owned by a neighboring
server's data, causing desync between what that server thinks exists there
and what the target server's world file shows. The safety margin isn't just
about player experience; it protects the border system's coordinate model
from being violated by RTP.

### Performance notes
- Run generation **async**, not on the main/region tick thread — chunk
  loading for validation checks can be slow if those chunks haven't been
  generated yet, and blocking gameplay ticks for this would cause lag spikes.
- On **Folia**, be careful here: scanning arbitrary `(x, z)` coordinates for
  safety means touching chunks outside any currently-loaded region. Use
  Folia's async chunk-load API (`getChunkAtAsync`) rather than assuming a
  synchronous region-scheduler task can safely touch far-away coordinates —
  a location being validated is very likely in a different region (or an
  unloaded one) than wherever the refill task itself is running.
- Rate-limit how many locations the refill task generates per cycle (e.g.,
  cap at 5-10 per run) even if the pool is very depleted, so a sudden burst
  of RTP usage doesn't cause a spike of simultaneous chunk-loading work.

## 4. On Player Request

RTP is **cluster-wide**, not server-local: a player on any NA server can be
sent to a random location on *any* server within the NA cluster — including
one they're not currently connected to. Since the pool lives in Redis
(network-wide, not per-server memory), any server can pop directly from any
other server's pool — no plugin message round-trip needed for this part.

```
/rtp
 -> check cooldown
 -> check cost (if using economy)
 -> start warmup timer (cancels on move/damage)
 -> on warmup complete:
      cluster = player's current cluster (na or eu)   [looked up from the
                region registry, using the server the player is currently on]
      candidate_servers = all servers in that cluster with a non-empty pool
        (check rtp:pool:<server> length in Redis)
      if candidate_servers is empty -> fall back to a live scan on the
        player's current server and log a warning
      target_server = random pick, uniformly across candidate_servers (see
        weighting note below)
      location = pop one location from rtp:pool:<target_server> (atomic
        RPOP/LPOP — safe even with concurrent requests from other players)

      if target_server == player's current server:
          teleport locally (no transfer needed)
      else:
          send cross-server transfer request (same Transfer-packet mechanism
          used by the border system) with the popped coordinates as the
          destination, on the correct dimension
```

**Weighting note**: picking uniformly at random across servers (rather than
weighting by pool size) keeps distribution even across the cluster even if one
server's pool is closer to depletion than another's. Recommend starting with
uniform-per-server selection and only revisiting if pools deplete unevenly in
practice.

## 5. Pool Health Monitoring

Track pool size per server (simple Redis `LLEN`) and alert if it drops below
a low-water mark (e.g., 50) — this tells you the pre-gen worker isn't keeping
up with RTP demand, before players start hitting the live-scan fallback.

## 6. Nether Handling

If Nether RTP is enabled, maintain a **separate pool** per server per
dimension (`rtp:pool:smp-world-na:overworld`, `rtp:pool:smp-world-na:nether`),
since Nether-safe locations (avoiding lava seas, tight cave pockets) need
different validation logic than Overworld ones. Cell bounds for the Nether
pool are the Overworld cell bounds ÷ `nether_ratio` (8), per §1.

**Server selection works exactly like Overworld RTP** (§4): the target
server within the player's current cluster is picked **automatically and
uniformly at random** — the player has no way to choose or influence which
server in the cluster they land on. This is the same mechanism, just reading
from the `:nether` pool instead of the `:overworld` one. There's no separate
"pick your cluster" step for Nether either — it's implicitly the player's
current cluster, exactly as with Overworld RTP.

## 7. Refresh / Depletion Over Time

As terrain around used locations gets built on by players, a location popped
long ago and never used might become stale (someone built there since). Two
options:
- **Don't worry about it** — pop-and-discard means each location is used once
  then removed from the pool anyway, so staleness mostly self-resolves.
- **TTL on pool entries** — if you want extra safety, expire pool entries
  after e.g. 24-48h and let the worker regenerate them, in case a location sat
  unused long enough for the area to change.

## 8. Core Location Library (shared by RTP, RTP-Zone, and Respawn)

A standalone internal library — not tied to any single module — that owns all
pool/Redis/validation/border logic and exposes a simple public surface:

```java
interface LocationLibrary {

    // Simplest case: random location within the caller's current server.
    RtpLocation getRandomLocation(String serverId, Dimension dimension);

    // Random location within an entire cluster (picks a random server in
    // that cluster, then pops from its pool). Used by RTP for cluster-wide
    // teleports.
    RtpLocation getRandomLocationInCluster(String cluster, Dimension dimension);

    // Random location within a named zone (smaller, custom-bounded area,
    // e.g. an event arena). Used by RTP-Zone.
    RtpLocation getRandomLocationInZone(String zoneId, Dimension dimension);
}
```

The library owns:
- Redis pool storage and atomic pops (§2)
- Startup scan + ongoing refill to keep every pool (server, cluster-member,
  and zone) topped up (§3)
- Safety validation (solid ground, no lava/void, no overhangs, claim checks)
- Strict clamping to the caller's cell/zone bounds + `border-safety-margin`,
  so nothing it ever returns can violate the border system (§3, "Why this
  matters")
- The cross-server transfer trigger, when the returned location is on a
  different server than the caller (handled transparently — the caller
  doesn't need to know or care whether a transfer happened)

It does **not** know about: cooldowns, costs, warmups, permissions, or any
other player-facing rule. Those live entirely in the three consumer modules.

### The three modules, as thin consumers

| Module | Calls | Player-facing rules it adds |
|---|---|---|
| **RTP** | `getRandomLocationInCluster()` | Cooldown, cost, warmup (cancels on move/damage) |
| **RTP-Zone** | `getRandomLocationInZone()` | Its own cooldown/cost, independent of RTP's |
| **Respawn** | `getRandomLocation()` or `getRandomLocationInCluster()` (decide per §"Respawn scope" below) | None — should resolve instantly on death, no gates |

**Respawn scope** — still an open decision: should death respawn stay on the
player's current server only (`getRandomLocation()`), or roam the whole
cluster like RTP does (`getRandomLocationInCluster()`)? Worth deciding before
implementation, since it changes which method Respawn calls.

### Why one shared library instead of three separate implementations
Every module that needs "give me a safe random spot" gets the same
guarantees for free: pools stay warm via the shared refill loop, every
returned location is already proven to respect the border system, and a fix
or improvement to the underlying pool/validation logic (e.g., a better
safe-ground check) automatically benefits all three modules at once instead
of needing to be patched in three places.

## 9. Open Questions

- Target pool size per server — how many locations should be kept ready at
  once? (Depends on expected RTP request rate; 200-500 is a reasonable start.)
- Should the pre-gen worker run inside the game server process itself, or as
  a separate lightweight tool (e.g., using a headless world-loader) so it
  doesn't compete with live gameplay for tick time?