# Velocity Msg Toggle And Spawn Return Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add proxy-level `/msgtoggle` and a safe spawn-only server return flow for normal backend restarts.

**Architecture:** Velocity owns these features because private messages and backend fallback routing happen at the proxy layer. Redis is the storage layer for Velocity state: permanent player message settings, server maintenance flags, and short-lived pending return intents. Auto-return is allowed only for backend servers whose configured role is `spawn`; combat/gameplay servers never receive automatic returns.

**Tech Stack:** Java 21, Velocity API, Adventure components, Redis/Jedis through Raven common library, Gradle.

## Global Constraints

- Do not add MySQL to Velocity for this phase.
- Store Velocity state in Redis.
- `/msg` and `/r` must respect receiver message toggle state.
- `/msgtoggle` must persist across proxy restarts.
- Auto-return is allowed only when the original backend server role is `spawn`.
- Maintenance mode disables auto-return even for spawn servers.
- Pending return keys must have a TTL.
- Do not return players automatically to survival/combat/gameplay servers.

---

## File Structure

- Create `velocity/src/main/java/com/kammoun/raven/velocity/privatemessage/settings/PrivateMessageSettingsStore.java`
  - Reads and writes `raven:velocity:settings:msg:<uuid>` in Redis.
- Create `velocity/src/main/java/com/kammoun/raven/velocity/privatemessage/command/MsgToggleCommand.java`
  - Toggles the caller's private message receive setting.
- Modify `velocity/src/main/java/com/kammoun/raven/velocity/privatemessage/PrivateMessageModule.java`
  - Owns the settings store, registers `/msgtoggle`, and blocks messages to disabled receivers.
- Create `velocity/src/main/java/com/kammoun/raven/velocity/serverreturn/ServerReturnModule.java`
  - Handles backend disconnect/fallback tracking, server ping polling, maintenance flags, and spawn-only auto-return.
- Create `velocity/src/main/java/com/kammoun/raven/velocity/serverreturn/ServerRoleConfig.java`
  - Reads `servers.<name>.role` from Velocity config.
- Create `velocity/src/main/java/com/kammoun/raven/velocity/serverreturn/ServerReturnStore.java`
  - Stores Redis keys for maintenance and pending returns.
- Modify `velocity/src/main/java/com/kammoun/raven/velocity/RavenVelocity.java`
  - Enables and reloads `ServerReturnModule`.
- Modify `velocity/src/main/resources/velocity-config.yml`
  - Adds server roles and return settings.
- Modify `velocity/src/main/java/com/kammoun/raven/velocity/command/RavenCoreVelocityCommand.java`
  - Adds maintenance commands.

---

### Task 1: Private Message Toggle Storage

**Files:**
- Create: `velocity/src/main/java/com/kammoun/raven/velocity/privatemessage/settings/PrivateMessageSettingsStore.java`
- Test: add tests if the Velocity module test harness supports pure Redis-store fakes; otherwise verify through build and manual proxy test.

**Interfaces:**
- Consumes: `redis.clients.jedis.JedisPool`
- Produces:
  - `boolean receivesMessages(UUID uuid)`
  - `boolean toggle(UUID uuid)`
  - `void setReceivesMessages(UUID uuid, boolean enabled)`

- [ ] **Step 1: Write the failing test**

```java
@Test
void defaultsToReceivingMessagesAndPersistsToggle() {
    FakeRedis redis = new FakeRedis();
    PrivateMessageSettingsStore store = new PrivateMessageSettingsStore(redis.pool());
    UUID uuid = UUID.randomUUID();

    assertTrue(store.receivesMessages(uuid));
    assertFalse(store.toggle(uuid));
    assertFalse(store.receivesMessages(uuid));
    assertTrue(store.toggle(uuid));
    assertTrue(store.receivesMessages(uuid));
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `.\gradlew.bat -g .gradle-codex --init-script .gradle-codex\isolated-build.gradle :velocity:test --tests "*PrivateMessageSettingsStoreTest"`

Expected: FAIL because `PrivateMessageSettingsStore` does not exist.

- [ ] **Step 3: Write minimal implementation**

```java
package com.kammoun.raven.velocity.privatemessage.settings;

import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;

import java.util.UUID;

public class PrivateMessageSettingsStore {
    private static final String KEY_PREFIX = "raven:velocity:settings:msg:";
    private final JedisPool pool;

    public PrivateMessageSettingsStore(JedisPool pool) {
        this.pool = pool;
    }

    public boolean receivesMessages(UUID uuid) {
        try (Jedis jedis = pool.getResource()) {
            String value = jedis.get(KEY_PREFIX + uuid);
            return value == null || Boolean.parseBoolean(value);
        }
    }

