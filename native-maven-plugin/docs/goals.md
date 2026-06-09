# GOAL-native-workflows: Maven users can use Native Image through Maven-native workflows

The Maven plugin must expose Native Image build, test, resource, metadata, tracing-agent, and
inspection workflows through Maven concepts: goals, lifecycle phases, plugin descriptors,
parameters, dependency scopes, toolchains, and local-repository based functional tests. This goal
realizes the repository goal in §root/GOAL-native-build-workflows for Maven and is specified
by §FS-goal-surface, §FS-native-builds,
§FS-config-model, §FS-tracing-agent, and §FS-native-tests.

# GOAL-idiomatic-maven: Maven integration follows Maven idioms and conventions

The Maven plugin should feel like a Maven plugin rather than a command wrapper hidden behind Maven
goals. Plugin code should use mojos, descriptors, `@Parameter` binding, lifecycle phases,
dependency scopes, project model lookup, toolchains, Aether/repository APIs, Plexus integration,
and isolated Maven functional tests where those concepts fit the behavior. This follows
§GRUND-plugin-purpose and is constrained by §REQ-maven-model.

# GOAL-shared-alignment: Maven behavior stays aligned with shared plugin behavior

When the shared product contract defines behavior that applies to both product plugins, the Maven
plugin must adapt that behavior through Maven APIs rather than inventing a Maven-only semantic.
The shared contract is §root/FS-plugin-common; Maven-specific adaptation is specified by
the focused Maven functional specs under `docs/functional/` and structured by
§AR-maven-plugin. This follows §GRUND-plugin-purpose.
