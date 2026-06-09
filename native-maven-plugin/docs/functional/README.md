# Functional Specification

The `native-maven-plugin` module provides a Maven plugin packaged as `maven-plugin`. Its mojos
translate Maven project state, XML configuration, system properties, dependency scopes, and
lifecycle phases into the shared Native Build Tools behavior defined by root and common specs.
The focused files below own the citable Maven functional contracts for
[§GOAL-native-workflows](../goals.md#goal-native-workflows-maven-users-can-use-native-image-through-maven-native-workflows),
[§GOAL-idiomatic-maven](../goals.md#goal-idiomatic-maven-maven-integration-follows-maven-idioms-and-conventions), and
[§GOAL-shared-alignment](../goals.md#goal-shared-alignment-maven-behavior-stays-aligned-with-shared-plugin-behavior), constrained by
[§REQ-maven-model](../requirements.md#req-maven-model-the-maven-plugin-preserves-maven-model-compatibility) and [§REQ-goal-surface](../requirements.md#req-goal-surface-maven-goal-and-parameter-names-remain-stable-across-compatible-releases).

## Functional Areas

| File | Holds |
| --- | --- |
| [goal-surface.md](goal-surface.md) | Maven goals, lifecycle bindings, support goals, and profile usage ([§FS-goal-surface](goal-surface.md#fs-goal-surface-maven-goals-expose-native-image-workflows)). |
| [native-image-builds.md](native-image-builds.md) | Native image build behavior, main-class discovery, skipping, classpath scopes, SBOM, argument files, and command examples ([§FS-native-builds](native-image-builds.md#fs-native-builds-maven-goals-build-native-image-outputs-from-project-state)). |
| [configuration-model.md](configuration-model.md) | Native Image options, command-line properties, parent POM merging, toolchain lookup, and override precedence ([§FS-config-model](configuration-model.md#fs-config-model-maven-xml-and-command-line-properties-configure-native-image-builds)). |
| [native-tests.md](native-tests.md) | Native test classpath, discovery preconditions, launcher selection, execution, and command examples ([§FS-native-tests](native-tests.md#fs-native-tests-maven-goals-compile-and-run-native-junit-tests)). |
| [tracing-agent.md](tracing-agent.md) | Tracing-agent enablement, modes, output, merge/copy behavior, and examples ([§FS-tracing-agent](tracing-agent.md#fs-tracing-agent-maven-goals-attach-and-post-process-native-image-tracing-agent-metadata)). |
| [resources-and-metadata.md](resources-and-metadata.md) | Resource configuration goals, reachability metadata, missing-metadata reports, schema validation, and entry points ([§FS-resources-metadata](resources-and-metadata.md#fs-resources-metadata-maven-goals-generate-resources-and-consume-reachability-metadata)). |

Use the focused IDs above for code and test citations.
