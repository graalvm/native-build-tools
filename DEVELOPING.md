# Documentation for Developers

This document describes how to set up and develop Native Build Tools on your local machine.

## Environment

Start by setting `JAVA_HOME` to a [Gradle-compatible JDK](https://docs.gradle.org/current/userguide/compatibility.html).

Some build tasks require a GraalVM JDK (e.g., tests). You should set `GRAALVM_HOME` to an appropriate GraalVM JDK.

## IDE Setup

The Native Build Tools repository is structured as a Gradle multi-project, with the Maven and Gradle plugins declared as subprojects of the root project.
To configure it in your IDE (e.g., IntelliJ IDEA), import the root project, and the IDE should automatically detect and include the subprojects.

## Projects

This repo contains the following projects:

- `native-gradle-plugin` — Gradle plugin that provides support for building and testing GraalVM native images in Gradle builds (tasks, DSL, and functional tests).
- `native-maven-plugin` — Maven plugin that provides support for building and testing GraalVM native images in Maven builds (mojos and functional tests).
- `junit-platform-native` (in `common/`) — JUnit Platform native support used by plugins to run on native image.
- `utils` (in `common/`) — Shared utility code used across the plugins, tests, and internal build logic.
- `graalvm-reachability-metadata` (in `common/`) — Common code related to the [GraalVM reachability metadata](https://github.com/oracle/graalvm-reachability-metadata) repository integration.
- `docs` — Documentation sources and build for the user guide and changelog. Please keep up to date.

Internal build logic (used to build this repository itself):

- `settings-plugins` (in `build-logic`) — Gradle settings plugins and supporting tooling.
- `aggregator` (in `build-logic`) — Composite build that aggregates internal build plugins and conventions.

## Building and Testing

You can use the various commands in the [Gradle build lifecycle](https://docs.gradle.org/current/userguide/build_lifecycle.html) to build and test the project (and all the subprojects).
Examples used in daily development follow (all executed from the root of the repository):

```bash
# Compile all projects
./gradlew assemble

# Compile only the native-gradle-plugin (for example)
./gradlew :native-gradle-plugin:assemble

# Run unit tests in all projects
./gradlew test

# Run functional tests in individual projects
./gradlew :native-maven-plugin:functionalTest
./gradlew :native-gradle-plugin:functionalTest

# Run a specific test class
 ./gradlew -DnoTestIsolation=true :native-maven-plugin:functionalTest --tests "org.graalvm.buildtools.maven.IntegrationTest"
 
 # Run a specific test method (with spaces in their name)
 ./gradlew -DnoTestIsolation=true :native-maven-plugin:functionalTest --tests "org.graalvm.buildtools.maven.IntegrationTest.run integration tests with failsafe plugin and agent"

# Checkstyle
./gradlew :graalvm-reachability-metadata:checkstyleMain :graalvm-reachability-metadata:checkstyleTest
./gradlew :junit-platform-native:checkstyleMain :junit-platform-native:checkstyleTest
./gradlew :native-gradle-plugin:inspections
./gradlew :native-maven-plugin:inspections

# Build and run all tests, complete (and very long) build
./gradlew build

# Clean all projects
./gradlew clean
```

## Debugging Plugin(s)

It is often useful to attach a debugger to the Gradle and Maven plugins during a project build.

For the Gradle plugin, this can be accomplished by passing debugger options to the Gradle daemon via `org.gradle.jvmargs`, for example:

```bash
JAVA_OPTS="-Dorg.gradle.jvmargs=-agentlib:jdwp=transport=dt_socket,server=y,suspend=y,address=8000" ./gradlew assemble
```

The Gradle daemon will suspend on start-up, wait for you to attach a debugger, and then remain attached for subsequent Gradle commands.

For the Maven plugin, simply use the `mvnDebug` command in place of the `mvn` command.

## Testing Local Changes with an Existing Project

A common development task is to modify a plugin and then test it with an existing project.

To do this, first modify the project as necessary, and then build and publish the plugins:
```bash
./gradlew publishAllPublicationsToCommonRepository --no-parallel
```
The above command publishes to a "common" repository located at `build/common-repo`.

Next, update the project build files:
1. Update the version string. The version can be found manually by searching for the published artifacts in `build/common-repo`, or alternatively by checking the `nativeBuildTools` property [here](gradle/libs.versions.toml).
2. Update the list of repositories to include and prioritize the common repo.

### Testing with Gradle

Make the following changes to the build files:
```bash
# build.gradle
 plugins {
     ...
-    id 'org.graalvm.buildtools.native' version '0.9.25'
+    id 'org.graalvm.buildtools.native' version '0.10.5-SNAPSHOT'
 }

 repositories {
+    maven {
+        name = "common"
+        url = "$NATIVE_BUILD_TOOLS_ROOT/build/common-repo"
+    }
     ...
 }

# settings.gradle
 pluginManagement {
     repositories {
+        maven {
+            name = "common"
+            url = "$NATIVE_BUILD_TOOLS_ROOT/build/common-repo"
+        }
         # NB: If repositories were not specified before, declaring the common
         # repo will overwrite the default repository; you may also need to
         # declare that repository explicitly.
         mavenCentral()
         ...
     }
 }
```
Then, run the Gradle command as usual.

### Testing with Maven

Make the following changes to _pom.xml_:
```bash
# pom.xml
 <project ...>
   <properties>
-    <native.maven.plugin.version>0.9.25</native.maven.plugin.version>
+    <native.maven.plugin.version>0.10.5-SNAPSHOT</native.maven.plugin.version>
   </properties>
   ...
+  <pluginRepositories>
+    <pluginRepository>
+      <id>common</id>
+      <url>file://$NATIVE_BUILD_TOOLS_ROOT/build/common-repo</url>
+    </pluginRepository>
+  </pluginRepositories>
 </project>
```

Then, run the Maven command with the `-U` flag to force Maven to use an updated snapshot (e.g., `mvn -Pnative package -U`).

## Changelog

Changelog must be updated for all significant changes. The changelog uses asciidoc and it is located in [docs](docs/src/docs/asciidoc/changelog.adoc).
