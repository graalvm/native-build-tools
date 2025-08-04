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

import com.github.openjson.JSONArray;
import com.github.openjson.JSONObject;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.plugin.BuildPluginManager;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.project.MavenProject;
import org.eclipse.aether.RepositorySystem;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.graalvm.buildtools.maven.NativeCompileNoForkMojo.SKIP_BASE_SBOM_PARAM_NAME;
import static org.twdata.maven.mojoexecutor.MojoExecutor.artifactId;
import static org.twdata.maven.mojoexecutor.MojoExecutor.configuration;
import static org.twdata.maven.mojoexecutor.MojoExecutor.element;
import static org.twdata.maven.mojoexecutor.MojoExecutor.executeMojo;
import static org.twdata.maven.mojoexecutor.MojoExecutor.executionEnvironment;
import static org.twdata.maven.mojoexecutor.MojoExecutor.goal;
import static org.twdata.maven.mojoexecutor.MojoExecutor.groupId;
import static org.twdata.maven.mojoexecutor.MojoExecutor.name;
import static org.twdata.maven.mojoexecutor.MojoExecutor.plugin;
import static org.twdata.maven.mojoexecutor.MojoExecutor.version;

/**
 * Generates a base Software Bill of Materials (SBOM) that is refined by Native Image. This feature is only
 * supported in Oracle GraalVM for JDK {@link SBOMGenerator#requiredNativeImageVersion} or later.
 * <p>
 * Approach:
 * 1. The cyclonedx-maven-plugin creates an initial SBOM.
 * 2. The components of the initial SBOM are updated with additional metadata, most importantly being the set of
 * package names associated with the component (see {@link AddedComponentFields} for all additional metadata).
 * The updated SBOM is referred to as the base SBOM.
 * 3. The base SBOM is stored at a known location.
 * 4. Native Image processes the base SBOM and removes unreachable components and unnecessary dependencies.
 * <p>
 * Creating the package-name-to-component mapping in the context of Native Image, without the knowledge known at the
 * plugin build-time is difficult, which was the primary motivation for realizing this approach.
 * <p>
 * Benefits:
 * - Great Baseline: Produces an industry-standard SBOM at minimum.
 * - Enhanced Accuracy: Native Image refines the SBOM, potentially significantly improving its accuracy.
 */
public final class SBOMGenerator {
    /**
     * The support for base SBOMs was added in this version of Native Image.
     */
    public static final int requiredNativeImageVersion = 24;
    /**
     * SBOMs are embedded by default in native images starting from this version (even
     * if `--enable-sbom` is not used).
     */
    private static final int embeddedByDefaultVersion = 25;

    private final MavenProject mavenProject;
    private final MavenSession mavenSession;
    private final BuildPluginManager pluginManager;
    private final RepositorySystem repositorySystem;
    private final String mainClass;

    private static final String SBOM_NATIVE_IMAGE_FLAG = "--enable-sbom";
    private static final String SBOM_FILE_FORMAT = "json";
    private static final String SBOM_FILENAME_WITHOUT_EXTENSION = "base_sbom";
    private final String outputDirectory;
    public static final String SBOM_FILENAME = SBOM_FILENAME_WITHOUT_EXTENSION + "." + SBOM_FILE_FORMAT;

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

    /**
     * The external plugin used to generate the initial SBOM.
     */
    private static final class Plugin {
        static final String artifactId = "cyclonedx-maven-plugin";
        static final String groupId = "org.cyclonedx";
        static final String version = "2.9.1";
        static final String goal = "makeAggregateBom";

        private static final class Configuration {
            static final String outputFormat = SBOM_FILE_FORMAT;
            static final String outputName = SBOM_FILENAME_WITHOUT_EXTENSION;
            static final String skipNotDeployed = "false";
            static final String schemaVersion = "1.5";
        }
    }

    public SBOMGenerator(
            MavenProject mavenProject,
            MavenSession mavenSession,
            BuildPluginManager pluginManager,
            RepositorySystem repositorySystem,
            String mainClass) {
        this.mavenProject = mavenProject;
        this.mavenSession = mavenSession;
        this.pluginManager = pluginManager;
        this.repositorySystem = repositorySystem;
        this.mainClass = mainClass;
        this.outputDirectory = mavenProject.getBuild().getDirectory();
    }

    public record Config(
            /* If Oracle GraalVM is used. */
            boolean isOracleGraalVM,
            /* The build arguments that will be passed to 'native-image'. */
            List<String> buildArgs,
            /* The version of 'native-image'. */
            int graalvmVersion
    ) {}

