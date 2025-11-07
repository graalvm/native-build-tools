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

import com.github.openjson.JSONArray;
import com.github.openjson.JSONObject;
import org.graalvm.buildtools.gradle.internal.GraalVMLogger;
import org.graalvm.buildtools.gradle.internal.GraalVMReachabilityMetadataService;
import org.gradle.api.DefaultTask;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.artifacts.ResolvedArtifact;
import org.gradle.api.artifacts.ResolvedDependency;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.Internal;
import org.gradle.api.tasks.OutputFile;
import org.gradle.api.tasks.TaskAction;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Generates a {@code dynamic-access-metadata.json} file used by the dynamic access tab of the native image
 * Build Report. This json file contains the mapping of all classpath entries that exist in the
 * {@code library-and-framework-list.json} to their transitive dependencies.
 * <p>
 * If {@code library-and-framework-list.json} doesn't exist in the used release of the
 * {@code GraalVM Reachability Metadata} repository, this task does nothing.
 * <p>
 * The format of the generated JSON file conforms the following
 * <a href="https://github.com/oracle/graal/blob/master/docs/reference-manual/native-image/assets/dynamic-access-metadata-schema-v1.0.0.json">schema</a>.
 */
public abstract class GenerateDynamicAccessMetadata extends DefaultTask {
    private static final String LIBRARY_AND_FRAMEWORK_LIST = "library-and-framework-list.json";

    @Internal
    public abstract Property<Configuration> getRuntimeClasspath();

    @Internal
    public abstract Property<GraalVMReachabilityMetadataService> getMetadataService();

    @OutputFile
    public abstract RegularFileProperty getOutputJson();

    @TaskAction
    public void generate() {
        File jsonFile = getMetadataService().get().getRepositoryDirectory().resolve(LIBRARY_AND_FRAMEWORK_LIST).toFile();
        if (!jsonFile.exists()) {
            GraalVMLogger.of(getLogger()).log("{} is not packaged with the provided reachability metadata repository.", LIBRARY_AND_FRAMEWORK_LIST);
            return;
        }

        try {
            Set<String> artifactsToInclude = readArtifacts(jsonFile);

            Configuration runtimeClasspathConfig = getRuntimeClasspath().get();
            Set<File> classpathEntries = runtimeClasspathConfig.getFiles();

            Map<String, Set<String>> exportMap = buildExportMap(
                    runtimeClasspathConfig.getResolvedConfiguration().getFirstLevelModuleDependencies(),
                    artifactsToInclude,
                    classpathEntries
            );

            writeMapToJson(getOutputJson().getAsFile().get(), exportMap);
        } catch (IOException e) {
            GraalVMLogger.of(getLogger()).log("Failed to generate dynamic access metadata: {}", e);
        }
    }

    /**
     * Collects all versionless artifact coordinates ({@code groupId:artifactId}) from each
     * entry in the {@code library-and-framework.json} file.
     */
    private Set<String> readArtifacts(File inputFile) throws IOException {
        Set<String> artifacts = new HashSet<>();
        String content = Files.readString(inputFile.toPath());
        JSONArray jsonArray = new JSONArray(content);
        for (int i = 0; i < jsonArray.length(); i++) {
            JSONObject entry = jsonArray.getJSONObject(i);
            if (entry.has("artifact")) {
                artifacts.add(entry.getString("artifact"));
            }
        }
        return artifacts;
    }

    /**
     * Builds a mapping from each entry in the classpath, whose corresponding artifact
     * exists in the {@code library-and-framework.json} file, to the set of all of its
     * transitive dependency entry paths.
     */
    private Map<String, Set<String>> buildExportMap(Set<ResolvedDependency> dependencies, Set<String> artifactsToInclude, Set<File> classpathEntries) {
        Map<String, Set<String>> exportMap = new HashMap<>();
        for (ResolvedDependency dependency : dependencies) {
            String dependencyCoordinates = dependency.getModuleGroup() + ":" + dependency.getModuleName();
            if (!artifactsToInclude.contains(dependencyCoordinates)) {
                continue;
            }

            for (ResolvedArtifact artifact : dependency.getModuleArtifacts()) {
                File file = artifact.getFile();
                if (classpathEntries.contains(file)) {
                    Set<String> files = new HashSet<>();
                    collectDependencies(dependency, files, classpathEntries);
                    exportMap.put(file.getAbsolutePath(), files);
                }
            }
        }
        return exportMap;
    }

    /**
     * Recursively collects all classpath entry paths for the given dependency and its transitive dependencies.
     */
    private void collectDependencies(ResolvedDependency dep, Set<String> collector, Set<File> classpathEntries) {
        for (ResolvedArtifact artifact : dep.getModuleArtifacts()) {
            File file = artifact.getFile();
            if (classpathEntries.contains(file)) {
                collector.add(file.getAbsolutePath());
            }
        }

        for (ResolvedDependency child : dep.getChildren()) {
            collectDependencies(child, collector, classpathEntries);
        }
    }

    /**
     * Writes the export map to a JSON file. Each key (a classpath entry) maps to
     * a JSON array of classpath entry paths of its dependencies.
     */
    private void writeMapToJson(File outputFile, Map<String, Set<String>> exportMap) {
        try {
            JSONArray jsonArray = new JSONArray();

            for (Map.Entry<String, Set<String>> entry : exportMap.entrySet()) {
                JSONObject obj = new JSONObject();
                obj.put("metadataProvider", entry.getKey());

                JSONArray providedArray = new JSONArray();
                entry.getValue().forEach(providedArray::put);
                obj.put("providesFor", providedArray);

                jsonArray.put(obj);
            }

            try (FileWriter writer = new FileWriter(outputFile)) {
                writer.write(jsonArray.toString(2));
            }
        } catch (Exception e) {
            GraalVMLogger.of(getLogger()).log("Failed to write dynamic access metadata JSON: {}", e);
        }
    }
}
