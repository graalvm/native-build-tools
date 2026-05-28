# FS-plugin-common-behavior: Gradle and Maven expose aligned Native Image plugin behavior

The Gradle and Maven plugins should support the same Native Image capability families wherever
their build-tool models overlap. This is a product-level functional contract, not the architecture
of the `common/` implementation modules. It realizes
§GOAL-shared-native-image-behavior-stays-consistent and is implemented by
§FS-gradle-plugin and §FS-maven-plugin with shared primitives from §FS-common-libraries.

## 1. Capability parity

Both product plugins must support native image builds, native test compilation and execution,
Native Image executable discovery, command-line assembly, argument-file handling, resource
configuration generation, reachability metadata repository consumption, missing metadata reports,
dynamic access metadata, Native Image tracing-agent modes, agent metadata merge/copy behavior,
schema validation, and Native Image version-dependent behavior unless a build-tool model makes the
capability impossible or intentionally different.

When a capability is intentionally different between Gradle and Maven, the product-specific specs
must explain the difference at the point where each plugin adapts this common contract.

## 2. Native Image builds

Both plugins must translate build-tool project state into a Native Image invocation with aligned
semantics for classpath/module path inputs, output names, main class selection, shared-library
mode, build arguments, JVM arguments, system properties, environment variables, generated
configuration directories, reachability metadata, PGO options, layer options, and argument-file
use. Gradle exposes this through tasks and DSL options in §FS-gradle-plugin.2 and
§FS-gradle-plugin.3. Maven exposes this through goals, parameters, and lifecycle behavior in
§FS-maven-plugin.1 and §FS-maven-plugin.2.

## 3. Native tests

Both plugins must compile native test images and execute them through the shared JUnit native
support where the build-tool test model allows it. The shared native test behavior is specified by
§FS-native-tests-and-fixtures. Gradle adapts it through test binaries and native test tasks in
§FS-gradle-plugin.6. Maven adapts it through the `native:test` goal in §FS-maven-plugin.4.

## 4. Resources and reachability metadata

Both plugins must expose resource configuration generation, reachability metadata repository
lookup, missing metadata reports, dynamic access metadata, and schema validation. Shared library
behavior for resource scanning, repository lookup, missing metadata support, and validation lives
in §FS-common-libraries.2, §FS-common-libraries.5, §FS-common-libraries.6, and
§FS-common-libraries.7. Gradle exposes these behaviors through §FS-gradle-plugin.4; Maven exposes
them through §FS-maven-plugin.6 and the support goals in §FS-maven-plugin.1.3.

## 5. Tracing agent workflows

Both plugins must expose standard, conditional, direct, and disabled Native Image tracing-agent
modes, along with agent output merge and copy workflows. Shared agent mode and post-processing
behavior lives in §FS-common-libraries.3 and §FS-common-libraries.4. Gradle exposes it through
§FS-gradle-plugin.5; Maven exposes it through §FS-maven-plugin.5.

## 6. Option precedence and command-line compatibility

Both plugins must keep command-line overrides and configured options predictable in the idioms of
their build tool. The exact precedence rules may differ because Gradle task options and Maven
parameter binding differ, but each plugin must document how temporary command-line input relates
to durable build configuration. Gradle precedence is specified by §FS-gradle-plugin.2.5. Maven
precedence is specified by §FS-maven-plugin.3.5.

## 7. Verification surface

Parity must be verified by shared samples, product functional tests, and common module tests.
Product functional tests should cover the same scenario families in both build tools where
possible, while product-specific tests cover behavior that only one build tool can express. The
end-to-end execution contract is §E2E-functional-test-suite, and fixture ownership is
§AR-native-tests-and-fixtures.
