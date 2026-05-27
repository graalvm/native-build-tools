# FS-repository-functional-spec: Native Build Tools repository functional specification

Native Build Tools gives JVM projects build-tool-native workflows for GraalVM Native Image. The
repository must provide Gradle and Maven plugins, shared Native Image support libraries, native
test support, samples, documentation, and CI gates that let users compile, run, test, and maintain
native images without leaving their build tool. This functional spec is the behavior-focused
entry point; the structural map is §AR-repository-architecture.
§GOAL-build-tool-native-image-workflows §GOAL-shared-native-image-behavior-stays-consistent
§GOAL-repository-fixtures-protect-real-build-scenarios

## 1. Users and use cases

| User | Goal | Repository behavior |
| --- | --- | --- |
| Gradle application or library user | Build and run a native executable, shared library, or native test image from a Gradle project. | The Gradle plugin exposes the `graalvmNative` DSL, native-image tasks, metadata tasks, and native-test integration specified by §GRADLE-plugin. |
| Maven application or library user | Build and test native images from Maven lifecycle goals and plugin configuration. | The Maven plugin exposes `native:*` goals, lifecycle bindings, metadata workflows, and native-test integration specified by §MAVEN-plugin. |
| Build author maintaining native-image metadata | Collect resource config, tracing-agent output, reachability metadata, and missing-metadata reports consistently across Gradle and Maven. | Shared utilities and metadata repository support are specified by §COMMON-libraries and adapted by both product plugins. |
| Repository maintainer | Change behavior without breaking existing builds, supported version ranges, or real sample scenarios. | Compatibility requirements, end-to-end tests, and PR CI gates are specified by §REQ-backwards-compatibility-across-plugin-versions, §E2E-functional-test-suite, and §CI-pull-request-ci. |

## 2. Product workflow surface

The repository must provide two first-class product plugins, not a generic wrapper around
`native-image`. The Gradle plugin owns Gradle idioms: plugin application, lazy task registration,
providers, configuration-cache-aware services, TestKit fixtures, Gradle task options, and the
`graalvmNative` extension (§GRADLE-plugin). The Maven plugin owns Maven idioms: mojos, descriptor
metadata, lifecycle phases, parameter binding, Maven toolchains, Maven dependency scopes, and
plugin-manager execution (§MAVEN-plugin).

Both product plugins must let users compile native images, run native executables, execute native
tests, pass Native Image build/runtime options, locate GraalVM toolchains, consume reachability
metadata, collect agent metadata, generate resource configuration, and inspect missing metadata.
They must do that through build-tool-native concepts and must not add build-tool flags that merely
mirror every `native-image` flag (§NGOAL-no-build-tool-flags-for-native-image-flags).

## 3. Shared behavior

Behavior that is not inherently tied to Gradle or Maven must live in common modules so both
plugins see the same Native Image semantics. Shared behavior includes argument-file conversion,
Native Image version parsing, resource configuration models, classpath/JAR resource analysis,
agent mode command-line construction, schema validation, reachability metadata repository lookup,
missing metadata report support, and JUnit native runtime support (§COMMON-libraries).

The product plugins may adapt shared behavior into Gradle tasks or Maven mojos, but they should
not fork option semantics, metadata lookup rules, or native-test launcher behavior just because the
build-tool APIs differ. This requirement realizes
§GOAL-shared-native-image-behavior-stays-consistent.

## 4. Native test behavior

Native test support must work as a product workflow, not only as an internal test harness.
Supported Gradle and Maven projects must be able to compile test code into a native image and run
that image with JUnit Platform behavior equivalent to the JVM test selection where Native Image
constraints allow it. The shared launcher, Native Image feature, unique-ID handling, compatibility
mode behavior, and sample coverage are specified by §TESTING-native-tests-and-fixtures.

Gradle exposes native tests through the `test` binary, `nativeTestCompile`, and `nativeTest`
tasks. Maven exposes them through `native:test` and Maven test-scope dependency handling. Both
adapters must preserve skip/no-test behavior, runtime arguments, test resources, and actionable
failure reporting.

## 5. Compatibility and supported versions

Plugin upgrades must keep existing supported Gradle and Maven builds working across minor and
patch releases unless a major release intentionally removes a deprecated surface
(§REQ-backwards-compatibility-across-plugin-versions). Compatibility covers task and goal names,
DSL and XML configuration names, documented command-line properties, generated output locations
when users rely on them, and shared metadata semantics.

The repository must declare and test the supported JDK, GraalVM, Gradle, and Maven version matrix
(§REQ-supported-build-tool-and-runtime-version-matrix). CI and end-to-end functional tests are the
authoritative evidence that the matrix still works (§CI-pull-request-ci,
§E2E-functional-test-suite).

## 6. Samples, fixtures, and end-to-end validation

Functional behavior must be backed by executable evidence. Samples and fixtures should represent
real project shapes: Java applications, Java libraries, Kotlin projects, custom source sets,
multi-project builds, resources, reflection, metadata repositories, tracing-agent workflows,
layered applications, Maven packaging edge cases, native tests, and compatibility-mode paths
where practical (§TESTING-native-tests-and-fixtures).

Maintainers must be able to run focused end-to-end checks locally with the Gradle functional test
tasks and individual `--tests` filters. Pull request CI must run the same functional test suites
for changed Gradle, Maven, common, sample, and workflow areas (§E2E-functional-test-suite,
§CI-test-native-gradle-plugin, §CI-test-native-maven-plugin).

## 7. CI and quality gates

Pull request CI must validate the parts of the repository that could be affected by a change:
Gradle plugin functional tests, Maven plugin functional tests, shared reachability metadata tests,
JUnit native tests, checkstyle, inspections, native tests, and grund citation validation. The
workflow contracts and shared action setup are specified by §CI-pull-request-ci.

CI is also responsible for running GraalVM dev-build compatibility checks on the product plugins
so the repository sees Native Image changes before users do. Workflow comments should cite the
most specific `CI` or `E2E` declaration that explains the job.

## 8. Documentation and grounded maintenance

User-facing guides remain under the AsciiDoc documentation and root README-style files. Maintainer
specifications live under `docs/spec/` and provide stable citation targets for design, behavior,
CI, code comments, and tests. Behavior changes should update the most specific component spec
before code; placement, dependency, or workflow changes should update the relevant architecture,
component, CI, or E2E section first (§AR-repository-architecture.5).

## 9. Non-goals

The product must not add dedicated build-tool flags for every flag already exposed by
`native-image`; users can pass those through the existing build-argument surfaces
(§NGOAL-no-build-tool-flags-for-native-image-flags). The product must not reimplement normal
Gradle or Maven capabilities unless Native Image multi-target behavior makes the build-tool-native
approach excessively complex (§NGOAL-no-duplication-of-existing-build-tool-capabilities).
