# LocationPool

Core module that owns and maintains the pools of pre-validated random
locations used by **RTP**, **RTP-Zone**, and **Respawn**. This module does
not implement any player-facing command or rule — it only generates, stores,
and hands out safe coordinates on request.

---

## 1. Purpose

Scanning for a safe random location live, on every request, is slow and can
cause lag — especially on Folia, where a target coordinate is almost always
outside the region the request originated in. LocationPool avoids this by
keeping a small set of already-validated locations ready in Redis at all
times, per server, per dimension.

---

## 2. Storage Schema (Redis)

One list per server, per dimension:

```
locationpool:<server-id>:overworld  -> ["1204,71,-3390", "890,64,410", ...]
locationpool:<server-id>:nether     -> ["150,80,-420", "-92,71,301", ...]
```

Entries are plain `x,y,z` strings — cheap to store and parse. Pops use
`RPOP`/`LPOP` so they're atomic: two simultaneous requests can never receive
the same coordinate.

Zone-scoped pools (used by RTP-Zone) follow the same pattern under a
separate key namespace:

```
locationpool:zone:<zone-id>:overworld -> [...]
locationpool:zone:<zone-id>:nether    -> [...]
```

---

## 3. Generation Rules

A candidate location is only added to a pool if it passes all of the
following:

1. **Inside bounds** — strictly within the owning server's grid cell (from
   `grid.cell-x` / `grid.cell-z` / `grid.cell-size` in `config.yml`), pulled
   in on every side by `grid.border-safety-margin`. Nether candidates use the
   cell size divided by `grid.nether-ratio`. A candidate is discarded
   immediately if it falls outside this box — there is no partial or
   edge-case acceptance.
2. **Safe ground** — the highest solid block at that column isn't lava,
   isn't water (unless explicitly allowed), and isn't void.
   2a. **Not a blacklisted block** — the landing block (and any block the
   player would stand in/on) isn't in `location-library.blacklisted-blocks`.
   This covers both surface hazards (water, lava, cactus) and unstandable
   blocks (leaves, air, void air, bedrock, kelp) — see full list in §6.
   2b. **Not a blacklisted biome** — the biome at the candidate column isn't in
   `location-library.blacklisted-biomes` (e.g. excluding `BEACH` if you don't
   want players landing on open sand next to water).
3. **No trap risk** — enough vertical clearance above the landing spot that
   the player doesn't spawn inside an overhang or a 1-block gap.
4. **Not claimed/protected** — if a claims plugin is present, the candidate
   must not fall inside any existing claim.
5. *(Optional, if enabled)* — minimum distance from other players' claims or
   builds, so RTP/Respawn don't drop players in the middle of someone's base.

Any candidate that fails is discarded and generation simply tries again — no
partial credit for "close enough."

---

## 4. Lifecycle

### Startup
On boot, before RTP/Respawn/RTP-Zone accept any requests, LocationPool fills
every pool it owns (overworld + nether, for this server, plus any zones
defined on it) from empty up to `location-library.pool-target-size`
(default 50).

### Ongoing Refill
Every `location-library.refill-interval-seconds` (default 45s), each pool's
current size is checked (`LLEN`). If it's below target, new candidates are
generated until it's back at target — capped at
`location-library.max-refill-per-cycle` (default 10) new locations per cycle,
so a heavily-drained pool refills gradually instead of in one expensive burst.

### On Empty
If a pool is fully drained and a request comes in before the next refill
cycle, the caller (RTP, Respawn, etc.) decides the fallback — LocationPool
itself does not queue or block requests. Typical fallback: pick a different
server in the cluster, or trigger a one-off live scan as a last resort.

---

## 5. Public API

```java
interface LocationPool {

    // Random location from this server's own pool.
    RtpLocation getRandomLocation(String serverId, Dimension dimension);

    // Random location from any server in the given cluster — picks a
    // server uniformly at random, then pops from its pool.
    RtpLocation getRandomLocationInCluster(String cluster, Dimension dimension);

    // Random location from a named zone's own pool.
    RtpLocation getRandomLocationInZone(String zoneId, Dimension dimension);

    // Current pool size, for monitoring/alerting.
    int getPoolSize(String serverId, Dimension dimension);
}
```

No cooldowns, costs, warmups, or permission checks live here — those belong
entirely to the calling module (RTP, RTP-Zone, Respawn). LocationPool's only
job is: *"give me a coordinate that's already proven safe and in-bounds."*

If the returned location is on a different server than the caller,
`LocationPool` transparently triggers the cross-server transfer — the caller
doesn't need to handle that itself.

---

## 6. Config Reference

All tunables live in each server's `config.yml`:

```yaml
grid:
  cell-size: 5000
  nether-ratio: 8
  cell-x: 0
  cell-z: 0
  border-safety-margin: 300

location-library:
  pool-target-size: 50
  refill-interval-seconds: 45
  max-refill-per-cycle: 10
  nether-pool-enabled: true
  # Blocks a candidate location will never be allowed to land on/in.
  # Full material list: https://hub.spigotmc.org/javadocs/bukkit/org/bukkit/Material.html
  blacklisted-blocks:
    - STATIONARY_WATER
    - STATIONARY_LAVA
    - WATER
    - FLOWING_WATER
    - LAVA
    - FLOWING_LAVA
    - CACTUS
    - LEAVES
    - LEAVES_2
    - AIR
    - VOID_AIR
    - BEDROCK
    - OAK_LEAVES
    - JUNGLE_LEAVES
    - KELP
  # Biomes a candidate location will never be allowed to land in.
  blacklisted-biomes:
    - BEACH
```

---

## 7. Monitoring

Track `getPoolSize()` per server/dimension and alert if it drops near zero
between refill cycles — this signals refill isn't keeping up with request
volume, before players start hitting fallback behavior.