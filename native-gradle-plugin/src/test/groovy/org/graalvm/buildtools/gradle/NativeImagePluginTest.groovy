package org.graalvm.buildtools.gradle

import org.graalvm.buildtools.gradle.dsl.GraalVMExtension
import org.graalvm.buildtools.gradle.dsl.GraalVMReachabilityMetadataRepositoryExtension
import org.graalvm.buildtools.gradle.dsl.NativeImageCompileOptions
import org.graalvm.buildtools.gradle.tasks.BuildNativeImageTask
import org.graalvm.buildtools.gradle.tasks.ValidateNativeImageLayerSelectionTask
import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.testfixtures.ProjectBuilder
import spock.lang.Issue
import spock.lang.Specification

import java.util.regex.Pattern

import static org.graalvm.buildtools.VersionInfo.METADATA_REPO_VERSION

// Verifies the durable Gradle extension and task model. §FS-plugin-model.2 §FS-native-tasks.1.
class NativeImagePluginTest extends Specification {

    private static final String DEFAULT_GITHUB_RELEASES_METADATA_URI = "https://github.com/oracle/graalvm-reachability-metadata/releases/download/${METADATA_REPO_VERSION}/graalvm-reachability-metadata-${METADATA_REPO_VERSION}.zip"

    private Project project
    private GraalVMReachabilityMetadataRepositoryExtension reachabilityMetadataRepositoryExtension


    private URI resultUri
    private URI fallbackUri

    def setup() {
        project = ProjectBuilder.builder()
                .build()
        project.plugins.apply(NativeImagePlugin)
        reachabilityMetadataRepositoryExtension = project.extensions
                .findByType(GraalVMExtension)
                .extensions
                .findByType(GraalVMReachabilityMetadataRepositoryExtension)
    }

    // Protects the deprecated Gradle fallback DSL surface. §FS-native-tasks.4.
    @Issue("https://github.com/graalvm/native-build-tools/issues/991")
    def "marks the fallback DSL option as deprecated"() {
        expect:
        NativeImageCompileOptions.getMethod("getFallback").isAnnotationPresent(Deprecated)
    }

    @Issue("https://github.com/graalvm/native-build-tools/issues/424")
    // Protects default and pinned Gradle metadata repository selection. §FS-resources-and-metadata.3.
    def "can set the version of the repository"() {
        when:
        repositoryUriFor(configuredUri, version)

        then:
        resultUri == new URI(expectedUri)
        fallbackUri == (expectedFallbackUri == null ? null : new URI(expectedFallbackUri))

        where:
        configuredUri                        | version               | expectedUri                                                                                                           | expectedFallbackUri
        null                                 | null                  | "https://lookup.on.maven.central"                                                                                     | DEFAULT_GITHUB_RELEASES_METADATA_URI
        DEFAULT_GITHUB_RELEASES_METADATA_URI | null                  | "https://lookup.on.maven.central"                                                                                     | DEFAULT_GITHUB_RELEASES_METADATA_URI
        "https://custom.uri"                 | null                  | 'https://custom.uri'                                                                                                  | null
        null                                 | '155'                 | 'https://github.com/oracle/graalvm-reachability-metadata/releases/download/155/graalvm-reachability-metadata-155.zip' | null
        null                                 | METADATA_REPO_VERSION | 'https://lookup.on.maven.central'                                                                                     | DEFAULT_GITHUB_RELEASES_METADATA_URI
        "https://custom.uri"                 | 'ignored'             | 'https://custom.uri'                                                                                                  | null
    }

    // Protects Gradle's version-versus-source repository description. §FS-resources-and-metadata.3.
    def "describes the selected metadata repository"() {
        expect:
        NativeImagePlugin.describeSelectedMetadataRepository(uri, version) == description

        where:
        uri                                                                                                                | version               | description
        new URI(DEFAULT_GITHUB_RELEASES_METADATA_URI)                                                                      | METADATA_REPO_VERSION | "version ${METADATA_REPO_VERSION}"
        new URI('https://github.com/oracle/graalvm-reachability-metadata/releases/download/155/graalvm-reachability-metadata-155.zip') | '155'                 | 'version 155'
        new URI('https://custom.uri/repository.zip')                                                                       | METADATA_REPO_VERSION | 'from https://custom.uri/repository.zip'
        new URI('file:/tmp/repository.zip')                                                                                | METADATA_REPO_VERSION | 'from file:/tmp/repository.zip'
        new URI('https://custom.uri/repository.zip')                                                                       | null                  | 'from https://custom.uri/repository.zip'
    }

