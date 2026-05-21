# FS-004-native-test-execution: The plugins support compiling and running tests as native images

Native Build Tools must let supported Gradle and Maven projects compile test code into a native
image and execute that native test binary. This behavior depends on the shared
`junit-platform-native` module and is exposed through both product plugins.

## Required behavior

- Discover and register JUnit Platform tests that must be available to the native image runtime.
- Provide native-image feature and launcher support for JUnit Platform execution.
- Support Jupiter, Vintage, and platform configuration providers needed by the native test binary.
- Allow Gradle and Maven plugin workflows to skip, execute, or fail native test execution according
  to their build-tool-specific configuration.

## Verification surface

The `common/junit-platform-native` module contains JUnit-focused tests. The Gradle and Maven
plugin functional test suites both include JUnit and application-with-tests scenarios that exercise
the shared support through real build-tool invocations.
