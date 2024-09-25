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
 * Generates a Software Bill of Materials (SBOM) that is augmented and refined by Native Image.
 * <p>
 * Approach:
 * 1. The cyclonedx-maven-plugin creates a baseline SBOM.
 * 2. The components of the baseline SBOM (referred to as the "base" SBOM) are updated with additional metadata,
 * most importantly being the set of package names associated with the component (see {@link AddedComponentFields}
 * for all additional metadata).
 * 3. The SBOM is stored at a known location.
 * 4. Native Image processes the SBOM and removes unreachable components and unnecessary dependencies.
 * <p>
 * Creating the package-name-to-component mapping in the context of Native Image, without the knowledge known at the
 * plugin build-time is difficult, which was the primary motivation for realizing this approach.
 * <p>
 * Benefits:
 * * Great Baseline: Produces an industry-standard SBOM at minimum.
 * * Enhanced Accuracy: Native Image augments and refines the SBOM, potentially significantly improving its accuracy.
 */
final public class SBOMGenerator {
    private final MavenProject mavenProject;
    private final MavenSession mavenSession;
    private final BuildPluginManager pluginManager;
    private final RepositorySystem repositorySystem;
    private final String mainClass;
    private final Logger logger;

    private static final String cycloneDXPluginName = "cyclonedx-maven-plugin";
    private static final String SBOM_NAME = "base_sbom";
    private static final String FILE_FORMAT = "json";

    private static final class AddedComponentFields {
        /**
         * The package names associated with this component.
         */
        static final String packageNames = "packageNames";
        /**
         * The path to the jar containing the class files. For a component embedded in a shaded jar, the path must
         * be pointing to the shaded jar.
         */
        static final String jarPath = "jarPath";
        /**
         * If set to false, then this component and all its transitive dependencies SHOULD NOT be pruned by Native Image.
         * This is set to false when the package names could not be derived accurately.
         */
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
        String outputDirectory = mavenProject.getBuild().getDirectory();
        Path sbomPath = Paths.get(outputDirectory, SBOM_NAME + "." + FILE_FORMAT);
        try {
            /* Suppress the output from the cyclonedx-maven-plugin. */
            int loggingLevel = logger.getThreshold();
            logger.setThreshold(Logger.LEVEL_DISABLED);
            executeMojo(
                    plugin(
                            groupId("org.cyclonedx"),
                            artifactId(cycloneDXPluginName),
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


            if (!Files.exists(sbomPath)) {
                return;
            }

            // TODO: debugging, remove before merge
            Path unmodifiedPath = Paths.get(outputDirectory, "SBOM_UNMODIFIED.json");
            Files.deleteIfExists(unmodifiedPath);
            Files.copy(sbomPath, unmodifiedPath);

            var resolver = new ArtifactToPackageNameResolver(mavenProject, repositorySystem, mavenSession.getRepositorySession(), mainClass);
            Set<ArtifactAdapter> artifacts = resolver.getArtifactAdapters();
            augmentSBOM(sbomPath, artifacts);

            // TODO: debugging, remove before merge
            Path testPath = Paths.get(outputDirectory, "SBOM_AUGMENTED.json");
            Files.deleteIfExists(testPath);
            Files.copy(sbomPath, testPath);

        } catch (Exception exception) {
            deleteFileIfExists(sbomPath);
            String errorMsg = String.format("Failed to create SBOM. Please try again and report this issue if it persists. " +
                    "To bypass this failure, disable SBOM generation by setting %s to false.", NativeCompileNoForkMojo.enableSBOMParamName);
            throw new MojoExecutionException(errorMsg, exception);
        }
    }

    private static void deleteFileIfExists(Path sbomPath) {
        try {
            Files.deleteIfExists(sbomPath);
        } catch (IOException e) {
            /* Failed to delete file. */
        }
    }

    /**
     * Augments the base SBOM with information from the derived {@param artifacts}.
     *
     * @param baseSBOMPath path to the base SBOM generated by the cyclonedx plugin.
     * @param artifacts artifacts that possibly have been extended with package name data.
     */
    private void augmentSBOM(Path baseSBOMPath, Set<ArtifactAdapter> artifacts) throws IOException {
        ObjectMapper objectMapper = new ObjectMapper();
        ObjectNode sbomJson = (ObjectNode) objectMapper.readTree(Files.newInputStream(baseSBOMPath));

        ArrayNode componentsArray = (ArrayNode) sbomJson.get("components");
        if (componentsArray == null) {
            throw new RuntimeException(String.format("SBOM generated by %s contained no components.", cycloneDXPluginName));
        }

        /* Augment the "components" */
        componentsArray.forEach(componentNode -> augmentComponentNode(componentNode, artifacts, objectMapper));

        /* Augment the main component in "metadata/component" */
        JsonNode metadataNode = sbomJson.get("metadata");
        if (metadataNode != null && metadataNode.has("component")) {
            augmentComponentNode(metadataNode.get("component"), artifacts, objectMapper);
        }

        /* Save the augmented SBOM back to the file */
        objectMapper.writerWithDefaultPrettyPrinter().writeValue(Files.newOutputStream(baseSBOMPath), sbomJson);
    }

    /**
     * Updates the {@param componentNode} with {@link AddedComponentFields} from the artifact in {@param artifactsWithPackageNames}
     * with matching GAV coordinates.
     *
     * @param componentNode the node in the base SBOM that should be augmented.
     * @param artifactsWithPackageNames the artifact with information for {@link AddedComponentFields}.
     * @param objectMapper the objectMapper that is used to write the updates.
     */
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
