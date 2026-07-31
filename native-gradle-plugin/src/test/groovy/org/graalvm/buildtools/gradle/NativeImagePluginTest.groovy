package org.graalvm.buildtools.gradle

import org.graalvm.buildtools.gradle.dsl.GraalVMExtension
import org.graalvm.buildtools.gradle.dsl.GraalVMReachabilityMetadataRepositoryExtension
import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.testfixtures.ProjectBuilder
import spock.lang.Issue
import spock.lang.Specification

import java.util.regex.Pattern

import static org.graalvm.buildtools.VersionInfo.METADATA_REPO_VERSION

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

    // §FS-native-invocation.1.2 — the plugin installs the javaLauncher convention on the public
    // binary property; provenance distinguishes convention-sourced from user-explicit launchers.
    def "binary javaLauncher resolves from the toolchain convention and is not explicit"() {
        given:
        project.plugins.apply("java")
        def extension = project.extensions.getByType(GraalVMExtension)
        def main = extension.binaries.getByName("main")
        extension.toolchainDetection.set(true)

        expect:
        // The restored convention supplies a launcher...
        main.javaLauncher.isPresent()
        // ...but the value came from the convention, not from an explicit assignment.
        !main.javaLauncherExplicit.get()
    }

    // §FS-native-invocation.1.2 — a user-assigned launcher is explicit even when it equals the
    // convention value: provenance tracks the assignment, not the resolved launcher.
    def "explicit javaLauncher set by user is reported explicit"() {
        given:
        project.plugins.apply("java")
        def extension = project.extensions.getByType(GraalVMExtension)
        def main = extension.binaries.getByName("main")
        extension.toolchainDetection.set(true)

        when:
        // Same launcher the convention would supply, but assigned directly by the user.
        main.javaLauncher.set(extension.defaultJavaLauncher.get())

        then:
        main.javaLauncherExplicit.get()
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
