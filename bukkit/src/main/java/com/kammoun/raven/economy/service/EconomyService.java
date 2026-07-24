package com.kammoun.raven.economy.service;

import com.kammoun.raven.RavenCore;
import com.kammoun.raven.economy.log.EconomyLogRepository;
import com.kammoun.raven.economy.model.EconomyReason;
import com.kammoun.raven.economy.model.EconomyTransactionLog;
import com.kammoun.raven.economy.model.EconomyTransactionResult;
import com.kammoun.raven.economy.model.EconomyTransactionType;
import com.kammoun.raven.library.redis.event.BalanceUpdateEvent;
import com.kammoun.raven.library.redis.event.NetworkEventBus;
import org.bukkit.Bukkit;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class EconomyService {

    private static final String KEY_PREFIX = "raven:economy:balance:";
    private static final String TX_PREFIX = "raven:economy:tx:processed:";
    private static final int TX_TTL_SECONDS = 86_400;

    private static final String TRANSFER_SCRIPT = """
            if redis.call('SETNX', KEYS[3], '1') == 0 then
              return {0, 0, 0, 0, 0, 'DUPLICATE'}
            end
            redis.call('EXPIRE', KEYS[3], tonumber(ARGV[3]))
            local amount = tonumber(ARGV[1])
            local starting = tonumber(ARGV[2])
            local fromBal = tonumber(redis.call('GET', KEYS[1]) or ARGV[2])
            local toBal = tonumber(redis.call('GET', KEYS[2]) or ARGV[2])
            if amount <= 0 then
              return {0, fromBal, fromBal, toBal, toBal, 'INVALID_AMOUNT'}
            end
            if fromBal < amount then
              return {0, fromBal, fromBal, toBal, toBal, 'INSUFFICIENT_FUNDS'}
            end
            local fromAfter = fromBal - amount
            local toAfter = toBal + amount
            redis.call('SET', KEYS[1], tostring(fromAfter))
            redis.call('SET', KEYS[2], tostring(toAfter))
            return {1, fromBal, fromAfter, toBal, toAfter, 'OK'}
            """;

    private static final String ADJUST_SCRIPT = """
            if redis.call('SETNX', KEYS[2], '1') == 0 then
              return {0, 0, 0, 'DUPLICATE'}
            end
            redis.call('EXPIRE', KEYS[2], tonumber(ARGV[4]))
            local mode = ARGV[1]
            local amount = tonumber(ARGV[2])
            local starting = tonumber(ARGV[3])
            local before = tonumber(redis.call('GET', KEYS[1]) or ARGV[3])
            if amount < 0 then
              return {0, before, before, 'INVALID_AMOUNT'}
            end
            local after = before
            if mode == 'DEPOSIT' then
              after = before + amount
            elseif mode == 'WITHDRAW' then
              if before < amount then
                return {0, before, before, 'INSUFFICIENT_FUNDS'}
              end
              after = before - amount
            elseif mode == 'SET' then
              after = amount
            else
              return {0, before, before, 'INVALID_MODE'}
            end
            redis.call('SET', KEYS[1], tostring(after))
            return {1, before, after, 'OK'}
            """;

    private final RavenCore plugin;
    private final JedisPool jedisPool;
    private final NetworkEventBus eventBus;
    private final String serverId;
    private final String serverDisplayName;
    private final double startingBalance;
    private final EconomyLogRepository logRepository;
    private final Map<UUID, Double> cache = new ConcurrentHashMap<>();

    public EconomyService(RavenCore plugin, JedisPool jedisPool, NetworkEventBus eventBus,
                          String serverId, String serverDisplayName, double startingBalance,
                          EconomyLogRepository logRepository) {
        this.plugin = plugin;
        this.jedisPool = jedisPool;
        this.eventBus = eventBus;
        this.serverId = serverId;
        this.serverDisplayName = serverDisplayName;
        this.startingBalance = startingBalance;
        this.logRepository = logRepository;

        eventBus.subscribe(BalanceUpdateEvent.class, this::onRemoteBalanceUpdate);
    }

    // Called on AsyncPlayerPreLoginEvent — already on an async thread
    public void loadPlayer(UUID uuid) {
        try (Jedis jedis = jedisPool.getResource()) {
            String val = jedis.get(KEY_PREFIX + uuid);
            if (val == null) {
                jedis.set(KEY_PREFIX + uuid, String.valueOf(startingBalance));
                cache.put(uuid, startingBalance);
            } else {
                cache.put(uuid, Double.parseDouble(val));
            }
        } catch (Exception e) {
            plugin.getLogger().warning("[Economy] Failed to load balance for " + uuid + ": " + e.getMessage());
            cache.put(uuid, startingBalance);
        }
    }

    // Do not write cached balances on quit. Redis is the source of truth; writing a
    // stale local cache during cross-server movement can duplicate or erase money.
    public void unloadPlayer(UUID uuid) {
        cache.remove(uuid);
    }

    public double getBalance(UUID uuid) {
        Double cached = cache.get(uuid);
        if (cached != null) return cached;
        try (Jedis jedis = jedisPool.getResource()) {
            String val = jedis.get(KEY_PREFIX + uuid);
            return val != null ? Double.parseDouble(val) : startingBalance;
        } catch (Exception e) {
            plugin.getLogger().warning("[Economy] Failed to get balance for " + uuid + ": " + e.getMessage());
            return startingBalance;
        }
    }

    public boolean hasAccount(UUID uuid) {
        if (cache.containsKey(uuid)) return true;
        try (Jedis jedis = jedisPool.getResource()) {
            return jedis.exists(KEY_PREFIX + uuid);
        } catch (Exception e) {
            return false;
        }
    }

    public boolean createAccount(UUID uuid) {
        if (hasAccount(uuid)) return false;
        try (Jedis jedis = jedisPool.getResource()) {
            jedis.setnx(KEY_PREFIX + uuid, String.valueOf(startingBalance));
        } catch (Exception e) {
            return false;
        }
        return true;
    }

    public boolean setBalance(UUID uuid, double amount) {
        return setBalance(uuid, amount, EconomyReason.system("Set balance"), "System", null, null).success();
    }

    public boolean deposit(UUID uuid, double amount) {
        return deposit(uuid, amount, EconomyReason.vault(EconomyTransactionType.VAULT_DEPOSIT), "Vault API", null, null).success();
    }

    public boolean withdraw(UUID uuid, double amount) {
        return withdraw(uuid, amount, EconomyReason.vault(EconomyTransactionType.VAULT_WITHDRAW), "Vault API", null, null).success();
    }

    public EconomyTransactionResult deposit(UUID uuid, double amount, EconomyReason reason,
                                            String actorName, UUID actorUuid, String targetName) {
        return adjust("DEPOSIT", uuid, amount, reason, actorName, actorUuid, targetName);
    }

    public EconomyTransactionResult withdraw(UUID uuid, double amount, EconomyReason reason,
                                             String actorName, UUID actorUuid, String targetName) {
        return adjust("WITHDRAW", uuid, amount, reason, actorName, actorUuid, targetName);
    }

    public EconomyTransactionResult setBalance(UUID uuid, double amount, EconomyReason reason,
                                               String actorName, UUID actorUuid, String targetName) {
        return adjust("SET", uuid, amount, reason, actorName, actorUuid, targetName);
    }

    public EconomyTransactionResult transfer(UUID senderUuid, UUID targetUuid, double amount, EconomyReason reason,
                                             String senderName, String targetName, String rawCommand) {
        if (!validAmount(amount)) {
            return EconomyTransactionResult.failure(senderUuid, targetUuid, amount, "INVALID_AMOUNT");
        }

        String transactionId = UUID.randomUUID().toString();
        try (Jedis jedis = jedisPool.getResource()) {
            List<String> keys = List.of(KEY_PREFIX + senderUuid, KEY_PREFIX + targetUuid, TX_PREFIX + transactionId);
            List<String> args = List.of(String.valueOf(amount), String.valueOf(startingBalance), String.valueOf(TX_TTL_SECONDS));
            @SuppressWarnings("unchecked")
            List<Object> response = (List<Object>) jedis.eval(TRANSFER_SCRIPT, keys, args);
            boolean success = asLong(response.get(0)) == 1L;
            double senderBefore = asDouble(response.get(1));
            double senderAfter = asDouble(response.get(2));
            double targetBefore = asDouble(response.get(3));
            double targetAfter = asDouble(response.get(4));
            String status = String.valueOf(response.get(5));

            EconomyTransactionResult result = new EconomyTransactionResult(success, transactionId, senderUuid, targetUuid,
                    amount, senderBefore, senderAfter, targetBefore, targetAfter, success ? null : status);
            if (success) {
                cache.put(senderUuid, senderAfter);
                cache.put(targetUuid, targetAfter);
                publish(senderUuid, senderAfter, -amount);
                publish(targetUuid, targetAfter, amount);
                logPay(result, reason, senderName, targetName, rawCommand);
            } else {
                logFailure(transactionId, reason.type(), senderUuid, senderName, senderUuid, senderName,
                        amount, senderBefore, senderAfter, reason.reason(), rawCommand, status);
            }
            return result;
        } catch (Exception e) {
            plugin.getLogger().warning("[Economy] Transfer failed for " + senderUuid + " -> " + targetUuid + ": " + e.getMessage());
            return EconomyTransactionResult.failure(senderUuid, targetUuid, amount, "REDIS_ERROR");
        }
    }

    public void shutdown() {
        eventBus.unsubscribe(BalanceUpdateEvent.class);
    }

    private EconomyTransactionResult adjust(String mode, UUID uuid, double amount, EconomyReason reason,
                                            String actorName, UUID actorUuid, String targetName) {
        if (!validAmount(amount)) {
            return EconomyTransactionResult.failure(uuid, null, amount, "INVALID_AMOUNT");
        }

        String transactionId = UUID.randomUUID().toString();
        try (Jedis jedis = jedisPool.getResource()) {
            List<String> keys = List.of(KEY_PREFIX + uuid, TX_PREFIX + transactionId);
            List<String> args = List.of(mode, String.valueOf(amount), String.valueOf(startingBalance), String.valueOf(TX_TTL_SECONDS));
            @SuppressWarnings("unchecked")
            List<Object> response = (List<Object>) jedis.eval(ADJUST_SCRIPT, keys, args);
            boolean success = asLong(response.get(0)) == 1L;
            double before = asDouble(response.get(1));
            double after = asDouble(response.get(2));
            String status = String.valueOf(response.get(3));
            double delta = after - before;

            EconomyTransactionResult result = new EconomyTransactionResult(success, transactionId, uuid, null,
                    amount, before, after, 0D, 0D, success ? null : status);
            if (success) {
                cache.put(uuid, after);
                publish(uuid, after, delta);
            }
            log(transactionId, reason.type(), uuid, targetName, actorUuid, actorName, delta == 0 ? amount : delta,
                    before, after, reason.reason(), reason.rawCommand(), success, success ? null : status);
            return result;
        } catch (Exception e) {
            plugin.getLogger().warning("[Economy] Balance adjustment failed for " + uuid + ": " + e.getMessage());
            return EconomyTransactionResult.failure(uuid, null, amount, "REDIS_ERROR");
        }
    }

    private void publish(UUID uuid, double newBalance, double delta) {
        BalanceUpdateEvent event = new BalanceUpdateEvent();
        event.setUuid(uuid.toString());
        event.setNewBalance(newBalance);
        event.setDelta(delta);
        eventBus.publish(event);
    }

    private void logPay(EconomyTransactionResult result, EconomyReason reason, String senderName, String targetName, String rawCommand) {
        log(result.transactionId() + ":debit", reason.type(), result.primaryUuid(), senderName,
                result.primaryUuid(), senderName, -result.amount(), result.primaryBefore(), result.primaryAfter(),
                "Paid " + targetName, rawCommand, true, null);
        log(result.transactionId() + ":credit", reason.type(), result.secondaryUuid(), targetName,
                result.primaryUuid(), senderName, result.amount(), result.secondaryBefore(), result.secondaryAfter(),
                "Received from " + senderName, rawCommand, true, null);
    }

    private void logFailure(String transactionId, EconomyTransactionType type, UUID targetUuid, String targetName,
                            UUID actorUuid, String actorName, double amount, double before, double after,
                            String reason, String rawCommand, String failureReason) {
        log(transactionId, type, targetUuid, targetName, actorUuid, actorName, amount, before, after, reason, rawCommand, false, failureReason);
    }

    private void log(String transactionId, EconomyTransactionType type, UUID targetUuid, String targetName,
                     UUID actorUuid, String actorName, double amount, double before, double after,
                     String reason, String rawCommand, boolean success, String failureReason) {
        if (logRepository == null) {
            return;
        }
        EconomyTransactionLog log = new EconomyTransactionLog(transactionId, type, targetUuid, targetName, actorUuid, actorName,
                amount, before, after, reason, rawCommand, serverId, serverDisplayName, success, failureReason, System.currentTimeMillis());
        Bukkit.getAsyncScheduler().runNow(plugin, task -> {
            try {
                logRepository.insert(log);
            } catch (Exception e) {
                plugin.getLogger().warning("[Economy] Failed to write transaction log " + transactionId + ": " + e.getMessage());
            }
        });
    }

    // Dispatched on the main thread via NetworkEventBus dispatcher.
    // Skip own-server events to avoid stale overwrites from in-flight async tasks.
    private void onRemoteBalanceUpdate(BalanceUpdateEvent event) {
        if (serverId.equals(event.getSourceServer())) return;
        UUID uuid = UUID.fromString(event.getUuid());
        if (cache.containsKey(uuid)) {
            cache.put(uuid, event.getNewBalance());
        }
    }

    private boolean validAmount(double amount) {
        return !Double.isNaN(amount) && !Double.isInfinite(amount) && amount >= 0D;
    }

    private static long asLong(Object value) {
        if (value instanceof Number number) {
            return number.longValue();
        }
        return Long.parseLong(String.valueOf(value));
    }

    private static double asDouble(Object value) {
        if (value instanceof Number number) {
            return number.doubleValue();
        }
        return Double.parseDouble(String.valueOf(value));
    }
}
