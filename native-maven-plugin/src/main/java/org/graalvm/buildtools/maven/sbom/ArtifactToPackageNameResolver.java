/*
 * Copyright (c) 2024, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * The Universal Permissive License (UPL), Version 1.0
 *
 * Subject to the condition set forth below, permission is hereby granted to any
 * person obtaining a copy of this software, associated documentation and/or
 * data (collectively the "Software"), free of charge and under any and all
 * copyright rights in the Software, and any and all patent rights owned or
 * freely licensable by each licensor hereunder covering either (i) the
 * unmodified Software as contributed to or provided by such licensor, or (ii)
 * the Larger Works (as defined below), to deal in both
 *
 * (a) the Software, and
 *
 * (b) any piece of software and/or hardware listed in the lrgrwrks.txt file if
 * one is included with the Software each a "Larger Work" to which the Software
 * is contributed by such licensors),
 *
 * without restriction, including without limitation the rights to copy, create
 * derivative works of, display, perform, and distribute the Software and make,
 * use, sell, offer for sale, import, export, have made, and have sold the
 * Software and the Larger Work(s), and to sublicense the foregoing rights on
 * either these or other terms.
 *
 * This license is subject to the following condition:
 *
 * The above copyright notice and either this complete permission notice or at a
 * minimum a reference to the UPL must be included in all copies or substantial
 * portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */
package org.graalvm.buildtools.maven.sbom;

import org.apache.maven.artifact.Artifact;
import org.apache.maven.project.MavenProject;
import org.eclipse.aether.RepositorySystem;
import org.eclipse.aether.RepositorySystemSession;
import org.eclipse.aether.artifact.DefaultArtifact;
import org.eclipse.aether.repository.RemoteRepository;
import org.eclipse.aether.resolution.ArtifactRequest;
import org.eclipse.aether.resolution.ArtifactResolutionException;
import org.eclipse.aether.resolution.ArtifactResult;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

final class ArtifactToPackageNameResolver {
    private final MavenProject mavenProject;
    private final RepositorySystem repositorySystem;
    private final RepositorySystemSession repositorySystemSession;
    private final List<RemoteRepository> remoteRepositories;
    private final ArtifactAdapterResolver shadedPackageNameResolver;

    ArtifactToPackageNameResolver(MavenProject mavenProject, RepositorySystem repositorySystem, RepositorySystemSession repositorySystemSession, String mainClass) {
        this.mavenProject = mavenProject;
        this.repositorySystem = repositorySystem;
        this.repositorySystemSession = repositorySystemSession;
        this.remoteRepositories = mavenProject.getRemoteProjectRepositories();
        this.shadedPackageNameResolver = new ArtifactAdapterResolver(mavenProject, mainClass);
    }

    /**
     * Maps the artifacts of the maven project to {@link ArtifactAdapter}s. {@link ArtifactAdapter#packageNames} will
     * be non-empty if package names could accurately be derived for an artifact. If not, it will be non-empty and
     * {@link ArtifactAdapter#prunable} will be set to false. {@link ArtifactAdapter#prunable} will also be set to
     * false if an artifact is not the main artifact and its part of a shaded jar.
     *
     * @return the artifacts of this project as {@link ArtifactAdapter}s.
     * @throws Exception if an error was encountered when deriving the artifacts.
     */
    Set<ArtifactAdapter> getArtifactAdapters() throws Exception {
        Set<ArtifactAdapter> artifactsWithPackageNameMappings = new HashSet<>();
        List<Artifact> artifacts = new ArrayList<>(mavenProject.getArtifacts());
        /* Purposefully add the project artifact last. This is important for the resolution of shaded jars.  */
        artifacts.add(mavenProject.getArtifact());
        for (Artifact artifact : artifacts) {
            Optional<ArtifactAdapter> optionalArtifact = resolvePackageNamesFromArtifact(artifact);
            if (optionalArtifact.isPresent()) {
                artifactsWithPackageNameMappings.add(optionalArtifact.get());
            } else {
                /* If resolve failed, then there are no package name mappings, so we mark it as not prunable. */
                var artifactAdapter = ArtifactAdapter.fromMavenArtifact(artifact);
                artifactAdapter.prunable = false;
                artifactsWithPackageNameMappings.add(artifactAdapter);
            }
        }

        /*
         * Currently we cannot ensure that package name are derived accurately for shaded dependencies.
         * Thus, we mark such artifacts as non-prunable.
         */
        Set<ArtifactAdapter> dependencies = artifactsWithPackageNameMappings.stream()
                .filter(v -> !v.equals(mavenProject.getArtifact()))
                .collect(Collectors.toSet());
        ArtifactAdapterResolver.markShadedArtifactsAsNonPrunable(dependencies);
        return artifactsWithPackageNameMappings;
    }

    private Optional<ArtifactAdapter> resolvePackageNamesFromArtifact(Artifact artifact) throws ArtifactResolutionException, IOException {
        File artifactFile = artifact.getFile();
        if (artifactFile != null && artifactFile.exists()) {
            return resolvePackageNamesFromArtifactFile(artifactFile, ArtifactAdapter.fromMavenArtifact(artifact));
        } else {
            DefaultArtifact sourceArtifact = new DefaultArtifact(
                    artifact.getGroupId(), artifact.getArtifactId(), "sources", "jar", artifact.getVersion()
            );
            ArtifactRequest request = new ArtifactRequest()
                    .setArtifact(sourceArtifact)
                    .setRepositories(remoteRepositories);

            ArtifactResult result = repositorySystem.resolveArtifact(repositorySystemSession, request);
            if (result != null && result.getArtifact() != null && result.getArtifact().getFile() != null) {
                File sourceFile = result.getArtifact().getFile();
                return resolvePackageNamesFromArtifactFile(sourceFile, ArtifactAdapter.fromEclipseArtifact(result.getArtifact()));
            }
            return Optional.empty();
        }
    }

    private Optional<ArtifactAdapter> resolvePackageNamesFromArtifactFile(File artifactFile, ArtifactAdapter artifact) throws IOException {
        if (!artifactFile.exists()) {
            return Optional.empty();
        }

        Path sourcePath = artifactFile.toPath();
        if (artifactFile.isDirectory()) {
            Set<String> packageNames = FileWalkerUtility.collectPackageNamesFromDirectory(artifactFile.toPath()).orElse(Set.of());
            artifact.setPackageNames(packageNames);
            return Optional.of(artifact);
        } else if (artifactFile.getName().endsWith(".jar")) {
            return shadedPackageNameResolver.populateWithAdditionalFields(sourcePath, artifact);
        } else {
            return Optional.empty();
        }
    }
}
