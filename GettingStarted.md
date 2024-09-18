# Getting started

This guideline shows how users can start using Native Image through Native Gradle Plugin.

## Prerequisites

- Before starting, make sure that you have GraalVM locally. You can [download Oracle GraalVM from the official website](https://www.graalvm.org/downloads/).
- In order to run Gradle project with older GraalVM versions (like Java 17) you should set `JAVA_HOME` environment variable to point to that release.
- Depending on the Gradle version you are using ([see compatibility matrix](https://docs.gradle.org/current/userguide/compatibility.html)), you may not be able to run your project with just `JAVA_HOME` environment variable configured.
However, in order to run Gradle project with any GraalVM version you should set `GRAALVM_HOME` environment variable to point to whatever GraalVM release you want and `JAVA_HOME` environment variable to point to some older version (like Java 17).
This way, Gradle plugin will build itself with Java specified in `JAVA_HOME` and your project with the version specified in `GRAALVM_HOME`.

## Enable GraalVM Native Image Gradle Plugin

In order to use Native Image with Gradle, you must add the following block into your `build.gradle` file inside `plugins` block:

```
id 'org.graalvm.buildtools.native' version '0.10.3'
```

**Note** that at the time of writing this document, `0.10.3` was the latest Native Build Tools released version.
You can find other released versions [here](https://github.com/graalvm/native-build-tools/releases).

## Setup testing

In order to run tests on Native Image, all you have to do is to write your tests under the test source and run `./gradlew nativeTestCompile` to compile tests or `./gradlew nativeTest` (compiles and runs the tests) to execute tests found in the `test` source set.
If the tests are simple, no additional work is required.
However, for some more complex tests, you will probably need some additional metadata to make them work(learn more in the [following section](#setup-metadata-collection)).

The main configuring point for Native Images you want to build is the `graalvmNative` block that you should add into `build.gradle` file.
You can apply configuring options specific to the main or the test binary (or you can provide options that applies to all binaries) like following:

```
graalvmNative {
    binaries.main {
        // options you want to pass only to the main binary
    }
    binaries.test {
        // options you want to pass only to the test binary
    }
    binaries.all {
        // common options you want to use for both main and test binaries
    }
}
```
In this testing case, you should configure `binaries.test` block.
You can pass build-time and run-time options to the Native Image using:
- `buildArgs.add('<buildArg>')` - You can find more about possible build arguments [here](https://www.graalvm.org/latest/reference-manual/native-image/overview/BuildConfiguration/)
- `runtimeArgs.add('<runtimeArg>')` - You can find more about possible runtime arguments [here](https://www.graalvm.org/latest/reference-manual/native-image/overview/Options/)


## Setup and Collect metadata

When your tests start to be a bit more complex, things like reflection, resources, serialization, proxies or jni may be required.
Since Native Image has closed world assumption, all of these things must be known in advance during the image build.
The easiest way how this information can be passed to the Native Image is through metadata config file(s) - depending on the GraalVM version you are using, there could be
a single `reachability-metadata.json` file (for newer GraalVM versions) or multiple json files (`reflect-config.json`, `resource-config.json`, `proxy-config.json`, `serialization-config.json`, `jni-config.json`).
To learn more about metadata that Native Image consumes, [see this](https://www.graalvm.org/latest/reference-manual/native-image/metadata/).

Let's say you want to run the test that loads resource called `resource.txt`, and you don't have entry for that resource in the metadata config file, the resource can't be found during the image runtime.

To make your tests work while using resources (like in this example) or other metadata, you should either generate metadata configurations or write them manually.
To generate metadata automatically, you can run your tests with the Native Image Agent, that will collect all the metadata your tests require.
To enable the agent (through Native Gradle Plugin) you should either:
* add `-Pagent` flag to the command you are executing
* add the following block to `graalvmNative` block in the `build.gradle`:

```
agent {
    enabled = true
}
```

To generate the metadata file(s) for your `tests` just run:
* `./gradlew test` if you added the agent block to the configuration or `./gradlew -Pagent test` if you didn't. This command runs on JVM with Native Image Agent and collects the metadata.
* `./gradlew nativeTest` if you added the agent block to the configuration or `./gradlew -Pagent nativeTest` if you didn't. This command runs on JVM with the Native Image Agent, collects the metadata and uses it for testing on native-image.

**Note** that because of some Gradle incompatibilities with newer GraalVM versions, you should probably set `GRAALVM_HOME` environment variable
to the GraalVM version you want to use for your project and `JAVA_HOME` to some older version (for example `JDK21`).
However, Gradle will still pick Native Image Agent from the `JAVA_HOME` (so your `JAVA_HOME` must point to GraalVM installation) unless you add the following into the `test` block inside `build.gradle`:
```
executable = providers.environmentVariable("GRAALVM_HOME").map {
    "$it/bin/java"
}.get()
```
This way, generated metadata config file(s) will have format that is specified in the GraalVM version (from `GRAALVM_HOME`) you are using.


### Configure metadata collection

Now, as your project grows, you should consider configuring the agent with some additional options to gain more control over the generated metadata.

The first thing you can configure is the agent mode.
There are three possible agent modes:
* `standard` - only generates metadata without any special processing (this is the default mode). No additional options available. This mode is a **great starting point** in your project development
* `conditional` - entries of the generated metadata will be included in the Native Image only if the condition in the entry is satisfied. This mode is mainly aimed towards **library maintainers** with the goal of reducing overall footprint. Conditional mode consumes following additional options:
  * `userCodeFilterPath` - specifies a filter file used to classify classes as user application classes. Generated conditions will only reference these classes. See the [agent filter file example](#agent-filter-file)
  * `extraFilterPath` - extra filter used to further filter the collected metadata. See the [agent filter file example](#agent-filter-file)
* `direct` - in this mode user configures the agent completely manually by adding all options with `options.add("<option>")`. This mode is **for experienced users** that knows how to configure the agent manually


You can configure each mode (and declare the one that will be used for generating metadata) inside the `agent` block in `build.gradle` file.
Here is the example of the `agent` block with configured conditional and direct modes, where the conditional mode is set as default and will be used to generate the metadata:
```
agent {
    enabled = true
    defaultMode = "conditional"
    modes {
        conditional {
            userCodeFilterPath = "path/to/user-code-filter.json"
        }
        direct {
            options.add("config-output-dir=path/to/metadata/output/directory")
            options.add("experimental-configuration-with-origins")
        }
    }
}
```

Once configured (and agent's enable property set to true), execution of `./gradlew test` or `./gradlew nativeTest` will automatically use the agent for generating metadata for tests.

**Note** that if you choose to **enable the agent through the command line**, you can also specify in which mode you want to run it.
For example, you can run the conditional mode (defined in the example agent block above) with `./gradlew -Pagent=conditional nativeTest`, or you can run the direct mode with `./gradlew -Pagent=direct nativeTest`.
In the end you can run the agent in the standard mode with `./gradlew -Pagent=standard nativeTest`.

All the mentioned modes shares certain common configuring options like:
* callerFilterFiles
* accessFilterFiles
* builtinCallerFilter
* builtinHeuristicFilter
* enableExperimentalPredefinedClasses
* enableExperimentalUnsafeAllocationTracing
* trackReflectionMetadata
These options are for advanced usages, and you can read more about them [here](https://www.graalvm.org/latest/reference-manual/native-image/metadata/AutomaticMetadataCollection/#agent-advanced-usage).

Complete example of the agent block with all possible common options should look like this:
```
agent {
    defaultMode = "standard" // Default agent mode if one isn't specified using `-Pagent=mode_name`
    enabled = true // Enables the agent

    modes {
        conditional {
            userCodeFilterPath = "path-to-filter.json"
            extraFilterPath = "path-to-another-filter.json"
        }
        direct {
            options.add("config-output-dir={output_dir}")
            options.add("experimental-configuration-with-origins")
        }
    }

    callerFilterFiles.from("filter.json")
    accessFilterFiles.from("filter.json")
    builtinCallerFilter = true
    builtinHeuristicFilter = true
    enableExperimentalPredefinedClasses = false
    enableExperimentalUnsafeAllocationTracing = false
    trackReflectionMetadata = true
}
```


## Copy Metadata to the Project META-INF (or some other location)

By default, generated metadata will be placed inside `build/native/agent-output` directory.
In many cases you may want to move generated metadata to some other location (most likely to the project's META-INF directory).
To do so, you can configure and run `metadataCopy` task.

You can configure `metadataCopy` task by adding a new block, named `metadataCopy` inside `agent` block that you added in the previous step.
Inside this block, you can specify:
* `outputDirectories` - location where you want to move the metadata
* `inputTaskNames` - tasks previously executed with the agent attached (tasks that generated metadata in the last step).
* `mergeWithExisting` - whether the metadata you want to copy, should be merged with the metadata that already exists on the give location, or not. This only makes sense when there is already some existing metadata, created before.

For example: you want to execute metadataCopy task on the metadata generated from your tests.
Your `agent` block should now contain `metadataCopy` block like this:
```
agent {
    defaultMode = "standard"
    enabled = true
    modes {
        conditional {
            userCodeFilterPath = "path-to-filter.json"
        }
        direct {
            options.add("config-output-dir={output_dir}")
        }
    }
    metadataCopy {
        inputTaskNames.add("test")
        outputDirectories.add("src/test/resources/META-INF/native-image/org.example")
        mergeWithExisting = false
    }
}
```

Explanation of the `metadataCopy` block from above:
* `inputTaskNames.add("test")` - means that metadata we want to copy was generated with the `./gradlew test` or `./gradlew nativeTest`
* `outputDirectories.add("src/test/resources/META-INF/native-image/org.example")` - means that we want to copy metadata into the given directory
* `mergeWithExisting = false` - means that we don't want to merge incoming metadata with the metadata that already exists on the location specified in `outputDirectories`

Now, when you generated metadata with the agent (as described in the [previous section](#setup-and-collect-metadata)), and you configured `metadatCopy` task in the agent block, you can execute `metadataCopy` task with `./gradlew metadataCopy`.
This task will move your metadata on the desired location.

Moreover, if you decided to configure `metadataCopy` task through the command line, you can run the following:
* `./gradlew metadataCopy --task test` if you used `test` (or `nativeTest`) to generate the metadata .
* `./gradlew metadataCopy --dir <pathToSomeDirectory>` to specify the output directory

**Note** that you can combine those flags. For example a valid command would be: `./gradlew metadataCopy --task test --dir src/test/resources/META-INF/native-image/org.example`.

**Note** that **if you stored generated metadata on location other than the default one**, you will need to pass that location as a Native image `buildArg` with `-H:ConfigurationFileDirectories` option.
You can pass that argument inside the `binaries.test` (or `binaries.all` depending on your use-case) block like this:
```
binaries.all {
    buildArgs.add("-H:ConfigurationFileDirectories=path/to/metadata")
}
```

## Setup CI

Depending on the platform you are using for running your CI, you can set up GraalVM installation and run your tests in many ways.

There are few ways how you can get GraalVM installation (just replace version with the one you want to use):
1. With GitHub actions:
```
- uses: graalvm/setup-graalvm@v1
  with:
    java-version: '23'
    distribution: 'graalvm'
    github-token: ${{ secrets.GITHUB_TOKEN }}
```
2. With SDKMAN:
```sdk install java 23-graal```
3. As docker image:
```docker pull container-registry.oracle.com/graalvm/native-image:23```

Once you have the GraalVM on your CI platform, you can execute your tests with native image.


## What happens when new metadata is needed

Considering that you run your tests in certain CI, at some point (after updating some dependency or adding new feature/test) you may notice some test failures like missing resources or that something is reflectively missing...
In that case, your metadata requires update.
Easiest way how you can update the existing metadata is to generate the new one and perform [metadataCopy task](#copy-metadata-to-the-project-meta-inf--or-some-other-location-) with `mergeWithExisting = true`.
**Be aware** that if you modified existing metadata file(s) on the default location, generating a new metadata will overwrite the existing one and your manual changes will be lost.
So if you modified existing metadata file(s) on the default location, please do the following:
1. move the previous metadata to some other, non-default, location (with the `metadataCopy` task for example)
2. add `mergeWithExisting = true` to the `metadataCopy` block
3. run your tests again to generate new metadata (as we already described in the [setup and collect metadata](#setup-and-collect-metadata) section)
4. run `metadataCopy` task again (with `mergeWithExisting = true` configured in step 2)

This way you will keep your original metadata, and merge an incoming one with it.

## Reachability metadata repository

In some cases, when you want to maintain multiple projects that share common metadata from various libraries, you should consider contributing metadata to [Reachability metadata project](https://github.com/oracle/graalvm-reachability-metadata).\
[Contributing to the repository](https://github.com/oracle/graalvm-reachability-metadata/blob/master/CONTRIBUTING.md) should be as simple as:
* clone repository locally - `git clone git@github.com:oracle/graalvm-reachability-metadata.git`
* generate metadata and test stubs - `./gradlew scaffold --coordinates com.example:my-library:1.0.0` (replace with the GAV coordinates of library you are providing metadata for)
* implement tests in test stubs that will show how you generated metadata
* [collect metadata](https://github.com/oracle/graalvm-reachability-metadata/blob/master/docs/CollectingMetadata.md#collecting-metadata-for-a-library)
* create a pull request and fill the checklist

Native Build Tools (both Gradle and Maven plugins) picks metadata from Reachability metadata repository to ensure your application works out-of-box (if all metadata required by your app is already contributed to the metadata repository).
Furthermore, you can configure Reachability metadata support through `metadataRepository` block added to our main `graalvmNative` block inside `build.gradle`.
Most common options you may want to configure in this block are:
* `enabled` - determines if you want to use Reachability metadata support or not (`true` by default)
* `version` - specifies exact Reachability metadata version you want to use
* `uri` - specifies the url where the metadata is stored. This can be used to point to the local repository

You can learn more about `Reachability metadata` support and see the examples, [here](https://graalvm.github.io/native-build-tools/latest/gradle-plugin.html#_configuring_the_metadata_repository).


## Additional explanations

### Agent filter file

Filter files that agent consumes in the `conditional` mode have the following structure:
```
{
 "rules": [
    {"includeClasses": "some.class.to.include.**"},
    {"excludeClasses": "some.class.to.exclude.**"},
  ],
  "regexRules": [
    {"includeClasses": "regex\.example\.class.*"},
    {"excludeClasses": "regex\.example\.exclude[0-9]+"},
  ]
}  
```

These files are used to instruct the agent how to filter generated metadata.

Here is the example how different filter files affects generated metadata:\
**Note that the following example was created with GraalVM 21 and that the format of the generated metadata can vary from version to version.**

We are starting with the simple agent filter file:
```
{
  "rules": [
    {"includeClasses": "**"}
  ]
}
```
This filter file will instruct the agent to include everything and therefore, you will get a massive config files.
For example this is how `resource-config.json` looks like for a very simple test:
```
{{
  "resources":{
  "includes":[{
    "condition":{"typeReachable":"jdk.internal.logger.BootstrapLogger$DetectBackend$1"},
    "pattern":"\\QMETA-INF/services/java.lang.System$LoggerFinder\\E"
  }, {
    "condition":{"typeReachable":"jdk.internal.logger.LoggerFinderLoader"},
    "pattern":"\\QMETA-INF/services/java.lang.System$LoggerFinder\\E"
  }, {
    "condition":{"typeReachable":"java.nio.channels.spi.SelectorProvider$Holder"},
    "pattern":"\\QMETA-INF/services/java.nio.channels.spi.SelectorProvider\\E"
  }, {
    "condition":{"typeReachable":"java.time.zone.ZoneRulesProvider"},
    "pattern":"\\QMETA-INF/services/java.time.zone.ZoneRulesProvider\\E"
  }, {
    "condition":{"typeReachable":"org.junit.platform.launcher.core.LauncherFactory"},
    "pattern":"\\QMETA-INF/services/org.junit.platform.engine.TestEngine\\E"
  }, {
    "condition":{"typeReachable":"org.junit.platform.launcher.core.LauncherFactory"},
    "pattern":"\\QMETA-INF/services/org.junit.platform.launcher.LauncherDiscoveryListener\\E"
  }, {
    "condition":{"typeReachable":"org.junit.platform.launcher.core.LauncherFactory"},
    "pattern":"\\QMETA-INF/services/org.junit.platform.launcher.LauncherSessionListener\\E"
  }, {
    "condition":{"typeReachable":"org.junit.platform.launcher.core.LauncherFactory"},
    "pattern":"\\QMETA-INF/services/org.junit.platform.launcher.PostDiscoveryFilter\\E"
  }, {
    "condition":{"typeReachable":"java.util.Iterator"},
    "pattern":"\\QMETA-INF/services/org.junit.platform.launcher.TestExecutionListener\\E"
  }, {
    "condition":{"typeReachable":"org.junit.platform.launcher.core.LauncherConfigurationParameters"},
    "pattern":"\\Qjunit-platform.properties\\E"
  }, {
    "condition":{"typeReachable":"org.slf4j.LoggerFactory"},
    "pattern":"\\Qorg/slf4j/impl/StaticLoggerBinder.class\\E"
  }, {
    "condition":{"typeReachable":"worker.org.gradle.internal.classloader.FilteringClassLoader"},
    "pattern":"\\Qorg/slf4j/impl/StaticLoggerBinder.class\\E"
  }, {
    "condition":{"typeReachable":"java.lang.ClassLoader"},
    "pattern":"\\Qresource.txt\\E"
  }]},
  "bundles":[]
}
```

As you can see, there is a lot of resource that you may don't want.
To reduce the amount of generated metadata, we will use the following `user-code-filter.json`:
```
{
  "rules": [
    {"includeClasses": "**"},
    {"excludeClasses": "java.time.zone.**"},
    {"excludeClasses": "org.junit.platform..**"}
  ]
}
```
After regenerating metadata, `resoruce-config.json` generated on the same example as above (but with new `user-code-filter`) will look like this:
```
{
  "resources":{
  "includes":[{
    "condition":{"typeReachable":"jdk.internal.logger.BootstrapLogger$DetectBackend$1"},
    "pattern":"\\QMETA-INF/services/java.lang.System$LoggerFinder\\E"
  }, {
    "condition":{"typeReachable":"jdk.internal.logger.LoggerFinderLoader"},
    "pattern":"\\QMETA-INF/services/java.lang.System$LoggerFinder\\E"
  }, {
    "condition":{"typeReachable":"java.nio.channels.spi.SelectorProvider$Holder"},
    "pattern":"\\QMETA-INF/services/java.nio.channels.spi.SelectorProvider\\E"
  }, {
    "condition":{"typeReachable":"java.util.Iterator"},
    "pattern":"\\QMETA-INF/services/org.junit.platform.launcher.TestExecutionListener\\E"
  }, {
    "condition":{"typeReachable":"org.slf4j.LoggerFactory"},
    "pattern":"\\Qorg/slf4j/impl/StaticLoggerBinder.class\\E"
  }, {
    "condition":{"typeReachable":"worker.org.gradle.internal.classloader.FilteringClassLoader"},
    "pattern":"\\Qorg/slf4j/impl/StaticLoggerBinder.class\\E"
  }, {
    "condition":{"typeReachable":"java.lang.ClassLoader"},
    "pattern":"\\Qresource.txt\\E"
  }]},
  "bundles":[]
}
```

As you can see there are no more entries that contain classes from `org.junit.platform.launcher` for example.

### Track diagnostics

If you want to explore details about native images you are generating, you can add:
* `buildArgs.add("--emit build-report")` - from the GraalVM for JDK23 
* `buildArgs.add("-H:+BuildReport")` - for older versions starting from the GraalVM for JDK21

When the Native Image build is completed, you will find a path to the generated Build Report HTML in `Build artifacts` section in the build output like this:
```
------------------------------------------------------------------------------------------------------------------------
Build artifacts:
 /build/native/nativeCompile/main (executable)
 /build/native/nativeCompile/main-build-report.html (build_info)
========================================================================================================================
```
You can read more about build report features [here](https://www.graalvm.org/latest/reference-manual/native-image/overview/build-report/).

**Note** that Build Report features vary depending on a GraalVM version you use.