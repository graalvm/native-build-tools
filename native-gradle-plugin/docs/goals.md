# GOAL-native-workflows: Gradle users can use Native Image through Gradle-native workflows

The Gradle plugin must expose Native Image build, run, test, resource, metadata, and tracing-agent
workflows through Gradle concepts: plugins, extensions, named containers, tasks, providers,
toolchains, build services, and TestKit-verifiable task graphs. This goal realizes the repository
goal in §root/GOAL-native-build-workflows for Gradle, follows
§GRUND-plugin-purpose, and is specified by §FS-plugin-model,
§FS-native-tasks, §FS-resources-metadata,
§FS-tracing-agent, and §FS-native-tests.

# GOAL-idiomatic-gradle: Gradle integration follows Gradle idioms and conventions

The Gradle plugin should feel like a Gradle plugin rather than a command wrapper hidden behind
Gradle tasks. Plugin code should use Gradle's lazy configuration model, providers, task inputs and
outputs, build services, toolchains, attributes, artifact transforms, configuration-cache rules,
and TestKit verification where those concepts fit the behavior. This follows
§GRUND-plugin-purpose and is constrained by
§REQ-gradle-model.

# GOAL-shared-alignment: Gradle behavior stays aligned with shared plugin behavior

When the shared product contract defines behavior that applies to both product plugins, the Gradle
plugin must adapt that behavior through Gradle APIs rather than inventing a Gradle-only semantic.
The shared contract is §root/FS-plugin-common; Gradle-specific adaptation is specified by
the focused Gradle functional specs under `docs/functional/` and structured by
§AR-gradle-plugin. This follows §GRUND-plugin-purpose.
