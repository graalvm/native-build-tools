package org.graalvm.buildtools.maven

import org.apache.maven.plugin.MojoExecutionException
import spock.lang.Specification
import spock.lang.TempDir

import java.nio.file.Path

// Protects Maven native-image argument handling and classpath requirements. §FS-native-builds.3 §FS-config-model.1.
class AbstractNativeImageMojoTest extends Specification {
    @TempDir
    Path testDirectory

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

    void "it allows empty classpath for layer-create builds"() {
        given:
        def mojo = newMojo([layerCreateArg])

        when:
        def args = mojo.getBuildArgs()

        then:
        !args.contains("-cp")
        args.contains(layerCreateArg)

        where:
        layerCreateArg << [
                "-H:LayerCreate=libbase.nil,module=java.base",
                "-H:LayerCreate@user=libbase.nil,module=java.base"
        ]
    }

    void "it still rejects empty classpath for regular builds"() {
        given:
        def mojo = newMojo([])

        when:
        mojo.getBuildArgs()

        then:
        def e = thrown(MojoExecutionException)
        e.message.contains("Image classpath is empty")
    }

    private TestNativeImageMojo newMojo(List<String> buildArgs) {
        def mojo = new TestNativeImageMojo()
        mojo.outputDirectory = testDirectory.resolve("target").toFile()
        mojo.resourcesConfigDirectory = testDirectory.resolve("target/native/generated").toFile()
        mojo.imageName = "libbase"
        mojo.buildArgs = buildArgs
        mojo.configFiles = []
        mojo.useArgFile = false
        mojo
    }

    private static class TestNativeImageMojo extends AbstractNativeImageMojo {
        @Override
        protected void executeInternal() {
        }

        @Override
        protected List<String> getDependencyScopes() {
            Collections.emptyList()
        }

        @Override
        protected void populateClasspath() {
        }
    }
}
