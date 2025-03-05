# Documentation for Developers

This document describes how to set up and develop Native Build Tools on your local machine.

## Environment

Start by setting `JAVA_HOME` to a [Gradle-compatible JDK](https://docs.gradle.org/current/userguide/compatibility.html).

Some build tasks require a GraalVM JDK (e.g., tests). You should set `GRAALVM_HOME` to an appropriate GraalVM JDK.

## IDE Setup

The Native Build Tools repository is structured as a Gradle multi-project, with the Maven and Gradle plugins declared as subprojects of the root project.
To configure it in your IDE (e.g., IntelliJ IDEA), import the root project, and the IDE should automatically detect and include the subprojects.

## Building and Testing

You can use the various commands in the [Gradle build lifecycle](https://docs.gradle.org/current/userguide/build_lifecycle.html) to build and test the project.
Some examples are (all executed from the root of the repository):

```bash
# Compile all projects
./gradlew assemble

# Run unit tests in all projects
./gradlew test

# Run functional tests in all projects
./gradlew funTest

# Compile only the native-gradle-plugin (for example)
./gradlew :native-gradle-plugin:assemble

# Build and run all tests, complete (and very long) build
./gradlew build
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

### Gradle

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

### Maven

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
