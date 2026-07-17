# FS-native-builds: Maven goals build Native Image outputs from project state

Maven native image builds translate project packaging, dependency scopes, plugin configuration,
and command-line properties into a Native Image invocation that satisfies
[§root/FS-native-builds](../../../docs/spec/functional/native-image-builds.md#fs-native-builds-both-plugins-build-native-images-from-build-tool-project-state).

## 1. Main class discovery

When `mainClass` is not configured directly, `native:compile-no-fork` must inspect common Maven
packaging plugins for a main class in this order: Maven Shade Plugin transformer configuration,
Maven Assembly Plugin archive manifest configuration, then Maven JAR Plugin archive manifest
configuration. Values must be evaluated through Maven expression evaluation before use.

## 2. Build skipping

`skipNativeBuild` must skip native image generation. `skipNativeBuildForPom` must skip native image
generation for projects packaged as `pom` when that parameter is enabled. These switches let
multi-module builds keep one profile across aggregator and leaf modules.

## 3. Classpath and scopes

Application native image builds must include compile, runtime, and combined compile-plus-runtime
dependencies unless users provide an explicit classpath. Exclusions remove selected artifacts from
native-image compilation without changing the Maven project dependency graph.

## 4. Generated resource configuration

Before building, the plugin must add generated resource configuration to the native image
arguments when resource autodetection is configured. Generation uses the shared resource contract
in [§common/FS-common-libraries.2](../../../common/docs/functional-spec.md#2-resource-configuration).

## 5. Dynamic access metadata

When configured build arguments ask Native Image to emit a build report, the build goal must run
`generateDynamicAccessMetadata` before native image compilation and make the resulting metadata
available to the build.

## 6. Base SBOM

When base SBOM generation is supported by the discovered Oracle GraalVM Native Image version and
the user has not disabled it, the build goal must attempt to generate a base SBOM. Failure to
generate that auxiliary SBOM must warn and fall back to Maven's regular SBOM behavior rather than
failing an otherwise valid native image build.

## 7. Argument files

`native:write-args-file` must write an argument file using the same argument conversion semantics
as native image compilation. It must log the generated file path and store it in the Maven project
property `graalvm.native-image.args-file`.

```bash
mvn -Pnative -DquickBuild native:write-args-file
```

## 8. Command surface examples

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

## 9. Console colors

The native-image invocation must follow Maven's detected console color state. It must use
`--color=always` or `--color=never` on JDK 21 and later, and `-H:+BuildOutputColorful` or
`-H:-BuildOutputColorful` on older versions. Explicit user build arguments come later and may
override this detected default, adapting [§root/FS-native-builds.2](../../../docs/spec/functional/native-image-builds.md#2-command-line-construction).
