# FS-maven-plugin: The Maven plugin wires Native Image behavior into Maven builds

The `native-maven-plugin` module provides a Maven plugin packaged as `maven-plugin`. Its mojos
translate Maven project state, XML configuration, system properties, dependency scopes, and
lifecycle phases into the shared Native Build Tools behavior in
§root/FS-plugin-common-behavior, §root/FS-native-tests, and
§common/FS-common-libraries. This realizes §GOAL-maven-plugin-native-image-workflows under
§GRUND-maven-plugin-purpose, keeps Maven behavior aligned through
§GOAL-maven-plugin-behavior-stays-aligned-with-shared-contract, and is constrained by
§REQ-maven-plugin-maven-model-compatibility and §REQ-maven-plugin-goal-surface-stability.

## At a Glance

| User wants to... | They configure | They run | Main output |
| --- | --- | --- | --- |
| Build during the Maven lifecycle | a `native` profile with `compile-no-fork` bound to `package` | `mvn -Pnative package` | native executable under `target/` |
| Build directly | plugin configuration and project packaging | `mvn -Pnative native:compile` | native executable under `target/` |
| Build and run native tests | `native:test` execution plus normal Maven test setup | `mvn -Pnative native:test` or `mvn -Pnative test` | Maven test success/failure |
| Inspect Native Image args | plugin build configuration | `mvn -Pnative native:write-args-file` | `target/native-image*.args` |
| Generate resource config | resource-generation goals | `native:generateResourceConfig` | `target/native/generated/generateResourceConfig/` |
| Use reachability metadata | `<metadataRepository>` | native build goal or `native:add-reachability-metadata` | selected metadata passed to Native Image |
| Collect agent metadata | `<agent>` or `-Dagent=true` | JVM/test run, then `native:metadata-copy` | copied or merged metadata files |

## Functional Areas

| File | Holds |
| --- | --- |
| [goal-surface.md](goal-surface.md) | Maven goals, lifecycle bindings, support goals, and profile usage (§FS-maven-goal-surface). |
| [native-image-builds.md](native-image-builds.md) | Native image build behavior, main-class discovery, skipping, classpath scopes, SBOM, argument files, and command examples (§FS-maven-native-image-builds). |
| [configuration-model.md](configuration-model.md) | Native Image options, command-line properties, parent POM merging, toolchain lookup, and override precedence (§FS-maven-configuration-model). |
| [native-tests.md](native-tests.md) | Native test classpath, discovery preconditions, launcher selection, execution, and command examples (§FS-maven-native-tests). |
| [tracing-agent.md](tracing-agent.md) | Tracing-agent enablement, modes, output, merge/copy behavior, and examples (§FS-maven-tracing-agent). |
| [resources-and-metadata.md](resources-and-metadata.md) | Resource configuration goals, reachability metadata, missing-metadata reports, schema validation, and entry points (§FS-maven-resources-and-metadata). |

Use the focused IDs above for code and test citations. Use §FS-maven-plugin only when citing the Maven plugin functional contract as a whole.

## Verification Surface

Unit tests cover mojos, configuration objects, command-line assembly, and shared utility
integration that can be tested locally. Functional tests exercise Maven sample projects through a
seeded local Maven repository, with scenario ownership defined by
§maven/E2E-maven-plugin-functional-tests and fixture ownership defined by
§root/AR-build-infrastructure.4.1.
