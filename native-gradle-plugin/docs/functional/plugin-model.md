# FS-plugin-model: Gradle plugin activation and DSL model

The plugin should behave like a normal Gradle Java plugin extension: users apply one plugin ID and
configure native behavior beside their existing `application`, `java-library`, source set, and test
configuration.

## 1. Plugin identity

The plugin ID is `org.graalvm.buildtools.native`. Applying it alone must not force a Java model into
the project. Java-dependent native tasks are registered when a Java plugin is present, so projects
can apply plugins in either order without relying on eager configuration.

## 2. Extension surface

The plugin must expose a `graalvmNative` extension. This is the durable Gradle DSL for native
binaries, Native Image options, generated resources, reachability metadata, native tests, and
tracing-agent workflows from [§root/FS-plugin-common](../../../docs/spec/functional/plugin-common.md#fs-plugin-common-gradle-and-maven-expose-aligned-native-image-plugin-behavior).

For a typical application, users configure the same binary that `nativeCompile` builds:

```groovy
graalvmNative {
    binaries {
        main {
            imageName = 'demo'
            buildArgs.add('--verbose')
            quickBuild = true
        }
    }
}
```

The extension feeds the option objects consumed by compile, run, resource-generation, metadata,
and native-test tasks. Users should not repeat `imageName`, `mainClass`, `buildArgs`, metadata
directories, or resource settings separately on each task.

Named Native Image layers are first-class entries in a `layers` container beside `binaries`.
Each layer owns its contents and deterministic `.nil` output. Binaries consume one or more layer
objects through typed, lazy references. `usesLayer('<name>')` is the order-independent Groovy DSL
form and resolves the named layer lazily, so consumers may be declared before their producers:

```groovy
graalvmNative {
    layers {
        base {
            contents {
                modules.add('java.base')
                fromConfiguration(configurations.runtimeClasspath)
            }
        }
    }
    binaries {
        main {
            usesLayer('base')
        }
    }
}
```

Inside a binary closure, the deprecated binary-scoped `layers` property shadows the extension
container. Groovy builds may use `usesLayer('<name>')` or the qualified
`graalvmNative.layers.<name>` path.
The singular `layer` property replaces its previous assignment, including that layer's compatibility
classpath contribution, while repeated `useLayer(...)` calls add distinct layers. Duplicate layer
selections fail before Native Image is invoked.
Every selection form (`layer =`, `useLayer(NativeImageLayer)`,
`useLayer(Provider<NativeImageLayer>)`, and `usesLayer(String)`) contributes its logical layer
name to the same selection set. A consuming binary may select each logical name only once. The
plugin resolves provider-backed names lazily and fails duplicate-name validation before any
selected producer task executes. This layer model must not introduce avoidable project or task
container serialization; general configuration-cache compatibility remains governed by the
plugin's existing configuration-cache baseline.

`fromConfiguration(...)` and `all` include resolved external and project dependencies. Dependency
selectors also accept Gradle providers, including version-catalog accessors.

The previous binary-scoped `createLayer`, string-based `useLayer`, and
`externalDependenciesOf(...)` surfaces remain as deprecated compatibility adapters with their
original naming and external-module-only semantics. Only legacy layer declarations retain the
`lib` binary-name rule. [§root/REQ-backwards-compatibility.1](../../../docs/spec/requirements.md#1-deprecation-over-removal).

### Layer support matrix

| Selector or consumer | Gradle DSL support | Executable evidence |
| --- | --- | --- |
| Modules, packages, explicit paths, `all` | `contents.modules`, `contents.packages`, `contents.from`, `contents.all` | `LayeredApplicationFunctionalTest` |
| Main executable | `binaries.main.usesLayer` | `LayeredApplicationFunctionalTest` |
| Native test | `binaries.test.usesLayer` | `LayeredApplicationFunctionalTest` |
| Shared library | `binaries.main.sharedLibrary` with `usesLayer` | `LayeredApplicationFunctionalTest` |
| Application distribution | Main layered binary is added to `installDist`, `distZip`, and `distTar` with staged runtime files and a native launcher | Installed and archived distributions run without producer build directories |
| Cross-project publication | Not first-class; manual `getLayerFiles()` or file wiring only | No consumable publication variant is promised |

Layer shared libraries remain external runtime dependencies. On Linux set `LD_LIBRARY_PATH`; on
macOS set `DYLD_LIBRARY_PATH`; and on Windows add the layer directory to `PATH` before running a
layered executable. `nativeRun` supplies this for its selected layers. When the `application`
plugin is present, the main distribution includes the native executable under `lib/native`, each
selected layer's runtime files under `lib/native-layers/<layer>`, and a `<imageName>-native`
launcher under `bin` that configures the loader before execution. Other packaging plugins can
consume the cacheable `nativeLayerRuntimeFiles` task output.
[§root/FS-native-builds.6](../../../docs/spec/functional/native-image-builds.md#6-layered-images).

## 3. Default binaries

For Java application projects, the plugin must create a `main` binary whose `mainClass` convention
comes from Gradle's `application` extension when available. `nativeCompile` builds that image and
`nativeRun` executes it. For Java library projects, the main binary defaults to shared-library
output.

```groovy
plugins {
    id 'application'
    id 'org.graalvm.buildtools.native'
}

application {
    mainClass.set('com.example.Main')
}
```

The plugin must also create a `test` binary connected to the default `test` task and `test` source
set so `nativeTest` can build and run native JUnit tests without a separate binary declaration.
Test and shared-library binaries use the same layer-consumption model as executable binaries;
layer selection is explicit on each binary and is not inherited from `binaries.main`.

## 4. Custom binaries

Users may add entries to the `binaries` container for extra source sets or entry points. Each entry
must create matching compile and run tasks with predictable task names derived from the binary
name. Custom binaries apply [§root/FS-native-builds](../../../docs/spec/functional/native-image-builds.md#fs-native-builds-both-plugins-build-native-images-from-build-tool-project-state) without forcing users outside the
plugin's option model. Custom application binaries default to the main runtime classpath unless a
specialized registration, such as a native test binary, provides a source-set-specific classpath.
Named layers create dedicated tasks outside the binary container.

## 5. Activation examples

The minimal application flow is `./gradlew nativeCompile` followed by `./gradlew nativeRun`.
Test builds use `./gradlew nativeTestCompile` to inspect the image or `./gradlew nativeTest` to
compile and execute it through the native test launcher.

```bash
./gradlew nativeCompile
./gradlew nativeRun
./gradlew nativeTestCompile
./gradlew nativeTest
```
