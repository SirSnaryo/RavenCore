 # Seamless Cross-Server Border System — Technical Design

## 1. Goal

Multiple backend servers per region (NA cluster, EU cluster), each covering a fixed
grid cell of one continuous virtual world. Players should be able to:

- Walk across a server's edge and be seamlessly transferred to the neighboring
  server, landing at the equivalent coordinate — no spawn reset, minimal stutter.
- Have this work independently in **both the Overworld and the Nether**, with the
  Nether grid scaled 1:8 against the Overworld grid (vanilla Nether/Overworld ratio).
- Be caught by **any out-of-bounds movement**, not just walking — portals,
  `/tp`, elytra, ender pearls, plugin-triggered teleports, etc. — and redirected
  to whichever server actually owns that destination coordinate.

Servers marked `server-role: survival` in RavenCore are the only ones this applies to
(each such server bundles its own local Overworld + Nether, per your existing config).

---

## 2. Core Components

### 2.1 Region Registry
A shared, network-wide source of truth mapping **(grid cluster, dimension, cell X, cell Z)
→ server ID**. This must be readable by every survival server and by the proxy.

Recommended: a Redis hash (fast, already in your stack) synced from a JSON/YAML
config that ops edit when adding new servers to the grid.

```yaml
# region-registry.yml (source config, pushed to Redis on change)
grid:
  cell_size: 5000       # blocks per cell edge, per dimension (overworld value)
  nether_ratio: 8        # overworld:nether scale — nether cell = cell_size / ratio

clusters:
  na:
    servers:
      smp-world-na-1: { cell_x: 0, cell_z: 0 }
      smp-world-na-2: { cell_x: 1, cell_z: 0 }
      smp-world-na-3: { cell_x: 0, cell_z: 1 }
      # ...
  eu:
    servers:
      smp-world-eu-1: { cell_x: 0, cell_z: 0 }
      smp-world-eu-2: { cell_x: 1, cell_z: 0 }
      # ...
```

Each server also needs to know its **own** cell coordinates and cluster on startup
(read from this file or injected via an env var/RavenCore field), so it can compute
its local boundary without querying every other server.

### 2.2 Boundary/Movement Listener (per survival server)
Runs on a scheduled tick (Folia: use the **region scheduler**, not the global
scheduler, since this must run per-region where the entity actually lives — a
global-scheduler task will not have authority over regionized entities in Folia).

Logic per tick, per online player:
1. Get player's current dimension (OVERWORLD / NETHER) and coordinates.
2. Compute the server's local boundary box for that dimension (Nether boundary =
   Overworld boundary ÷ 8, rounded to this server's assigned cell).
3. If the player is outside that box → look up the registry for the neighboring
   cell in the direction they exited → trigger transfer (§2.4).

### 2.3 Portal / Teleport Interceptor
Separate from the movement listener — this must hook:
- `PlayerPortalEvent` (Nether portals, End portals if ever extended)
- `PlayerTeleportEvent` (covers plugin/command teleports, ender pearls' internal
  teleport, elytra-boosted long jumps that could skip past the tick-based border
  check if unhandled)

For each, **before** completing the teleport: compute the destination coordinate
and dimension, resolve it against the registry, and if it resolves to a *different*
server than the current one, cancel the local teleport and issue a cross-server
transfer with the target coordinates instead.

This is the more failure-prone part of the system — anything that can move a
player instantly (commands, other plugins, minecarts on rails crossing very
fast) needs to be individually audited, since a tick-based-only check can miss
fast movement between ticks.

### 2.4 Cross-Server Transfer
Use the **Minecraft 1.20.5+ Transfer packet** via Velocity, not a raw `/server`
switch — it avoids a full disconnect/reconnect and is materially smoother for
players.

Sequence:
1. Source server sends a plugin message to Velocity: target server ID + exact
   destination (x, y, z, yaw, pitch, dimension).
2. Velocity issues the Transfer packet, moving the player's connection to the
   target backend.
3. Target server, on player join, checks for a pending "incoming transfer"
   record (Redis, short TTL) and places the player at the exact coordinate
   instead of world spawn.

### 2.5 Chunk Pre-loading Near Borders
Without this, every crossing causes a visible chunk-load stutter. Each server
should keep a buffer strip of chunks loaded along its *own* edge continuously
(ticket-based chunk loading, not full always-on — a few chunks deep is enough).
This doesn't eliminate the target server's own load time on arrival, but avoids
the "falling through unloaded terrain" problem right at the boundary.

---

## 3. Open Design Decisions (need your input before build starts)

- **Cell size**: how many blocks per server's Overworld region? This directly
  drives Nether cell size (÷8) and total server count for a given world size.
- **Grid shape**: fixed rectangular grid (like the Donut SMP reference image) or
  irregular per-cluster shapes?
- **Fallback behavior**: what happens if a player crosses into a cell whose
  server is offline/crashed? (Suggest: block the crossing and nudge them back,
  rather than losing them — silently failing here risks stranding players.)
- **Elytra/high-speed edge case**: do you want a *predictive* check (extrapolate
  velocity forward a tick or two) in addition to the reactive tick check, to
  avoid a player skipping past the border check entirely at high speed?

---

## 4. Build Complexity Note

This is a substantial custom build: a Folia-aware plugin (region scheduler
correctness matters a lot here), a Velocity-side companion plugin for the
Transfer packet handling, and a shared registry service. Expect this to be a
multi-week project for an experienced Paper/Folia + Velocity plugin developer,
not a quick script.