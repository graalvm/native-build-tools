/*
 * Copyright (c) 2025, 2025, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.buildtools.gradle.tasks;

import org.graalvm.buildtools.gradle.internal.GraalVMLogger;
import org.graalvm.buildtools.gradle.internal.GraalVMReachabilityMetadataService;
import org.graalvm.buildtools.utils.DynamicAccessMetadataUtils;
import org.gradle.api.DefaultTask;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.artifacts.component.ModuleComponentIdentifier;
import org.gradle.api.artifacts.result.DependencyResult;
import org.gradle.api.artifacts.result.ResolvedArtifactResult;
import org.gradle.api.artifacts.result.ResolvedComponentResult;
import org.gradle.api.artifacts.result.ResolvedDependencyResult;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.provider.SetProperty;
import org.gradle.api.tasks.Internal;
import org.gradle.api.tasks.OutputFile;
import org.gradle.api.tasks.TaskAction;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Generates a {@code dynamic-access-metadata.json} file used by the dynamic access tab of the native image
 * Build Report. This json file contains the mapping of all classpath entries that exist in the
 * {@value #LIBRARY_AND_FRAMEWORK_LIST} to their transitive dependencies.
 * <p>
 * If {@value #LIBRARY_AND_FRAMEWORK_LIST} doesn't exist in the used release of the
 * {@code GraalVM Reachability Metadata} repository, this task does nothing.
 * <p>
 * The format of the generated JSON file conforms the following
 * <a href="https://github.com/oracle/graal/blob/master/docs/reference-manual/native-image/assets/dynamic-access-metadata-schema-v1.0.0.json">schema</a>.
 */
public abstract class GenerateDynamicAccessMetadata extends DefaultTask {
    private static final String LIBRARY_AND_FRAMEWORK_LIST = "library-and-framework-list.json";

    public void setClasspath(Configuration classpath) {
        getRuntimeClasspathGraph().set(classpath.getIncoming().getResolutionResult().getRootComponent());
        getRuntimeClasspathArtifacts().set(classpath.getIncoming().getArtifacts().getResolvedArtifacts());
    }

    @Internal
    public abstract Property<ResolvedComponentResult> getRuntimeClasspathGraph();

    @Internal
    public abstract SetProperty<ResolvedArtifactResult> getRuntimeClasspathArtifacts();

    @Internal
    public abstract Property<GraalVMReachabilityMetadataService> getMetadataService();

    @OutputFile
    public abstract RegularFileProperty getOutputJson();

    @TaskAction
    public void generate() {
        Optional<Path> repositoryDirectory = getMetadataService().get().getRepositoryDirectory();
        if (repositoryDirectory.isEmpty()) {
            GraalVMLogger.of(getLogger())
                    .log("No reachability metadata repository is configured or available.");
            return;
        }
        File jsonFile = repositoryDirectory.get().resolve(LIBRARY_AND_FRAMEWORK_LIST).toFile();
        if (!jsonFile.exists()) {
            GraalVMLogger.of(getLogger())
                    .log("{} is not packaged with the provided reachability metadata repository.", LIBRARY_AND_FRAMEWORK_LIST);
            return;
        }

        try {
            Set<String> artifactsToInclude = DynamicAccessMetadataUtils.readArtifacts(jsonFile);

            Map<String, String> coordinatesToPath = new HashMap<>();
            for (ResolvedArtifactResult artifact : getRuntimeClasspathArtifacts().get()) {
                if (artifact.getId().getComponentIdentifier() instanceof ModuleComponentIdentifier mci) {
                    String coordinates = mci.getGroup() + ":" + mci.getModule();
                    coordinatesToPath.put(coordinates, artifact.getFile().getAbsolutePath());
                }
            }

            ResolvedComponentResult root = getRuntimeClasspathGraph().get();

            Map<String, Set<String>> exportMap = buildExportMap(root, artifactsToInclude, coordinatesToPath);

            serializeExportMap(getOutputJson().getAsFile().get(), exportMap);
        } catch (IOException e) {
            GraalVMLogger.of(getLogger()).log("Failed to generate dynamic access metadata: {}", e);
        }
    }

    /**
     * Builds a mapping from each entry in the classpath, whose corresponding artifact
     * exists in the {@value #LIBRARY_AND_FRAMEWORK_LIST} file, to the set of all of its
     * transitive dependency entry paths.
     */
    private Map<String, Set<String>> buildExportMap(ResolvedComponentResult root, Set<String> artifactsToInclude, Map<String, String> coordinatesToPath) {
        Map<String, Set<String>> exportMap = new HashMap<>();
        Map<String, Set<String>> dependencyMap = new HashMap<>();

        collectDependencies(root, dependencyMap, new LinkedHashSet<>(), coordinatesToPath);

        for (Map.Entry<String, Set<String>> entry : dependencyMap.entrySet()) {
            String coordinates = entry.getKey();
            if (artifactsToInclude.contains(coordinates)) {
                String absolutePath = coordinatesToPath.get(coordinates);
                if (absolutePath != null) {
                    exportMap.put(absolutePath, entry.getValue());
                }
            }
        }

        return exportMap;
    }

    /**
     * Recursively collects all classpath entry paths for the given dependency and its transitive dependencies.
     */
    private void collectDependencies(ResolvedComponentResult node, Map<String, Set<String>> dependencyMap, Set<String> visited, Map<String, String> coordinatesToPath) {
        String coordinates = null;
        if (node.getId() instanceof ModuleComponentIdentifier mci) {
            coordinates = mci.getGroup() + ":" + mci.getModule();
        }

        if (coordinates != null && !visited.add(coordinates)) {
            return;
        }

        Set<String> dependencies = new LinkedHashSet<>();
        for (DependencyResult dep : node.getDependencies()) {
            if (dep instanceof ResolvedDependencyResult resolved) {
                ResolvedComponentResult target = resolved.getSelected();

                if (target.getId() instanceof ModuleComponentIdentifier targetMci) {
                    String dependencyCoordinates = targetMci.getGroup() + ":" + targetMci.getModule();
                    String dependencyPath = coordinatesToPath.get(dependencyCoordinates);

                    if (dependencyPath != null) {
                        dependencies.add(dependencyPath);
                    }

                    collectDependencies(target, dependencyMap, visited, coordinatesToPath);

                    Set<String> transitiveDependencies = dependencyMap.get(dependencyCoordinates);
                    if (transitiveDependencies != null) {
                        dependencies.addAll(transitiveDependencies);
                    }
                }
            }
        }
        dependencyMap.put(coordinates, dependencies);
    }

    /**
     * Writes the export map to a JSON file. Each key (a classpath entry) maps to
     * a JSON array of classpath entry paths of its dependencies.
     */
    private void serializeExportMap(File outputFile, Map<String, Set<String>> exportMap) throws IOException {
        DynamicAccessMetadataUtils.serialize(outputFile, exportMap);
        GraalVMLogger.of(getLogger()).lifecycle("Dynamic Access Metadata written into " + outputFile);
    }
}
