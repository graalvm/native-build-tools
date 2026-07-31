package org.graalvm.buildtools.maven

import org.apache.maven.execution.DefaultMavenExecutionRequest
import org.apache.maven.execution.DefaultMavenExecutionResult
import org.apache.maven.execution.MavenSession
import org.apache.maven.plugin.MojoExecutionException
import org.apache.maven.artifact.DefaultArtifact
import org.apache.maven.artifact.handler.DefaultArtifactHandler
import org.apache.maven.project.MavenProject
import org.codehaus.plexus.logging.Logger
import org.graalvm.buildtools.maven.config.UseLayerConfiguration
import org.graalvm.buildtools.model.resources.NativeImageFlags
import spock.lang.Issue
import spock.lang.Specification
import spock.lang.TempDir

import java.nio.file.Path

// Protects Maven native-image argument handling, layer resolution, and classpath requirements.
// §FS-native-builds.3 §FS-config-model.1 §FS-config-model.7.
class AbstractNativeImageMojoTest extends Specification {
    @TempDir
    Path testDirectory

    // Protects the deprecated Maven fallback parameter surface. §FS-config-model.1.
    @Issue("https://github.com/graalvm/native-build-tools/issues/991")
    def "marks the fallback parameter as deprecated"() {
        expect:
        AbstractNativeImageMojo.getDeclaredField("fallback").isAnnotationPresent(Deprecated)
    }

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

    // An explicit Maven style selects the Native Image version's color argument. §FS-native-builds.9.
    @Issue(["https://github.com/graalvm/native-build-tools/issues/366",
            "https://github.com/graalvm/native-build-tools/issues/1000"])
    def "uses Maven's explicit #styleColor style with JDK #nativeImageMajorVersion"() {
        given:
        def mojo = newMojo([])
        mojo.imageClasspath.add(testDirectory.resolve("application.jar"))
        mojo.session.userProperties.setProperty("style.color", styleColor)
        mojo.nativeImageMajorVersion = nativeImageMajorVersion

        when:
        def args = mojo.getBuildArgs()

        then:
        args.contains(expectedColorArgument)

        where:
        styleColor | nativeImageMajorVersion | expectedColorArgument
        "never"    | 17                      | NativeImageFlags.BUILD_OUTPUT_COLORLESS
        "never"    | 21                      | "--color=never"
        "always"   | 17                      | NativeImageFlags.BUILD_OUTPUT_COLORFUL
        "always"   | 21                      | "--color=always"
        "none"     | 21                      | "--color=never"
        "force"    | 21                      | "--color=always"
    }

    // Maven batch mode disables Native Image colors even when no color property is present. §FS-native-builds.9.
    def "disables colors for Maven batch mode"() {
        given:
        def mojo = newMojo([])
        mojo.imageClasspath.add(testDirectory.resolve("application.jar"))
        mojo.session.request.interactiveMode = false
        mojo.nativeImageMajorVersion = 21

        expect:
        mojo.getBuildArgs().contains("--color=never")
    }

    // Unresolved color selection remains Native Image's responsibility without Maven implementation classes. §FS-native-builds.9.
    @Issue("https://github.com/graalvm/native-build-tools/issues/1000")
    def "omits a color argument when Maven color mode is #styleColor"() {
        given:
        def mojo = newMojo([])
        mojo.imageClasspath.add(testDirectory.resolve("application.jar"))
        if (styleColor != null) {
            mojo.session.userProperties.setProperty("style.color", styleColor)
        }

        when:
        def args = mojo.getBuildArgs()

        then:
        !args.any {
            it.startsWith(NativeImageFlags.COLOR) ||
                    it == NativeImageFlags.BUILD_OUTPUT_COLORFUL ||
                    it == NativeImageFlags.BUILD_OUTPUT_COLORLESS
        }

        where:
        styleColor << [null, "auto"]
    }

    // Explicit build arguments retain precedence over Maven's explicit color mode. §FS-native-builds.9.
    @Issue("https://github.com/graalvm/native-build-tools/issues/366")
    def "places explicit color build arguments after Maven's explicit selection"() {
        given:
        def mojo = newMojo(["--color=never"])
        mojo.imageClasspath.add(testDirectory.resolve("application.jar"))
        mojo.session.userProperties.setProperty("style.color", "always")
        mojo.nativeImageMajorVersion = 21

        when:
        def args = mojo.getBuildArgs()

        then:
        args.indexOf("--color=always") < args.lastIndexOf("--color=never")
    }

    // GraalVM 25.1 removes only the plugin-generated compatibility flag. §FS-config-model.1.
    @Issue("https://github.com/graalvm/native-build-tools/issues/991")
    def "#label the generated no-fallback argument"() {
        given:
        def mojo = newMojo([])
        mojo.imageClasspath.add(testDirectory.resolve("application.jar"))
        mojo.fallbackRemoved = fallbackRemoved

        when:
        def args = mojo.getBuildArgs()

        then:
        args.contains(NativeImageFlags.NO_FALLBACK) == expected

        where:
        label        | fallbackRemoved | expected
        "retains"    | false           | true
        "suppresses" | true            | false
    }

