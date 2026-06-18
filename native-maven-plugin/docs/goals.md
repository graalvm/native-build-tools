# GOAL-native-workflows: Maven users can use Native Image through Maven-native workflows

The Maven plugin must expose Native Image build, test, resource, metadata, tracing-agent, and
inspection workflows through Maven concepts: goals, lifecycle phases, plugin descriptors,
parameters, dependency scopes, toolchains, and local-repository based functional tests. This goal
realizes the repository goal in [§root/GOAL-native-build-workflows](../../docs/spec/goals.md#goal-native-build-workflows-users-can-easily-build-run-and-test-native-images-with-their-build-tool) for Maven and is specified
by [§FS-goal-surface](functional/goal-surface.md#fs-goal-surface-maven-goals-expose-native-image-workflows), [§FS-native-builds](functional/native-image-builds.md#fs-native-builds-maven-goals-build-native-image-outputs-from-project-state),
[§FS-config-model](functional/configuration-model.md#fs-config-model-maven-xml-and-command-line-properties-configure-native-image-builds), [§FS-tracing-agent](functional/tracing-agent.md#fs-tracing-agent-maven-goals-attach-and-post-process-native-image-tracing-agent-metadata), and [§FS-native-tests](functional/native-tests.md#fs-native-tests-maven-goals-compile-and-run-native-junit-tests).

# GOAL-idiomatic-maven: Maven integration follows Maven idioms and conventions

The Maven plugin should feel like a Maven plugin rather than a command wrapper hidden behind Maven
goals. Plugin code should use mojos, descriptors, `@Parameter` binding, lifecycle phases,
dependency scopes, project model lookup, toolchains, Aether/repository APIs, Plexus integration,
and isolated Maven functional tests where those concepts fit the behavior. This follows
[§GRUND-plugin-purpose](grund.md#grund-plugin-purpose-the-maven-plugin-realizes-native-build-tools-through-maven-idioms) and is constrained by [§REQ-maven-model](requirements.md#req-maven-model-the-maven-plugin-preserves-maven-model-compatibility).

# GOAL-shared-alignment: Maven behavior stays aligned with shared plugin behavior

When the shared product contract defines behavior that applies to both product plugins, the Maven
plugin must adapt that behavior through Maven APIs rather than inventing a Maven-only semantic.
The shared contract is [§root/FS-plugin-common](../../docs/spec/functional/plugin-common.md#fs-plugin-common-gradle-and-maven-expose-aligned-native-image-plugin-behavior); Maven-specific adaptation is specified by
the focused Maven functional specs under `docs/functional/` and structured by
[§AR-maven-plugin](architecture.md#ar-maven-plugin-the-maven-plugin-adapts-shared-native-image-behavior-to-maven-apis). This follows [§GRUND-plugin-purpose](grund.md#grund-plugin-purpose-the-maven-plugin-realizes-native-build-tools-through-maven-idioms).
