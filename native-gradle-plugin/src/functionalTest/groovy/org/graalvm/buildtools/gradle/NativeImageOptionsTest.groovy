package org.graalvm.buildtools.gradle


import org.gradle.testkit.runner.GradleRunner
import spock.lang.Issue
import spock.lang.Specification
import spock.lang.TempDir

import java.nio.file.Path

class NativeImageOptionsTest extends Specification {
    @TempDir
    Path testDirectory

    @Issue("https://github.com/graalvm/native-build-tools/issues/109")
    def "toolchain defaults to the current Java version"() {
        when:
        def runner = GradleRunner.create()
                .forwardStdOutput(new PrintWriter(System.out))
                .forwardStdError(new PrintWriter(System.err))
                .withPluginClasspath()
                .withProjectDir(testDirectory.toFile())


        def buildFile = testDirectory.resolve("build.gradle")
        buildFile.text = """
            plugins {
                id 'java'
                id 'org.graalvm.buildtools.native'
            }
            
            graalvmNative.toolchainDetection = true
            
            assert graalvmNative.binaries.main.javaLauncher
                .get()
                .metadata
                .languageVersion
                .toString() == JavaVersion.current().majorVersion
        """

        runner.build()

        then:
        noExceptionThrown()
    }
}
