# Network Setup — NA Survival / EU Survival / End

Ready-to-paste configs for the three backends. Boot each server once so the module folders
regenerate, stop it, paste these in, then start again.

## Which file holds what

Two different files are involved, and neither reads the other's keys:

| Keys | File | Read by |
|---|---|---|
| `network`, `server-role`, `redis`, `database`, `grid`, `border`, `world-border` | `plugins/RavenCore/config.yml` | core, border module, `LocationPoolConfig` (grid half) |
| `location-library` (pool bounds, blacklists) | `plugins/RavenCore/locationpool/config.yml` | `LocationPoolConfig` (bounds half) |

`LocationPoolConfig.load(getConfig(), api.getConfig(), …)` takes the module config *and* the
root config — bounds come from the first, `grid.*` from the second. Putting `location-library`
in the root config.yml does nothing; it is silently ignored.

> The `redis` and `database` blocks below are the shipped defaults and are already correct
> after a regenerate. They contain live credentials — rotate them if this repo ever goes public.

## Grid layout

```
        z=0 ─────────────────────────────────────────────
            │  smp-world-na       │  smp-world-eu       │
            │  cluster: na        │  cluster: eu        │
            │  cell 0:0           │  cell 1:0           │
            │  x 0 … 5000         │  x 5000 … 10000     │
     z=5000 ─────────────────────────────────────────────
```

Both cells sit in one continuous coordinate space, so walking east out of NA transfers to EU.
The cells must not overlap: the registry writes every server into `raven:grid:global:<dim>`
under the field `<cellX>:<cellZ>`, and two servers sharing a cell would overwrite each other.

Nether cells are the overworld cell divided by `nether-ratio` (8): NA `0…625`, EU `625…1250`.
Every pool bound below is its cell inset by `border-safety-margin` (300 overworld,
`ceil(300/8) = 38` nether).

---

# 1. NA Survival — `smp-world-na`

## `plugins/RavenCore/config.yml`

```yml
network:
  server-id: 'smp-world-na'
  server-display-name: 'World NA'
  cluster: 'na'
  # Shared across every survival server so terrain lines up at the borders.
  expected-seed: '2059850848596003338'

# survival — normal survival world plus its own local Nether
server-role: survival

redis:
  host: '172.18.0.2'
  port: 6379
  password: 'v9OvHKGQ3EXLqE0F'
  database: 0

database:
  host: 172.18.0.1
  port: 3306
  name: s10_SMP_DATABASE
  user: u10_Y8LJHuTiM8
  password: 'D@o!DfVIJPOA9DnPGmF9o@Xh'

# This server owns overworld x/z 0..5000, nether x/z 0..625.
grid:
  cell-size: 5000
  nether-ratio: 8
  cell-x: 0
  cell-z: 0
  border-safety-margin: 300

border:
  detection-distance: 30
  particle-spacing: 1.5
  wall-height: 50
  particle: DUST_PLUME
  deny-message: ""

# Outer ring of the map: hard-stops players at the west and north edges so they
# never walk into an unowned cell. See "Outer edges" at the bottom.
world-border:
  enabled: true
  huge-size: 2000000
  damage-amount: 0.2
  damage-buffer: 5.0
  warning-distance: 5
  warning-time: 15
  true-edges:
    west: 0
    north: 0
```

## `plugins/RavenCore/locationpool/config.yml`

Only the `bounds` block needs editing; keep the blacklists that regenerate with the file.

```yml
location-library:
  refill-interval-seconds: 45
  nether-pool-enabled: true
  bounds:
    overworld:
      min: { x: 300, y: 60, z: 300 }
      max: { x: 4700, y: 320, z: 4700 }
    nether:
      min: { x: 38, y: 40, z: 38 }
      max: { x: 587, y: 120, z: 587 }
    # end: never read on a survival server — leave at default
```

---

# 2. EU Survival — `smp-world-eu`

## `plugins/RavenCore/config.yml`

```yml
network:
  server-id: 'smp-world-eu'
  server-display-name: 'World EU'
  cluster: 'eu'
  expected-seed: '2059850848596003338'

server-role: survival

redis:
  host: '172.18.0.2'
  port: 6379
  password: 'v9OvHKGQ3EXLqE0F'
  database: 0

database:
  host: 172.18.0.1
  port: 3306
  name: s10_SMP_DATABASE
  user: u10_Y8LJHuTiM8
  password: 'D@o!DfVIJPOA9DnPGmF9o@Xh'

# This server owns overworld x 5000..10000, z 0..5000; nether x 625..1250, z 0..625.
grid:
  cell-size: 5000
  nether-ratio: 8
  cell-x: 1
  cell-z: 0
  border-safety-margin: 300

border:
  detection-distance: 30
  particle-spacing: 1.5
  wall-height: 50
  particle: DUST_PLUME
  deny-message: ""

world-border:
  enabled: true
  huge-size: 2000000
  damage-amount: 0.2
  damage-buffer: 5.0
  warning-distance: 5
  warning-time: 15
  true-edges:
    east: 10000
    north: 0
```

