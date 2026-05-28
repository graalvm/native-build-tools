# Native Gradle Plugin — agent instructions

## Grounding with grund (v2)

This subproject is a standalone grund project for the Gradle plugin. Resolve local behavior from
the local docs first:

- [GRUND](docs/grund.md): Gradle plugin purpose
- [GOAL](docs/goals.md): Gradle plugin goals
- [REQ](docs/requirements.md): Gradle plugin requirements
- [FS](docs/functional-spec.md): Gradle plugin functional specification
- [AR](docs/architecture.md): Gradle plugin architecture
- [E2E](docs/e2e.md): Gradle plugin functional test execution

Repository-wide grounding, non-goals, shared plugin behavior, common library behavior, and CI
remain in the root specification under `../docs/spec`. Prefer local IDs such as
`§FS-gradle-plugin.2.1` for Gradle behavior, and cite root IDs such as
`§FS-plugin-common-behavior` or `§NGOAL-no-build-tool-flags-for-native-image-flags` only for
cross-project contracts.

Use `grund <ID>`, `grund <ID> --toc`, and `grund <ID> --full` from this directory to resolve
local and linked root citations. Run `grund check` here before changing Gradle plugin specs or
source citations.
