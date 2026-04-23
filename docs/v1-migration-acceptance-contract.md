# V1 Migration Acceptance Contract

This note is the acceptance contract for all implementation tasks in the v1 migration.

## 1) Supported GeoServer Deployment Modes

The migrated plugin **must** run in both deployment models:

1. **Classic (monolithic GeoServer WAR/webapp)**
   - Traditional servlet-container deployment (e.g., Tomcat/Jetty) of GeoServer.
2. **Cloud-native GeoServer**
   - Containerized, horizontally scalable runtime aligned with the GeoServer Cloud model.

No implementation task is complete unless behavior is verified against both modes (or validated via equivalent automated coverage that enforces the same contract).

## 2) Required Plugin Behavior Parity

v1 migration must preserve current externally observable behavior unless explicitly called out as a non-goal below.

Required parity includes:

- Same request/response contract for supported MVT/slippy map endpoints.
- Same tile content semantics for equivalent inputs:
  - geometry generalization behavior,
  - layer/content filtering behavior,
  - output encoding expectations.
- Same configuration intent and default outcomes from an operator perspective.
- No regressions in backward compatibility for existing clients using current plugin capabilities.

Acceptance is based on parity with the current implementation in this repository as the source of truth.

## 3) Explicit Non-goals for v1

The following are out of scope for v1 migration:

- Introducing new end-user features, new public APIs, or format extensions.
- Re-designing rendering algorithms or changing tile schema/semantics beyond compatibility fixes.
- Performance tuning work beyond what is required to avoid material regressions.
- Operational platform expansion beyond the deployment/version matrix defined here.
- Broad refactors unrelated to migration correctness and parity.

## 4) Version & Runtime Matrix (Contractual)

Implementations must conform to the matrix below.

| Dimension | v1 Contract |
|---|---|
| **GeoServer baseline** | **2.25.x and 2.26.x** |
| **Java runtime** | **Java 17** |
| **Spring framework model** | **Spring Framework 5.x / Spring Security 5.x line used by GeoServer baselines above** |
| **Servlet API** | **Jakarta Servlet 5.0+ (Spring 6 / Boot 3 APIs are non-target for v1)** |
| **Classic deployment runtime** | **Servlet container compatible with selected GeoServer baseline (Tomcat/Jetty class)** |
| **Cloud-native runtime** | **GeoServer Cloud deployment profile matching selected baseline, containerized (OCI/Kubernetes-oriented)** |
| **Packaging assumption** | **Plugin artifact remains consumable by current GeoServer extension loading model** |

If an implementation change requires deviation from this matrix, that change must be treated as out-of-contract and requires a separate design decision.
