# Native Maven Plugin — agent instructions

## Grounding with grund (v2)

This subproject is a standalone grund project for the Maven plugin. Resolve local behavior from
the local docs first:

- [GRUND](docs/grund.md): Maven plugin purpose
- [GOAL](docs/goals.md): Maven plugin goals
- [REQ](docs/requirements.md): Maven plugin requirements
- [FS](docs/functional-spec.md): Maven plugin functional specification
- [AR](docs/architecture.md): Maven plugin architecture
- [E2E](docs/e2e.md): Maven plugin functional test execution

Repository-wide grounding, non-goals, shared plugin behavior, common library behavior, and CI
remain in the root specification under `../docs/spec`. Prefer local IDs such as
`§FS-maven-plugin.1.1` for Maven behavior, and cite root IDs such as
`§FS-plugin-common-behavior` or `§NGOAL-no-build-tool-flags-for-native-image-flags` only for
cross-project contracts.

Use `grund <ID>`, `grund <ID> --toc`, and `grund <ID> --full` from this directory to resolve
local and linked root citations. Run `grund check` here before changing Maven plugin specs or
source citations.
