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
}
