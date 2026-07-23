package com.kammoun.raven.library.locationpool;

import java.util.Collection;
import java.util.Optional;

public interface LocationPool {
    Optional<RtpLocation> getRandomLocation(String serverId, LocationDimension dimension);
    Optional<RtpLocation> getRandomLocationInCluster(String cluster, LocationDimension dimension);
    Optional<RtpLocation> getRandomLocationAcrossClusters(Collection<String> clusters, LocationDimension dimension);
    long getPoolSize(String serverId, LocationDimension dimension);
    boolean isReady(String serverId, LocationDimension dimension);
}
