# FS-goal-surface: Maven goals expose Native Image workflows

The Maven plugin exposes Native Image workflows as Maven goals. Goal names must work for direct
command-line use and for profile-bound builds that run through normal Maven lifecycle phases.

## 1. Build goals

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
point to `native:compile-no-fork`, protecting [§REQ-goal-surface](../requirements.md#req-goal-surface-maven-goal-and-parameter-names-remain-stable-across-compatible-releases).

## 2. Test goal

`native:test` compiles the Maven test classpath into a native test image and executes that image
unless test execution is skipped. When bound to the `test` phase, it must honor Maven skip settings
and Native Build Tools test settings while using [§root/FS-native-tests](../../../docs/spec/functional/native-tests.md#fs-native-tests-both-plugins-compile-and-execute-junit-tests-as-a-native-image).

```bash
mvn -Pnative -DquickBuild native:test
```

## 3. Metadata and support goals

The support goals should each answer a practical user question:

`native:generateResourceConfig` and `native:generateTestResourceConfig` write Native Image resource
configuration for the main and test classpaths. `native:generateDynamicAccessMetadata` prepares
dynamic access metadata when a build report is requested. `native:add-reachability-metadata`
resolves repository metadata and adds it to the configuration directories used by native builds.
These goals expose [§root/FS-resources-metadata](../../../docs/spec/functional/resources-and-metadata.md#fs-resources-metadata-both-plugins-generate-resource-config-and-consume-reachability-metadata).

`native:merge-agent-files` merges tracing-agent output with `native-image-configure`, and
`native:metadata-copy` copies or merges selected agent output into a configured metadata
directory. These goals expose [§root/FS-tracing-agent](../../../docs/spec/functional/tracing-agent.md#fs-tracing-agent-both-plugins-attach-the-native-image-tracing-agent-and-post-process-its-output) and [§common/FS-common-libraries.4](../../../common/docs/functional-spec.md#4-agent-metadata-post-processing).

`native:list-libraries-missing-metadata` reports dependencies not covered by the configured
reachability metadata repository. `native:write-args-file` writes the native-image arguments that
Maven would pass, so users can inspect or reuse the invocation outside Maven. These goals expose
[§root/FS-resources-metadata](../../../docs/spec/functional/resources-and-metadata.md#fs-resources-metadata-both-plugins-generate-resource-config-and-consume-reachability-metadata) and [§root/FS-option-precedence](../../../docs/spec/functional/option-precedence.md#fs-option-precedence-command-line-input-and-durable-configuration-produce-one-option-state).

## 4. Lifecycle bindings

Goals that mutate generated project resources or build native images must bind to Maven lifecycle
phases only when that behavior is safe for normal profile usage. Utility goals such as
`metadata-copy`, `list-libraries-missing-metadata`, and `write-args-file` may remain manual so
users invoke them intentionally.

## 5. Lifecycle profile example

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
