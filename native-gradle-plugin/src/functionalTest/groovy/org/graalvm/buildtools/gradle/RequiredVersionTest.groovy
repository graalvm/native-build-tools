package org.graalvm.buildtools.gradle;

import org.graalvm.buildtools.gradle.fixtures.AbstractFunctionalTest;

class RequiredVersionTest extends AbstractFunctionalTest {

    def "can build a native image with a valid required version"() {
        def nativeApp = file("build/native/nativeCompile/java-application")

        given:
        withSample("java-application")

        buildFile << """
           graalvmNative.binaries.all {
                requiredVersion = '22.2'
            }
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
    }

    def "can't build a native image with an invalid required version"() {
        given:
        withSample("java-application")

        buildFile << """
           graalvmNative.binaries.all {
                requiredVersion = '100'
            }
        """.stripIndent()

        when:
        fails 'nativeCompile'

        then:
        errorOutputContains "GraalVM version 100 is required but 22 has been detected, please upgrade."
    }
}
