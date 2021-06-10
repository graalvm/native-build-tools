# *Gradle* and *Maven* plugins for *Native Image* with initial testing support
We are proud to announce the release of our new plugins which should largely simplify adding *GraalVM Native Image* support to your existing projects. In addition to our existing *Maven* plugin (which has been revamped and updated), we are moving forward with the brand new official *Gradle* plugin with the same feature set. We are also rolling out our initial support for *AOT* testing with the intention for it to be a cornerstone of the community.

All projects mentioned here are released under [*Universal Permissive License*](https://www.oracle.com/downloads/licenses/upl-license1.html).

___
For the impatient, examples are available [here](https://github.com/graalvm/native-build-tools/blob/master/examples/README.md) with the invoking script available [here](https://github.com/graalvm/native-build-tools/blob/master/common/scripts/testAll.sh).

## Gradle plugin
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
Plugin then adds `nativeBuild` and `nativeRun` tasks which are doing exactly what one may expect from them. If the reflection configuration is necessary for Native Image building, this plugin also provides simple option which activates `native-image-agent` without any additional user setup. As always, more information (and *Kotlin* configuration syntax) is available in the [documentation](https://github.com/graalvm/native-build-tools/blob/master/native-gradle-plugin/README.md).

> At the moment we support subset of configuration syntax and aliased task names from the most popular unofficial *GraalVM* *Gradle* plugins (`io.micronaut.application` and `com.palantir.graal`) in order to ease the community transition. Note that this behavior might eventually be deprecated.

## Maven plugin
Our tried and tested `native-image-maven-plugin` was forked and simplified. Due to the fact that we intend to move faster with plugin development, we decided to untangle versioning from *GraalVM* one (latest being `21.1.0`), and restart it from `1.0.0-M1` with artifact moving from `org.graalvm.nativeimage:native-image-maven-plugin` to `org.graalvm.buildtools:native-image-plugin`. New plugin is backwards compatible with the old one, so in order to migrate, user should just change plugin's `groupId` , `artifactId` and `version` in their `pom.xml`.

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
Documentation and more configuration options are available [here](https://github.com/graalvm/native-build-tools/blob/master/native-maven-plugin/README.md).

## Testing support
In coordination with [*JUnit*](https://junit.org/junit5/) and [*Spring*](https://spring.io/) teams we are rolling out our initial *AOT* *JUnit Platform* support feature (`org.graalvm.buildtools:junit-platform-native`). For the end-user this means that configuring and running *JUnit Platform* tests ahead-of-time is now handled by aforementioned build plugins.

In *Gradle* user can start Native Image testing by invoking `nativeTest` task (with possibility to add additional `native-image` configuration using `nativeTest` configuration block).
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

In *Maven* user starts *Native Image* testing by running `mvn -Pnative test`. 

Both *Maven* and *Gradle* are (at the moment) using custom *JUnit* Test Listener from `junit-platform-native` which - during invocation of standard `test` task - logs tests that are discovered in order to register them for *AOT* compilation. *Maven* currently needs an extra step in order to enable said listener, e.g. user needs to add a dependency to `junit-platform-native` in their `pom.xml`. This step will not be necessary [when *JUnit* 5.8 lands](https://github.com/junit-team/junit5/issues/2619).
> In the future we intend to work on removing the need for `junit-platform-native` by upstreaming much of the logic into JUnit project itself.

For more information user should consult plugin documentation for their preferred build tool.

## Recapitulation
Many thanks to [Sébastien Deleuze](https://twitter.com/sdeleuze), [Sam Brannen](https://twitter.com/sam_brannen) and [Graeme Rocher](https://twitter.com/graemerocher) for their contributions and advices. Moving forward, our new plugins are already landing in *Spring Native 0.10.0*, and hopefully soon many more projects will follow. 

We are looking forwards to hearing about your experiences and/or potential issues. Contributions are also very welcome. 

Project repository is available at [github.com/graalvm/native-build-tools](https://github.com/graalvm/native-build-tools/).

## Future goals
Our next big focus is on creating standardized configuration for big libraries which would automatically be added when necessary, so that development for *Native Image* feels more frictionless. We intend to follow up with patches and PRs for original libraries, working closely with the community in order to bring first party support to most of the ecosystem.
