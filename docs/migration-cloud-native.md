# Migration Guide: Legacy to Cloud-Native Artifact

## Purpose

This document explains how to migrate from the legacy `gs-mvt` build artifact to the cloud-native artifact using a **phased rollout**:

1. Internal test
2. Pilot environment
3. General release

It also documents backward compatibility expectations, known limitations, and rollback steps.

## Decide Which Artifact to Use

Choose the artifact explicitly per environment:

- **Legacy artifact** (default for existing production)
  - Use when stability and strict runbook continuity are the highest priority.
  - Recommended starting point for environments not yet validated for cloud-native runtime behavior.

- **Cloud-native artifact** (opt-in)
  - Use for containerized deployment targets and progressive migration.
  - Recommended only with staged validation and a tested rollback path.

## Backward Compatibility Contract

During this migration window:

- Legacy artifact remains a supported deployment path.
- Request/response behavior for core MVT operations remains compatible.
- Existing client integrations should not require API-level changes.
- Migration does not require data conversion or schema migration.

## Known Limitations in the Initial Cloud-Native Release

Expect and plan for the following:

- Runtime tuning may differ from legacy long-lived JVM deployment patterns.
- Existing monitoring may not fully cover container/runtime-specific failure modes.
- Operational scripts that rely on legacy packaging conventions may require updates.
- Full operational parity should be validated in your own workload profile before broad rollout.

## Phased Rollout Procedure

### Phase 1 — Internal Test

**Goal**: verify technical readiness in a controlled environment.

Actions:

1. Deploy cloud-native artifact to internal test stack.
2. Run smoke tests for representative layers/styles.
3. Compare with legacy baseline:
   - tile correctness
   - latency (p50/p95/p99)
   - error rates
   - resource usage
4. Validate deployment lifecycle behavior (start, restart, scale up/down).

Promotion criteria:

- No blocking functional regressions.
- SLO deltas within accepted tolerance.
- Monitoring and alerting validated.

### Phase 2 — Pilot Environment

**Goal**: validate behavior under real but limited production traffic.

Actions:

1. Select low-risk workloads/tenants.
2. Deploy cloud-native artifact with limited traffic share.
3. Increase exposure gradually based on daily review.
4. Execute a planned rollback rehearsal.

Promotion criteria:

- Stable operation over agreed pilot window.
- No Sev1/Sev2 incidents linked to artifact change.
- Rollback rehearsal completed successfully.

### Phase 3 — General Release

**Goal**: broaden availability while preserving safety.

Actions:

1. Promote cloud-native artifact as standard deployment target.
2. Keep legacy artifact available through stabilization window.
3. Continue close monitoring and incident trend review.

Completion criteria:

- Stable SLOs across full traffic.
- No unresolved high-severity migration defects.

## Rollback Steps

If degradation is detected at any phase:

1. Halt progression immediately.
2. Shift traffic back to legacy deployment.
3. Redeploy known-good legacy artifact and config.
4. Validate recovery using:
   - health checks
   - golden tile comparisons
   - latency/error dashboards
5. Declare rollback complete only after sustained stability.

## Rollback Triggers (Recommended)

Define thresholds before rollout, for example:

- sustained p95 latency increase beyond agreed threshold
- error-rate increase beyond agreed threshold
- repeatable functional tile correctness failures
- cache instability or severe miss-rate regression

## Operator Checklist

Before migration:

- [ ] Legacy artifact and manifests are versioned and readily deployable.
- [ ] Baseline metrics from legacy production are captured.
- [ ] Pilot scope and success criteria are documented.
- [ ] Rollback owner and communications path are assigned.

During migration:

- [ ] Daily review of SLOs and error budget consumption.
- [ ] Change log of rollout percentages and observed effects.
- [ ] Rollback rehearsal evidence captured.

After general release:

- [ ] Stabilization period completed.
- [ ] Post-migration review documented.
- [ ] Decision recorded on legacy-path long-term support/deprecation timeline.
