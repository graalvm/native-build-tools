# FS-maven-native-tests: Maven goals compile and run native JUnit tests

Maven native tests let users keep their normal test source tree and ask the plugin to build and
run those tests as a native image through Maven's test lifecycle.

## 1. Test classpath

`native:test` must include compile, runtime, test, compile-plus-runtime, and provided-scope
dependencies needed by the test image. It must add compiled test classes, test resources, plugin
artifacts relevant to Native Build Tools and JUnit, and inferred `junit-platform-native`
dependencies.

## 2. Test discovery preconditions

If `skipTests` or `skipNativeTests` is set, the goal must skip native tests. If no test classes are
present, it must skip native tests. If test identifier files are missing, `failNoTests` determines
whether the goal fails or skips.

## 3. Launcher and feature selection

By default, `native:test` must build the image with
`org.graalvm.junit.platform.NativeImageJUnitLauncher` and the `JUnitPlatformFeature`. If Native
Image compatibility mode is enabled, it must use the original JUnit ConsoleLauncher path described
by §root/FS-native-tests.5.

## 4. Test execution

After building the native test image, `native:test` must run it unless `skipTestExecution` is set.
Runtime arguments configured for the test goal must be passed to the native test executable.

## 5. Native test example

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
