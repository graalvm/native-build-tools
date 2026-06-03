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

The sections below keep Maven goal and mojo behavior available as narrow citation targets such as
§FS-maven-plugin.1.1 for build goals, §FS-maven-plugin.4 for native tests, and
§FS-maven-plugin.5.4 for metadata copy.

## 1. Plugin goal surface

The Maven plugin exposes Native Image workflows as Maven goals. Goal names must work for direct
command-line use and for profile-bound builds that run through normal Maven lifecycle phases.

### 1.1 Build goals

`native:compile` is the direct command-line build goal. Users run it when Maven has prepared the
project and they want the plugin to build a native image explicitly.

```bash
mvn -Pnative native:compile
```

`native:compile-no-fork` is the lifecycle-friendly build goal. It runs inside the current Maven
build, normally bound to `package`, so a profile can produce the JAR and native executable with one
command. Both goals must use the same Native Image command construction once Maven project state is
ready.

```bash
mvn -Pnative -DquickBuild -DskipTests package
```

The deprecated `native:build` goal may remain as a compatibility alias, but it must warn users and
point to `native:compile-no-fork`, protecting §REQ-maven-plugin-goal-surface-stability.

### 1.2 Test goal

`native:test` compiles the Maven test classpath into a native test image and executes that image
unless test execution is skipped. When bound to the `test` phase, it must honor Maven skip settings
and Native Build Tools test settings while using §root/FS-native-tests.

```bash
mvn -Pnative -DquickBuild native:test
```

### 1.3 Metadata and support goals

The support goals should each answer a practical user question:

`native:generateResourceConfig` and `native:generateTestResourceConfig` write Native Image resource
configuration for the main and test classpaths. `native:generateDynamicAccessMetadata` prepares
dynamic access metadata when a build report is requested. `native:add-reachability-metadata`
resolves repository metadata and adds it to the configuration directories used by native builds.
These goals expose §root/FS-resources-and-metadata.

`native:merge-agent-files` merges tracing-agent output with `native-image-configure`, and
`native:metadata-copy` copies or merges selected agent output into a configured metadata
directory. These goals expose §root/FS-tracing-agent-workflows and §common/FS-common-libraries.4.

`native:list-libraries-missing-metadata` reports dependencies not covered by the configured
reachability metadata repository. `native:write-args-file` writes the native-image arguments that
Maven would pass, so users can inspect or reuse the invocation outside Maven. These goals expose
§root/FS-resources-and-metadata and §root/FS-option-precedence.

### 1.4 Lifecycle bindings

Goals that mutate generated project resources or build native images must bind to Maven lifecycle
phases only when that behavior is safe for normal profile usage. Utility goals such as
`metadata-copy`, `list-libraries-missing-metadata`, and `write-args-file` may remain manual so
users invoke them intentionally.

### 1.5 Lifecycle profile example

A normal native profile binds `compile-no-fork` to `package`. With that setup,
`mvn -Pnative package` produces the regular Maven outputs and the native image from the same
project model.

```xml
<profile>
    <id>native</id>
    <build>
        <plugins>
            <plugin>
                <groupId>org.graalvm.buildtools</groupId>
                <artifactId>native-maven-plugin</artifactId>
                <version>${native.maven.plugin.version}</version>
                <extensions>true</extensions>
                <executions>
                    <execution>
                        <id>build-native</id>
                        <goals>
                            <goal>compile-no-fork</goal>
                        </goals>
                        <phase>package</phase>
                    </execution>
                </executions>
                <configuration>
                    <imageName>demo</imageName>
                    <mainClass>com.example.Main</mainClass>
                    <fallback>false</fallback>
                    <buildArgs>
                        <buildArg>--verbose</buildArg>
                    </buildArgs>
                </configuration>
            </plugin>
        </plugins>
    </build>
</profile>
```

## 2. Native image build behavior

Maven native image builds translate project packaging, dependency scopes, plugin configuration,
and command-line properties into a Native Image invocation that satisfies
§root/FS-native-image-builds.

### 2.1 Main class discovery

When `mainClass` is not configured directly, `native:compile-no-fork` must inspect common Maven
packaging plugins for a main class in this order: Maven Shade Plugin transformer configuration,
Maven Assembly Plugin archive manifest configuration, then Maven JAR Plugin archive manifest
configuration. Values must be evaluated through Maven expression evaluation before use.

### 2.2 Build skipping

`skipNativeBuild` must skip native image generation. `skipNativeBuildForPom` must skip native image
generation for projects packaged as `pom` when that parameter is enabled. These switches let
multi-module builds keep one profile across aggregator and leaf modules.

