# Release Notes: Cloud-Native Build Artifact (Initial Release)

## Release Summary

This release introduces a **cloud-native build artifact** option for `gs-mvt` alongside the existing **legacy artifact**.
The goal is to support modern containerized deployment patterns while preserving compatibility for existing GeoServer installations.

## Build Artifact Options

Users can now choose between two packaging tracks:

- **Legacy artifact**
  - Intended for existing/non-containerized deployments and established operational runbooks.
  - Keeps the current packaging and behavior expectations used in prior releases.
- **Cloud-native artifact**
  - Intended for container-first and immutable-infrastructure environments.
  - Optimized for modern deployment pipelines and staged rollouts.

### How to choose

Select the artifact based on deployment model:

1. Use **legacy artifact** if your environment depends on current file/layout conventions or you need lowest migration risk.
2. Use **cloud-native artifact** for pilot and progressive rollout in Kubernetes/container environments.
3. Keep rollback path ready (see [Rollback Plan](#rollback-plan)).

## Backward Compatibility Guarantees

For this initial cloud-native release, we guarantee:

- **No forced migration**: legacy artifact remains available and supported.
- **Protocol continuity**: MVT output protocol and MIME expectations remain unchanged.
- **Configuration continuity**: existing WMS/Slippy request semantics and ENV parameters are preserved.
- **Operational fallback**: teams can revert to legacy artifact without schema/data migration.

## Known Limitations (Initial Cloud-Native Release)

The first cloud-native release has the following known limitations:

- **Parity caveats**: some operational behavior may differ from long-running legacy JVM deployments under very high load.
- **Observability gaps**: dashboards/alerts may need adaptation for new runtime/container metrics.
- **Packaging assumptions**: scripts that assume legacy filesystem paths may need updates.
- **Conservative recommendation**: production use is recommended only after completing phased rollout and SLO validation.

## Phased Rollout Plan

Use the following controlled rollout sequence:

### Phase 1: Internal Test

- Deploy cloud-native artifact in internal/non-customer environment.
- Run functional checks on representative layers and styles.
- Compare tile payload size, response latency, and error rates against legacy baseline.
- Validate startup, restart, and scaling behavior.

**Exit criteria**
- No functional regressions in core tile generation.
- Performance within accepted tolerance from baseline.
- Monitoring and alerting visibility confirmed.

### Phase 2: Pilot Environment

- Deploy cloud-native artifact to a limited pilot environment (low-risk users/workloads).
- Start with a small traffic share; gradually increase if stable.
- Track SLOs, cache behavior, and support tickets daily.

**Exit criteria**
- Stable operations across agreed observation window.
- No critical incidents attributable to artifact change.
- Rollback drill successfully executed.

### Phase 3: General Release

- Promote cloud-native artifact to standard production path.
- Continue to keep legacy artifact available as a fallback during stabilization window.
- Communicate final support timelines and deprecation notices separately (if applicable).

## Rollback Plan

If issues occur after deploying the cloud-native artifact:

1. **Freeze rollout**: stop further promotion immediately.
2. **Route traffic back**: redirect traffic to legacy deployment (or prior stable release).
3. **Redeploy legacy artifact**: use previously verified image/package and configuration bundle.
4. **Validate recovery**: confirm health checks, tile correctness, latency, and error-rate recovery.
5. **Incident review**: capture root cause, mitigation, and required fixes before reattempting rollout.

### Rollback Readiness Checklist

- Legacy artifact build available in registry/release storage.
- Deployment manifests for legacy path versioned and tested.
- One-command or one-pipeline rollback path documented.
- On-call team aware of rollback trigger thresholds.

## Upgrade Guidance Snapshot

- New adopters in containerized environments should begin with **internal test** using cloud-native artifact.
- Existing stable environments should remain on legacy artifact until pilot success criteria are met.
- Do not skip phases; progressive exposure is required for this initial release.
