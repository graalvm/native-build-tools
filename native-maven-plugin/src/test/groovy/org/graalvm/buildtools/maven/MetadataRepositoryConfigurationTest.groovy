package org.graalvm.buildtools.maven

import org.apache.maven.artifact.DefaultArtifact
import org.graalvm.buildtools.maven.config.MetadataRepositoryConfiguration
import spock.lang.Specification

class MetadataRepositoryConfigurationTest extends Specification {

    void "it checks whether a dependency is excluded"(String artifactId, boolean expectedExcluded) {
        given:
        MetadataRepositoryConfiguration config = new MetadataRepositoryConfiguration()
        config.enabled = true
        config.dependencies = [
                new MetadataRepositoryConfiguration.DependencyConfiguration("my-group", "my-artifact-included", false),
                new MetadataRepositoryConfiguration.DependencyConfiguration("my-group", "my-artifact-excluded", true)
        ]

        when:
        boolean isExcluded = config.isArtifactExcluded(new DefaultArtifact("my-group", artifactId, "1.0.0", "compile", "jar", "jar", null))

        then:
        isExcluded == expectedExcluded

        where:
        artifactId              || expectedExcluded
        "my-artifact-included"  || false
        "my-artifact-excluded"  || true
    }

    void "it returns the metadata version for a dependency"(String artifactId, Optional<String> expectedVersion) {
        given:
        MetadataRepositoryConfiguration config = new MetadataRepositoryConfiguration()
        config.enabled = true
        config.dependencies = [
                new MetadataRepositoryConfiguration.DependencyConfiguration("my-group", "my-artifact-included", false),
                new MetadataRepositoryConfiguration.DependencyConfiguration("my-group", "my-artifact-excluded", "2")
        ]

        when:
        Optional<String> metadataVersion = config.getMetadataVersion(new DefaultArtifact("my-group", artifactId, "1.0.0", "compile", "jar", "jar", null))

        then:
        metadataVersion == expectedVersion

        where:
        artifactId              || expectedVersion
        "my-artifact-included"  || Optional.empty()
        "my-artifact-excluded"  || Optional.of("2")
    }
}