    /**
     * Generates a base SBOM that will help Native Image when building its SBOM. The base
     * SBOM is only generated if the SBOM feature is enabled and if the base SBOM is supported by
     * the used Native Image version.
     *
     * @throws MojoExecutionException if base SBOM creation fails.
     */
    public void generateIfSupportedAndEnabled(Config config) throws MojoExecutionException {
        if (!isBaseSBOMSupported(config.isOracleGraalVM(), config.graalvmVersion()) || !isSBOMEnabled(config)) {
            return;
        }

        Path sbomPath = Paths.get(outputDirectory, SBOM_FILENAME);
        try {
            executeMojo(
                    plugin(
                            groupId(Plugin.groupId),
                            artifactId(Plugin.artifactId),
                            version(Plugin.version)
                    ),
                    goal(Plugin.goal),
                    configuration(
                            element(name("outputFormat"), Plugin.Configuration.outputFormat),
                            element(name("outputName"), Plugin.Configuration.outputName),
                            element(name("outputDirectory"), outputDirectory),
                            element(name("skipNotDeployed"), Plugin.Configuration.skipNotDeployed),
                            element(name("schemaVersion"), Plugin.Configuration.schemaVersion)
                    ),
                    executionEnvironment(mavenProject, mavenSession, pluginManager)
            );

            if (!Files.exists(sbomPath)) {
                return;
            }

            var resolver = new ArtifactToPackageNameResolver(mavenProject, repositorySystem, mavenSession.getRepositorySession(), mainClass);
            Set<ArtifactAdapter> artifacts = resolver.getArtifactAdapters();
            augmentInitialSBOM(sbomPath, artifacts);
        } catch (Exception exception) {
            deleteFileIfExists(sbomPath);
            String errorMsg = String.format("Failed to create SBOM. Please try again and report this issue if it persists. " +
                    "To bypass this failure, disable base SBOM generation by setting configuration option '%s' to true.", SKIP_BASE_SBOM_PARAM_NAME);
            throw new MojoExecutionException(errorMsg, exception);
        }
    }

    public static boolean isBaseSBOMSupported(boolean isOracleGraalVM, int graalvmMajorVersion) {
        return isOracleGraalVM && graalvmMajorVersion >= requiredNativeImageVersion;
    }

    /**
     * Returns true if the SBOM feature is enabled in the upcoming `native-image` build.
     */
    private static boolean isSBOMEnabled(Config config) {
        if (!config.isOracleGraalVM()) {
            /* SBOM is only supported in Oracle GraalVM */
            return false;
        }

        if (config.graalvmVersion() >= embeddedByDefaultVersion) {
            /*
             * SBOMs are enabled by default starting from version embeddedByDefaultVersion. Therefore,
             * it is enabled as long as it is not explicitly disabled via `--enable-sbom=false`.
             */
            return config.buildArgs().stream().noneMatch(v -> isSBOMFlag(v) && v.contains("false"));
        } else {
            /* NI SBOM is enabled if `--enable-sbom` is passed as a build argument */
            return config.buildArgs().stream().anyMatch(SBOMGenerator::isSBOMFlag);
        }
    }

    private static boolean isSBOMFlag(String v) {
        return v.contains(SBOM_NATIVE_IMAGE_FLAG);
    }

    private static void deleteFileIfExists(Path sbomPath) {
        try {
            Files.deleteIfExists(sbomPath);
        } catch (IOException e) {
            /* Failed to delete file. */
        }
    }

    /**
     * Augments the initial SBOM with information from the derived {@param artifacts}.
     *
     * @param baseSBOMPath path to the initial SBOM generated by the cyclonedx plugin.
     * @param artifacts artifacts that possibly have been extended with package name data.
     */
    private void augmentInitialSBOM(Path baseSBOMPath, Set<ArtifactAdapter> artifacts) throws IOException {
        JSONObject sbomJson = new JSONObject(Files.readString(baseSBOMPath));

        JSONArray componentsArray = sbomJson.optJSONArray("components");
        if (componentsArray != null) {
            componentsArray.forEach(componentNode -> augmentComponentNode((JSONObject) componentNode, artifacts));
        }

        /* Augment the main component in "metadata/component" */
        JSONObject metadataNode = sbomJson.optJSONObject("metadata");
        if (metadataNode != null && metadataNode.has("component")) {
            augmentComponentNode(metadataNode.getJSONObject("component"), artifacts);
        }

        /* Save the base SBOM back to the file */
        Files.writeString(baseSBOMPath, sbomJson.toString(2));
    }

    /**
     * Updates the {@param componentNode} with {@link AddedComponentFields} from the artifact in {@param artifactsWithPackageNames}
     * with matching GAV coordinates.
     *
     * @param componentNode the node in the base SBOM that should be augmented.
     * @param artifactsWithPackageNames the artifact with information for {@link AddedComponentFields}.
     */
    private void augmentComponentNode(JSONObject componentNode, Set<ArtifactAdapter> artifactsWithPackageNames) {
        String groupField = "group";
        String nameField = "name";
        String versionField = "version";
        if (componentNode.has(groupField) && componentNode.has(nameField) && componentNode.has(versionField)) {
            String groupId = componentNode.getString(groupField);
            String artifactId = componentNode.getString(nameField);
            String version = componentNode.getString(versionField);

            Optional<ArtifactAdapter> optionalArtifact = artifactsWithPackageNames.stream()
                    .filter(artifact -> artifact.groupId.equals(groupId)
                            && artifact.artifactId.equals(artifactId)
                            && artifact.version.equals(version))
                    .findFirst();

            if (optionalArtifact.isPresent()) {
                ArtifactAdapter artifact = optionalArtifact.get();
                JSONArray packageNamesArray = new JSONArray();
                if (artifact.prunable) {
                    List<String> sortedPackageNames = artifact.packageNames.stream().sorted().toList();
                    sortedPackageNames.forEach(packageNamesArray::put);
                }
                componentNode.put(AddedComponentFields.packageNames, packageNamesArray);

                String jarPath = "";
                if (artifact.jarPath != null) {
                    jarPath = artifact.jarPath.toString();
                }
                componentNode.put(AddedComponentFields.jarPath, jarPath);
                componentNode.put(AddedComponentFields.prunable, artifact.prunable);
            }
        }
    }
}
