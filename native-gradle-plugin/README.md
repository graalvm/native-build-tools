# Native Image Gradle Plugin
Gradle plugin for GraalVM Native Image building
![](https://github.com/graalvm/native-build-tools/actions/workflows/test-native-gradle-plugin.yml/badge.svg)

End-user documentation about the plugins can be found [here](https://graalvm.github.io/native-build-tools/).

## Building

This plugin can be built with this command (from the root directory):

```bash
./gradlew :native-gradle-plugin:publishAllPublicationsToCommonRepository --no-parallel
```

The command will publish a snapshot to `build/common-repo`. For more details, see the [Developer documentation](../DEVELOPING.md).

In order to run testing part of this plugin you need to get (or build) corresponding `junit-platform-native` artifact.

*You can also take a look at CI workflow [here](../.github/workflows/test-native-gradle-plugin.yml).*


## Compatibility Mode fallback (native tests)

When the Native Image Compatibility Mode flag is enabled (`-H:+CompatibilityMode`), the Gradle plugin automatically disables native-image JUnit execution and falls back to running tests on the JVM.

- When fallback happens
  - If `-H:+CompatibilityMode` is detected in the effective native-image options for the native test image, native test image build/run is skipped and JVM tests run instead.

- How detection works (sources)
  - Task/extension build arguments (effective native-image `buildArgs`) for the test binary.
  - Environment variable `NATIVE_IMAGE_OPTIONS`:
    - From the environment of the Gradle process.
    - Or if configured on the test binary via `environmentVariables["NATIVE_IMAGE_OPTIONS"]`.

- Behavior when Compatibility Mode is enabled
  - The native JUnit wiring is not applied by default:
    - The native JUnit main class `org.graalvm.junit.platform.NativeImageJUnitLauncher` is not set.
    - The native feature flag `--features=org.graalvm.junit.platform.JUnitPlatformFeature` is not added by default.
  - The native test image tasks are skipped via `onlyIf` predicates:
    - Build: `nativeTestCompile`
    - Run: `nativeTest` (or `native<BinaryName>` for additional test binaries)
  - A once-per-build INFO log is emitted with the exact text:
    - Compatibility Mode detected (-H:+CompatibilityMode); skipping native-image test build/run, JVM tests will run instead.
  - JVM tests still run normally; no extra configuration is required.

- How to trigger and verify
  - Shell example:
    ```bash
    NATIVE_IMAGE_OPTIONS=-H:+CompatibilityMode ./gradlew test
    ```
  - Gradle DSL example (set on native test build args):
    ```groovy
    graalvmNative {
      binaries {
        test {
          buildArgs.add("-H:+CompatibilityMode")
        }
      }
    }
    ```
  - Expected output:
    - Logs contain: `Compatibility Mode detected (-H:+CompatibilityMode); skipping native-image test build/run, JVM tests will run instead.`
    - Tasks show SKIPPED for native test image build/run, e.g.:
      - `:nativeTestCompile SKIPPED`
      - `:nativeTest SKIPPED`
    - JVM test task executes as usual, e.g. `:test`.

- Resetting back to native tests
  - Remove `-H:+CompatibilityMode` from the test binary `buildArgs` and ensure `NATIVE_IMAGE_OPTIONS` does not contain it.

- Notes
  - The fallback is automatic; no additional flags are needed to run JVM tests.
  - When the flag is absent, behavior is unchanged and the plugin uses the native JUnit path where applicable.

See the end-user documentation for native testing for more background and guidance.
