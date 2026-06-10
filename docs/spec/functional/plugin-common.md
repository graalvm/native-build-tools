# FS-plugin-common: Gradle and Maven expose aligned Native Image plugin behavior

Native Build Tools gives Java build users a build-tool-native path to GraalVM Native Image. The
Gradle and Maven plugins use different build models, but they should answer the same practical
questions: build a native executable, run it, test it, supply metadata, inspect missing metadata,
and collect tracing-agent output. This functional contract is the parity boundary; the detailed
shared behavior lives in the sibling specs below. It realizes
[§GOAL-plugin-parity](../goals.md#goal-plugin-parity-shared-native-image-behavior-remains-consistent-across-gradle-and-maven) and is implemented by the focused Gradle and Maven functional specs with
shared primitives from [§common/FS-common-libraries](../../../common/docs/functional-spec.md#fs-common-libraries-common-libraries-provide-shared-native-image-utilities-and-metadata-workflows). User-facing diagnostics should remain concise
and actionable under [§GOAL-concise-actionable-output](../goals.md#goal-concise-actionable-output-build-output-is-concise-actionable-and-token-efficient).

## Reader View

| User goal | Gradle adaptation | Maven adaptation | Shared spec |
| --- | --- | --- | --- |
| Build the main application image | [§gradle/FS-native-tasks](../../../native-gradle-plugin/docs/functional/native-image-tasks.md#fs-native-tasks-gradle-native-image-tasks-build-and-run-native-image-outputs) | [§maven/FS-goal-surface](../../../native-maven-plugin/docs/functional/goal-surface.md#fs-goal-surface-maven-goals-expose-native-image-workflows), [§maven/FS-native-builds](../../../native-maven-plugin/docs/functional/native-image-builds.md#fs-native-builds-maven-goals-build-native-image-outputs-from-project-state) | [§FS-native-builds](native-image-builds.md#fs-native-builds-both-plugins-build-native-images-from-build-tool-project-state) |
| Run the application image | [§gradle/FS-native-tasks](../../../native-gradle-plugin/docs/functional/native-image-tasks.md#fs-native-tasks-gradle-native-image-tasks-build-and-run-native-image-outputs) | [§maven/FS-native-builds](../../../native-maven-plugin/docs/functional/native-image-builds.md#fs-native-builds-maven-goals-build-native-image-outputs-from-project-state) | [§FS-native-builds](native-image-builds.md#fs-native-builds-both-plugins-build-native-images-from-build-tool-project-state) |
| Build and run tests as a native image | [§gradle/FS-native-tests](../../../native-gradle-plugin/docs/functional/native-tests.md#fs-native-tests-gradle-tasks-compile-and-run-native-junit-tests) | [§maven/FS-native-tests](../../../native-maven-plugin/docs/functional/native-tests.md#fs-native-tests-maven-goals-compile-and-run-native-junit-tests) | [§FS-native-tests](native-tests.md#fs-native-tests-both-plugins-compile-and-execute-junit-tests-as-a-native-image) |
| Generate resource configuration | [§gradle/FS-resources-and-metadata](../../../native-gradle-plugin/docs/functional/resources-and-metadata.md#fs-resources-and-metadata-gradle-tasks-generate-resources-and-consume-reachability-metadata) | [§maven/FS-resources-and-metadata](../../../native-maven-plugin/docs/functional/resources-and-metadata.md#fs-resources-and-metadata-maven-goals-generate-resources-and-consume-reachability-metadata) | [§FS-resources-and-metadata.1](resources-and-metadata.md#1-resource-configuration) |
| Use reachability metadata | [§gradle/FS-resources-and-metadata](../../../native-gradle-plugin/docs/functional/resources-and-metadata.md#fs-resources-and-metadata-gradle-tasks-generate-resources-and-consume-reachability-metadata) | [§maven/FS-resources-and-metadata](../../../native-maven-plugin/docs/functional/resources-and-metadata.md#fs-resources-and-metadata-maven-goals-generate-resources-and-consume-reachability-metadata) | [§FS-resources-and-metadata.2](resources-and-metadata.md#2-reachability-metadata-repository) |
| Inspect missing metadata | [§gradle/FS-resources-and-metadata](../../../native-gradle-plugin/docs/functional/resources-and-metadata.md#fs-resources-and-metadata-gradle-tasks-generate-resources-and-consume-reachability-metadata) | [§maven/FS-resources-and-metadata](../../../native-maven-plugin/docs/functional/resources-and-metadata.md#fs-resources-and-metadata-maven-goals-generate-resources-and-consume-reachability-metadata) | [§FS-resources-and-metadata.3](resources-and-metadata.md#3-missing-metadata-reports) |
| Collect agent output | [§gradle/FS-tracing-agent](../../../native-gradle-plugin/docs/functional/tracing-agent.md#fs-tracing-agent-gradle-tasks-attach-and-post-process-native-image-tracing-agent-metadata) | [§maven/FS-tracing-agent](../../../native-maven-plugin/docs/functional/tracing-agent.md#fs-tracing-agent-maven-goals-attach-and-post-process-native-image-tracing-agent-metadata) | [§FS-tracing-agent](tracing-agent.md#fs-tracing-agent-both-plugins-attach-the-native-image-tracing-agent-and-post-process-its-output) |

```mermaid
sequenceDiagram
    autonumber
    participant User as Build user
    participant Tool as Gradle or Maven build
    participant Plugin as Native Build Tools plugin
    participant Common as Shared common libraries
    participant Metadata as Reachability metadata repository
    participant NI as native-image

    User->>Tool: run native build/test/metadata command
    Tool->>Plugin: provide project model + plugin configuration
    Plugin->>Common: normalize options, resources, metadata, agent behavior
    Plugin->>Metadata: resolve selected metadata for dependencies
    Plugin->>NI: invoke native-image with classpath, args, resources, metadata
    NI-->>Tool: executable, test image, reports, or diagnostics
```

## 1. Capability parity

Both product plugins must support the following capabilities unless the build-tool model makes the
capability impossible or intentionally different:

- native image builds ([§FS-native-builds](native-image-builds.md#fs-native-builds-both-plugins-build-native-images-from-build-tool-project-state))
- native test compilation and execution ([§FS-native-tests](native-tests.md#fs-native-tests-both-plugins-compile-and-execute-junit-tests-as-a-native-image))
- Native Image executable discovery and command-line assembly ([§FS-native-builds.2](native-image-builds.md#2-command-line-construction), [§FS-native-builds.3](native-image-builds.md#3-executable-lookup))
- argument-file handling ([§GLOSS-argument-file](../glossary.md#gloss-argument-file-native-image-argument-file))
- resource configuration generation ([§FS-resources-and-metadata.1](resources-and-metadata.md#1-resource-configuration))
- reachability metadata repository consumption ([§FS-resources-and-metadata.2](resources-and-metadata.md#2-reachability-metadata-repository))
- missing metadata reports ([§FS-resources-and-metadata.3](resources-and-metadata.md#3-missing-metadata-reports))
- dynamic access metadata ([§FS-resources-and-metadata.4](resources-and-metadata.md#4-dynamic-access-metadata))
- Native Image tracing-agent modes and merge/copy workflows ([§FS-tracing-agent](tracing-agent.md#fs-tracing-agent-both-plugins-attach-the-native-image-tracing-agent-and-post-process-its-output))
- schema validation ([§FS-resources-and-metadata.5](resources-and-metadata.md#5-schema-validation))
- Native Image version-dependent behavior ([§FS-native-builds.4](native-image-builds.md#4-version-and-schema-gates))
- predictable option precedence ([§FS-option-precedence](option-precedence.md#fs-option-precedence-command-line-input-and-durable-configuration-produce-one-option-state))

When a capability is intentionally different between Gradle and Maven, the product-specific specs
must explain the difference at the point where each plugin adapts this common contract.
Differences should follow the build tool's normal user experience rather than inventing a
cross-tool abstraction that feels natural in neither tool.

## 2. Verification surface

Parity must be verified by shared samples, product functional tests, and common module tests.
Product functional tests should cover the same scenario families in both build tools where
possible; product-specific tests cover behavior that only one build tool can express. The plugin
end-to-end execution contracts are [§gradle/E2E-functional-tests](../../../native-gradle-plugin/docs/e2e.md#e2e-functional-tests-gradle-functional-tests-exercise-real-gradle-native-image-builds) and
[§maven/E2E-functional-tests](../../../native-maven-plugin/docs/e2e.md#e2e-functional-tests-maven-functional-tests-exercise-real-maven-native-image-builds); fixture ownership is [§AR-build-infrastructure.4](../architecture/build-infrastructure.md#4-fixture-and-sample-boundary).

When a new capability is added to one plugin, the implementation should either add the equivalent
capability to the other plugin, cite the existing matching behavior, or explicitly document why
the other build tool cannot or should not expose it.
