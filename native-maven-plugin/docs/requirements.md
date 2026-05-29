# REQ-maven-plugin-maven-model-compatibility: The Maven plugin preserves Maven model compatibility

The Maven plugin must integrate with Maven lifecycle phases, dependency scopes, plugin
configuration, system-property binding, toolchains, and project model lookup without replacing
Maven's build model. It may add Native Image-specific goals and configuration, but those additions
must remain compatible with Maven descriptor generation and normal lifecycle execution. This
requirement constrains §FS-maven-plugin.1 and §AR-maven-plugin.3.

# REQ-maven-plugin-goal-surface-stability: Maven goal and parameter names remain stable across compatible releases

Maven goal names, parameter names, default phases, and documented command-line properties are
user-facing compatibility surfaces. Changes to those names must follow the repository
compatibility requirement in §root/REQ-backwards-compatibility-across-plugin-versions and the
deprecated goal behavior in §FS-maven-plugin.1.1.
