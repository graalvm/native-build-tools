/*
 * Copyright (c) 2021, Oracle and/or its affiliates. All rights reserved.
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

package org.graalvm.buildtools.gradle

import org.graalvm.buildtools.gradle.fixtures.AbstractFunctionalTest
import spock.lang.Ignore
import spock.lang.IgnoreIf
import spock.lang.Issue
import spock.lang.Requires

import java.nio.file.Files

class JavaApplicationFunctionalTest extends AbstractFunctionalTest {
    def "can build a native image for a simple application"() {
        def nativeApp = getExecutableFile("build/native/nativeCompile/java-application")
        debug = true

        given:
        withSample("java-application")

        buildFile << """
            // force realization of the run task to verify that it
            // doesn't accidentally introduce a dependency
            tasks.getByName('run')
        """.stripIndent()

        when:
        run 'nativeCompile'

        then:
        tasks {
            succeeded ':jar', ':nativeCompile'
            doesNotContain ':build', ':run'
        }

        and:
        nativeApp.exists()

        when:
        def process = execute(nativeApp)

        then:
        process.output.contains "Hello, native!"

    }

    @Issue("https://github.com/graalvm/native-build-tools/issues/129")
    def "can build a native image with dependencies only needed by native image"() {
        def nativeApp = getExecutableFile("build/native/nativeCompile/java-application")

        given:
        withSample("java-application-with-extra-sourceset")

        when:
        run 'nativeCompile'

        then:
        tasks {
            succeeded ':jar', ':nativeCompile', ':compileGraalJava'
            doesNotContain ':build', ':run'
        }

        and:
        new File(nativeApp.parentFile, 'app.txt').text.contains(
                'Application Feature'
        )
        nativeApp.exists()

        when:
        def process = execute(nativeApp)

        then:
        process.output.contains "Hello, native!"

    }

    @Requires({
        def graalvmHome = System.getenv("GRAALVM_HOME")
        graalvmHome != null
    })
    @Ignore("Need to find another way to test this since toolchains will now always be evaluated early")
    def "can override toolchain selection"() {
        def nativeApp = getExecutableFile("build/native/nativeCompile/java-application")

        given:
        withSample("java-application")

            buildFile << """graalvmNative.binaries.configureEach {
                javaLauncher.set(javaToolchains.launcherFor {
                    languageVersion.set(JavaLanguageVersion.of(JavaVersion.current().getMajorVersion()))
                    vendor.set(JvmVendorSpec.matching("non existing vendor"))
                })
            }
            
            tasks.withType(org.graalvm.buildtools.gradle.tasks.BuildNativeImageTask).configureEach {
                disableToolchainDetection = true
            }
        """.stripIndent()

        when:
        run 'nativeCompile', '-i'

        then:
        tasks {
            succeeded ':jar', ':nativeCompile'
            doesNotContain ':build', ':run'
        }

        and:
        nativeApp.exists()

        when:
        def process = execute(nativeApp)

        then:
        process.output.contains "Hello, native!"

    }

    @Issue("https://github.com/graalvm/native-build-tools/issues/224")
    def "handles spaces in file names"() {
        given:
        withSpacesInProjectDir()
        withSample("java-application")

        buildFile << """
            // force realization of the run task to verify that it
            // doesn't accidentally introduce a dependency
            tasks.getByName('run')
        """.stripIndent()

        when:
        run 'nativeCompile'

        then:
        tasks {
            succeeded ':jar', ':nativeCompile'
            doesNotContain ':build', ':run'
        }

        and:
        def nativeApp = getExecutableFile("build/native/nativeCompile/java-application")
        nativeApp.exists()

        when:
        def process = execute(nativeApp)

        then:
        process.output.contains "Hello, native!"

    }

    @Issue("https://github.com/graalvm/native-build-tools/issues/275")
    def "can pass environment variables to the builder process"() {
        def nativeApp = getExecutableFile("build/native/nativeCompile/java-application")

        given:
        withSample("java-application")

        buildFile << """
            graalvmNative.binaries.all {
                buildArgs.add("--initialize-at-build-time=org.graalvm.demo.Application")
                buildArgs.add("-ECUSTOM_MESSAGE")
                environmentVariables.put('CUSTOM_MESSAGE', 'Hello, custom message!')
            }
        """.stripIndent()

        when:
        run 'nativeCompile'

        then:
        nativeApp.exists()

        when:
        def process = execute(nativeApp)

        then:
        process.output.contains "Hello, custom message!"

    }

    @IgnoreIf({ System.getenv("IS_GRAALVM_DEV_BUILD") })
    def "can build a native image with PGO instrumentation"() {
        def pgoDir = file("src/pgo-profiles/main").toPath()
        def nativeApp = getExecutableFile("build/native/nativeCompile/java-application-instrumented")
        def pgoFile = file("build/native/nativeCompile/default.iprof")

        given:
        withSample("java-application", false)
        buildFile << """
            graalvmNative {
                useArgFile = false // required to check for --pgo flag
                binaries {
                    all {
                        verbose = true
                    }
                }
            }
        """

        when:
        run 'nativeCompile', '--pgo-instrument', 'nativeRun'

        then:
        tasks {
            succeeded ':jar', ':nativeCompile', ':nativeRun'
        }

        and:
        nativeApp.exists()
        pgoFile.exists()

        when:
        Files.createDirectories(pgoDir)
        Files.copy(pgoFile.toPath(), pgoDir.resolve("default.iprof"))
        run 'nativeCompile', 'nativeRun'

        then:
        tasks {
            succeeded ':nativeCompile', ':nativeRun'
        }
        outputContains "--pgo="
        outputContains "PGO: user-provided"
    }

}
