# GOAL-gradle-plugin-native-image-workflows: Gradle users can use Native Image through Gradle-native workflows

The Gradle plugin must expose Native Image build, run, test, resource, metadata, and tracing-agent
workflows through Gradle concepts: plugins, extensions, named containers, tasks, providers,
toolchains, build services, and TestKit-verifiable task graphs. This goal realizes the repository
goal in §root/GOAL-native-build-workflows for Gradle, follows
§GRUND-gradle-plugin-purpose, and is specified by §FS-gradle-plugin-model,
§FS-gradle-native-image-tasks, §FS-gradle-resources-and-metadata,
§FS-gradle-tracing-agent, and §FS-gradle-native-tests.

# GOAL-gradle-plugin-behavior-stays-aligned-with-shared-contract: Gradle behavior stays aligned with shared plugin behavior

When the shared product contract defines behavior that applies to both product plugins, the Gradle
plugin must adapt that behavior through Gradle APIs rather than inventing a Gradle-only semantic.
The shared contract is §root/FS-plugin-common-behavior; Gradle-specific adaptation is specified by
the focused Gradle functional specs under `docs/functional/` and structured by
§AR-gradle-plugin. This follows §GRUND-gradle-plugin-purpose.
