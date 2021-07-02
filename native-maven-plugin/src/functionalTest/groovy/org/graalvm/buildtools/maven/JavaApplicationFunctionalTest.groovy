package org.graalvm.buildtools.maven

class JavaApplicationFunctionalTest extends AbstractGraalVMMavenFunctionalTest {
    def "can build and execute a native image with the Maven plugin"() {
        withSample("java-application")

        when:
        mvn '-Pnative', '-DskipTests', 'package', 'exec:exec@native'

        then:
        buildSucceeded
        outputContains "Hello, native!"
    }
}