    public boolean toggle(UUID uuid) {
        boolean next = !receivesMessages(uuid);
        setReceivesMessages(uuid, next);
        return next;
    }

    public void setReceivesMessages(UUID uuid, boolean enabled) {
        try (Jedis jedis = pool.getResource()) {
            jedis.set(KEY_PREFIX + uuid, Boolean.toString(enabled));
        }
    }
}
```

- [ ] **Step 4: Run test/build to verify**

Run: `.\gradlew.bat -g .gradle-codex --init-script .gradle-codex\isolated-build.gradle :velocity:build`

Expected: BUILD SUCCESSFUL.

- [ ] **Step 5: Commit**

```bash
git add velocity/src/main/java/com/kammoun/raven/velocity/privatemessage/settings
git commit -m "feat: add velocity msg settings store"
```

---

### Task 2: `/msgtoggle` And Message Blocking

**Files:**
- Create: `velocity/src/main/java/com/kammoun/raven/velocity/privatemessage/command/MsgToggleCommand.java`
- Modify: `velocity/src/main/java/com/kammoun/raven/velocity/privatemessage/PrivateMessageModule.java`
- Modify: `velocity/src/main/resources/privatemessage/config.yml`

**Interfaces:**
- Consumes: `PrivateMessageSettingsStore`
- Produces:
  - `boolean receivesMessages(Player player)`
  - `/msgtoggle`

- [ ] **Step 1: Write the failing behavior check**

```java
@Test
void sendDoesNotDeliverWhenTargetDisabledMessages() {
    PrivateMessageModule module = moduleWithStoreReturning(false);
    FakePlayer sender = new FakePlayer("Sender");
    FakePlayer target = new FakePlayer("Target");

    module.send(sender.velocityPlayer(), target.velocityPlayer(), "hello");

    assertTrue(target.messages().isEmpty());
    assertTrue(sender.lastMessagePlain().contains("not receiving private messages"));
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `.\gradlew.bat -g .gradle-codex --init-script .gradle-codex\isolated-build.gradle :velocity:test --tests "*PrivateMessageModuleTest"`

Expected: FAIL because disabled receivers are not checked.

- [ ] **Step 3: Implement command and blocking**

Add command registration:

```java
settingsStore = new PrivateMessageSettingsStore(api.getRedisMessenger().getConnection().getPool());
registerCommand("msgtoggle", new MsgToggleCommand(this), "togglemsg", "pmtoggle");
```

Block disabled targets inside `send`:

```java
if (!receivesMessages(target)) {
    sender.sendMessage(messages.format("target-disabled", "&c%player% is not receiving private messages.",
            "%player%", target.getUsername()));
    return;
}
```

Expose:

```java
public boolean receivesMessages(Player player) {
    return settingsStore.receivesMessages(player.getUniqueId());
}

public boolean toggleMessages(Player player) {
    return settingsStore.toggle(player.getUniqueId());
}
```

Command:

```java
public final class MsgToggleCommand implements SimpleCommand {
    private final PrivateMessageModule module;

    public MsgToggleCommand(PrivateMessageModule module) {
        this.module = module;
    }

    @Override
    public void execute(Invocation invocation) {
        if (!(invocation.source() instanceof Player player)) {
            invocation.source().sendMessage(Component.text("Only players can use this command."));
            return;
        }
        boolean enabled = module.toggleMessages(player);
        player.sendMessage(module.getMessages().format(enabled ? "toggle-on" : "toggle-off",
                enabled ? "&aPrivate messages enabled." : "&cPrivate messages disabled."));
    }
}
```

- [ ] **Step 4: Run build**

Run: `.\gradlew.bat -g .gradle-codex --init-script .gradle-codex\isolated-build.gradle :velocity:build`

Expected: BUILD SUCCESSFUL.

- [ ] **Step 5: Commit**

```bash
git add velocity/src/main/java/com/kammoun/raven/velocity/privatemessage velocity/src/main/resources/privatemessage/config.yml
git commit -m "feat: add velocity msg toggle"
```

---

### Task 3: Spawn-Only Return Store

**Files:**
- Create: `velocity/src/main/java/com/kammoun/raven/velocity/serverreturn/ServerReturnStore.java`

**Interfaces:**
- Consumes: `JedisPool`
- Produces:
  - `void setMaintenance(String server, boolean enabled)`
  - `boolean isMaintenance(String server)`
  - `void saveReturn(UUID player, String server, long ttlSeconds)`
  - `Optional<String> consumeReturn(UUID player)`

- [ ] **Step 1: Write the failing test**

```java
@Test
void pendingReturnIsConsumedOnce() {
    ServerReturnStore store = new ServerReturnStore(fakeRedis.pool());
    UUID uuid = UUID.randomUUID();

    store.saveReturn(uuid, "smp-spawn-1", 600);

    assertEquals(Optional.of("smp-spawn-1"), store.consumeReturn(uuid));
    assertEquals(Optional.empty(), store.consumeReturn(uuid));
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `.\gradlew.bat -g .gradle-codex --init-script .gradle-codex\isolated-build.gradle :velocity:test --tests "*ServerReturnStoreTest"`

Expected: FAIL because `ServerReturnStore` does not exist.

- [ ] **Step 3: Implement Redis keys**

```java
public class ServerReturnStore {
    private static final String MAINTENANCE_PREFIX = "raven:velocity:maintenance:";
    private static final String RETURN_PREFIX = "raven:velocity:return:";
    private final JedisPool pool;

    public ServerReturnStore(JedisPool pool) {
        this.pool = pool;
    }

    public void setMaintenance(String server, boolean enabled) {
        try (Jedis jedis = pool.getResource()) {
            if (enabled) jedis.set(MAINTENANCE_PREFIX + server, "true");
            else jedis.del(MAINTENANCE_PREFIX + server);
        }
    }

    public boolean isMaintenance(String server) {
        try (Jedis jedis = pool.getResource()) {
            return Boolean.parseBoolean(jedis.get(MAINTENANCE_PREFIX + server));
        }
    }

    public void saveReturn(UUID player, String server, long ttlSeconds) {
        try (Jedis jedis = pool.getResource()) {
            jedis.setex(RETURN_PREFIX + player, (int) ttlSeconds, server);
        }
    }

    public Optional<String> consumeReturn(UUID player) {
        try (Jedis jedis = pool.getResource()) {
            return Optional.ofNullable(jedis.getDel(RETURN_PREFIX + player));
        }
    }
}
```

- [ ] **Step 4: Run build**

Run: `.\gradlew.bat -g .gradle-codex --init-script .gradle-codex\isolated-build.gradle :velocity:build`

Expected: BUILD SUCCESSFUL.

- [ ] **Step 5: Commit**

```bash
git add velocity/src/main/java/com/kammoun/raven/velocity/serverreturn/ServerReturnStore.java
git commit -m "feat: add velocity return store"
```

---

### Task 4: Server Roles And Safe Return Logic

**Files:**
- Create: `velocity/src/main/java/com/kammoun/raven/velocity/serverreturn/ServerRoleConfig.java`
- Create: `velocity/src/main/java/com/kammoun/raven/velocity/serverreturn/ServerReturnModule.java`
- Modify: `velocity/src/main/resources/velocity-config.yml`
- Modify: `velocity/src/main/java/com/kammoun/raven/velocity/RavenVelocity.java`

**Interfaces:**
- Consumes:
  - `servers.<server>.role` from Velocity config
  - `ServerReturnStore`
- Produces:
  - spawn-only pending return records
  - fallback transfer when backend disconnects
  - polling return when server comes back online

- [ ] **Step 1: Write role decision test**

```java
@Test
void onlySpawnServersAreEligibleForAutoReturn() {
    ServerRoleConfig roles = ServerRoleConfig.from(Map.of(
            "servers", Map.of(
                    "smp-spawn-1", Map.of("role", "spawn"),
                    "smp-world-na", Map.of("role", "survival")
            )
    ));

    assertTrue(roles.canAutoReturn("smp-spawn-1"));
    assertFalse(roles.canAutoReturn("smp-world-na"));
    assertFalse(roles.canAutoReturn("unknown"));
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `.\gradlew.bat -g .gradle-codex --init-script .gradle-codex\isolated-build.gradle :velocity:test --tests "*ServerRoleConfigTest"`

Expected: FAIL because `ServerRoleConfig` does not exist.

- [ ] **Step 3: Implement roles**

```java
public final class ServerRoleConfig {
    private final Map<String, String> roles;

    public ServerRoleConfig(Map<String, String> roles) {
        this.roles = Map.copyOf(roles);
    }

    public boolean canAutoReturn(String server) {
        return "spawn".equalsIgnoreCase(roles.get(server));
    }
}
```

- [ ] **Step 4: Add config**

```yaml
server-return:
  enabled: true
  pending-return-ttl-seconds: 600
  poll-interval-seconds: 5
  fallback-servers:
    - smp-spawn-1

servers:
  smp-spawn-1:
    role: spawn
  smp-world-na:
    role: survival
```

- [ ] **Step 5: Implement module event flow**

Use Velocity connection events:

```java
@Subscribe
public void onServerDisconnect(ServerDisconnectEvent event) {
    String original = event.getServer().getServerInfo().getName();
    if (!roles.canAutoReturn(original)) return;
    if (store.isMaintenance(original)) return;
    store.saveReturn(event.getPlayer().getUniqueId(), original, ttlSeconds);
    event.getPlayer().sendActionBar(Component.text("Server restarting. You will be returned when it is online."));
}
```

Poll online status:

```java
server.getScheduler().buildTask(plugin, this::pollPendingReturns)
        .repeat(Duration.ofSeconds(pollIntervalSeconds))
        .schedule();
```

Ping registered server before returning:

```java
registeredServer.ping().thenRun(() ->
        player.createConnectionRequest(registeredServer).connect()
);
```

- [ ] **Step 6: Run build**

Run: `.\gradlew.bat -g .gradle-codex --init-script .gradle-codex\isolated-build.gradle :velocity:build`

Expected: BUILD SUCCESSFUL.

- [ ] **Step 7: Commit**

```bash
git add velocity/src/main/java/com/kammoun/raven/velocity/serverreturn velocity/src/main/java/com/kammoun/raven/velocity/RavenVelocity.java velocity/src/main/resources/velocity-config.yml
git commit -m "feat: add spawn-only server return"
```

---

### Task 5: Maintenance Commands

**Files:**
- Modify: `velocity/src/main/java/com/kammoun/raven/velocity/command/RavenCoreVelocityCommand.java`
- Modify: `velocity/src/main/java/com/kammoun/raven/velocity/serverreturn/ServerReturnModule.java`

**Interfaces:**
- Consumes: `ServerReturnStore`
- Produces:
  - `/ravencorevelo maintenance <server> on`
  - `/ravencorevelo maintenance <server> off`
  - `/ravencorevelo maintenance <server> status`

- [ ] **Step 1: Write command behavior test**

```java
@Test
void maintenanceOnPersistsServerFlag() {
    ServerReturnStore store = fakeStore();
    RavenCoreVelocityCommand command = commandWithReturnStore(store);

    command.execute(invocation("maintenance", "smp-spawn-1", "on"));

    assertTrue(store.isMaintenance("smp-spawn-1"));
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `.\gradlew.bat -g .gradle-codex --init-script .gradle-codex\isolated-build.gradle :velocity:test --tests "*RavenCoreVelocityCommandTest"`

Expected: FAIL because maintenance subcommand is missing.

- [ ] **Step 3: Implement subcommand**

```java
if (args.length == 3 && args[0].equalsIgnoreCase("maintenance")) {
    String serverName = args[1];
    String mode = args[2];
    if (mode.equalsIgnoreCase("on")) {
        plugin.getServerReturnModule().setMaintenance(serverName, true);
        source.sendMessage(Component.text("Maintenance enabled for " + serverName));
        return;
    }
    if (mode.equalsIgnoreCase("off")) {
        plugin.getServerReturnModule().setMaintenance(serverName, false);
        source.sendMessage(Component.text("Maintenance disabled for " + serverName));
        return;
    }
    if (mode.equalsIgnoreCase("status")) {
        boolean enabled = plugin.getServerReturnModule().isMaintenance(serverName);
        source.sendMessage(Component.text(serverName + " maintenance: " + enabled));
        return;
    }
}
```

- [ ] **Step 4: Run full build**

Run: `.\gradlew.bat -g .gradle-codex --init-script .gradle-codex\isolated-build.gradle clean build`

Expected: BUILD SUCCESSFUL.

- [ ] **Step 5: Commit**

```bash
git add velocity/src/main/java/com/kammoun/raven/velocity/command/RavenCoreVelocityCommand.java velocity/src/main/java/com/kammoun/raven/velocity/serverreturn
git commit -m "feat: add velocity maintenance command"
```

---

## Manual Acceptance Checklist

- [ ] `/msg <player> <message>` works when receiver has messages enabled.
- [ ] `/msgtoggle` disables receiving messages and survives proxy restart.
- [ ] `/msg` tells sender when receiver disabled private messages.
- [ ] `/r` follows the same block rule.
- [ ] Restarting `smp-spawn-1` moves players to fallback and returns them when it is online.
- [ ] Restarting `smp-world-na` moves players to fallback and does not return them.
- [ ] `maintenance smp-spawn-1 on` prevents return even for spawn.
- [ ] `maintenance smp-spawn-1 off` allows normal spawn return again.
