# Cross-Server Ender Pearl Stasis Chambers

## 1. How Stasis Chambers Actually Work

A stasis chamber is a player-built contraption that keeps an ender pearl
entity frozen indefinitely by keeping its chunk permanently unloaded. The
pearl sits frozen in the world file — potentially for days, weeks, or months
— and only resumes its flight the moment its chunk loads again. Since pearl
entities persist across server restarts (they're stored in the world file
like any other entity), no custom tracking or physics simulation is needed.
The pearl handles itself entirely.

When the chunk finally loads and the player is on the correct server,
`PlayerTeleportEvent (ENDER_PEARL)` fires normally — our existing
border-aware teleport interceptor already handles this, including cross-cell
transfers if the landing point is in a neighboring server's territory.

**If the player is offline when the chunk loads**: nothing happens. Minecraft
won't process a teleport for an offline player. The pearl simply stays frozen
until the chunk loads again while the player is actually online and on the
correct server.

---

## 2. The One Case That Needs Custom Handling

The only scenario vanilla can't handle is:

```
Player is currently on Server-EU (or Spawn, or anywhere else on the network)
Their stasis pearl's chunk loads on Server-NA (e.g. another player walks
  near it, or a chunk loader activates)
Pearl entity resumes, fires PlayerTeleportEvent (ENDER_PEARL) on Server-NA
But the player isn't on Server-NA — they're somewhere else on the network
```

Vanilla silently fails here — it tries to teleport a player who isn't
present on this server and nothing happens. The pearl is consumed, the
landing fires, and the player never goes anywhere.

---

## 3. Handling It

```
On PlayerTeleportEvent (cause = ENDER_PEARL) on any survival server:
    if player IS on this server:
        proceed normally — existing border-aware interceptor handles it,
        including cross-cell transfer if the landing crosses a cell boundary.
        Nothing new needed here.

    if player is NOT on this server (they're connected to a different
    backend server on the network):
        cancel the event locally (consume the pearl, don't let vanilla
        fail silently)
        look up which server the player is currently on (via Redis —
        the proxy already publishes connected-server state there)
        store a pending_pearl record in Redis:
            pending_pearl:<player-uuid> -> {
                server: <this server — where the pearl landed>,
                world: <world name>,
                x, y, z, yaw, pitch,
                createdAt
            }
        publish a notification (Redis pub/sub) to the server the player
        is currently on, telling it to transfer the player here to the
        landing coordinates
        the receiving server triggers a cross-server transfer (Transfer
        packet) immediately
```

---

## 4. Multiple Pending Pearls

A player can have multiple stasis chambers built across the network. If more
than one fires while they're on a different server, pending records queue up
as a Redis list (FIFO — oldest landing first):

```
pending_pearl:<player-uuid> -> [ {record1}, {record2}, ... ]
```

Each is applied in order as the player arrives at each destination. Once a
record is consumed (transfer successfully triggered), it is removed from
the list.

---

## 5. Pearl vs Respawn Priority

If the player dies at the moment a stasis pearl fires on a different server,
the pearl landing takes priority — it represents a deliberate action the
player set up. Respawn logic only applies to a subsequent death after they've
arrived at the stasis destination.

---

## 6. Config

```yaml
pearl-stasis:
  # Whether cross-server stasis handling is enabled at all.
  enabled: true
  # How long (seconds) a pending pearl landing record stays valid in Redis
  # before expiring. If it expires before the transfer completes (e.g. the
  # target server is temporarily unreachable), the pearl landing is lost.
  pending-record-ttl-seconds: 300
```

TTL here is short (5 minutes default) unlike the earlier design — since
this is a live transfer to an online player, not a "wait until they log in"
record. If the transfer doesn't complete in 5 minutes something has gone
seriously wrong with the network anyway.
