package org.graalvm.buildtools.gradle

import org.graalvm.buildtools.gradle.dsl.GraalVMExtension
import org.graalvm.buildtools.gradle.dsl.GraalVMReachabilityMetadataRepositoryExtension
import org.gradle.api.Project
import org.gradle.testfixtures.ProjectBuilder
import spock.lang.Issue
import spock.lang.Specification

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
