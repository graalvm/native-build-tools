# E2E-common-tests: Common library tests exercise shared Native Image support behavior

Common executable evidence lives under `common/*/src/test/` and in product plugin functional tests
that consume common modules through real Gradle and Maven builds. Common tests validate the
build-tool-neutral behavior specified by [§FS-common-libraries](functional-spec.md#fs-common-libraries-common-libraries-provide-shared-native-image-utilities-and-metadata-workflows) and the boundaries in
[§AR-common-libraries](architecture.md#ar-common-libraries-shared-libraries-stay-independent-from-gradle-and-maven-apis); product plugin E2E suites validate adaptation through [§gradle/E2E-functional-tests](../../native-gradle-plugin/docs/e2e.md#e2e-functional-tests-gradle-functional-tests-exercise-real-gradle-native-image-builds)
and [§maven/E2E-functional-tests](../../native-maven-plugin/docs/e2e.md#e2e-functional-tests-maven-functional-tests-exercise-real-maven-native-image-builds).

## 1. Full local suite

Run common module tests locally through the root build or individual included builds. The broad
repository check is:

```bash
./gradlew check
```

For focused common validation, run the relevant included build task, such as `:utils:test`,
`:graalvm-reachability-metadata:test`, or `:junit-platform-native:test` from the common build
context. Use the repository CI workflows in [§root/AR-repository-ci.1.5](../../docs/spec/architecture/ci.md#15-reachability-metadata-library-pr-workflow) and
[§root/AR-repository-ci.1.6](../../docs/spec/architecture/ci.md#16-junit-native-support-pr-workflow), and
[§root/AR-repository-ci.1.7](../../docs/spec/architecture/ci.md#17-common-utilities-pr-workflow) as the merge-gate equivalents.

## 2. Scenario Coverage

### 2.1 Native Image utilities and resource analysis

Common utility tests cover argument escaping, argument-file conversion, version parsing, shared
constants, classpath/JAR resource scanning, resource configuration serialization, and schema
validation. This protects [§FS-common-libraries.1](functional-spec.md#1-shared-native-image-utilities), [§FS-common-libraries.2](functional-spec.md#2-resource-configuration), and
[§FS-common-libraries.7](functional-spec.md#7-schema-validation).

### 2.2 Tracing-agent modes and metadata post-processing

Common tests and plugin functional tests cover standard, conditional, direct, and disabled agent
modes plus merge/copy support for generated Native Image metadata. This protects
[§FS-common-libraries.3](functional-spec.md#3-native-image-tracing-agent) and [§FS-common-libraries.4](functional-spec.md#4-agent-metadata-post-processing).

### 2.3 Reachability metadata repository behavior

`common/graalvm-reachability-metadata` tests cover repository indexes, version selection, query
classification, exclusions, not-for-Native-Image entries, missing-metadata reports, and issue
creation payloads. This protects [§FS-common-libraries.5](functional-spec.md#5-reachability-metadata-repository) and [§FS-common-libraries.6](functional-spec.md#6-missing-metadata-reporting).

### 2.4 JUnit native runtime support

`common/junit-platform-native` tests and product native-test functional suites cover native test
launcher behavior, Native Image feature registration, test-engine configuration providers, and
compatibility-mode support. This protects [§root/FS-native-tests.3](../../docs/spec/functional/native-tests.md#3-native-launcher-and-feature) and
[§GOAL-reusable-runtime](goals.md#goal-reusable-runtime-native-test-and-metadata-support-remains-reusable-by-product-plugins).

## 3. CI coverage

`test-common-utils.yml` validates the shared utility module with checkstyle and unit tests.
`test-graalvm-metadata.yml` validates the reachability metadata library with checkstyle and unit
tests. `test-junit-platform-native.yml` validates the JUnit native support module with checkstyle,
JVM tests, and native tests. Product plugin CI also exercises common behavior through Gradle and
Maven functional-test matrices. These workflow gates are specified by
[§root/AR-repository-ci.1.7](../../docs/spec/architecture/ci.md#17-common-utilities-pr-workflow),
[§root/AR-repository-ci.1.5](../../docs/spec/architecture/ci.md#15-reachability-metadata-library-pr-workflow),
[§root/AR-repository-ci.1.6](../../docs/spec/architecture/ci.md#16-junit-native-support-pr-workflow),
[§root/AR-repository-ci.1.3](../../docs/spec/architecture/ci.md#13-gradle-plugin-pr-workflow), and
[§root/AR-repository-ci.1.4](../../docs/spec/architecture/ci.md#14-maven-plugin-pr-workflow).
