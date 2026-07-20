/*
 * Copyright (c) 2026, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.buildtools.gradle.internal

import org.gradle.api.GradleException
import org.gradle.api.logging.Logger
import org.gradle.api.provider.Provider
import org.gradle.api.file.Directory
import org.gradle.api.file.RegularFile
import org.gradle.jvm.toolchain.JavaInstallationMetadata
import org.gradle.jvm.toolchain.JavaLauncher
import org.gradle.process.ExecOperations
import spock.lang.Specification
import spock.lang.TempDir

import static org.graalvm.buildtools.utils.SharedConstants.NATIVE_IMAGE_EXE

/**
 * Unit tests for {@link NativeImageExecutableLocator}.
 * Covers the explicit-vs-convention launcher selection logic (§FS-native-invocation.1).
 */
class NativeImageExecutableLocatorTest extends Specification {

    @TempDir
    File tempDir

    def "explicit launcher with missing native-image fails with diagnostic for §FS-native-invocation.1.1"() {
        given:
        def toolchainDir = new File(tempDir, "toolchain-jdk")
        toolchainDir.mkdirs()
        def toolchainNativeImage = new File(toolchainDir, "bin/$NATIVE_IMAGE_EXE")

        def metadata = Stub(JavaInstallationMetadata) {
            getInstallationPath() >> Stub(Directory) {
                file(_) >> Stub(RegularFile) {
                    getAsFile() >> toolchainNativeImage
                }
                getAsFile() >> toolchainDir
            }
        }
        def launcher = Stub(JavaLauncher) {
            getMetadata() >> metadata
        }
        def diagnostics = new NativeImageExecutableLocator.Diagnostics()
        def logger = GraalVMLogger.of(Stub(Logger))
        def execOperations = Stub(ExecOperations)

        when:
        NativeImageExecutableLocator.findNativeImageExecutable(
                launcher, true,
                Stub(Provider),
                Stub(Provider),
                execOperations,
                logger,
                diagnostics,
                List.of())

        then:
        def ex = thrown(GradleException)
        ex.message.contains("does not contain the 'native-image' executable")
        ex.message.contains("remove the javaLauncher configuration")
    }

    def "convention launcher without native-image falls back to GRAALVM_HOME for §FS-native-invocation.1.2/1.3"() {
        given:
        def toolchainDir = new File(tempDir, "toolchain-jdk")
        toolchainDir.mkdirs()
        def toolchainNativeImage = new File(toolchainDir, "bin/$NATIVE_IMAGE_EXE")

        def graalvmHome = new File(tempDir, "graalvm")
        new File(graalvmHome, "bin").mkdirs()
        def graalvmNativeImage = new File(graalvmHome, "bin/$NATIVE_IMAGE_EXE")
        graalvmNativeImage.createNewFile()
        assert graalvmNativeImage.exists()

        def metadata = Stub(JavaInstallationMetadata) {
            getInstallationPath() >> Stub(Directory) {
                file(_) >> Stub(RegularFile) {
                    getAsFile() >> toolchainNativeImage
                }
                getAsFile() >> toolchainDir
            }
        }
        def launcher = Stub(JavaLauncher) {
            getMetadata() >> metadata
        }
        def diagnostics = new NativeImageExecutableLocator.Diagnostics()
        def logger = GraalVMLogger.of(Stub(Logger))
        def execOperations = Stub(ExecOperations)
        def graalvmHomeProvider = Stub(Provider) {
            isPresent() >> true
            get() >> graalvmHome.absolutePath
            getOrNull() >> graalvmHome.absolutePath
        }

        when:
        def result = NativeImageExecutableLocator.findNativeImageExecutable(
                launcher, false,
                Stub(Provider),
                graalvmHomeProvider,
                execOperations,
                logger,
                diagnostics,
                List.of())

        then:
        result != null
        result.absolutePath == graalvmNativeImage.absolutePath
    }

