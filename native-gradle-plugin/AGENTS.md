# Native Gradle Plugin — agent instructions

## Grounding with grund (v2)

This subproject is the `gradle` grund workspace member for the Gradle plugin. Resolve local
behavior from the local docs first:

- [GRUND](docs/grund.md): Gradle plugin purpose
- [GOAL](docs/goals.md): Gradle plugin goals
- [REQ](docs/requirements.md): Gradle plugin requirements
- [FS](docs/functional/README.md): Gradle plugin functional specifications
- [AR](docs/architecture.md): Gradle plugin architecture
- [E2E](docs/e2e.md): Gradle plugin functional test execution

Repository-wide grounding, non-goals, shared plugin behavior, and CI remain in the `root`
namespace under `../docs/spec`. Common library behavior lives in the `common` namespace under
`../common/docs`. Prefer local IDs such as `§FS-native-tasks.1` for Gradle behavior, and cite
root or common IDs such as `§root/FS-plugin-common`,
`§root/NGOAL-no-flag-mirroring`, or `§common/FS-common-libraries` only for
cross-project contracts. Java comments use the same marked citation shape because Checkstyle allows
`§` as the only non-ASCII citation exception.

Use `grund gradle/<ID>`, `grund gradle/<ID> --toc`, and `grund gradle/<ID> --full` from the
repository root to resolve Gradle citations. Run `grund check` from the repository root before
changing Gradle plugin specs or source citations.

### Module Map

Use this map before editing: pick the owning path, then resolve the cited spec before changing
behavior.

- `src/main/java/org/graalvm/buildtools/gradle/NativeImagePlugin.java`: plugin registration,
  extension setup, default binary wiring, task registration, and Gradle model integration.
  §FS-plugin-model, §AR-gradle-plugin.2, §AR-gradle-plugin.3
- `src/main/java/org/graalvm/buildtools/gradle/tasks/`: user-visible Gradle tasks for native
  compile/run/test, resource generation, metadata collection, missing metadata reporting, dynamic
  access metadata, and agent metadata copy. §FS-native-tasks, §FS-resources-metadata,
  §FS-tracing-agent, §FS-native-tests
- `src/main/java/org/graalvm/buildtools/gradle/internal/NativeImageCommandLineProvider.java`:
  native-image argument construction and argument-file behavior. §FS-native-invocation.3,
  §FS-native-invocation.4
- `src/main/java/org/graalvm/buildtools/gradle/internal/NativeImageExecutableLocator.java`:
  GraalVM and native-image executable discovery for Gradle tasks. §FS-native-invocation.1,
  §AR-gradle-plugin.4
- `src/main/java/org/graalvm/buildtools/gradle/NativeImageService.java`: concurrency control for
  parallel native-image builds. §FS-native-invocation.6
- `src/functionalTest/`: Gradle TestKit scenarios and generated/sample project coverage.
  §E2E-functional-tests
- `src/testFixtures/`: reusable Gradle functional-test fixtures and project builders.
  §AR-gradle-plugin.6