### 2.3 Classpath and scopes

Application native image builds must include compile, runtime, and combined compile-plus-runtime
dependencies unless users provide an explicit classpath. Exclusions remove selected artifacts from
native-image compilation without changing the Maven project dependency graph.

### 2.4 Generated resource configuration

Before building, the plugin must add generated resource configuration to the native image
arguments when resource autodetection is configured. Generation uses the shared resource contract
in §common/FS-common-libraries.2.

### 2.5 Dynamic access metadata

When configured build arguments ask Native Image to emit a build report, the build goal must run
`generateDynamicAccessMetadata` before native image compilation and make the resulting metadata
available to the build.

### 2.6 Base SBOM

When base SBOM generation is supported by the discovered Oracle GraalVM Native Image version and
the user has not disabled it, the build goal must attempt to generate a base SBOM. Failure to
generate that auxiliary SBOM must warn and fall back to Maven's regular SBOM behavior rather than
failing an otherwise valid native image build.

### 2.7 Argument files

`native:write-args-file` must write an argument file using the same argument conversion semantics
as native image compilation. It must log the generated file path and store it in the Maven project
property `graalvm.native-image.args-file`.

```bash
mvn -Pnative -DquickBuild native:write-args-file
```

### 2.8 Command surface examples

The main command forms are `mvn -Pnative package` for lifecycle builds, `mvn -Pnative
native:compile` for direct application images, `mvn -Pnative native:test` for native tests,
`mvn -Pnative native:write-args-file` for invocation inspection, and `mvn -Pnative
native:list-libraries-missing-metadata` for metadata coverage reports.

```bash
mvn -Pnative -DquickBuild -DskipTests package
mvn -Pnative native:compile
mvn -Pnative -DquickBuild native:test
mvn -Pnative native:write-args-file
mvn -Pnative native:list-libraries-missing-metadata
```

## 3. Configuration model

The plugin maps Maven XML configuration and system properties into one native image option model.
Users should be able to keep stable build settings in the POM and pass short-lived overrides with
`-D...` properties.

### 3.1 Native Image options

The plugin must support image name, main class, build args, runtime args, debug, verbose, fallback,
shared-library output, quick build, argument-file usage, classpath, classes directory, dependency
exclusions, environment variables, system properties, JVM args, configuration file directories,
metadata repository settings, required Native Image version, and agent configuration.

### 3.2 Command-line properties

Configuration values documented as Maven command-line properties must be overridable through
`-D...` properties for temporary runs. The property path must feed the same option state as XML
configuration so behavior does not diverge by configuration source.

```bash
mvn -Pnative -DquickBuild -Dverbose -DskipTests package
```

### 3.3 Parent POM merging

Configuration that Maven natively supports as mergeable, such as `<buildArgs>`, must preserve
Maven's parent/child merge behavior. Child projects must be able to append to parent build
arguments when they use Maven's `combine.children="append"` convention.

### 3.4 Toolchain and executable lookup

The plugin must locate a Native Image executable using Maven toolchains when appropriate and
environment/path fallbacks otherwise. When toolchain enforcement is enabled, failing to find a
toolchain-provided Native Image executable must fail clearly.

### 3.5 Override precedence

Maven's standard parameter binding decides precedence between configuration sources. When a
parameter is set in `<configuration>` XML, that explicit value takes precedence; the matching
command-line property from §FS-maven-plugin.3.2, such as `-DskipNativeBuild=...`, applies only
when no explicit configuration is present. The exception is a parameter intentionally modeled to
let the property win for one run, such as the agent toggle in §FS-maven-plugin.5.1 where
`-Dagent=false` disables an agent enabled in the POM. This is the Maven adaptation of
§root/FS-option-precedence.

## 4. Native tests

Maven native tests let users keep their normal test source tree and ask the plugin to build and
run those tests as a native image through Maven's test lifecycle.

### 4.1 Test classpath

`native:test` must include compile, runtime, test, compile-plus-runtime, and provided-scope
dependencies needed by the test image. It must add compiled test classes, test resources, plugin
artifacts relevant to Native Build Tools and JUnit, and inferred `junit-platform-native`
dependencies.

### 4.2 Test discovery preconditions

If `skipTests` or `skipNativeTests` is set, the goal must skip native tests. If no test classes are
present, it must skip native tests. If test identifier files are missing, `failNoTests` determines
whether the goal fails or skips.

### 4.3 Launcher and feature selection

