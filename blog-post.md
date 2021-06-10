# *Gradle* and *Maven* plugins for *Native Image* with initial JUnit testing support
We are pleased to announce the release of our new official *Gradle* and *Maven* plugins that will largely simplify supporting *GraalVM Native Image* in your existing projects. Best thing about them is out of the box initial support for Native JUnit testing with the intention for it to become a cornerstone of the community.

All projects mentioned here are released under [*Universal Permissive License*](https://www.oracle.com/downloads/licenses/upl-license1.html).
___
Complete examples are available [here](https://github.com/graalvm/native-build-tools/blob/master/examples/README.md) with the invoking script available [here](https://github.com/graalvm/native-build-tools/blob/master/common/scripts/testAll.sh).

## Gradle Plugin
Adding `native-gradle-plugin` to your existing *Gradle* project is as simple as adding:
```groovy
plugins {
  id 'org.graalvm.buildtools.native' version "1.0.0-M1" // or newer version (when available)
}
```
to the `plugins` section of your `build.gradle`, after which you can configure image building using `nativeBuild` configuration block, like:
```groovy
nativeBuild {
  imageName = "my-app"
  mainClass = "org.test.Main"
  verbose = true
  fallback = false
}
```
Plugin then adds `nativeBuild` and `nativeRun` tasks which are doing exactly what one may expect from them. If the reflection configuration is necessary for Native Image building, this plugin also provides simple option which activates `native-image-agent` without any additional user setup. More information (and *Kotlin* configuration syntax) is available in the [documentation](https://github.com/graalvm/native-build-tools/blob/master/native-gradle-plugin/README.md).

> At the moment we support subset of configuration syntax and aliased task names from the most popular unofficial *GraalVM* *Gradle* plugins (`io.micronaut.application` and `com.palantir.graal`) in order to ease the community transition. Note that this behavior might eventually be deprecated.

### Testing
User can start Native Image testing by invoking `nativeTest` task (with possibility to add additional `native-image` configuration using `nativeTest` configuration block).
```shell
$ ./gradlew nativeTest

> Task :nativeTest
JUnit Platform on Native Image - report
----------------------------------------
org.graalvm.buildtools.example.App2Test > appHasAGreeting() SUCCESSFUL
org.graalvm.buildtools.example.AppTest > appHasAGreeting() SUCCESSFUL

Test run finished after 3 ms
[         3 containers found      ]
[         0 containers skipped    ]
[         3 containers started    ]
[         0 containers aborted    ]
[         3 containers successful ]
[         0 containers failed     ]
[         2 tests found           ]
[         0 tests skipped         ]
[         2 tests started         ]
[         0 tests aborted         ]
[         2 tests successful      ]
[         0 tests failed          ]


BUILD SUCCESSFUL in 771ms
5 actionable tasks: 1 executed, 4 up-to-date
```
> Note that the native testing depends on running standard `test` task in the *JVM* mode beforehand. More information is avaliable [here](#Testing-support).

## Maven Plugin
We are releasing our new plugin under new maven coordinates - `org.graalvm.buildtools:native-image-plugin`. Reason for this change is our intention to move faster with plugin development. Users of existing `native-image-maven-plugin` only need to change plugin's `groupId`, `artifactId` and `version` in their `pom.xml`, as the new plugin is backwards compatible with the old one. Initial plugin release is using `1.0.0-M1` version.

Adding our new plugin to the existing *Maven* project requires adding following to `pom.xml`:
```xml
<profiles>
  <profile>
    <id>native</id>
    <build>
      <plugins>
        <plugin>
          <groupId>org.graalvm.buildtools</groupId>
          <artifactId>native-maven-plugin</artifactId>
          <version>1.0.0-M1</version> <!-- or newer version (when available) -->
          <executions>
            <execution>
              <id>test-native</id>
              <goals>
                <goal>test</goal>
              </goals>
              <phase>test</phase>
            </execution>
            <execution>
              <id>build-native</id>
              <goals>
                <goal>build</goal>
              </goals>
              <phase>package</phase>
            </execution>
          </executions>
          <configuration>
            <imageName>my-app</imageName>
            <mainClass>org.test.Main</mainClass>
            <buildArgs>
            --no-fallback
            --verbose
            </buildArgs>
          </configuration>
        </plugin>
      </plugins>
    </build>
  </profile>
</profiles>
```
After that, user can build native images by running:
```shell
mvn -Pnative -DskipTests package
```
### Testing
User can start *Native Image* testing by running:
```shell
mvn -Pnative test
```
> Note that the native testing depends on running standard `test` task in the *JVM* mode beforehand.

> Additionally, Maven at the moment requires extra step in order to use the recommended test listener. More information is avaliable [here](#Testing-support).
___

Documentation and more configuration options are available [here](https://github.com/graalvm/native-build-tools/blob/master/native-maven-plugin/README.md).

## Testing support
Our testing support is provided using *JUnit Platform* support feature (`org.graalvm.buildtools:junit-platform-native`). For the end-user this means that configuring and running *JUnit Platform* tests ahead-of-time is now handled by aforementioned build plugins.

Both *Maven* and *Gradle* are (at the moment) using custom *JUnit* Test Listener from `junit-platform-native` which - during invocation of standard `test` task - logs tests that are discovered in order to register them for Native compilation. *Maven* currently needs an extra step in order to enable said listener, e.g. user needs to add a dependency to `junit-platform-native` in their `pom.xml`. This step will not be necessary [when *JUnit* 5.8 lands](https://github.com/junit-team/junit5/issues/2619).
> In the future we intend to work on removing the need for `junit-platform-native` by upstreaming much of the logic into JUnit project itself.

For more information user should consult plugin documentation for their preferred build tool.

Our testing support was developed in collaboration with [*JUnit*](https://junit.org/junit5/) and [*Spring*](https://spring.io/) teams.

## Recapitulation
Many thanks to [Sébastien Deleuze](https://twitter.com/sdeleuze), [Sam Brannen](https://twitter.com/sam_brannen) and [Graeme Rocher](https://twitter.com/graemerocher) for their contributions and advices. Moving forward, our new plugins are already landing in *Spring Native 0.10.0*, and hopefully soon many more projects will follow.

We are looking forwards to hearing about your experiences and/or potential issues. Contributions are also very welcome.

Project repository is available at [github.com/graalvm/native-build-tools](https://github.com/graalvm/native-build-tools/).

## Future goals
Our next big focus is on creating standardized configuration for big libraries which would automatically be added when necessary, so that development for *Native Image* feels more frictionless. We intend to follow up with patches and PRs for original libraries, working closely with the community in order to bring first party support to most of the ecosystem.
