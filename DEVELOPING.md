# Documentation for Developers

This document describes how to set up and develop Native Build Tools on your local machine.

## Environment

The project uses Gradle as its build system. At the very minimum, you should set `JAVA_HOME` to a [Gradle-compatible JDK](https://docs.gradle.org/current/userguide/compatibility.html).

Some build tasks require a GraalVM JDK (e.g., tests). You should set `GRAALVM_HOME` to an appropriate GraalVM JDK.

## IDE Setup

The Native Build Tools repo is set up as a multi-project Gradle project, with the Maven and Gradle plugins declared as subprojects of the root project.
To set the project up in your IDE (e.g., IntelliJ IDEA), import the root project and the IDE should automatically import the subprojects.

## Building and Testing

You can use the various commands in the [Gradle build lifecycle](https://docs.gradle.org/current/userguide/build_lifecycle.html) to build and test the project.
Some examples (all executed from the root of the repository):

```bash
# Build all projects
./gradlew assemble

# Build only the native-gradle-plugin (for example)
./gradlew :native-gradle-plugin:assemble

# Build and run all tests
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

To do this, first modify the project as necessary, and then build and publish the plugins to the local Maven repository:
```bash
./gradlew publishToMavenLocal --no-parallel
```

Next, update the plugin version string in the project build files.
The version can be found manually by searching for the published artifacts in `~/.m2/repository/org/graalvm/buildtools/native/`, or alternatively by checking the `nativeBuildTools` property [here](gradle/libs.versions.toml).

For Gradle, the change looks like the following.
You may also need to direct Gradle to use the local Maven repository to resolve the plugin:
```bash
# build.gradle
 plugins {
     ...
-    id 'org.graalvm.buildtools.native' version '0.9.25'
+    id 'org.graalvm.buildtools.native' version '0.10.5-SNAPSHOT'
 }

  repositories {
+    mavenLocal()
     ...
 }

# settings.gradle
pluginManagement {
  repositories {
+     mavenLocal()
      ...
  }
}
```

For Maven, simply bump the version and it should try the local repository automatically:
```bash
# pom.xml
-        <native.maven.plugin.version>0.9.25</native.maven.plugin.version>
+        <native.maven.plugin.version>0.10.5-SNAPSHOT</native.maven.plugin.version>
```

Then, run your build as usual. Gradle/Maven should find the plugin in the local repository and use it for the build.