    def "registers descriptions for user-facing tasks"() {
        when:
        project.plugins.apply("java")

        then:
        taskDescription("nativeCompile") == "Builds a native executable for the main binary."
        taskDescription("nativeRun") == "Runs the main native binary."
        taskDescription("nativeBuild") == "Deprecated alias for nativeCompile."
        taskDescription("metadataCopy") == "Copies and optionally merges metadata collected by agent-instrumented tasks into target directories."
        taskDescription("collectReachabilityMetadata") == "Collects reachability metadata for the runtime classpath."
        taskDescription("nativeCompileClasspathJar") == "Builds a pathing JAR for the main native binary classpath."
        taskDescription("generateResourcesConfigFile") == "Scans resources and generates a resource-config.json file for the main binary."
        taskDescription("nativeTestCompile") == "Builds a native executable for the test binary."
        taskDescription("nativeTest") == "Runs the test native binary."
        taskDescription("nativeTestBuild") == "Deprecated alias for nativeTestCompile."
        taskDescription("generateTestResourcesConfigFile") == "Scans resources and generates a resource-config.json file for the test binary."
    }

    def "named layers own dedicated tasks and can be assigned to binaries"() {
        given:
        project.plugins.apply("java")
        def extension = project.extensions.getByType(GraalVMExtension)

        when:
        def layer = extension.layers.create("dependencies")
        layer.contents.modules("java.base")
        extension.binaries.main.layer = layer
        def task = project.tasks.getByName("nativeDependenciesLayer") as BuildNativeImageTask

        then:
        task.description == "Builds the dependencies Native Image layer."
        task.options.get().layerCreate.get().layerName.get() == "dependencies"
        task.options.get().layerCreate.get().modules.get() == ["java.base"]
        task.outputDirectory.get().asFile == project.layout.buildDirectory.dir("native/layers/dependencies").get().asFile
        extension.binaries.main.layerFiles.files == [layer.outputFile.get().asFile] as Set
        extension.binaries.main.layer == layer
    }

    def "singular layer assignment replaces its previous value"() {
        given:
        project.plugins.apply("java")
        def extension = project.extensions.getByType(GraalVMExtension)
        def first = extension.layers.create("first")
        first.contents.modules("java.base")
        def second = extension.layers.create("second")
        second.contents.modules("java.logging")

        when:
        extension.binaries.main.layer = first
        extension.binaries.main.layer = second

        then:
        extension.binaries.main.layer == second
        extension.binaries.main.layerNames.get() == ["second"]
        extension.binaries.main.layerFiles.files == [second.outputFile.get().asFile] as Set
    }

    def "name based layer selection is independent of declaration order"() {
        given:
        project.plugins.apply("java")
        def extension = project.extensions.getByType(GraalVMExtension)

        when:
        extension.binaries.main.usesLayer("dependencies")
        def layer = extension.layers.create("dependencies")
        layer.contents.modules("java.base")

        then:
        extension.binaries.main.layerNames.get() == ["dependencies"]
        extension.binaries.main.layerFiles.files == [layer.outputFile.get().asFile] as Set
    }

    def "named layers can consume other named layers independent of declaration order"() {
        given:
        project.plugins.apply("java")
        def extension = project.extensions.getByType(GraalVMExtension)

        when:
        def framework = extension.layers.create("framework")
        framework.usesLayer("base")
        framework.contents.packages("com.example.framework")
        def base = extension.layers.create("base")
        base.contents.modules("java.base")

        then:
        framework.useLayerNames.get() == ["base"]
        framework.useLayerFiles.files == [base.outputFile.get().asFile] as Set

        and:
        def frameworkTask = project.tasks.named("nativeFrameworkLayer").get()
        frameworkTask.options.get().layerNames.get() == ["base"]
        frameworkTask.options.get().layerFiles.files == [base.outputFile.get().asFile] as Set
    }

    def "duplicate layer chaining fails during configuration"() {
        given:
        project.plugins.apply("java")
        def extension = project.extensions.getByType(GraalVMExtension)
        def framework = extension.layers.create("framework")

        when:
        framework.usesLayer("base")
        framework.usesLayer("base")

        then:
        def e = thrown(IllegalArgumentException)
        e.message.contains("selected more than once")
    }

