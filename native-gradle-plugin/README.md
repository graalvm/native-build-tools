# Native Image Gradle Plugin
Gradle plugin for GraalVM Native Image building
![](https://github.com/graalvm/native-build-tools/actions/workflows/test-native-gradle-plugin.yml/badge.svg)

End-user documentation about the plugins can be found [here](https://graalvm.github.io/native-build-tools/).

## Maintainer specification

This subproject has its own grund specification:

* [Purpose](docs/grund.md)
* [Goals](docs/goals.md)
* [Requirements](docs/requirements.md)
* [Functional specification](docs/functional/README.md)
* [Architecture](docs/architecture.md)
* [End-to-end tests](docs/e2e.md)

## Building

This plugin can be built with this command (from the root directory):

```bash
./gradlew :native-gradle-plugin:publishAllPublicationsToCommonRepository --no-parallel
```

The command will publish a snapshot to `build/common-repo`. For more details, see the [Developer documentation](../DEVELOPING.md).

In order to run testing part of this plugin you need to get (or build) corresponding `junit-platform-native` artifact.

*You can also take a look at CI workflow [here](../.github/workflows/test-native-gradle-plugin.yml).*
