# Deployment examples for gs-mvt plugin

This document provides Kubernetes deployment examples for loading the built `gs-mvt` plugin JAR into a GeoServer pod.

## 1) Plugin artifact loading

### Option A: Bake plugin into the GeoServer image (recommended for production)

```dockerfile
FROM docker.osgeo.org/geoserver:2.28.2

# Copy extension into GeoServer webapp libs
COPY target/gs-mvt-*.jar /opt/geoserver/webapps/geoserver/WEB-INF/lib/

# If your build/release also provides extra runtime jars, copy them as well.
# COPY target/dependency/*.jar /opt/geoserver/webapps/geoserver/WEB-INF/lib/
```

### Option B: Mount plugin via ConfigMap/PVC/initContainer (cluster validation)

Use an initContainer to copy the plugin into a shared volume mounted at `WEB-INF/lib`.

```yaml
apiVersion: apps/v1
kind: Deployment
metadata:
  name: geoserver-mvt
spec:
  replicas: 1
  selector:
    matchLabels:
      app: geoserver-mvt
  template:
    metadata:
      labels:
        app: geoserver-mvt
    spec:
      initContainers:
        - name: copy-plugin
          image: alpine:3.20
          command:
            - /bin/sh
            - -c
            - |
              cp /plugins/gs-mvt.jar /shared-lib/gs-mvt.jar
          volumeMounts:
            - name: plugin-artifact
              mountPath: /plugins
            - name: geoserver-lib
              mountPath: /shared-lib
      containers:
        - name: geoserver
          image: docker.osgeo.org/geoserver:2.28.2
          ports:
            - name: http
              containerPort: 8080
          volumeMounts:
            - name: geoserver-lib
              mountPath: /opt/geoserver/webapps/geoserver/WEB-INF/lib
      volumes:
        - name: plugin-artifact
          configMap:
            name: gs-mvt-plugin
        - name: geoserver-lib
          emptyDir: {}
```

> Notes:
> - ConfigMap size limits usually make this unsuitable for large binaries in production.
> - For production, prefer immutable container images or an artifact fetch in initContainer from object storage.

## 2) Required config / env vars / secrets

The plugin is driven by WMS `ENV` request parameters (for example `small_geom_mode`, `strip_attributes`, `avoid_empty_proto`) and does not require mandatory process-level environment variables by default.

Recommended container env for stable GeoServer operation:

```yaml
env:
  - name: JAVA_OPTS
    value: "-Xms512m -Xmx2g -Djava.awt.headless=true"
  - name: GEOSERVER_DATA_DIR
    value: "/opt/geoserver_data"
```

If GeoServer requires external credentials (database, object store, etc.), provide them by Secret and reference them in env vars:

```yaml
env:
  - name: DB_USER
    valueFrom:
      secretKeyRef:
        name: geoserver-db
        key: username
  - name: DB_PASSWORD
    valueFrom:
      secretKeyRef:
        name: geoserver-db
        key: password
```

## 3) Readiness/liveness expectations

Use startup/readiness/liveness probes against GeoServer HTTP endpoints. Initial startup can be slow, so tune thresholds.

```yaml
startupProbe:
  httpGet:
    path: /geoserver/web/
    port: 8080
  periodSeconds: 10
  failureThreshold: 30

readinessProbe:
  httpGet:
    path: /geoserver/web/
    port: 8080
  initialDelaySeconds: 20
  periodSeconds: 10
  timeoutSeconds: 5
  failureThreshold: 6

livenessProbe:
  httpGet:
    path: /geoserver/web/
    port: 8080
  initialDelaySeconds: 60
  periodSeconds: 20
  timeoutSeconds: 5
  failureThreshold: 3
```

Validation expectation after pod is Ready:
- WMS capabilities should advertise `application/vnd.mapbox-vector-tile` (or your custom MIME if changed).
- A sample WMS GetMap request in MVT format should return protobuf bytes (not 0-byte unless no features and you did not enable `avoid_empty_proto:true`).

## 4) Volume and network requirements

### Volumes

Minimum recommended:
- Persistent volume for GeoServer data dir (`GEOSERVER_DATA_DIR`).
- Read-only mounted plugin artifact source (if not baked into image).
- Writable lib dir target only when using initContainer copy pattern.

Example:

```yaml
volumeMounts:
  - name: data-dir
    mountPath: /opt/geoserver_data
  - name: geoserver-lib
    mountPath: /opt/geoserver/webapps/geoserver/WEB-INF/lib

volumes:
  - name: data-dir
    persistentVolumeClaim:
      claimName: geoserver-data-pvc
  - name: geoserver-lib
    emptyDir: {}
```

### Network

- Ingress/Service must expose HTTP 8080 (or your remapped container port).
- Allow egress from GeoServer to backing stores (PostGIS, WFS/WMS dependencies, object storage).
- If using namespace isolation, add NetworkPolicy rules to permit:
  - client/ingress -> GeoServer service
  - GeoServer -> database/service dependencies

## 5) Helm values example

If using a generic GeoServer Helm chart, these values typically cover plugin loading and probes:

```yaml
image:
  repository: docker.osgeo.org/geoserver
  tag: "2.28.2"

extraEnv:
  - name: JAVA_OPTS
    value: "-Xms512m -Xmx2g -Djava.awt.headless=true"
  - name: GEOSERVER_DATA_DIR
    value: "/opt/geoserver_data"

extraVolumeMounts:
  - name: data-dir
    mountPath: /opt/geoserver_data
  - name: geoserver-lib
    mountPath: /opt/geoserver/webapps/geoserver/WEB-INF/lib

extraVolumes:
  - name: data-dir
    persistentVolumeClaim:
      claimName: geoserver-data-pvc
  - name: geoserver-lib
    emptyDir: {}

startupProbe:
  httpGet:
    path: /geoserver/web/
    port: 8080
  periodSeconds: 10
  failureThreshold: 30

readinessProbe:
  httpGet:
    path: /geoserver/web/
    port: 8080

livenessProbe:
  httpGet:
    path: /geoserver/web/
    port: 8080
```

## 6) Minimal quickstart path (cluster validation)

1. Build plugin jar locally:
   ```bash
   mvn -DskipTests clean package
   cp target/gs-mvt-*.jar ./gs-mvt.jar
   ```
2. Create namespace and data PVC (example):
   ```bash
   kubectl create ns geoserver
   kubectl -n geoserver apply -f geoserver-data-pvc.yaml
   ```
3. Create ConfigMap containing plugin artifact (validation only):
   ```bash
   kubectl -n geoserver create configmap gs-mvt-plugin --from-file=gs-mvt.jar
   ```
4. Apply Deployment + Service using the initContainer pattern above.
5. Wait for readiness:
   ```bash
   kubectl -n geoserver rollout status deploy/geoserver-mvt
   kubectl -n geoserver get pods
   ```
6. Port-forward and validate MVT output:
   ```bash
   kubectl -n geoserver port-forward deploy/geoserver-mvt 8080:8080
   curl -I "http://localhost:8080/geoserver/wms?SERVICE=WMS&VERSION=1.1.1&REQUEST=GetMap&LAYERS=<workspace:layer>&STYLES=&FORMAT=application/vnd.mapbox-vector-tile&SRS=EPSG:3857&WIDTH=256&HEIGHT=256&BBOX=-20037508.34,-20037508.34,0,0"
   ```

Expected validation result:
- HTTP `200 OK`
- Response content type `application/vnd.mapbox-vector-tile` (or custom MIME if configured)
- Non-empty tile body for areas containing features.
