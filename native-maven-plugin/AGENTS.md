# Native Maven Plugin — agent instructions

## Grounding with grund (v2)

This subproject is the `maven` grund workspace member for the Maven plugin. Resolve local behavior
from the local docs first:

- [GRUND](docs/grund.md): Maven plugin purpose
- [GOAL](docs/goals.md): Maven plugin goals
- [REQ](docs/requirements.md): Maven plugin requirements
- [FS](docs/functional-spec.md): Maven plugin functional specification
- [AR](docs/architecture.md): Maven plugin architecture
- [E2E](docs/e2e.md): Maven plugin functional test execution

Repository-wide grounding, non-goals, shared plugin behavior, common library behavior, and CI
remain in the `root` namespace under `../docs/spec`. Prefer local IDs such as
`§FS-maven-plugin.1.1` for Maven behavior, and cite root IDs such as
`§root/FS-plugin-common-behavior` or `§root/NGOAL-no-build-tool-flags-for-native-image-flags` only for
cross-project contracts. In Java comments, cite local Maven IDs; keep cross-namespace root
citations in Markdown or YAML where `§root/<ID>` can be checked.

Use `grund maven/<ID>`, `grund maven/<ID> --toc`, and `grund maven/<ID> --full` from the
repository root to resolve Maven citations. Run `grund check` from the repository root before
changing Maven plugin specs or source citations.

### Module Map

Use this map before editing: pick the owning path, then resolve the cited spec before changing
behavior.

- `src/main/java/org/graalvm/buildtools/maven/*Mojo.java`: user-visible Maven goals, lifecycle
  bindings, parameters, and goal execution. §FS-maven-plugin.1, §FS-maven-plugin.2,
  §AR-maven-plugin.2
- `src/main/java/org/graalvm/buildtools/maven/AbstractNativeImageMojo.java`: native-image
  invocation, executable lookup, shared configuration handling, and resource/metadata workflow
  support. §FS-maven-plugin.2, §FS-maven-plugin.3.4, §AR-maven-plugin.4
- `src/main/java/org/graalvm/buildtools/maven/NativeCompileNoForkMojo.java`: lifecycle-bound
  native compilation, main-class discovery, skipping, generated resources, dynamic access metadata,
  and SBOM behavior. §FS-maven-plugin.1.1, §FS-maven-plugin.2
- `src/main/java/org/graalvm/buildtools/maven/NativeTestMojo.java`: native test build and
  execution through Maven. §FS-maven-plugin.1.2, §FS-maven-plugin.4
- `src/main/java/org/graalvm/buildtools/maven/MetadataCopyMojo.java` and
  `MergeAgentFilesMojo.java`: tracing-agent metadata merge and copy workflows.
  §FS-maven-plugin.5
- `src/functionalTest/`: isolated Maven executor scenarios and generated/sample project coverage.
  §E2E-maven-plugin-functional-tests
- `reproducers/`: issue reproducer projects that protect Maven-specific regressions.
  §AR-maven-plugin.6
