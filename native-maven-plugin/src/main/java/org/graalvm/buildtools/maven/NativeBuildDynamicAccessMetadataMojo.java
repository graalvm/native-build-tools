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
package org.graalvm.buildtools.maven;

import com.github.openjson.JSONArray;
import com.github.openjson.JSONObject;
import org.apache.maven.artifact.Artifact;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.Component;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.plugins.annotations.ResolutionScope;
import org.eclipse.aether.RepositorySystem;
import org.eclipse.aether.RepositorySystemSession;
import org.eclipse.aether.artifact.DefaultArtifact;
import org.eclipse.aether.collection.CollectRequest;
import org.eclipse.aether.collection.DependencyCollectionException;
import org.eclipse.aether.graph.Dependency;
import org.eclipse.aether.graph.DependencyNode;
import org.eclipse.aether.repository.RemoteRepository;
import org.eclipse.aether.util.graph.visitor.PreorderNodeListGenerator;
import org.graalvm.reachability.internal.FileSystemRepository;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
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
@Mojo(name = "generateDynamicAccessMetadata", defaultPhase = LifecyclePhase.GENERATE_RESOURCES, requiresDependencyResolution = ResolutionScope.RUNTIME, requiresDependencyCollection = ResolutionScope.RUNTIME)
public class NativeBuildDynamicAccessMetadataMojo extends AbstractNativeMojo {
    private static final String LIBRARY_AND_FRAMEWORK_LIST = "library-and-framework-list.json";

    @Component
    private RepositorySystem repoSystem;

    @Parameter(defaultValue = "${repositorySystemSession}", readonly = true)
    private RepositorySystemSession repoSession;

    @Parameter(defaultValue = "${project.remoteProjectRepositories}", readonly = true)
    private List<RemoteRepository> remoteRepos;

    @Parameter(defaultValue = "${project.build.directory}/dynamic-access-metadata.json", required = true)
    private File outputJson;

    @Override
    public void execute() throws MojoExecutionException, MojoFailureException {
        configureMetadataRepository();
        File jsonFile = ((FileSystemRepository) metadataRepository).getRootDirectory().resolve(LIBRARY_AND_FRAMEWORK_LIST).toFile();

        if (!jsonFile.exists()) {
            getLog().warn(LIBRARY_AND_FRAMEWORK_LIST + " is not packaged with the provided reachability metadata repository.");
            return;
        }

        try {
            Set<String> artifactsToInclude = readArtifacts(jsonFile);

            Map<String, String> coordinatesToPath = new HashMap<>();
            for (Artifact a : project.getArtifacts()) {
                if (a.getFile() != null) {
                    String coordinates = a.getGroupId() + ":" + a.getArtifactId();
                    coordinatesToPath.put(coordinates, a.getFile().getAbsolutePath());
                }
            }

            Map<String, Set<String>> exportMap = buildExportMap(artifactsToInclude, coordinatesToPath);

            writeMapToJson(outputJson, exportMap);
        } catch (IOException e) {
            getLog().warn("Failed generating dynamic access metadata: " + e);
        } catch (DependencyCollectionException e) {
            getLog().warn("Failed collecting dependencies: " + e);
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
    private Map<String, Set<String>> buildExportMap(Set<String> artifactsToInclude, Map<String, String> coordinatesToPath) throws DependencyCollectionException {
        Map<String, Set<String>> exportMap = new HashMap<>();

        for (Artifact artifact : project.getArtifacts()) {
            String coordinates = artifact.getGroupId() + ":" + artifact.getArtifactId();
            if (!artifactsToInclude.contains(coordinates) || artifact.getFile() == null) {
                continue;
            }

            Set<String> transitiveDependencies = collectDependencies(
                    artifact.getGroupId() + ":" + artifact.getArtifactId() + ":" + artifact.getVersion(),
                    coordinatesToPath);

            exportMap.put(artifact.getFile().getAbsolutePath(), transitiveDependencies);
        }
        return exportMap;
    }

    /**
     * Recursively collects all classpath entry paths for the given dependency and its transitive dependencies.
     */
    private Set<String> collectDependencies(String coordinates, Map<String, String> coordinatesToPath) throws DependencyCollectionException {
        DefaultArtifact artifact = new DefaultArtifact(coordinates);

        CollectRequest collectRequest = new CollectRequest();
        collectRequest.setRoot(new Dependency(artifact, ""));
        collectRequest.setRepositories(remoteRepos);

        DependencyNode node = repoSystem.collectDependencies(repoSession, collectRequest).getRoot();

        PreorderNodeListGenerator nlg = new PreorderNodeListGenerator();
        node.accept(nlg);

        Set<String> dependencies = new HashSet<>();
        nlg.getNodes().forEach(dependencyNode -> {
            if (dependencyNode.getDependency() != null) {
                DefaultArtifact dependencyArtifact = (DefaultArtifact) dependencyNode.getDependency().getArtifact();
                String dependencyPath = coordinatesToPath.get(dependencyArtifact.getGroupId() + ":" + dependencyArtifact.getArtifactId());
                if (dependencyPath != null) {
                    dependencies.add(dependencyPath);
                }
            }
        });

        return dependencies;
    }

    /**
     * Writes the export map to a JSON file. Each key (a classpath entry) maps to
     * a JSON array of classpath entry paths of its dependencies.
     */
    private void writeMapToJson(File outputFile, Map<String, Set<String>> exportMap) {
        try {
            JSONObject root = new JSONObject();
            for (Map.Entry<String, Set<String>> entry : exportMap.entrySet()) {
                JSONArray array = new JSONArray();
                entry.getValue().forEach(array::put);
                root.put(entry.getKey(), array);
            }

            try (FileWriter writer = new FileWriter(outputFile)) {
                writer.write(root.toString(2));
            }
        } catch (IOException e) {
            getLog().warn("Failed to write export map to JSON: " + e);
        }
    }
}
