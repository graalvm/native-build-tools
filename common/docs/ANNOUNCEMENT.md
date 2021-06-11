# *Gradle* and *Maven* plugins for *Native Image* with initial *JUnit* testing support

We are pleased to announce the release of the official *GraalVM Native Image* *Gradle* and *Maven* plugins. The new plugins aim to make *GraalVM Native Image* support in *JVM* projects a breeze. The key feature of the plugins is the **out-of-the-box** support for *native JUnit* testing.

The native *JUnit* support will allow libraries in the *JVM* ecosystem to run their full test suites via *GraalVM Native Image*. With integrated testing, library configuration should be always up to date and ready for the end user. We believe that this is the biggest step towards making the native-ready ecosystem.

All projects mentioned here are released under the [*Universal Permissive License*](https://www.oracle.com/downloads/licenses/upl-license1.html).
___
Complete examples are available [here](https://github.com/graalvm/native-build-tools/blob/master/examples/README.md) with the script demonstrating the usage available [here](https://github.com/graalvm/native-build-tools/blob/master/common/scripts/testAll.sh).

## Gradle Plugin
Adding `native-gradle-plugin` to an existing *Gradle* project is as simple as adding:
```groovy
plugins {
  id 'org.graalvm.buildtools.native' version "0.9.0" // or a newer version
}
```
to the `plugins` section of of the `build.gradle` as well as adding following to the `settings.gradle`:
```groovy
pluginManagement {
	repositories {
		mavenCentral()
		gradlePluginPortal()
	}
}
```
*(this step will be redundant once this plugin is published to the Gradle Plugin Portal).*

After that we can configure image building using a `nativeBuild` configuration block, like:
```groovy
nativeBuild {
  imageName = "my-app"
  mainClass = "org.test.Main"
  verbose = true
  fallback = false
}
```
The plugin then adds `nativeBuild` and `nativeRun` tasks that build and run the main class (as one might expect :). If the reflection configuration is necessary for the Native Image building, this plugin also provides a simple option that activates the [`native-image-agent`](https://www.graalvm.org/reference-manual/native-image/BuildConfiguration/#assisted-configuration-of-native-image-builds) without any additional user setup. More information (and *Kotlin* configuration syntax) is available in the [documentation](https://github.com/graalvm/native-build-tools/blob/master/native-gradle-plugin/README.md).

> To help ease the community transition, at the moment we support a subset of the configuration syntax and aliased task names from the most popular unofficial *GraalVM* *Gradle* plugins (`io.micronaut.application` and `com.palantir.graal`). Note that this behavior might eventually be deprecated.

### Testing

User can start Native Image testing by invoking the `nativeTest` task (with the ability to add additional `native-image` configuration using a `nativeTest` configuration block).
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
> Note that the native testing depends on running the standard `test` task in the *JVM* mode beforehand.
>
> More information is avaliable [here](#Testing-Support).

## Maven Plugin

We are releasing our existing plugin under the new maven coordinates - `org.graalvm.buildtools:native-image-plugin`. This change was motivated by our intention to move faster with the plugin development by decoupling it from the *GraalVM* release cycle. Users of our existing `native-image-maven-plugin` only need to change the plugin's `groupId`, `artifactId` and `version` in their `pom.xml`, as the new plugin is backwards compatible with the old one. Versioning of the new plugin will start at `0.9.0`.

Adding our new plugin to the existing *Maven* project requires adding the following to `pom.xml`:
```xml
<profiles>
  <profile>
    <id>native</id>
    <build>
      <plugins>
        <plugin>
          <groupId>org.graalvm.buildtools</groupId>
          <artifactId>native-maven-plugin</artifactId>
          <version>0.9.0</version> <!-- or newer version -->
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
After that, the user can build native images by running:
```shell
mvn -Pnative -DskipTests package
```
### Testing
The user can start *Native Image* testing by running:
```shell
mvn -Pnative test
```
> Note that the native testing depends on running the standard `test` task in the *JVM* mode beforehand.
>
> Additionally, Maven at the moment requires an extra step in order to use the recommended test listener.
>
> More information is avaliable [here](#Testing-support).
___

Documentation and more configuration options are available [here](https://github.com/graalvm/native-build-tools/blob/master/native-maven-plugin/README.md).

## Testing Support

Testing support is provided using our *JUnit Platform* support feature (`org.graalvm.buildtools:junit-platform-native`). For the end-user this means that configuring and running *JUnit Platform* tests ahead-of-time is now handled by aforementioned build plugins.

Both *Maven* and *Gradle* are (at the moment) using the custom *JUnit* Test Listener from `junit-platform-native` which - during invocation of the standard `test` task - logs tests that are discovered in order to register them for Native compilation. *Maven* currently needs an extra step in order to enable said listener: The user needs to add a dependency to `junit-platform-native` in their `pom.xml`. This step will not be necessary [once *JUnit* 5.8 lands](https://github.com/junit-team/junit5/issues/2619).
> In the future we intend to work on removing the need for `junit-platform-native` by upstreaming much of the logic into the *JUnit* project itself.

For more information users should consult plugin documentation for their preferred build tool.

Our testing support was developed in collaboration with [*JUnit*](https://junit.org/junit5/) and [*Spring*](https://spring.io/) teams.

## Built with Great Collaboration

Many thanks to [Sébastien Deleuze](https://twitter.com/sdeleuze), [Sam Brannen](https://twitter.com/sam_brannen) and [Graeme Rocher](https://twitter.com/graemerocher) for their contributions and advices. Moving forward, our new plugins are already landing in *Spring Native 0.10.0*, and hopefully soon many more projects will follow.

We are looking forwards to hearing about your experiences and/or potential issues. Contributions are also very welcome.

The project repository is available at [github.com/graalvm/native-build-tools](https://github.com/graalvm/native-build-tools/).

## Future Goals

Now we will focus on creating a standardized configuration repository for libraries that would be automatically added for legacy libraries. This should make the development for *Native Image* feels more frictionless even for libraries that don't support it yet. We intend to follow up with patches and PRs for original libraries, working closely with the community in order to bring first party support to the most of the ecosystem.
