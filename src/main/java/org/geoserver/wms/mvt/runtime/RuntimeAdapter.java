package org.geoserver.wms.mvt.runtime;

/** Bundles runtime-specific environmental services. */
public interface RuntimeAdapter {

    String flavor();

    ConfigurationSource configuration();

    StorageAccess storage();

    ClusterStateCoordinator clusterState();
}
