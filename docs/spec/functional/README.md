# Functional Specification

The functional specs describe observable repository behavior: commands, outputs, workflows,
configuration contracts, generated artifacts, and verification expectations. They say what Native
Build Tools must do, while architecture specs explain where that behavior is implemented.

## Cross-plugin (Gradle + Maven) contracts

| File | Holds |
| --- | --- |
| [plugin-common.md](plugin-common.md) | Parity boundary ([§FS-plugin-common](plugin-common.md#fs-plugin-common-gradle-and-maven-expose-aligned-native-image-plugin-behavior)) and Reader View. |
| [native-image-builds.md](native-image-builds.md) | Building native images from project state ([§FS-native-builds](native-image-builds.md#fs-native-builds-both-plugins-build-native-images-from-build-tool-project-state)). |
| [native-tests.md](native-tests.md) | Compiling and running JUnit tests as a native image ([§FS-native-tests](native-tests.md#fs-native-tests-both-plugins-compile-and-execute-junit-tests-as-a-native-image)). |
| [resources-and-metadata.md](resources-and-metadata.md) | Resource config, reachability metadata, missing-metadata reports, dynamic access metadata, schema validation ([§FS-resources-metadata](resources-and-metadata.md#fs-resources-metadata-both-plugins-generate-resource-config-and-consume-reachability-metadata)). |
| [tracing-agent.md](tracing-agent.md) | Native Image tracing-agent attachment and post-processing ([§FS-tracing-agent](tracing-agent.md#fs-tracing-agent-both-plugins-attach-the-native-image-tracing-agent-and-post-process-its-output)). |
| [option-precedence.md](option-precedence.md) | Command-line vs durable configuration precedence ([§FS-option-precedence](option-precedence.md#fs-option-precedence-command-line-input-and-durable-configuration-produce-one-option-state)). |
| [build-infrastructure.md](build-infrastructure.md) | Build, documentation, release, and generated artifact behavior ([§FS-build-infrastructure](build-infrastructure.md#fs-build-infrastructure-build-documentation-and-release-infrastructure)). |

Build-tool-specific behavior lives in the Gradle and Maven plugin functional specs.
