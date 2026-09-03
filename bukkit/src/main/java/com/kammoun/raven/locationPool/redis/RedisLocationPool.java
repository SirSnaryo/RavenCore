package com.kammoun.raven.locationPool.redis;

import com.kammoun.raven.library.locationpool.ClusterServerIds;
import com.kammoun.raven.library.locationpool.LocationDimension;
import com.kammoun.raven.library.locationpool.LocationPool;
import com.kammoun.raven.library.locationpool.LocationPoolCodec;
import com.kammoun.raven.library.locationpool.LocationPoolKeys;
import com.kammoun.raven.library.locationpool.LocationSearchBounds;
import com.kammoun.raven.library.locationpool.RtpLocation;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;

import java.util.List;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Optional;
import java.util.Map;
import java.util.Objects;
import java.util.logging.Logger;

public final class RedisLocationPool implements LocationPool {

    private final JedisPool pool;
    private final Logger logger;

    public RedisLocationPool(JedisPool pool, Logger logger) {
        this.pool = pool;
        this.logger = logger;
    }

    @Override
    public Optional<RtpLocation> getRandomLocation(String serverId, LocationDimension dimension) {
        if (pool == null) {
            return Optional.empty();
        }
        try (Jedis jedis = pool.getResource()) {
            String payload = jedis.rpop(LocationPoolKeys.serverPool(serverId, dimension));
            return LocationPoolCodec.decode(serverId, dimension, payload);
        } catch (Exception e) {
            logger.warning("[LocationPool] Failed to pop " + serverId + "/" + dimension + ": " + e.getMessage());
            return Optional.empty();
        }
    }

    @Override
    public Optional<RtpLocation> getRandomLocationInCluster(String cluster, LocationDimension dimension) {
        if (pool == null) {
            return Optional.empty();
        }
        try (Jedis jedis = pool.getResource()) {
            List<String> servers = nonEmptyServers(jedis, List.of(cluster), dimension);
            return popRandomAvailable(jedis, servers, dimension);
        } catch (Exception e) {
            logger.warning("[LocationPool] Failed to pop cluster " + cluster + "/" + dimension
                    + ": " + e.getMessage());
            return Optional.empty();
        }
    }

    @Override
    public Optional<RtpLocation> getRandomLocationAcrossClusters(Collection<String> clusters,
                                                                  LocationDimension dimension) {
        if (pool == null || clusters == null || clusters.isEmpty()) {
            return Optional.empty();
        }
        try (Jedis jedis = pool.getResource()) {
            return popRandomAvailable(jedis, nonEmptyServers(jedis, clusters, dimension), dimension);
        } catch (Exception e) {
            logger.warning("[LocationPool] Failed to pop across clusters for " + dimension + ": " + e.getMessage());
            return Optional.empty();
        }
    }

    @Override
    public Optional<RtpLocation> getRandomLocationGlobal(LocationDimension dimension) {
        if (pool == null) {
            return Optional.empty();
        }
        try (Jedis jedis = pool.getResource()) {
            List<String> servers = ClusterServerIds.fromRegistry(
                    jedis.hgetAll(LocationPoolKeys.globalGridRegistry(dimension)));
            return popRandomAvailable(jedis, servers, dimension);
        } catch (Exception e) {
            logger.warning("[LocationPool] Failed to pop global location for "
                    + dimension + ": " + e.getMessage());
            return Optional.empty();
        }
    }

    @Override
    public long getPoolSize(String serverId, LocationDimension dimension) {
        if (pool == null) {
            return 0;
        }
        try (Jedis jedis = pool.getResource()) {
            return jedis.llen(LocationPoolKeys.serverPool(serverId, dimension));
        } catch (Exception e) {
            logger.warning("[LocationPool] Failed to read pool size for " + serverId + "/" + dimension
                    + ": " + e.getMessage());
            return 0;
        }
    }

    @Override
    public boolean isReady(String serverId, LocationDimension dimension) {
        return getPoolSize(serverId, dimension) > 0;
    }

    public List<String> getRegisteredServers(String cluster, LocationDimension dimension) {
        if (pool == null || cluster == null || cluster.isBlank()) {
            return List.of();
        }
        try (Jedis jedis = pool.getResource()) {
            return ClusterServerIds.fromRegistry(jedis.hgetAll(LocationPoolKeys.clusterRegistry(cluster, dimension)));
        } catch (Exception e) {
            logger.warning("[LocationPool] Failed to read cluster servers for " + cluster + "/"
                    + dimension + ": " + e.getMessage());
            return List.of();
        }
    }

    public List<String> getRegisteredServersAcrossClusters(Collection<String> clusters,
                                                            LocationDimension dimension) {
        if (pool == null || clusters == null || clusters.isEmpty()) {
            return List.of();
        }
        LinkedHashSet<String> serverIds = new LinkedHashSet<>();
        try (Jedis jedis = pool.getResource()) {
            for (String cluster : clusters) {
                if (cluster == null || cluster.isBlank()) continue;
                serverIds.addAll(ClusterServerIds.fromRegistry(
                        jedis.hgetAll(LocationPoolKeys.clusterRegistry(cluster, dimension))));
            }
        } catch (Exception e) {
            logger.warning("[LocationPool] Failed to read cluster servers for " + dimension
                    + ": " + e.getMessage());
        }
        return serverIds.stream().sorted().toList();
    }

