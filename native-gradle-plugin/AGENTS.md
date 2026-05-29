# Native Gradle Plugin — agent instructions

## Grounding with grund (v2)

This subproject is the `gradle` grund workspace member for the Gradle plugin. Resolve local
behavior from the local docs first:

- [GRUND](docs/grund.md): Gradle plugin purpose
- [GOAL](docs/goals.md): Gradle plugin goals
- [REQ](docs/requirements.md): Gradle plugin requirements
- [FS](docs/functional-spec.md): Gradle plugin functional specification
- [AR](docs/architecture.md): Gradle plugin architecture
- [E2E](docs/e2e.md): Gradle plugin functional test execution

Repository-wide grounding, non-goals, shared plugin behavior, common library behavior, and CI
remain in the `root` namespace under `../docs/spec`. Prefer local IDs such as
`§FS-gradle-plugin.2.1` for Gradle behavior, and cite root IDs such as
`§root/FS-plugin-common-behavior` or `§root/NGOAL-no-build-tool-flags-for-native-image-flags` only for
cross-project contracts. In Java comments, cite local Gradle IDs; keep cross-namespace root
citations in Markdown or YAML where `§root/<ID>` can be checked.

Use `grund gradle/<ID>`, `grund gradle/<ID> --toc`, and `grund gradle/<ID> --full` from the
repository root to resolve Gradle citations. Run `grund check` from the repository root before
changing Gradle plugin specs or source citations.
