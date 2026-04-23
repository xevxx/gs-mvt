package org.geoserver.wms.mvt.runtime;

import java.util.function.Supplier;

/**
 * Abstracts cluster-safe cache/state coordination so business logic does not need runtime branching.
 */
public interface ClusterStateCoordinator {

    <T> T getOrCompute(String key, Supplier<T> loader);

    void invalidate(String key);
}
