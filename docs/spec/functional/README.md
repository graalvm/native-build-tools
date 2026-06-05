# Functional Specification

The functional specs describe observable repository behavior: commands, outputs, workflows,
configuration contracts, generated artifacts, and verification expectations. They say what Native
Build Tools must do, while architecture specs explain where that behavior is implemented.

## Cross-plugin (Gradle + Maven) contracts

| File | Holds |
| --- | --- |
| [plugin-common.md](plugin-common.md) | Parity boundary (§FS-plugin-common-behavior) and Reader View. |
| [native-image-builds.md](native-image-builds.md) | Building native images from project state (§FS-native-image-builds). |
| [native-tests.md](native-tests.md) | Compiling and running JUnit tests as a native image (§FS-native-tests). |
| [resources-and-metadata.md](resources-and-metadata.md) | Resource config, reachability metadata, missing-metadata reports, dynamic access metadata, schema validation (§FS-resources-and-metadata). |
| [tracing-agent.md](tracing-agent.md) | Native Image tracing-agent attachment and post-processing (§FS-tracing-agent-workflows). |
| [option-precedence.md](option-precedence.md) | Command-line vs durable configuration precedence (§FS-option-precedence). |
| [build-infrastructure.md](build-infrastructure.md) | Build, documentation, release, and generated artifact behavior (§FS-build-infrastructure). |

Build-tool-specific behavior lives in the Gradle and Maven plugin functional specs.
