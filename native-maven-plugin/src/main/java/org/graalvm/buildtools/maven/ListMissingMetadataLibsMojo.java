/*
 * Copyright (c) 2026, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.buildtools.maven;

import org.apache.maven.artifact.Artifact;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.plugins.annotations.ResolutionScope;
import org.graalvm.reachability.MissingMetadataCommandSupport;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Mojo(name = "list-missing-metadata-libs", defaultPhase = LifecyclePhase.NONE,
    requiresDependencyResolution = ResolutionScope.RUNTIME, requiresDependencyCollection = ResolutionScope.RUNTIME)
public class ListMissingMetadataLibsMojo extends AbstractNativeMojo {
    @Parameter(property = "createIssues", defaultValue = "false")
    private boolean createIssues;

    @Parameter(property = "githubToken")
    private String githubToken;

    @Parameter(property = "targetRepository", defaultValue = MissingMetadataCommandSupport.DEFAULT_TARGET_REPOSITORY)
    private String targetRepository;

    @Parameter(property = "githubApiUrl", defaultValue = MissingMetadataCommandSupport.DEFAULT_GITHUB_API_URL)
    private String githubApiUrl;

    @Parameter(property = "reportFile", defaultValue = "${project.build.directory}/native/list-missing-metadata-libs.json")
    private File reportFile;

    @Override
    public void execute() throws MojoExecutionException, MojoFailureException {
        if (!isMetadataRepositoryEnabled()) {
            throw new MojoExecutionException("GraalVM reachability metadata repository is disabled.");
        }
        try {
            configureMetadataRepository();
            MissingMetadataCommandSupport.Report report = MissingMetadataCommandSupport.run(
                directRuntimeDependencies(),
                metadataRepository,
                project.getArtifacts().stream()
                    .filter(this::isArtifactExcludedFromMetadataRepository)
                    .map(artifact -> artifact.getGroupId() + ":" + artifact.getArtifactId())
                    .collect(Collectors.toSet()),
                forcedVersions(),
                new MissingMetadataCommandSupport.Options(
                    "maven",
                    project.getArtifactId(),
                    describeMetadataRepositoryLocation(),
                    createIssues,
                    resolveGithubToken(),
                    targetRepository,
                    githubApiUrl,
                    null
                )
            );
            getLog().info(report.renderConsoleOutput());
            writeReport(report.toJsonString());
        } catch (RuntimeException ex) {
            throw new MojoExecutionException(ex.getMessage(), ex);
        }
    }

    private List<MissingMetadataCommandSupport.DependencyCoordinate> directRuntimeDependencies() {
        List<MissingMetadataCommandSupport.DependencyCoordinate> dependencies = new ArrayList<>();
        for (Artifact artifact : project.getArtifacts()) {
            if (isDirectRuntimeDependency(artifact) && !isArtifactExcludedFromMetadataRepository(artifact)) {
                dependencies.add(new MissingMetadataCommandSupport.DependencyCoordinate(
                    artifact.getGroupId(),
                    artifact.getArtifactId(),
                    artifact.getVersion()
                ));
            }
        }
        return dependencies;
    }

    private boolean isDirectRuntimeDependency(Artifact artifact) {
        List<String> dependencyTrail = artifact.getDependencyTrail();
        if (dependencyTrail == null || dependencyTrail.size() != 2) {
            return false;
        }
        String scope = artifact.getScope();
        return scope == null
            || Artifact.SCOPE_COMPILE.equals(scope)
            || Artifact.SCOPE_RUNTIME.equals(scope);
    }

    private Map<String, String> forcedVersions() {
        return project.getArtifacts().stream()
            .filter(artifact -> !isArtifactExcludedFromMetadataRepository(artifact))
            .flatMap(artifact -> getMetadataVersion(artifact)
                .map(version -> Map.entry(artifact.getGroupId() + ":" + artifact.getArtifactId(), version))
                .stream())
            .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue, (left, right) -> right));
    }

    private String resolveGithubToken() {
        if (githubToken != null && !githubToken.isBlank()) {
            return githubToken;
        }
        String envToken = System.getenv("GITHUB_TOKEN");
        if (envToken != null && !envToken.isBlank()) {
            return envToken;
        }
        String ghToken = System.getenv("GH_TOKEN");
        if (ghToken != null && !ghToken.isBlank()) {
            return ghToken;
        }
        return null;
    }

    private void writeReport(String json) {
        try {
            if (reportFile.getParentFile() != null) {
                Files.createDirectories(reportFile.getParentFile().toPath());
            }
            Files.writeString(reportFile.toPath(), json, StandardCharsets.UTF_8);
        } catch (IOException ex) {
            throw new RuntimeException("Unable to write report file " + reportFile, ex);
        }
    }
}
