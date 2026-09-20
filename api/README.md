# API contract pin

`openapi.yaml` in this folder is a **verbatim copy** of the contract published by the
`finance-backend` repository (`finance-backend/api/openapi.yaml`). It is the single source of
truth for the mobile client: `core/network` generates its Kotlin client from this file at build
time (`:core:network:openApiGenerate`), and nothing in the app talks to the backend outside the
generated client.

`VERSION` holds the semantic version of the pinned contract (`info.version` of the spec).

## Why a copy instead of a Maven dependency?

ARCHITECTURE.md §2 foresees the backend CI publishing `finance-api-client-kotlin:<semver>` to a
registry. Until that registry exists, the spec file itself is the artifact: pinning it in-repo gives
the same guarantees (explicit version, deliberate upgrade, reproducible builds) with zero
infrastructure. When the registry is live, replace the generation step with the published artifact
and keep this folder as documentation of the pinned version.

## Updating the pin

1. Read the backend changelog for the target version. Breaking changes bump the major version and
   ship under `/api/v2`; `/api/v1` stays available for at least two releases.
2. Copy the new spec and bump the version in one commit:
   ```sh
   cp ../finance-backend/api/openapi.yaml api/openapi.yaml
   echo "1.1.0" > api/VERSION
   ```
3. Regenerate and compile: `make api`.
4. Fix the adapters in `core/network/src/commonMain/kotlin/.../network/api/` (they are the only
   code allowed to import the generated `...core.network.generated.*` packages; an architecture test
   enforces this) and any feature mappers.
5. Run the full check: `make test arch`.
6. Open a PR titled `api: pin contract <old> -> <new>`. Never edit `openapi.yaml` by hand in this
   repo; API changes start in the backend repo.
