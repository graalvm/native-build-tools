package org.graalvm.buildtools.maven

import org.apache.maven.plugin.MojoExecutionException
import org.graalvm.buildtools.model.resources.NativeImageFlags
import spock.lang.Issue
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

    // Maven console mode selects the Native Image version's color argument. §FS-native-builds.9.
    @Issue("https://github.com/graalvm/native-build-tools/issues/366")
    def "uses Maven's #label console color mode with JDK #nativeImageMajorVersion"() {
        given:
        def mojo = newMojo([])
        mojo.imageClasspath.add(testDirectory.resolve("application.jar"))
        mojo.colorEnabled = colorEnabled
        mojo.nativeImageMajorVersion = nativeImageMajorVersion

        when:
        def args = mojo.getBuildArgs()

        then:
        args.contains(expectedColorArgument)

        where:
        label      | colorEnabled | nativeImageMajorVersion | expectedColorArgument
        "disabled" | false        | 17                      | NativeImageFlags.BUILD_OUTPUT_COLORLESS
        "disabled" | false        | 21                      | "--color=never"
        "enabled"  | true         | 17                      | NativeImageFlags.BUILD_OUTPUT_COLORFUL
        "enabled"  | true         | 21                      | "--color=always"
    }

    // Explicit build arguments retain precedence over Maven's detected color mode. §FS-native-builds.9.
    @Issue("https://github.com/graalvm/native-build-tools/issues/366")
    def "places explicit color build arguments after Maven's detected default"() {
        given:
        def mojo = newMojo(["--color=never"])
        mojo.imageClasspath.add(testDirectory.resolve("application.jar"))
        mojo.colorEnabled = true
        mojo.nativeImageMajorVersion = 21

        when:
        def args = mojo.getBuildArgs()

        then:
        args.indexOf("--color=always") < args.lastIndexOf("--color=never")
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
        boolean colorEnabled
        int nativeImageMajorVersion = 25

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

        @Override
        protected boolean isColorEnabled() {
            colorEnabled
        }

        @Override
        protected int getNativeImageMajorVersion() {
            nativeImageMajorVersion
        }
    }
}