    public void push(RtpLocation location) {
        if (pool == null) {
            return;
        }
        try (Jedis jedis = pool.getResource()) {
            jedis.lpush(LocationPoolKeys.serverPool(location.serverId(), location.dimension()),
                    LocationPoolCodec.encode(location));
        } catch (Exception e) {
            logger.warning("[LocationPool] Failed to store location for " + location.serverId() + "/"
                    + location.dimension() + ": " + e.getMessage());
        }
    }

    public void clear(String serverId, LocationDimension dimension) {
        if (pool == null) {
            return;
        }
        try (Jedis jedis = pool.getResource()) {
            jedis.del(LocationPoolKeys.serverPool(serverId, dimension));
            jedis.del(LocationPoolKeys.poolDefinition(serverId, dimension));
        } catch (Exception e) {
            logger.warning("[LocationPool] Failed to clear " + serverId + "/" + dimension + ": " + e.getMessage());
        }
    }

    public void unregisterServer(String cluster, String serverId, Collection<LocationDimension> dimensions) {
        if (pool == null || serverId == null || serverId.isBlank() || dimensions == null) {
            return;
        }
        try (Jedis jedis = pool.getResource()) {
            for (LocationDimension dimension : dimensions) {
                if (cluster != null && !cluster.isBlank()) {
                    removeServerRegistrations(jedis, LocationPoolKeys.clusterRegistry(cluster, dimension), serverId);
                }
                removeServerRegistrations(jedis, LocationPoolKeys.globalGridRegistry(dimension), serverId);
            }
        } catch (Exception e) {
            logger.warning("[LocationPool] Failed to unregister " + serverId + ": " + e.getMessage());
        }
    }

    public boolean registerServer(String cluster, int cellX, int cellZ, String serverId,
                                  Collection<LocationDimension> dimensions) {
        if (pool == null || cluster == null || cluster.isBlank()
                || serverId == null || serverId.isBlank()) {
            return false;
        }
        String field = cellX + ":" + cellZ;
        try (Jedis jedis = pool.getResource()) {
            for (LocationDimension dimension : dimensions) {
                String clusterKey = LocationPoolKeys.clusterRegistry(cluster, dimension);
                String globalKey = LocationPoolKeys.globalGridRegistry(dimension);
                removeServerRegistrations(jedis, clusterKey, serverId);
                removeServerRegistrations(jedis, globalKey, serverId);
                jedis.hset(clusterKey, field, serverId);
                jedis.hset(globalKey, field, serverId);
            }
            return true;
        } catch (Exception e) {
            logger.warning("[LocationPool] Failed to register " + serverId + " in cluster "
                    + cluster + " at cell " + field + ": " + e.getMessage());
            return false;
        }
    }

    public boolean resetIfDefinitionChanged(String serverId, LocationDimension dimension,
                                            LocationSearchBounds bounds) {
        String definition = bounds.minX() + ":" + bounds.minY() + ":" + bounds.minZ() + ":"
                + bounds.maxX() + ":" + bounds.maxY() + ":" + bounds.maxZ();
        return resetIfDefinitionChanged(serverId, dimension, definition);
    }

    public boolean resetIfDefinitionChanged(String serverId, LocationDimension dimension,
                                            String definition) {
        if (pool == null) {
            return false;
        }
        String definitionKey = LocationPoolKeys.poolDefinition(serverId, dimension);
        try (Jedis jedis = pool.getResource()) {
            String previous = jedis.get(definitionKey);
            if (Objects.equals(previous, definition)) {
                return false;
            }
            jedis.del(LocationPoolKeys.serverPool(serverId, dimension));
            jedis.set(definitionKey, definition);
            logger.info("[LocationPool] Cleared stale " + serverId + "/"
                    + dimension.redisName() + " locations after search bounds changed.");
            return true;
        } catch (Exception e) {
            logger.warning("[LocationPool] Failed to validate pool definition for " + serverId
                    + "/" + dimension.redisName() + ": " + e.getMessage());
            return false;
        }
    }

    private void removeServerRegistrations(Jedis jedis, String key, String serverId) {
        for (Map.Entry<String, String> entry : jedis.hgetAll(key).entrySet()) {
            if (serverId.equalsIgnoreCase(entry.getValue())) {
                jedis.hdel(key, entry.getKey());
            }
        }
    }

    private List<String> nonEmptyServers(Jedis jedis, Collection<String> clusters,
                                         LocationDimension dimension) {
        LinkedHashSet<String> serverIds = new LinkedHashSet<>();
        for (String cluster : clusters) {
            if (cluster == null || cluster.isBlank()) continue;
            serverIds.addAll(ClusterServerIds.fromRegistry(
                    jedis.hgetAll(LocationPoolKeys.clusterRegistry(cluster, dimension))));
        }
        List<String> available = new ArrayList<>();
        for (String serverId : serverIds) {
            if (jedis.llen(LocationPoolKeys.serverPool(serverId, dimension)) > 0) {
                available.add(serverId);
            }
        }
        return available;
    }

    private Optional<RtpLocation> popRandomAvailable(Jedis jedis, List<String> serverIds,
                                                      LocationDimension dimension) {
        Collections.shuffle(serverIds);
        for (String serverId : serverIds) {
            String payload = jedis.rpop(LocationPoolKeys.serverPool(serverId, dimension));
            Optional<RtpLocation> location = LocationPoolCodec.decode(serverId, dimension, payload);
            if (location.isPresent()) {
                return location;
            }
        }
        return Optional.empty();
    }
}
