# GOAL-native-workflows: Gradle users can use Native Image through Gradle-native workflows

The Gradle plugin must expose Native Image build, run, test, resource, metadata, and tracing-agent
workflows through Gradle concepts: plugins, extensions, named containers, tasks, providers,
toolchains, build services, and TestKit-verifiable task graphs. This goal realizes the repository
goal in [§root/GOAL-native-build-workflows](../../docs/spec/goals.md#goal-native-build-workflows-users-can-easily-build-run-and-test-native-images-with-their-build-tool) for Gradle, follows
[§GRUND-plugin-purpose](grund.md#grund-plugin-purpose-the-gradle-plugin-realizes-native-build-tools-through-gradle-idioms), and is specified by [§FS-plugin-model](functional/plugin-model.md#fs-plugin-model-gradle-plugin-activation-and-dsl-model),
[§FS-native-tasks](functional/native-image-tasks.md#fs-native-tasks-gradle-native-image-tasks-build-and-run-native-image-outputs), [§FS-resources-metadata](functional/resources-and-metadata.md#fs-resources-metadata-gradle-tasks-generate-resources-and-consume-reachability-metadata),
[§FS-tracing-agent](functional/tracing-agent.md#fs-tracing-agent-gradle-tasks-attach-and-post-process-native-image-tracing-agent-metadata), and [§FS-native-tests](functional/native-tests.md#fs-native-tests-gradle-tasks-compile-and-run-native-junit-tests).

# GOAL-idiomatic-gradle: Gradle integration follows Gradle idioms and conventions

The Gradle plugin should feel like a Gradle plugin rather than a command wrapper hidden behind
Gradle tasks. Plugin code should use Gradle's lazy configuration model, providers, task inputs and
outputs, build services, toolchains, attributes, artifact transforms, configuration-cache rules,
and TestKit verification where those concepts fit the behavior. This follows
[§GRUND-plugin-purpose](grund.md#grund-plugin-purpose-the-gradle-plugin-realizes-native-build-tools-through-gradle-idioms) and is constrained by
[§REQ-gradle-model](requirements.md#req-gradle-model-the-gradle-plugin-preserves-gradle-model-compatibility).

# GOAL-shared-alignment: Gradle behavior stays aligned with shared plugin behavior

When the shared product contract defines behavior that applies to both product plugins, the Gradle
plugin must adapt that behavior through Gradle APIs rather than inventing a Gradle-only semantic.
The shared contract is [§root/FS-plugin-common](../../docs/spec/functional/plugin-common.md#fs-plugin-common-gradle-and-maven-expose-aligned-native-image-plugin-behavior); Gradle-specific adaptation is specified by
the focused Gradle functional specs under `docs/functional/` and structured by
[§AR-gradle-plugin](architecture.md#ar-gradle-plugin-the-gradle-plugin-adapts-shared-native-image-behavior-to-gradle-apis). This follows [§GRUND-plugin-purpose](grund.md#grund-plugin-purpose-the-gradle-plugin-realizes-native-build-tools-through-gradle-idioms).
