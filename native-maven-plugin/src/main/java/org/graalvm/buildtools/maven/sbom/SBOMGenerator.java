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

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.plugin.BuildPluginManager;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.project.MavenProject;
import org.codehaus.plexus.logging.Logger;
import org.eclipse.aether.RepositorySystem;
import org.graalvm.buildtools.maven.NativeCompileNoForkMojo;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import static org.twdata.maven.mojoexecutor.MojoExecutor.*;

/**
 * Generates an enhanced Software Bill of Materials (SBOM) for Native Image consumption and refinement.
 * <p>
 * Process overview:
 * 1. Utilizes the cyclonedx-maven-plugin to create a baseline SBOM.
 * 2. Augments the baseline SBOM components with additional metadata (see {@link AddedComponentFields}):
 * * "packageNames": A list of all package names associated with each component.
 * * "jarPath": Path to the component jar.
 * * "prunable": Boolean indicating if the component can be pruned. We currently set this to false for
 * any dependencies to the main component that are shaded.
 * 3. Stores the enhanced SBOM at a known location.
 * 4. Native Image then processes this SBOM during its static analysis:
 * * Unreachable components are removed.
 * * Unnecessary dependency relationships are pruned.
 * <p>
 * Creating the package-name-to-component mapping in the context of Native Image, without any build-system
 * knowledge is difficult, which was the primary motivation for realizing this approach.
 * <p>
 * Benefits:
 * * Great Baseline: Produces an industry-standard SBOM at minimum.
 * * Enhanced Accuracy: Native Image static analysis refines the SBOM,
 * potentially significantly improving its accuracy.
 */
final public class SBOMGenerator {
    private final MavenProject mavenProject;
    private final MavenSession mavenSession;
    private final BuildPluginManager pluginManager;
    private final RepositorySystem repositorySystem;
    private final String mainClass;
    private final Logger logger;

    private static final String SBOM_NAME = "WIP_SBOM";
    private static final String FILE_FORMAT = "json";

    private static final class AddedComponentFields {
        static final String packageNames = "packageNames";
        static final String jarPath = "jarPath";
        static final String prunable = "prunable";
    }

    public SBOMGenerator(
            MavenProject mavenProject,
            MavenSession mavenSession,
            BuildPluginManager pluginManager,
            RepositorySystem repositorySystem,
            String mainClass,
            Logger logger) {
        this.mavenProject = mavenProject;
        this.mavenSession = mavenSession;
        this.pluginManager = pluginManager;
        this.repositorySystem = repositorySystem;
        this.mainClass = mainClass;
        this.logger = logger;
    }

    /**
     * Generates an SBOM that will be further augmented by Native Image. The SBOM is stored in the build directory.
     *
     * @throws MojoExecutionException if SBOM creation fails.
     */
    public void generate() throws MojoExecutionException {
        try {
            String outputDirectory = mavenProject.getBuild().getDirectory();
            /* Suppress the output from the cyclonedx-maven-plugin. */
            int loggingLevel = logger.getThreshold();
            logger.setThreshold(Logger.LEVEL_DISABLED);
            executeMojo(
                    plugin(
                            groupId("org.cyclonedx"),
                            artifactId("cyclonedx-maven-plugin"),
                            version("2.8.1")
                    ),
                    goal("makeAggregateBom"),
                    configuration(
                            element(name("outputFormat"), FILE_FORMAT),
                            element(name("outputName"), SBOM_NAME),
                            element(name("outputDirectory"), outputDirectory),
                            element(name("skipNotDeployed"), "false")
                    ),
                    executionEnvironment(mavenProject, mavenSession, pluginManager)
            );
            logger.setThreshold(loggingLevel);

            Path sbomPath = Paths.get(outputDirectory, SBOM_NAME + "." + FILE_FORMAT);
            if (!Files.exists(sbomPath)) {
                return;
            }

            var resolver = new ArtifactToPackageNameResolver(mavenProject, repositorySystem, mavenSession.getRepositorySession(), mainClass);
            Set<ArtifactAdapter> artifactsWithPackageNames = resolver.getArtifactPackageMappings();
            augmentSBOM(sbomPath, artifactsWithPackageNames);
        } catch (Exception exception) {
            String errorMsg = String.format("Failed to create SBOM. Please try again and report this issue if it persists. " +
                    "To bypass this failure, disable SBOM generation by setting %s to false.", NativeCompileNoForkMojo.enableSBOMParamName);
            throw new MojoExecutionException(errorMsg, exception);
        }
    }

    private void augmentSBOM(Path sbomPath, Set<ArtifactAdapter> artifactToPackageNames) throws IOException {
        ObjectMapper objectMapper = new ObjectMapper();
        ObjectNode sbomJson = (ObjectNode) objectMapper.readTree(Files.newInputStream(sbomPath));

        ArrayNode componentsArray = (ArrayNode) sbomJson.get("components");
        if (componentsArray == null) {
            return;
        }

        /*
         * Iterates over the components and finds the associated artifact by equality checks of the GAV coordinates.
         * If a match is found, the component is augmented.
         */
        componentsArray.forEach(componentNode -> augmentComponentNode(componentNode, artifactToPackageNames, objectMapper));

        /* Augment the main component in "metadata/component" */
        JsonNode metadataNode = sbomJson.get("metadata");
        if (metadataNode != null && metadataNode.has("component")) {
            augmentComponentNode(metadataNode.get("component"), artifactToPackageNames, objectMapper);
        }

        /* Save the augmented SBOM back to the file */
        objectMapper.writerWithDefaultPrettyPrinter().writeValue(Files.newOutputStream(sbomPath), sbomJson);
    }

    private void augmentComponentNode(JsonNode componentNode, Set<ArtifactAdapter> artifactsWithPackageNames, ObjectMapper objectMapper) {
        String groupField = "group";
        String nameField = "name";
        String versionField = "version";
        if (componentNode.has(groupField) && componentNode.has(nameField) && componentNode.has(versionField)) {
            String groupId = componentNode.get(groupField).asText();
            String artifactId = componentNode.get(nameField).asText();
            String version = componentNode.get(versionField).asText();

            Optional<ArtifactAdapter> optionalArtifact = artifactsWithPackageNames.stream()
                    .filter(artifact -> artifact.groupId.equals(groupId)
                            && artifact.artifactId.equals(artifactId)
                            && artifact.version.equals(version))
                    .findFirst();

            if (optionalArtifact.isPresent()) {
                ArtifactAdapter artifact = optionalArtifact.get();
                ArrayNode packageNamesArray = objectMapper.createArrayNode();
                List<String> sortedPackageNames = artifact.packageNames.stream().sorted().collect(Collectors.toList());
                sortedPackageNames.forEach(packageNamesArray::add);
                ((ObjectNode) componentNode).set(AddedComponentFields.packageNames, packageNamesArray);

                String jarPath = "";
                if (artifact.jarPath != null) {
                    jarPath = artifact.jarPath.toString();
                }
                ((ObjectNode) componentNode).put(AddedComponentFields.jarPath, jarPath);
                ((ObjectNode) componentNode).put(AddedComponentFields.prunable, artifact.prunable);
            }
        }
    }
}