    def "convention launcher falls back to alternative GraalVM home when gu install cannot provide native-image for §FS-native-invocation.1.3"() {
        given:
        def toolchainDir = new File(tempDir, "toolchain-jdk")
        toolchainDir.mkdirs()
        def toolchainNativeImage = new File(toolchainDir, "bin/$NATIVE_IMAGE_EXE")

        // Primary candidate (from the graalvmHome provider) has no native-image and no gu.
        def primaryHome = new File(tempDir, "primary-graalvm")
        new File(primaryHome, "bin").mkdirs()

        // Alternative home provided as a fallback candidate contains native-image.
        def altHome = new File(tempDir, "alt-graalvm")
        new File(altHome, "bin").mkdirs()
        def altNativeImage = new File(altHome, "bin/$NATIVE_IMAGE_EXE")
        altNativeImage.createNewFile()
        assert altNativeImage.exists()

        def metadata = Stub(JavaInstallationMetadata) {
            getInstallationPath() >> Stub(Directory) {
                file(_) >> Stub(RegularFile) {
                    getAsFile() >> toolchainNativeImage
                }
                getAsFile() >> toolchainDir
            }
        }
        def launcher = Stub(JavaLauncher) {
            getMetadata() >> metadata
        }
        def diagnostics = new NativeImageExecutableLocator.Diagnostics()
        def logger = GraalVMLogger.of(Stub(Logger))
        def execOperations = Stub(ExecOperations)
        def graalvmHomeProvider = Stub(Provider) {
            isPresent() >> true
            get() >> primaryHome.absolutePath
            getOrNull() >> primaryHome.absolutePath
        }
        def fallbackCandidates = List.of(
                new NativeImageExecutableLocator.VmHomeCandidate(altHome.absolutePath, "GRAALVM_HOME"),
                new NativeImageExecutableLocator.VmHomeCandidate(null, "JAVA_HOME"),
                new NativeImageExecutableLocator.VmHomeCandidate(null, "Gradle JVM (java.home)")
        )

        when:
        def result = NativeImageExecutableLocator.findNativeImageExecutable(
                launcher, false,
                Stub(Provider),
                graalvmHomeProvider,
                execOperations,
                logger,
                diagnostics,
                fallbackCandidates)

        then:
        result != null
        result.absolutePath == altNativeImage.absolutePath
        diagnostics.getEnvVarName() == "GRAALVM_HOME"
    }

    def "failure message notes non-GraalVM JDK only when no environment home is set for §FS-native-invocation.1.5"() {
        given:
        def toolchainDir = new File(tempDir, "toolchain-jdk")
        toolchainDir.mkdirs()
        def toolchainNativeImage = new File(toolchainDir, "bin/$NATIVE_IMAGE_EXE")

        def metadata = Stub(JavaInstallationMetadata) {
            getInstallationPath() >> Stub(Directory) {
                file(_) >> Stub(RegularFile) {
                    getAsFile() >> toolchainNativeImage
                }
                getAsFile() >> toolchainDir
            }
        }
        def launcher = Stub(JavaLauncher) {
            getMetadata() >> metadata
        }
        def diagnostics = new NativeImageExecutableLocator.Diagnostics()
        def logger = GraalVMLogger.of(Stub(Logger))
        def execOperations = Stub(ExecOperations)
        // graalvmHome provider present but points at a home without native-image and without gu.
        def graalvmHome = new File(tempDir, "empty-graalvm")
        new File(graalvmHome, "bin").mkdirs()
        def graalvmHomeProvider = Stub(Provider) {
            isPresent() >> true
            get() >> graalvmHome.absolutePath
            getOrNull() >> graalvmHome.absolutePath
        }
        // All fallback candidates are null, so no alternative GraalVM home is found.
        def fallbackCandidates = List.of(
                new NativeImageExecutableLocator.VmHomeCandidate(null, "GRAALVM_HOME"),
                new NativeImageExecutableLocator.VmHomeCandidate(null, "JAVA_HOME"),
                new NativeImageExecutableLocator.VmHomeCandidate(null, "Gradle JVM (java.home)")
        )

        when:
        NativeImageExecutableLocator.findNativeImageExecutable(
                launcher, false,
                Stub(Provider) { get() >> false },
                graalvmHomeProvider,
                execOperations,
                logger,
                diagnostics,
                fallbackCandidates)

        then:
        def ex = thrown(GradleException)
        // env-var home is set, so the message must not claim a non-GraalVM JDK.
        !ex.message.contains("non-GraalVM JDK")
        ex.message.contains("even after attempting gu install")
    }

    def "Gradle JVM source is recorded in diagnostics when used as last resort for §FS-native-invocation.1.5"() {
        given:
        def diagnostics = new NativeImageExecutableLocator.Diagnostics()
        def gradleJvmHome = Mock(Provider)
        def resolved = Stub(Provider) {
            getOrNull() >> System.getProperty("java.home")
            get() >> System.getProperty("java.home")
        }
        gradleJvmHome.map(_) >> { transformer ->
            def t = (transformer instanceof List) ? transformer[0] : transformer
            t.transform(System.getProperty("java.home"))
            return resolved
        }

        when:
        def provider = diagnostics.fromGradleJvm(gradleJvmHome)
        provider.getOrNull()

        then:
        diagnostics.getEnvVarName() == "Gradle JVM (java.home)"
        provider.getOrNull() == System.getProperty("java.home")
    }
}