# Native Maven Plugin — agent instructions

## Grounding with grund (v2)

This subproject is the `maven` grund workspace member for the Maven plugin. Resolve local behavior
from the local docs first:

- [GRUND](docs/grund.md): Maven plugin purpose
- [GOAL](docs/goals.md): Maven plugin goals
- [REQ](docs/requirements.md): Maven plugin requirements
- [FS](docs/functional/README.md): Maven plugin functional specifications
- [AR](docs/architecture.md): Maven plugin architecture
- [E2E](docs/e2e.md): Maven plugin functional test execution

Repository-wide grounding, non-goals, shared plugin behavior, and CI remain in the `root`
namespace under `../docs/spec`. Common library behavior lives in the `common` namespace under
`../common/docs`. Prefer local IDs such as `§FS-goal-surface.1` for Maven behavior, and cite root
or common IDs such as `§root/FS-plugin-common`,
`§root/NGOAL-no-flag-mirroring`, or `§common/FS-common-libraries` only for
cross-project contracts. Java comments use the same marked citation shape because Checkstyle allows
`§` as the only non-ASCII citation exception.

Use `grund maven/<ID>`, `grund maven/<ID> --toc`, and `grund maven/<ID> --full` from the
repository root to resolve Maven citations. Run `grund check` from the repository root before
changing Maven plugin specs or source citations.

### Module Map

Use this map before editing: pick the owning path, then resolve the cited spec before changing
behavior.

- `src/main/java/org/graalvm/buildtools/maven/*Mojo.java`: user-visible Maven goals, lifecycle
  bindings, parameters, and goal execution. §FS-goal-surface, §FS-native-builds,
  §AR-maven-plugin.2
- `src/main/java/org/graalvm/buildtools/maven/AbstractNativeImageMojo.java`: native-image
  invocation, executable lookup, shared configuration handling, and resource/metadata workflow
  support. §FS-native-builds, §FS-config-model.4, §AR-maven-plugin.4
- `src/main/java/org/graalvm/buildtools/maven/NativeCompileNoForkMojo.java`: lifecycle-bound
  native compilation, main-class discovery, skipping, generated resources, dynamic access metadata,
  and SBOM behavior. §FS-goal-surface.1, §FS-native-builds
- `src/main/java/org/graalvm/buildtools/maven/NativeTestMojo.java`: native test build and
  execution through Maven. §FS-goal-surface.2, §FS-native-tests
- `src/main/java/org/graalvm/buildtools/maven/MetadataCopyMojo.java` and
  `MergeAgentFilesMojo.java`: tracing-agent metadata merge and copy workflows.
  §FS-tracing-agent
- `src/functionalTest/`: isolated Maven executor scenarios and generated/sample project coverage.
  §E2E-functional-tests
- `reproducers/`: issue reproducer projects that protect Maven-specific regressions.
  §AR-maven-plugin.6
