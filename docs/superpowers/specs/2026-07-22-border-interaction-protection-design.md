# Border Interaction Protection Design

## Goal

Prevent players from directly interacting with blocks outside the current server's virtual grid cell. The protection applies to every player with no permission bypass.

The existing border-transfer behavior remains unchanged. This feature only protects blocks owned by another virtual cell while a player is still connected to the current backend server.

## Scope

The protection blocks:

- breaking a block outside the local cell;
- placing a block outside the local cell;
- left- or right-clicking a block outside the local cell, including containers, doors, buttons, levers, beds, crafting blocks, and other usable blocks.

The protection does not block:

- entity interaction;
- projectiles;
- explosions;
- fluid flow;
- pistons;
- redstone propagation;
- other indirect effects.

Only Overworld (`NORMAL`) and Nether (`NETHER`) worlds are managed. Other dimensions are unaffected.

## Architecture

Add a dedicated `BorderInteractionListener` under the Bukkit border module. The listener remains separate from `BorderMovementListener` so interaction protection and player transfer have independent responsibilities.

The listener receives the current `BorderConfig`. For each supported event, it obtains the affected block's world environment and coordinates, resolves the local `GridCell` through `BorderConfig.cellFor`, and cancels the event when `GridCell.contains` returns false.

`BorderModule` constructs and registers the listener during module enable.

## Event Behavior

### Block breaking

For `BlockBreakEvent`, inspect the block being broken. Cancel the event if that block is outside the local cell.

### Block placement

For `BlockPlaceEvent`, inspect the newly placed block. Cancel the event if that block is outside the local cell.

### Block interaction

For `PlayerInteractEvent`, act only when `getClickedBlock()` is non-null. Cancel the event if the clicked block is outside the local cell. This covers both physical use and explicit left/right clicks without maintaining a material allow-list.

The listener ignores events already cancelled by another plugin.

## Boundaries

Cell boundaries preserve the existing `GridCell` contract:

- minimum X and Z are inclusive;
- maximum X and Z are exclusive;
- negative cell coordinates are supported;
- Nether cells use `cell-size / nether-ratio`.

The block's integer X/Z coordinates are checked directly. A block at `maxX` or `maxZ` belongs to the neighboring cell and is protected.

## Player Feedback

Protection is silent when `border.deny-message` is empty. When it is non-empty, send that existing configured message to the player after cancelling the event. No new configuration key is introduced.

## Reload Behavior

The listener must observe the latest border configuration after `/ravencore reload borders`. It must not retain an obsolete `BorderConfig` instance. The module will provide the current configuration to the listener at event time rather than copying the initial configuration permanently.

This also corrects the same stale-configuration risk for the new protection without expanding scope into movement-listener refactoring.

## Failure Behavior

If the world environment is unsupported, no protection is applied. No Redis lookup or network operation is required for interaction protection, so Redis availability does not affect these checks.

## Testing

Unit tests will cover the boundary decision independently of the Bukkit event dispatcher:

- a block inside the cell is allowed;
- blocks on inclusive minimum edges are allowed;
- blocks on exclusive maximum edges are denied;
- blocks below minimum edges are denied;
- negative grid cells are calculated correctly;
- Nether boundaries use the configured ratio;
- unsupported dimensions are allowed.

Listener wiring will be verified through compilation and targeted inspection. Existing project tests will also be run when the private RavenSell dependency is resolvable.
