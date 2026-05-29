# GOAL-maven-plugin-native-image-workflows: Maven users can use Native Image through Maven-native workflows

The Maven plugin must expose Native Image build, test, resource, metadata, tracing-agent, and
inspection workflows through Maven concepts: goals, lifecycle phases, plugin descriptors,
parameters, dependency scopes, toolchains, and local-repository based functional tests. This goal
realizes the repository goal in §root/GOAL-build-tool-native-image-workflows for Maven and is specified
by §FS-maven-plugin under §GRUND-maven-plugin-purpose.

# GOAL-maven-plugin-behavior-stays-aligned-with-shared-contract: Maven behavior stays aligned with shared plugin behavior

When the shared product contract defines behavior that applies to both product plugins, the Maven
plugin must adapt that behavior through Maven APIs rather than inventing a Maven-only semantic.
The shared contract is §root/FS-plugin-common-behavior; Maven-specific adaptation is specified by
§FS-maven-plugin and structured by §AR-maven-plugin. This follows §GRUND-maven-plugin-purpose.