## `plugins/RavenCore/locationpool/config.yml`

```yml
location-library:
  refill-interval-seconds: 45
  nether-pool-enabled: true
  bounds:
    overworld:
      min: { x: 5300, y: 60, z: 300 }
      max: { x: 9700, y: 320, z: 4700 }
    nether:
      min: { x: 663, y: 40, z: 38 }
      max: { x: 1212, y: 120, z: 587 }
```

---

# 3. End — `smp-end`

Not part of the NA/EU grid: a single shared backend reached only by RTP and End portals.

## `plugins/RavenCore/config.yml`

```yml
network:
  server-id: 'smp-end'
  server-display-name: 'The End'
  # Must be non-empty or the server cannot register in the Redis grid and logs
  # "RTP cluster discovery is unavailable". Its own name keeps it out of na/eu.
  cluster: 'end'
  # Seed check only runs for server-role: survival.
  expected-seed: ''

# end — dedicated End server; all non-THE_END worlds are unloaded
server-role: end

redis:
  host: '172.18.0.2'
  port: 6379
  password: 'v9OvHKGQ3EXLqE0F'
  database: 0

database:
  host: 172.18.0.1
  port: 3306
  name: s10_SMP_DATABASE
  user: u10_Y8LJHuTiM8
  password: 'D@o!DfVIJPOA9DnPGmF9o@Xh'

# No gameplay grid here. These only form the registry field "0:0" under THE_END.
grid:
  cell-size: 5000
  nether-ratio: 8
  cell-x: 0
  cell-z: 0
  border-safety-margin: 300

border:
  detection-distance: 30
  particle-spacing: 1.5
  wall-height: 50
  particle: DUST_PLUME
  deny-message: ""

world-border:
  enabled: false
```

## `plugins/RavenCore/locationpool/config.yml`

Only the `end` block is read for this role, and there is no grid validation, so the bounds
are free-form.

```yml
location-library:
  refill-interval-seconds: 45
  nether-pool-enabled: true
  bounds:
    end:
      min: { x: -5000, y: 35, z: -5000 }
      max: { x: 5000, y: 120, z: 5000 }
```

`min.y: 35` matters. `SafeLocationGenerator` scans each column downward from `maxY - 3` and
stops at `minY`, so a floor of 60 rejects every outer End island whose surface sits below
y 60 — which is most of them.

---

# 4. `plugins/RavenCore/rtp/config.yml` — all three servers

Defaults are already correct; just confirm the End id matches.

```yml
end-server: smp-end
overworld-clusters:
  na:
    display-name: "North America"
  eu:
    display-name: "Europe"
```

Both survival servers and the End need this — the RTP menu reads cluster names from the local
config and resolves the actual servers from Redis.

---

# 5. Verifying

Each server logs on startup:

```
[LocationPool] Registered smp-world-na in Redis grid cluster na at cell 0:0 for [NORMAL, NETHER].
```

Then in `redis-cli`:

```
HGETALL raven:grid:na:NORMAL       → {"0:0": "smp-world-na"}
HGETALL raven:grid:eu:NORMAL       → {"1:0": "smp-world-eu"}
HGETALL raven:grid:global:NORMAL   → {"0:0": "smp-world-na", "1:0": "smp-world-eu"}
HGETALL raven:grid:end:THE_END     → {"0:0": "smp-end"}
LLEN locationpool:smp-world-na:overworld   → climbs toward 50
```

The NA→EU walk-across resolves through that global hash: leaving NA eastward looks up cell
`1:0` in `raven:grid:na:NORMAL`, misses (EU is a different cluster), then falls back to
`raven:grid:global:NORMAL` and finds `smp-world-eu`.

If pool generation refuses to start, the error now names the file and the bounds it loaded:

```
[LocationPool] Generation disabled: LocationPool overworld X/Z bounds [300,300] -> [4700,4700]
from locationpool/config.yml exceed the safe grid interior [5300,300] -> [9700,4700]
for this server's grid cell
```

Bounds that don't match the server you're on means the module config was never updated.

---

# 6. Outer edges — known gap

Crossing into a cell no server owns cancels the move and **kicks** the player
(`BorderMovementListener:132`). With only two cells, each survival server borders unowned
space on three sides:

| Server | West | North | East | South |
|---|---|---|---|---|
| `smp-world-na` | outer | outer | → EU | outer |
| `smp-world-eu` | → NA | outer | outer | outer |

A vanilla world border is square, so a server can clamp only one X side and one Z side —
`TrueWorldBorderConfig` rejects `north` + `south` or `west` + `east` together, and each value
must equal that cell edge exactly or startup throws. The configs above clamp west/east and
north, which leaves **south** (z > 5000) open on both servers: players who walk that way get
kicked rather than stopped.

Options if that matters: swap `north: 0` for `south: 5000` to pick which side stays exposed,
or change `BorderMovementListener` to push the player back instead of kicking when the
neighbour cell is unregistered.
