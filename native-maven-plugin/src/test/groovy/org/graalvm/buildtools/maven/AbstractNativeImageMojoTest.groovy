package org.graalvm.buildtools.maven

import spock.lang.Specification

class AbstractNativeImageMojoTest extends Specification {

    void "it can process build args"() {
        given:
        def buildArgs = [
                "--exclude-config",
                "\\QC:\\Users\\Lahoucine EL ADDALI\\.m2\\repository\\io\\netty\\netty-transport\\4.1.108.Final\\netty-transport-4.1.108.Final.jar\\E",
                "^/META-INF/native-image/",
                "-cp C:\\Users\\Lahoucine EL ADDALI\\Desktop\\outdir\\target/java-application-with-custom-packaging-0.1.jar",
                "-H:ConfigurationFileDirectories=C:\\Users\\Lahoucine EL ADDALI\\Downloads\\4.5.0.0_kubernetes_kubernetes-demo-java-maven\\api\\target\\native\\generated\\generateResourceConfig"
        ]

        when:
        def processedArgs = AbstractNativeImageMojo.processBuildArgs(buildArgs)

        then:
        processedArgs == [
                "--exclude-config",
                "\\QC:\\Users\\Lahoucine EL ADDALI\\.m2\\repository\\io\\netty\\netty-transport\\4.1.108.Final\\netty-transport-4.1.108.Final.jar\\E",
                "^/META-INF/native-image/",
                "-cp",
                "C:\\Users\\Lahoucine EL ADDALI\\Desktop\\outdir\\target/java-application-with-custom-packaging-0.1.jar",
                "-H:ConfigurationFileDirectories=C:\\Users\\Lahoucine EL ADDALI\\Downloads\\4.5.0.0_kubernetes_kubernetes-demo-java-maven\\api\\target\\native\\generated\\generateResourceConfig"
        ]
    }

    void "platform specific build args - leading"() {
        given:
        def buildArgs = [
                '$linux:--GC=G1',
                '$linux:-R:MaxGCPauseMillis=50',
                '--foo',
                '$macos:--myMacOption'
        ]

        when:
        def processedArgs = AbstractNativeImageMojo.processPlatformBuildArgs('linux', buildArgs)

        then:
        processedArgs == [
                '--GC=G1',
                '-R:MaxGCPauseMillis=50',
                '--foo'
        ]
    }

    void "platform specific build args - trailing"() {
        given:
        def buildArgs = [
                '$linux:--GC=G1',
                '$linux:-R:MaxGCPauseMillis=50',
                '--foo',
                '$mac:--myMacOption'
        ]

        when:
        def processedArgs = AbstractNativeImageMojo.processPlatformBuildArgs('mac', buildArgs)

        then:
        processedArgs == [
                '--foo',
                '--myMacOption'
        ]
    }

    void "normalizeOs"() {
        when:
        def resultOs = AbstractNativeImageMojo.normalizeOs(inputOs)

        then:
        resultOs == expectedOs

        where:
        inputOs                 || expectedOs
        'Some@Unknown'          || 'someunknown'
        'Some@_|Unknown019'     || 'someunknown019'
        'mac'                   || 'mac'
        'MacOs'                 || 'mac'
        'linux'                 || 'linux'
        'LinuxAnyFoo'           || 'linux'
        'windows'               || 'windows'
        'WindowsAnyFoo'         || 'windows'
    }
}