    def "retains an explicit no-fallback argument after fallback removal"() {
        given:
        def mojo = newMojo([NativeImageFlags.NO_FALLBACK])
        mojo.imageClasspath.add(testDirectory.resolve("application.jar"))
        mojo.fallbackRemoved = true

        when:
        def args = mojo.getBuildArgs()

        then:
        args.count { it == NativeImageFlags.NO_FALLBACK } == 1
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

    void "it resolves configured nil dependencies outside the Java classpath"() {
        given:
        def layerFile = testDirectory.resolve("base.nil").toFile()
        layerFile.text = "layer"
        def artifact = new DefaultArtifact(
                "com.acme", "base-layer", "1.0", "runtime", "nil", null,
                new DefaultArtifactHandler("nil"))
        artifact.file = layerFile
        def mojo = newMojo([])
        mojo.imageClasspath.add(testDirectory.resolve("application.jar"))
        mojo.project = new MavenProject()
        mojo.project.artifacts = [artifact] as Set
        def useLayer = new UseLayerConfiguration()
        useLayer.artifact = "com.acme:base-layer"
        mojo.useLayers = [useLayer]

        when:
        def args = mojo.getBuildArgs()

        then:
        args.contains(NativeImageFlags.UNLOCK_EXPERIMENTAL_VMOPTIONS)
        args.contains("${NativeImageFlags.LAYER_USE}=${layerFile.absolutePath}".toString())
        !args.contains(layerFile.absolutePath)
    }

    void "it reports malformed layer coordinate '#coordinate' as a Maven execution error"() {
        when:
        AbstractNativeImageMojo.parseLayerCoordinate(coordinate)

        then:
        def e = thrown(MojoExecutionException)
        e.message.contains("groupId:artifactId[:version]")

        where:
        coordinate << ["base", ":base", "com.acme:", "com:acme:base:1.0"]
    }

    void "it explains how to declare a missing nil layer dependency"() {
        given:
        def mojo = newMojo([])
        mojo.imageClasspath.add(testDirectory.resolve("application.jar"))
        mojo.project = new MavenProject()
        mojo.project.artifacts = [] as Set
        def useLayer = new UseLayerConfiguration()
        useLayer.artifact = "com.acme:base-layer"
        mojo.useLayers = [useLayer]

        when:
        mojo.getBuildArgs()

        then:
        def e = thrown(MojoExecutionException)
        e.message.contains("<type>nil</type>")
    }

    void "it warns about nil dependencies not selected by useLayers"() {
        given:
        def layerFile = testDirectory.resolve("base.nil").toFile()
        layerFile.text = "layer"
        def artifact = new DefaultArtifact(
            "com.acme", "base-layer", "1.0", "runtime", "nil", null,
            new DefaultArtifactHandler("nil"))
        artifact.file = layerFile
        def logger = Mock(Logger)
        def mojo = newMojo([])
        mojo.logger = logger
        mojo.imageClasspath.add(testDirectory.resolve("application.jar"))
        mojo.project = new MavenProject()
        mojo.project.artifacts = [artifact] as Set

        when:
        mojo.getBuildArgs()

        then:
        1 * logger.warn({ it.contains("has type nil but is not referenced by useLayers") })
    }

    // The Maven adapter rejects the Native Image release with a known layer-consumption crash. §FS-native-builds.3.
    void "it rejects layer consumption on Native Image 25.0.3"() {
        when:
        AbstractNativeImageMojo.checkLayerConsumptionCompatibility(
                "native-image 25.0.3 2026-04-21\nGraalVM Runtime Environment Oracle GraalVM 25.0.3+9.1")

        then:
        def e = thrown(MojoExecutionException)
        e.message.contains("Native Image 25.0.3 does not support reliable layer consumption")
        e.message.contains("Upgrade Native Image")
        e.message.contains("remove the useLayers configuration")
    }

    void "it permits layer consumption on other Native Image versions"() {
        expect:
        AbstractNativeImageMojo.checkLayerConsumptionCompatibility(versionInformation)

        where:
        versionInformation << [
                "native-image 25 2025-09-16",
                "native-image 25.0.2 2026-01-20",
                "native-image 25.0.4 2026-07-21",
                "native-image 26-dev 2026-07-30"
        ]
    }

    private TestNativeImageMojo newMojo(List<String> buildArgs) {
        def mojo = new TestNativeImageMojo()
        mojo.outputDirectory = testDirectory.resolve("target").toFile()
        mojo.resourcesConfigDirectory = testDirectory.resolve("target/native/generated").toFile()
        mojo.imageName = "libbase"
        mojo.buildArgs = buildArgs
        mojo.configFiles = []
        mojo.useArgFile = false
        mojo.logger = Mock(Logger)
        mojo.project = new MavenProject()
        mojo.project.artifacts = [] as Set
        def userProperties = new Properties()
        def systemProperties = new Properties()
        def request = new DefaultMavenExecutionRequest()
                .setUserProperties(userProperties)
                .setSystemProperties(systemProperties)
                .setInteractiveMode(true)
        mojo.session = new MavenSession(null, null, request, new DefaultMavenExecutionResult())
        mojo
    }

    private static class TestNativeImageMojo extends AbstractNativeImageMojo {
        boolean fallbackRemoved
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
        protected int getNativeImageMajorVersion() {
            nativeImageMajorVersion
        }

        @Override
        protected boolean isFallbackRemoved() {
            fallbackRemoved
        }
    }
}
