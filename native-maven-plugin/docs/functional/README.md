# Functional Specification

The `native-maven-plugin` module provides a Maven plugin packaged as `maven-plugin`. Its mojos
translate Maven project state, XML configuration, system properties, dependency scopes, and
lifecycle phases into the shared Native Build Tools behavior defined by root and common specs.
The focused files below own the citable Maven functional contracts for
§GOAL-native-workflows,
§GOAL-idiomatic-maven, and
§GOAL-shared-alignment, constrained by
§REQ-maven-model and §REQ-goal-surface.

## Functional Areas

| File | Holds |
| --- | --- |
| [goal-surface.md](goal-surface.md) | Maven goals, lifecycle bindings, support goals, and profile usage (§FS-goal-surface). |
| [native-image-builds.md](native-image-builds.md) | Native image build behavior, main-class discovery, skipping, classpath scopes, SBOM, argument files, and command examples (§FS-native-builds). |
| [configuration-model.md](configuration-model.md) | Native Image options, command-line properties, parent POM merging, toolchain lookup, and override precedence (§FS-config-model). |
| [native-tests.md](native-tests.md) | Native test classpath, discovery preconditions, launcher selection, execution, and command examples (§FS-native-tests). |
| [tracing-agent.md](tracing-agent.md) | Tracing-agent enablement, modes, output, merge/copy behavior, and examples (§FS-tracing-agent). |
| [resources-and-metadata.md](resources-and-metadata.md) | Resource configuration goals, reachability metadata, missing-metadata reports, schema validation, and entry points (§FS-resources-metadata). |

Use the focused IDs above for code and test citations.
