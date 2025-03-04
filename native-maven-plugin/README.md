# Native Image Maven Plugin
Maven plugin for GraalVM Native Image building
![](https://github.com/graalvm/native-build-tools/actions/workflows/test-native-maven-plugin.yml/badge.svg)

End-user documentation about the plugins can be found [here](https://graalvm.github.io/native-build-tools/).

## Building

This plugin can be built with this command (from the root directory):

```bash
./gradlew :native-maven-plugin:publishAllPublicationsToCommonRepository --no-parallel
```

A snapshot will be published to `build/common-repo`. For more details, see the [Developer documentation](../DEVELOPING.md).

*You can also take a look at CI workflow [here](../.github/workflows/test-native-maven-plugin.yml).*
