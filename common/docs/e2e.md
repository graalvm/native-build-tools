# E2E-common-tests: Common library tests exercise shared Native Image support behavior

Common executable evidence lives under `common/*/src/test/` and in product plugin functional tests
that consume common modules through real Gradle and Maven builds. Common tests validate the
build-tool-neutral behavior specified by §FS-common-libraries and the boundaries in
§AR-common-libraries; product plugin E2E suites validate adaptation through §gradle/E2E-functional-tests
and §maven/E2E-functional-tests.

## 1. Full local suite

Run common module tests locally through the root build or individual included builds. The broad
repository check is:

```bash
./gradlew check
```

For focused common validation, run the relevant included build task, such as `:utils:test`,
`:graalvm-reachability-metadata:test`, or `:junit-platform-native:test` from the common build
context. Use the repository CI workflows in §root/AR-repository-ci.1.5 and
§root/AR-repository-ci.1.6 as the merge-gate equivalents.

## 2. Scenario Coverage

### 2.1 Native Image utilities and resource analysis

Common utility tests cover argument escaping, argument-file conversion, version parsing, shared
constants, classpath/JAR resource scanning, resource configuration serialization, and schema
validation. This protects §FS-common-libraries.1, §FS-common-libraries.2, and
§FS-common-libraries.7.

### 2.2 Tracing-agent modes and metadata post-processing

Common tests and plugin functional tests cover standard, conditional, direct, and disabled agent
modes plus merge/copy support for generated Native Image metadata. This protects
§FS-common-libraries.3 and §FS-common-libraries.4.

### 2.3 Reachability metadata repository behavior

`common/graalvm-reachability-metadata` tests cover repository indexes, version selection, query
classification, exclusions, not-for-Native-Image entries, missing-metadata reports, and issue
creation payloads. This protects §FS-common-libraries.5 and §FS-common-libraries.6.

### 2.4 JUnit native runtime support

`common/junit-platform-native` tests and product native-test functional suites cover native test
launcher behavior, Native Image feature registration, test-engine configuration providers, and
compatibility-mode support. This protects §root/FS-native-tests.3 and
§GOAL-reusable-runtime.

## 3. CI coverage

`test-graalvm-metadata.yml` validates the reachability metadata library with checkstyle and unit
tests. `test-junit-platform-native.yml` validates the JUnit native support module with checkstyle,
JVM tests, and native tests. Product plugin CI also exercises common behavior through Gradle and
Maven functional-test matrices. These workflow gates are specified by
§root/AR-repository-ci.1.5, §root/AR-repository-ci.1.6,
§root/AR-repository-ci.1.3, and §root/AR-repository-ci.1.4.
