# REQ-maven-plugin-maven-model-compatibility: The Maven plugin preserves Maven model compatibility

The Maven plugin must integrate with Maven lifecycle phases, dependency scopes, plugin
configuration, system-property binding, toolchains, and project model lookup without replacing
Maven's build model. It may add Native Image-specific goals and configuration, but those additions
must remain compatible with Maven descriptor generation and normal lifecycle execution.

Maven-facing implementation must use Maven APIs and conventions for Maven behavior: mojos and
plugin descriptors for goal exposure, `@Parameter` binding for configuration and system
properties, lifecycle phases for lifecycle integration, dependency scopes for classpath assembly,
project model lookup for configured plugin state, toolchains for executable lookup, and
Aether/repository and Plexus APIs for Maven-managed services. Behavior that is genuinely
build-tool-neutral should move to `common` instead of reimplementing Maven-specific logic.
This requirement constrains §FS-maven-goal-surface and §AR-maven-plugin.3, and supports
§GOAL-maven-plugin-idiomatic-maven-integration.

# REQ-maven-plugin-goal-surface-stability: Maven goal and parameter names remain stable across compatible releases

Maven goal names, parameter names, default phases, and documented command-line properties are
user-facing compatibility surfaces. Changes to those names must follow the repository
compatibility requirement in §root/REQ-backwards-compatibility-across-plugin-versions and the
deprecated goal behavior in §FS-maven-goal-surface.1.
