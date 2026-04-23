# Test Plan: Classic vs Cloud-Native Runtime Validation

## Goal
Validate that the plugin behaves the same in **classic mode** and **cloud-native/containerized mode**, while confirming container startup and configuration paths are healthy.

## Scope
- Existing plugin behavior in classic deployment remains unchanged.
- Plugin functions correctly in containerized GeoServer.
- Critical flows are behaviorally identical across both modes.

## Test Environments

### E1: Classic mode (baseline)
- GeoServer running in traditional servlet container / local runtime.
- Plugin JAR installed in `WEB-INF/lib`.
- Baseline catalog/workspace/layers preloaded.

### E2: Cloud-native mode (target)
- GeoServer running in container (Docker/Kubernetes pod).
- Same plugin artifact version as E1.
- Equivalent dataset, styles, and service config mounted via volume/ConfigMap/secret as applicable.

## Test Data & Fixtures
- One point layer, one line layer, one polygon layer.
- One mixed-complexity layer (large + small geometries).
- SLD used by existing tests (including filter case).
- Reference expected tile fixture(s) where deterministic output is required.

## Entry / Exit Criteria

### Entry
- Plugin build is successful and artifact published.
- E1 and E2 are reachable and healthy.
- Same GeoServer and Java major versions in both environments.

### Exit
- All smoke checks pass in both environments.
- No regression in classic mode.
- Cross-mode parity checks pass for critical flows.
- Any intentional differences are documented and approved.

## Test Tracks

## 1) Classic-Mode Regression (Behavior Unchanged)
Purpose: prove no change from existing behavior.

### 1.1 Startup Smoke
- GeoServer starts without plugin-related errors.
- Plugin beans load (MVT output format and slippy controller wiring where enabled).
- WMS capabilities advertises expected MVT format(s).

### 1.2 Config Loading Smoke
- `applicationContext.xml` and plugin Spring wiring load without conflicts.
- Optional slippy map context loads and endpoint is registered.
- Existing defaults resolve as before (format, tile size, buffer, etc.).

### 1.3 Core Plugin Operations
- WMS `GetMap` with MVT format returns valid protobuf tile bytes.
- Slippy endpoint forwards to expected WMS query and returns tile payload.
- ENV options still honored:
  - `gen_level`, `gen_factor`
  - `small_geom_mode`, `small_geom_threshold`, `pixel_size`, `PIXEL_AS_POINT`
  - `strip_attributes`, `keep_attrs`
  - `avoid_empty_proto`
- Error handling unchanged for invalid/missing required params.

## 2) Cloud-Native Mode Validation (Containerized GeoServer)
Purpose: verify plugin is production-ready in container runtime.

### 2.1 Container Startup Smoke
- Container reaches ready/healthy state.
- Plugin JAR is present in classpath (`WEB-INF/lib`).
- Startup logs show plugin initialization with no classpath conflicts.

### 2.2 Container Config Loading Smoke
- Mounted config is consumed correctly (catalog, Spring context, plugin settings).
- Environment-variable / mounted-file configuration is effective after boot.
- Restart preserves expected configuration behavior.

### 2.3 Core Operations in Container
- Same WMS MVT requests as E1 succeed.
- Slippy tile endpoint works through container ingress/service URL.
- Tile generation works under repeated requests (basic stability run).

## 3) Cross-Mode Parity (Critical Flows Must Match)
Purpose: verify behavior is equivalent between E1 and E2.

### 3.1 Parity Criteria
For each critical flow, compare E1 vs E2:
- HTTP status code.
- Response `Content-Type`.
- Tile decodes successfully.
- Feature counts per layer (and geometry types) match.
- Attribute presence/omission rules match.
- Deterministic fixture comparisons where possible (byte equality if deterministic, semantic equality otherwise).

### 3.2 Critical Flows
1. Basic MVT WMS request (single layer).
2. Slippy endpoint request (`z/x/y`) mapped to same WMS semantics.
3. Small geometry handling in `drop`, `keep`, `pixel` modes.
4. Attribute stripping/whitelist behavior.
5. Empty/near-empty tile handling with `avoid_empty_proto`.
6. Filtered style/request path (SLD/filter fixture).

## Integration Test Targets

### Existing in-repo targets (must continue passing)
- `org.geoserver.wms.mvt.MVTTest`
- `org.geoserver.wms.mvt.SlippyTilesControllerTest`

### New/extended integration targets
- **IT-CL-001** Classic startup + capabilities smoke.
- **IT-CN-001** Container startup + readiness + plugin registration smoke.
- **IT-CN-002** Container config load/reload smoke.
- **IT-PR-001** Cross-mode parity harness for critical flows (semantic diff tool).
- **IT-PR-002** Slippy-to-WMS forwarding parity (query + payload semantics).

## Smoke Check Suite (Fast Gate)
Run on every PR and deployment candidate:
1. Startup healthy in E1 and E2.
2. MVT format listed in WMS capabilities.
3. One canonical WMS MVT request returns valid non-empty tile.
4. One canonical slippy request returns valid tile.
5. One ENV-driven request (`strip_attributes=true`) behaves as expected.

## Execution Strategy
- PR CI: unit + integration tests + smoke checks.
- Nightly: full cross-mode parity matrix over all critical flows.
- Pre-release: repeat in production-like Kubernetes environment.

## Reporting
For each run, capture:
- Environment metadata (GeoServer version, plugin version, image digest).
- Request/response samples for each critical flow.
- Parity diff report (pass/fail + reason).
- Log excerpts for startup/config loading failures.

## Risks & Mitigations
- **Non-deterministic protobuf serialization**: use semantic decoding comparison as fallback.
- **Env drift between E1/E2**: pin versions and declaratively provision test fixtures.
- **Classpath/library mismatch in container**: include explicit startup log checks for protobuf/shading conflicts.

## Suggested Acceptance Gate
Release is blocked unless:
- Classic regression track passes 100%.
- Cloud-native smoke and core operations pass 100%.
- Cross-mode parity passes all critical flows, or approved exceptions are documented.
