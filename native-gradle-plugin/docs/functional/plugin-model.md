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
objects through typed, lazy references. Layers may consume earlier layers through the same API,
allowing a build to express a multi-level Native Image layer stack. `usesLayer('<name>')` is the
order-independent Groovy DSL form and resolves the named layer lazily, so consumers may be
declared before their producers:

```groovy
graalvmNative {
    layers {
        base {
            contents {
                modules.add('java.base')
                fromConfiguration(configurations.runtimeClasspath)
            }
        }
        applicationFramework {
            usesLayer('base')
            contents {
                packages('com.example.framework')
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
The singular `layer` property replaces its previous assignment, while repeated `useLayer(...)`
calls add distinct layers. Duplicate layer selections fail before Native Image is invoked.
The same duplicate and name validation applies when a named layer consumes another named layer.
Every selection form (`layer =`, `useLayer(NativeImageLayer)`,
`useLayer(Provider<NativeImageLayer>)`, and `usesLayer(String)`) contributes its logical layer
name to the same selection set. A consuming binary may select each logical name only once. The
plugin resolves provider-backed names at a cache-safe validation boundary after configuration and
fails that validation before any selected producer task executes; it retains lazy name resolution
and configuration-cache compatibility.

`fromConfiguration(...)` and `all` include resolved external and project dependencies. Dependency
selectors also accept Gradle providers, including version-catalog accessors.

The previous binary-scoped `createLayer`, string-based `useLayer`, and
`externalDependenciesOf(...)` surfaces remain as deprecated compatibility adapters with their
original naming and external-module-only semantics. Only legacy layer declarations retain the
`lib` binary-name rule. [§root/REQ-backwards-compatibility.1](../../../docs/spec/requirements.md#1-deprecation-over-removal).

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
