package org.graalvm.buildtools.maven

import org.apache.maven.execution.DefaultMavenExecutionRequest
import org.apache.maven.execution.DefaultMavenExecutionResult
import org.apache.maven.execution.MavenSession
import org.apache.maven.plugin.MojoExecutionException
import org.apache.maven.artifact.DefaultArtifact
import org.apache.maven.artifact.handler.DefaultArtifactHandler
import org.apache.maven.project.MavenProject
import org.codehaus.plexus.logging.Logger
import org.eclipse.aether.resolution.ArtifactRequest
import org.graalvm.buildtools.maven.config.UseLayerConfiguration
import org.graalvm.buildtools.maven.config.PreserveConfiguration
import org.graalvm.buildtools.maven.config.PreserveDependencyConfiguration
import org.graalvm.buildtools.model.resources.NativeImageFlags
import org.graalvm.buildtools.utils.NativeImageLayerRuntime
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

    // Maven resolves one dependency closure to a shared path-only Preserve argument. §FS-config-model.8.
    @Issue("https://github.com/graalvm/native-build-tools/issues/978")
    def "renders Preserve dependencies before user build arguments"() {
        given:
        def directDirectory = testDirectory.resolve("direct dependency").toFile()
        def transitiveDirectory = testDirectory.resolve("transitive").toFile()
        directDirectory.mkdirs()
        transitiveDirectory.mkdirs()
        def direct = artifact("com.acme", "extension", "1.0", directDirectory,
                ["org.example:application:jar:1.0", "com.acme:extension:jar:1.0"])
        def transitive = artifact("com.acme", "support", "2.0", transitiveDirectory,
                ["org.example:application:jar:1.0", "com.acme:extension:jar:1.0", "com.acme:support:jar:2.0"])
        def mojo = newMojo(["--user-option"])
        mojo.imageClasspath.add(testDirectory.resolve("application.jar"))
        mojo.project.artifacts = [direct, transitive] as Set
        mojo.preserve = preserve("com.acme:extension")

        when:
        def args = mojo.getBuildArgs()
        def preserveArgument = args.find { it.startsWith(NativeImageFlags.PRESERVE + "=") }

        then:
        !args.contains(NativeImageFlags.UNLOCK_EXPERIMENTAL_VMOPTIONS)
        preserveArgument.contains("path=${directDirectory.absolutePath}")
        preserveArgument.contains("path=${transitiveDirectory.absolutePath}")
        args.indexOf(preserveArgument) < args.indexOf("--user-option")
    }

    def "supports non-transitive Preserve selection"() {
        given:
        def directDirectory = testDirectory.resolve("direct").toFile()
        def transitiveDirectory = testDirectory.resolve("transitive").toFile()
        directDirectory.mkdirs()
        transitiveDirectory.mkdirs()
        def direct = artifact("com.acme", "extension", "1.0", directDirectory,
                ["org.example:application:jar:1.0", "com.acme:extension:jar:1.0"])
        def transitive = artifact("com.acme", "support", "2.0", transitiveDirectory,
                ["org.example:application:jar:1.0", "com.acme:extension:jar:1.0", "com.acme:support:jar:2.0"])
        def mojo = newMojo([])
        mojo.imageClasspath.add(testDirectory.resolve("application.jar"))
        mojo.project.artifacts = [direct, transitive] as Set
        mojo.preserve = preserve("com.acme:extension", false)

        when:
        def argument = mojo.getBuildArgs().find { it.startsWith(NativeImageFlags.PRESERVE + "=") }

        then:
        argument.contains("path=${directDirectory.absolutePath}")
        !argument.contains("path=${transitiveDirectory.absolutePath}")
    }

    def "keeps version-qualified Preserve closure order and de-duplicates overlapping selectors"() {
        given:
        def directDirectory = testDirectory.resolve("extension-1").toFile()
        def transitiveDirectory = testDirectory.resolve("support").toFile()
        def otherVersionDirectory = testDirectory.resolve("extension-2").toFile()
        [directDirectory, transitiveDirectory, otherVersionDirectory]*.mkdirs()
        def direct = artifact("com.acme", "extension", "1.0", directDirectory,
                ["org.example:application:jar:1.0", "com.acme:extension:jar:1.0"])
        def transitive = artifact("com.acme", "support", "2.0", transitiveDirectory,
                ["org.example:application:jar:1.0", "com.acme:extension:jar:1.0", "com.acme:support:jar:2.0"])
        def otherVersion = artifact("com.acme", "extension", "2.0", otherVersionDirectory,
                ["org.example:application:jar:1.0", "com.acme:extension:jar:2.0"])
        def mojo = newMojo([])
        mojo.imageClasspath.add(testDirectory.resolve("application.jar"))
        mojo.project.artifacts = [direct, transitive, otherVersion] as Set
        mojo.preserve = new PreserveConfiguration(dependencies: [
                new PreserveDependencyConfiguration(artifact: "com.acme:extension:1.0"),
                new PreserveDependencyConfiguration(artifact: "com.acme:support:2.0", transitive: false)
        ])

        when:
        def argument = mojo.getBuildArgs().find { it.startsWith(NativeImageFlags.PRESERVE + "=") }

        then:
        argument.indexOf("path=${directDirectory.absolutePath}") <
                argument.indexOf("path=${transitiveDirectory.absolutePath}")
        argument.count("path=${directDirectory.absolutePath}") == 1
        argument.count("path=${transitiveDirectory.absolutePath}") == 1
        !argument.contains("path=${otherVersionDirectory.absolutePath}")
    }

    def "reports invalid Preserve selection as a Maven execution error"() {
        given:
        def mojo = newMojo([])
        mojo.imageClasspath.add(testDirectory.resolve("application.jar"))
        mojo.preserve = configuration

        when:
        mojo.getBuildArgs()

        then:
        def error = thrown(MojoExecutionException)
        error.message.contains(expected)

        where:
        configuration                       | expected
        new PreserveConfiguration()         | "Preserve has no dependencies"
        preserve(" ")                      | "must not be blank"
        preserve("malformed")              | "groupId:artifactId[:version]"
        preserve("com.acme:missing")        | "was not found"
    }

    def "reports a fileless Preserve root"() {
        given:
        def mojo = newMojo([])
        mojo.imageClasspath.add(testDirectory.resolve("application.jar"))
        mojo.project.artifacts = [artifact("com.acme", "extension", "1.0", null, null)] as Set
        mojo.preserve = preserve("com.acme:extension")

        when:
        mojo.getBuildArgs()

        then:
        def error = thrown(MojoExecutionException)
        error.message.contains("without a resolved file")
    }

    def "reports an ambiguous Preserve root"() {
        given:
        def one = testDirectory.resolve("one").toFile()
        def two = testDirectory.resolve("two").toFile()
        one.mkdirs()
        two.mkdirs()
        def mojo = newMojo([])
        mojo.imageClasspath.add(testDirectory.resolve("application.jar"))
        mojo.project.artifacts = [
                artifact("com.acme", "extension", "1.0", one, null),
                artifact("com.acme", "extension", "2.0", two, null)
        ] as Set
        mojo.preserve = preserve("com.acme:extension")

        when:
        mojo.getBuildArgs()

        then:
        def error = thrown(MojoExecutionException)
        error.message.contains("is ambiguous")
    }

    def "uses reactor classes for a Preserve dependency without a packaged artifact"() {
        given:
        def classesDirectory = testDirectory.resolve("reactor-classes")
        classesDirectory.toFile().mkdirs()
        def selected = artifact("com.acme", "extension", "1.0", null,
                ["org.example:application:jar:1.0", "com.acme:extension:jar:1.0"])
        def reactorProject = new MavenProject()
        reactorProject.groupId = "com.acme"
        reactorProject.artifactId = "extension"
        reactorProject.version = "1.0"
        reactorProject.build.outputDirectory = classesDirectory.toString()
        def mojo = newMojo([])
        mojo.imageClasspath.add(testDirectory.resolve("application.jar"))
        mojo.project.artifacts = [selected] as Set
        mojo.session.allProjects = [reactorProject]
        mojo.preserve = preserve("com.acme:extension")

        when:
        def argument = mojo.getBuildArgs().find { it.startsWith(NativeImageFlags.PRESERVE + "=") }

        then:
        argument.contains("path=${classesDirectory.toAbsolutePath()}")
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
        mojo.project.build.directory = testDirectory.resolve("target").toString()
        def runtimeOutput = testDirectory.resolve("runtime-output")
        runtimeOutput.toFile().mkdirs()
        def runtimeFile = runtimeOutput.resolve("runtime-library.bin")
        runtimeFile.toFile().text = "runtime"
        mojo.layerRuntimeArchive = testDirectory.resolve("runtime.zip")
        NativeImageLayerRuntime.createArchive(runtimeOutput, [runtimeFile], mojo.layerRuntimeArchive)
        def useLayer = new UseLayerConfiguration()
        useLayer.artifact = "com.acme:base-layer"
        mojo.useLayers = [useLayer]

        when:
        def args = mojo.getBuildArgs()

        then:
        args.contains(NativeImageFlags.UNLOCK_EXPERIMENTAL_VMOPTIONS)
        args.contains("${NativeImageFlags.LAYER_USE}=${layerFile.absolutePath}".toString())
        !args.contains(layerFile.absolutePath)
        testDirectory.resolve("target/native/layer-runtime/com.acme/base-layer/1.0")
            .toFile().listFiles().any { it.isDirectory() && it.toPath().resolve("runtime-library.bin").toFile().isFile() }
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
        mojo.session.allProjects = []
        mojo
    }

    private static PreserveConfiguration preserve(String selector, boolean transitive = true) {
        new PreserveConfiguration(dependencies: [
                new PreserveDependencyConfiguration(artifact: selector, transitive: transitive)
        ])
    }

    private static DefaultArtifact artifact(String group, String name, String version, File file, List<String> trail) {
        def artifact = new DefaultArtifact(group, name, version, "runtime", "jar", null,
                new DefaultArtifactHandler("jar"))
        artifact.file = file
        artifact.dependencyTrail = trail
        artifact
    }

    private static class TestNativeImageMojo extends AbstractNativeImageMojo {
        boolean fallbackRemoved
        int nativeImageMajorVersion = 25
        Path layerRuntimeArchive
        PreserveConfiguration preserve

        @Override
        protected void executeInternal() {
        }

        @Override
        protected List<String> getDependencyScopes() {
            Collections.singletonList(org.apache.maven.artifact.Artifact.SCOPE_RUNTIME)
        }

        @Override
        protected PreserveConfiguration preserveConfiguration() {
            preserve
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

        @Override
        protected Path resolveLayerRuntimeArchive(org.apache.maven.artifact.Artifact layerArtifact,
                                                  org.eclipse.aether.artifact.Artifact runtimeArtifact,
                                                  ArtifactRequest request) {
            layerRuntimeArchive ?: super.resolveLayerRuntimeArchive(layerArtifact, runtimeArtifact, request)
        }
    }
}
