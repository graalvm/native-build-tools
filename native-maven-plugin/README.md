# Native Image Maven Plugin
Maven plugin for GraalVM Native Image building
![](https://github.com/graalvm/native-build-tools/actions/workflows/test-native-maven-plugin.yml/badge.svg)

End-user documentation about the plugins can be found [here](https://graalvm.github.io/native-build-tools/).

## Building

This plugin can be built with this command (from the root directory):

```bash
./gradlew :native-maven-plugin:publishAllPublicationsToCommonRepository --no-parallel
```

The command will publish a snapshot to `build/common-repo`.
For more details, see the [Developer documentation](../DEVELOPING.md).

In order to run testing part of this plugin you need to get (or build) corresponding `junit-platform-native` artifact.

*You can also take a look at CI workflow [here](../.github/workflows/test-native-maven-plugin.yml).*

## Compatibility Mode fallback (native tests)

When the Native Image Compatibility Mode flag is enabled (`-H:+CompatibilityMode`), the Maven native test goal is automatically short-circuited: native-image based JUnit tests are skipped and tests run on the JVM via Surefire/Failsafe instead.

- What happens
  - If `-H:+CompatibilityMode` is detected, the `native:test` goal is skipped early and JVM tests execute (Surefire/Failsafe).

- Detection sources
  - Effective native-image build arguments configured for the plugin/goal (e.g. `-DbuildArgs=...`).
  - Environment variable `NATIVE_IMAGE_OPTIONS`:
    - From the process environment.
    - From Surefire/Failsafe environment passed via plugin configuration (`<environmentVariables>NATIVE_IMAGE_OPTIONS</environmentVariables>`).

- Behavior details
  - The native test goal returns early right after environment setup when Compatibility Mode is enabled.
  - The plugin logs once at INFO level with the exact text:
    - "Compatibility Mode detected (-H:+CompatibilityMode); skipping native-image test goal, JVM tests will run instead."
  - JVM tests still run normally; no extra configuration needed.
  - Default behavior is unchanged when the flag is absent; native-image tests run as before.

- How to trigger and verify
  - CLI build args example:
    ```bash
    mvn -Pnative -DbuildArgs=-H:+CompatibilityMode test
    ```
  - Using `NATIVE_IMAGE_OPTIONS`:
    - Shell export:
      ```bash
      NATIVE_IMAGE_OPTIONS="-H:+CompatibilityMode" mvn -Pnative test
      ```
    - Or configure `NATIVE_IMAGE_OPTIONS` in the Surefire/Failsafe plugin environment within the POM.
  - Expected output:
    - The INFO log appears once with the exact text above.
    - No native-image build/run steps execute for the test goal.
    - JVM test results are reported by Surefire/Failsafe.

- Resetting back to native tests
  - Remove `-H:+CompatibilityMode` from build args and ensure `NATIVE_IMAGE_OPTIONS` does not contain it.

- References (implementation/tests)
  - Short-circuit and logging implemented in [native-maven-plugin/src/main/java/org/graalvm/buildtools/maven/NativeTestMojo.java](native-maven-plugin/src/main/java/org/graalvm/buildtools/maven/NativeTestMojo.java)
    - Skip point: right after [NativeTestMojo.configureEnvironment()](native-maven-plugin/src/main/java/org/graalvm/buildtools/maven/NativeTestMojo.java:220) within [NativeTestMojo.execute()](native-maven-plugin/src/main/java/org/graalvm/buildtools/maven/NativeTestMojo.java:165); detection via [NativeTestMojo.isCompatibilityModeEnabled()](native-maven-plugin/src/main/java/org/graalvm/buildtools/maven/NativeTestMojo.java:265).
  - Functional verification: [native-maven-plugin/src/functionalTest/groovy/org/graalvm/buildtools/maven/CompatibilityModeNativeTestsFunctionalTest.groovy](native-maven-plugin/src/functionalTest/groovy/org/graalvm/buildtools/maven/CompatibilityModeNativeTestsFunctionalTest.groovy)
