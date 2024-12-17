/*
 * Copyright (c) 2020, 2022 Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * The Universal Permissive License (UPL), Version 1.0
 *
 * Subject to the condition set forth below, permission is hereby granted to any
 * person obtaining a copy of this software, associated documentation and/or
 * data (collectively the "Software"), free of charge and under any and all
 * copyright rights in the Software, and any and all patent rights owned or
 * freely licensable by each licensor hereunder covering either (i) the
 * unmodified Software as contributed to or provided by such licensor, or (ii)
 * the Larger Works (as defined below), to deal in both
 *
 * (a) the Software, and
 *
 * (b) any piece of software and/or hardware listed in the lrgrwrks.txt file if
 * one is included with the Software each a "Larger Work" to which the Software
 * is contributed by such licensors),
 *
 * without restriction, including without limitation the rights to copy, create
 * derivative works of, display, perform, and distribute the Software and make,
 * use, sell, offer for sale, import, export, have made, and have sold the
 * Software and the Larger Work(s), and to sublicense the foregoing rights on
 * either these or other terms.
 *
 * This license is subject to the following condition:
 *
 * The above copyright notice and either this complete permission notice or at a
 * minimum a reference to the UPL must be included in all copies or substantial
 * portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */
import org.graalvm.buildtools.gradle.tasks.BuildNativeImageTask

plugins {
    java
    id("org.graalvm.buildtools.native")
}

// tag::select-toolchain[]
graalvmNative {
    binaries {
        named("main") {
            javaLauncher.set(javaToolchains.launcherFor {
                languageVersion.set(JavaLanguageVersion.of(20))
                vendor.set(JvmVendorSpec.matching("Oracle Corporation"))
            })
        }
    }
}
// end::select-toolchain[]

if (providers.environmentVariable("ENABLE_TOOLCHAIN").isPresent()) {
// tag::enabling-toolchain[]
    graalvmNative {
        toolchainDetection.set(true)
    }
// end::enabling-toolchain[]
}

// tag::all-config-options[]
graalvmNative {
    binaries {
        named("main") {
            imageName.set("application")
            mainClass.set("org.test.Main")
            debug.set(true)
            verbose.set(true)
            fallback.set(true)
            sharedLibrary.set(false)
            richOutput.set(false)
            quickBuild.set(false)

            systemProperties.putAll(mapOf("name1" to "value1", "name2" to "value2"))
            configurationFileDirectories.from(file("src/my-config"))
            excludeConfig.put("org.example.test:artifact:version", listOf("^/META-INF/native-image/.*", "^/config/.*"))
            excludeConfig.put(file("path/to/artifact.jar"), listOf("^/META-INF/native-image/.*", "^/config/.*"))

            // Advanced options
            buildArgs.add("--link-at-build-time")
            jvmArgs.add("flag")

            // Runtime options
            runtimeArgs.add("--help")

            useFatJar.set(true)
        }
    }

    agent {
        defaultMode.set("standard")
        enabled.set(true)

        modes {
            standard {
            }
            conditional {
                userCodeFilterPath.set("path-to-filter.json")
                extraFilterPath.set("path-to-another-filter.json") // Optional
            }
            direct {
                options.add("config-output-dir={output_dir}")
                options.add("experimental-configuration-with-origins")
            }
        }

        callerFilterFiles.from("filter.json")
        accessFilterFiles.from("filter.json")
        builtinCallerFilter.set(true)
        builtinHeuristicFilter.set(true)
        enableExperimentalPredefinedClasses.set(false)
        enableExperimentalUnsafeAllocationTracing.set(false)
        trackReflectionMetadata.set(true)

        metadataCopy {
            inputTaskNames.add("test")
            outputDirectories.add("/META-INF/native-image/<groupId>/<artifactId>/")
            mergeWithExisting.set(true)
        }

        tasksToInstrumentPredicate.set(t -> true)
    }
}
// end::all-config-options[]

// tag::enable-fatjar[]
graalvmNative {
    useFatJar.set(false) // required for older GraalVM releases
    binaries {
        named("main") {
            useFatJar.set(true)
        }
    }
}
// end::enable-fatjar[]

val myFatJar = tasks.register<Jar>("myFatJar")

// tag::custom-fatjar[]
tasks.named<BuildNativeImageTask>("nativeCompile") {
    classpathJar.set(myFatJar.flatMap { it.archiveFile })
}
// end::custom-fatjar[]

// tag::disable-test-support[]
graalvmNative {
    testSupport.set(false)
}
// end::disable-test-support[]

val integTest by sourceSets.creating
val integTest = tasks.register<Test>("integTest")

// tag::custom-binary[]
graalvmNative {
    registerTestBinary("integTest") {
        usingSourceSet(sourceSets.getByName("integTest"))
        forTestTask(tasks.named<Test>("integTest"))
    }
}
// end::custom-binary[]

// tag::disable-metadata-repository[]
graalvmNative {
    metadataRepository {
        enabled.set(false)
    }
}
// end::disable-metadata-repository[]

// tag::specify-metadata-repository-version[]
graalvmNative {
    metadataRepository {
        version.set("0.1.0")
    }
}
// end::specify-metadata-repository-version[]


// tag::specify-metadata-repository-file[]
graalvmNative {
    metadataRepository {
        uri(file("metadata-repository"))
    }
}
// end::specify-metadata-repository-file[]

// tag::exclude-module-from-metadata-repo[]
graalvmNative {
    metadataRepository {
        // Exclude this library from automatic metadata
        // repository search
        excludes.add("com.company:some-library")
    }
}
// end::exclude-module-from-metadata-repo[]

// tag::specify-metadata-version-for-library[]
graalvmNative {
    metadataRepository {
        // Force the version of the metadata for a particular library
        moduleToConfigVersion.put("com.company:some-library", "3")
    }
}
// end::specify-metadata-version-for-library[]

// tag::include-metadata[]
tasks.named("jar", Jar) {
	from(collectReachabilityMetadata)
}
// end::include-metadata[]

// tag::configure-binaries[]
graalvmNative {
    binaries {
        named("main") {
            imageName.set("my-app")
            mainClass.set("org.jackup.Runner")
            buildArgs.add("-O4")
        }
        named("test") {
            buildArgs.add("-O0")
        }
    }
    binaries.all {
        buildArgs.add("--verbose")
    }
}
// end::configure-binaries[]

// tag::configure-test-binary[]
graalvmNative {
    binaries {
        named("main") {
            mainClass.set("org.test.Main")
        }
        named("test") {
            buildArgs.addAll("--verbose", "-O0")
        }
    }
}
// end::configure-test-binary[]