By default, `native:test` must build the image with
`org.graalvm.junit.platform.NativeImageJUnitLauncher` and the `JUnitPlatformFeature`. If Native
Image compatibility mode is enabled, it must use the original JUnit ConsoleLauncher path described
by §root/FS-native-tests.5.

### 4.4 Test execution

After building the native test image, `native:test` must run it unless `skipTestExecution` is set.
Runtime arguments configured for the test goal must be passed to the native test executable.

### 4.5 Native test example

Native tests are invoked with `native:test` directly or by binding that goal to the Maven `test`
phase. `skipNativeTests` skips the native test image while preserving the normal Maven test
controls.

```xml
<execution>
    <id>test-native</id>
    <goals>
        <goal>test</goal>
    </goals>
    <phase>test</phase>
</execution>
```

```bash
mvn -Pnative -DquickBuild test
```

## 5. Agent and metadata copy workflows

The Maven plugin exposes the Native Image tracing agent and post-processing workflows through
Maven configuration and goals. Users can collect metadata from JVM test or application runs, then
merge or copy it into a metadata directory.

### 5.1 Agent enablement

The agent is disabled by default and can be enabled through plugin configuration or the
`-Dagent=true` command-line property. If enabled in the POM, `-Dagent=false` must disable it for a
single invocation.

### 5.2 Agent modes

The Maven configuration must support standard, direct, conditional, and disabled agent modes using
the shared agent mode contract in §common/FS-common-libraries.3. Conditional mode must support
user-code and extra filters, and direct mode must let users provide the raw agent command line when
they need full control.

### 5.3 Agent output

Agent output from tests must be stored under `target/native/agent-output/test`; agent output from
application runs must be stored under `target/native/agent-output/main` unless direct mode changes
the destination.

### 5.4 Merge and copy

`native:merge-agent-files` must merge generated agent output through `native-image-configure`.
`native:metadata-copy` must copy or merge selected agent stages into the configured output
directory and honor disabled main/test stages.

### 5.5 Agent example

Agent collection is enabled through `<agent>` configuration or `-Dagent=true`; post-processing is
invoked with `native:metadata-copy`. The default output location for test-stage output is
`target/native/agent-output/test` unless direct mode changes it.

```xml
<configuration>
    <agent>
        <enabled>true</enabled>
        <defaultMode>Standard</defaultMode>
        <metadataCopy>
            <merge>true</merge>
        </metadataCopy>
    </agent>
</configuration>
```

```bash
mvn -Pnative -Dagent=true test
mvn -Pnative native:metadata-copy
```

## 6. Resource and reachability metadata workflows

Maven projects use shared metadata and resource behavior through Maven goals. Generated Native
Image configuration should stay in Maven's `target` tree and feed later native builds
automatically.

### 6.1 Resource configuration goals

The plugin must generate resource configuration for main and test classpaths. Generation must
respect existing Native Image resource configuration and the shared classpath scanning behavior in
§common/FS-common-libraries.2.

### 6.2 Reachability metadata

`native:add-reachability-metadata` resolves metadata for project dependencies from the configured
metadata repository, exclusions, and module-to-config-version overrides. It must add the resulting
configuration directory to the native image build without requiring users to manually copy
repository contents.

### 6.3 Missing metadata reports

`native:list-libraries-missing-metadata` reports project dependencies that do not appear to have
reachability metadata support and may create GitHub issues when issue creation is configured. Its
behavior must remain aligned with §root/FS-resources-and-metadata.

### 6.4 Schema validation

Before using repository metadata, native image goals must validate metadata against the schema
expected by the discovered Native Image major version when schema validation is applicable.

### 6.5 Resource and metadata entry points

Resource generation is exposed through `native:generateResourceConfig` and
`native:generateTestResourceConfig`. Reachability metadata is configured with
`<metadataRepository>` and consumed by native build goals through the selected metadata
directories.

```xml
<execution>
    <id>build-native</id>
    <goals>
        <goal>generateResourceConfig</goal>
        <goal>compile-no-fork</goal>
    </goals>
    <phase>package</phase>
</execution>
```

```xml
<metadataRepository>
    <enabled>true</enabled>
    <version>1.0-M1</version>
</metadataRepository>
```

## 7. Verification surface

Unit tests cover mojos, configuration objects, command-line assembly, and shared utility
integration that can be tested locally. Functional tests exercise Maven sample projects through a
seeded local Maven repository, with scenario ownership defined by
§maven/E2E-maven-plugin-functional-tests and fixture ownership defined by
§root/AR-build-infrastructure.4.1.
