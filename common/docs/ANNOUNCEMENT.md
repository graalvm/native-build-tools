
### Gradle and Maven Plugins for Native Image with Initial JUnit 5 Testing Support

![](https://cdn-images-1.medium.com/max/800/1*xyDPne0XE1sK69i4wyHC6Q.png)


We are pleased to announce the release of the new official _GraalVM Native Image_ _Gradle_ and _Maven_ plugins. The plugins aim to make [_GraalVM Native Image_](https://www.graalvm.org/reference-manual/native-image/) a first-class citizen in the _JVM_ ecosystem: it makes building, testing, and running _Java_ applications as native executables a breeze. The key new feature is the **out-of-the-box** support for _native_ [_JUnit 5_](https://junit.org/junit5/)  testing

The native _JUnit_ support allows libraries in the _JVM_ ecosystem to run their test suites via _GraalVM Native Image_. With integrated testing, library configuration should be always up to date and ready for the end user. We believe that this is the biggest step towards making the _Java_ ecosystem _native-ready_.

Complete examples are available [here](https://github.com/graalvm/native-build-tools/blob/master/samples/README.md).

### Native Testing: The Way You Are Used to It

We’ve made it: testing _Java_ code with _JUnit 5_ behaves exactly the same* in native execution as with the _JVM_. To make this work, we implemented a custom `junit-platform-native` [feature](https://github.com/graalvm/native-build-tools/tree/master/common/junit-platform-native) that logs tests that are discovered during the previous execution of the standard _JVM_ `test` task and registers them for native compilation and reflection. Based on this information, special test executable that contains all the tests (and their metadata) is built, and then invoked when using the corresponding build-tool task.

Collection of metadata imposes the dependency to the _JVM_ `test` task execution. The downside of this is **increased testing time**, but it also provides a suitable entry point for users to enable `[native-image-agent](https://www.graalvm.org/reference-manual/native-image/BuildConfiguration/#assisted-configuration-of-native-image-builds)` invocation that can be used to **generate missing reflection configuration** for the project. Note that the agent invocation is independent from the test-metadata collection — the test metadata is always collected.

> _In the future we intend to work on removing the dependency on the_ JVM `test` _execution. For this, we need modifications in the testing frameworks that will allow metadata collection without the execution of individual tests (a.k.a., dry-run support)._

For more information users should consult the plugin documentation for [_Maven_](https://github.com/graalvm/native-build-tools/blob/master/native-maven-plugin/README.md) or [_Gradle_](https://github.com/graalvm/native-build-tools/blob/master/native-gradle-plugin/README.md).

_* Given proper configuration, and given that the project under test doesn’t include native-specific code (e.g., build-time initialization, substitutions, and plugins)._

###

### Gradle Plugin

Adding `native-gradle-plugin` to an existing _Gradle_ project is as simple as including following to the `plugins` section of the `build.gradle`
```groovy
plugins {
  id 'org.graalvm.buildtools.native' version "0.9.0"  // or a newer version
}
```
as well as adding the following to the `settings.gradle`:
```groovy
pluginManagement {
  repositories {
    mavenCentral()
    gradlePluginPortal()
  }
}
```
_(this step will be redundant once this plugin is published to the Gradle Plugin Portal)._

After that, we can configure the image build by using a `graal` configuration block:
```groovy
graal {
    nativeImages {
        main {
            imageName = "my-app"
            mainClass = "org.test.Main"
            verbose = true
            fallback = false
        }
    }
}
```

The plugin then adds `nativeAssemble` and `nativeRun` tasks that respectively creates a native executable and runs the main class (as one might expect ☺). If the reflection configuration is necessary for the Native Image building, this plugin also provides a simple option that activates the `[native-image-agent](https://www.graalvm.org/reference-manual/native-image/BuildConfiguration/#assisted-configuration-of-native-image-builds)` without any additional user setup. More information (and _Kotlin_ configuration syntax) is available in the [documentation](https://github.com/graalvm/native-build-tools/blob/master/native-gradle-plugin/README.md).

#### Testing in _Gradle_

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

> Note that the native testing depends on running the standard `test` task in the JVM mode beforehand.


### Maven Plugin

We are releasing our existing plugin under the new maven coordinates — `org.graalvm.buildtools:native-maven-plugin`. This change was motivated by our intention to move faster with the plugin development by decoupling it from the _GraalVM_ release cycle. Users of our existing `native-image-maven-plugin` only need to change the plugin's `groupId`, `artifactId` and `version` in their `pom.xml`, as the new plugin is backwards compatible with the old one. Versioning of the new plugin will start at `0.9.0`.

Adding our new plugin to the existing _Maven_ project requires adding the following to the `pom.xml`:
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
#### Testing in Maven

The user can start _Native Image_ testing by running:
```shell
mvn -Pnative test
```

_Maven_ plugin currently requires an extra step in order to enable said listener: The user needs to add a dependency to `org.graalvm.buildtools:junit-platform-native` in their `pom.xml`. This step will not be necessary [once _JUnit_ 5.8 lands](https://github.com/junit-team/junit5/issues/2619).

> Note that the native testing depends on running the standard test task in the JVM mode beforehand.

Documentation and more configuration options are available [here](https://github.com/graalvm/native-build-tools/blob/master/native-maven-plugin/README.md).


### Future Goals

The next step is creating a standardized repository with [configuration](https://www.graalvm.org/reference-manual/native-image/BuildConfiguration/) that would be automatically added for legacy libraries. This should make the development for _Native Image_ feels more frictionless even for libraries that don’t support it yet. We intend to follow up with patches and _PR_s for original libraries, working closely with the community in order to bring first party support to the most of the ecosystem.


### Built with Love Through Collaboration

Our testing support was developed in collaboration with [_JUnit_](https://junit.org/junit5/ "https://junit.org/junit5/"), [Micronaut](https://micronaut.io/), and [_Spring_](https://spring.io/ "https://spring.io/") teams. Many thanks to [Sam Brannen](https://twitter.com/sam_brannen), [Graeme Rocher](https://twitter.com/graemerocher), and [Sébastien Deleuze](https://twitter.com/sdeleuze) for their contributions and advice. Moving forward, our new plugins are already landing in [_Spring Native 0.10.0_](https://github.com/spring-projects-experimental/spring-native), and hopefully soon many more projects will follow.

We are looking forwards to hearing about your experiences and/or potential issues. Contributions are also very welcome.

The project repository is available at [github.com/graalvm/native-build-tools](https://github.com/graalvm/native-build-tools/).

All projects mentioned here are released under the [_Universal Permissive License_](https://www.oracle.com/downloads/licenses/upl-license1.html).