    def "invalid layer names fail when declared"() {
        given:
        project.plugins.apply("java")
        def extension = project.extensions.getByType(GraalVMExtension)

        when:
        extension.layers.create("my,base layer")

        then:
        def e = thrown(IllegalArgumentException)
        e.message.contains("letters, digits, dots, underscores, or hyphens")
    }

    def "provider layer selections preserve names for duplicate validation"() {
        given:
        project.plugins.apply("java")
        def extension = project.extensions.getByType(GraalVMExtension)
        def layer = extension.layers.create("dependencies")
        layer.contents.modules("java.base")

        when:
        extension.binaries.main.useLayer(layer)
        extension.binaries.main.useLayer(project.provider { layer })

        then:
        extension.binaries.main.layerNames.get() == ["dependencies", "dependencies"]
    }

    def "provider layer selections use a pre-producer validation task"() {
        given:
        project.plugins.apply("java")
        def extension = project.extensions.getByType(GraalVMExtension)
        def layer = extension.layers.create("dependencies")
        layer.contents.modules("java.base")

        when:
        extension.binaries.main.useLayer(layer)
        extension.binaries.main.useLayer(project.provider { layer })
        def validation = project.tasks.getByName("validateNativeCompileLayerSelection") as ValidateNativeImageLayerSelectionTask

        then:
        validation.binaryName.get() == "main"
        validation.layerNames.get() == ["dependencies", "dependencies"]
        project.tasks.getByName("nativeCompile").taskDependencies.getDependencies(null).contains(validation)
    }

    def "layer configurations include project dependency artifacts"() {
        given:
        def root = ProjectBuilder.builder().withName("root").build()
        def producer = ProjectBuilder.builder().withName("producer").withParent(root).build()
        def consumer = ProjectBuilder.builder().withName("consumer").withParent(root).build()
        producer.plugins.apply("java-library")
        consumer.plugins.apply("java")
        consumer.plugins.apply(NativeImagePlugin)
        consumer.dependencies.add("implementation", consumer.dependencies.project(path: ":producer"))
        def layer = consumer.extensions.getByType(GraalVMExtension).layers.create("dependencies")

        when:
        layer.contents.fromConfiguration(consumer.configurations.runtimeClasspath)

        then:
        layer.contents.files.buildDependencies.getDependencies(null).contains(producer.tasks.named("jar").get())
    }

    // Protects custom binary classpath wiring and exclusion args. §FS-plugin-model.4
    // §FS-native-tasks.1 §FS-resources-and-metadata.6
    @Issue("https://github.com/graalvm/native-build-tools/issues/478")
    def "custom application binaries use native image classpath configuration"() {
        given:
        project.plugins.apply("java")
        def extension = project.extensions.getByType(GraalVMExtension)
        def testJar = project.file("test.jar")

        when:
        def qa = extension.binaries.create("qa")
        def classpathConfiguration = project.configurations.getByName("nativeImageQaClasspath")
        qa.excludeConfig.put(testJar, ["META-INF/*"])

        then:
        taskDescription("nativeQaCompile") == "Builds a native executable for the qa binary."
        taskDescription("nativeQaRun") == "Runs the qa native binary."
        qa.classpath.files.containsAll(classpathConfiguration.files)
        qa.excludeConfigArgs.get() == [
                "--exclude-config",
                Pattern.quote(testJar.toPath().toAbsolutePath().toString()),
                "META-INF/*"
        ]
    }

    private String taskDescription(String name) {
        Task task = project.tasks.getByName(name)
        assert task.description != null
        task.description
    }

    private void repositoryUriFor(String configuredUri, String version) {
        if (configuredUri != null) {
            reachabilityMetadataRepositoryExtension.uri.set(new URI(configuredUri))
        }
        if (version != null) {
            reachabilityMetadataRepositoryExtension.version.set(version)
        }
        fallbackUri = null
        resultUri = NativeImagePlugin.computeMetadataRepositoryUri(project, reachabilityMetadataRepositoryExtension) {
            fallbackUri = it
        }
        if (fallbackUri != null) {
            // if we have a fallback uri, then it means we tried to look on Maven Central
            resultUri = new URI("https://lookup.on.maven.central")
        }
    }
}
