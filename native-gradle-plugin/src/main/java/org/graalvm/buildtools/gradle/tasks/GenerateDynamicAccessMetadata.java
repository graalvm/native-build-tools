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
import java.nio.file.Files;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Generates a {@code dynamic-access-metadata.json} file used by the dynamic access tab of the native image
 * Build Report. This json file contains the mapping of all classpath entries that exist in the
 * {@code library-and-framework-list.json} to their transitive dependencies.
 * <br>
 * If {@code library-and-framework-list.json} doesn't exist in the used release of the
 * {@code Graalvm Reachability Metadata} repository, this task does nothing.
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
            Set<File> classpathJars = runtimeClasspathConfig.getFiles();

            Map<String, Set<String>> exportMap = buildExportMap(
                    runtimeClasspathConfig.getResolvedConfiguration().getFirstLevelModuleDependencies(),
                    artifactsToInclude,
                    classpathJars
            );

            writeMapToJson(getOutputJson().getAsFile().get(), exportMap);
        } catch (Exception e) {
            throw new RuntimeException("Failed to generate dynamic access metadata", e);
        }
    }

    private Set<String> readArtifacts(File inputFile) throws Exception {
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

    private Map<String, Set<String>> buildExportMap(Set<ResolvedDependency> dependencies,
                                                    Set<String> artifactsToInclude,
                                                    Set<File> classpathJars) {
        Map<String, Set<String>> exportMap = new HashMap<>();
        for (ResolvedDependency dep : dependencies) {
            String depKey = dep.getModuleGroup() + ":" + dep.getModuleName();
            if (!artifactsToInclude.contains(depKey)) {
                continue;
            }

            for (ResolvedArtifact artifact : dep.getModuleArtifacts()) {
                File file = artifact.getFile();
                if (classpathJars.contains(file)) {
                    Set<String> files = new HashSet<>();
                    collectArtifacts(dep, files, classpathJars);
                    exportMap.put(file.getAbsolutePath(), files);
                }
            }
        }
        return exportMap;
    }

    private void collectArtifacts(ResolvedDependency dep, Set<String> collector, Set<File> classpathJars) {
        for (ResolvedArtifact artifact : dep.getModuleArtifacts()) {
            File file = artifact.getFile();
            if (classpathJars.contains(file)) {
                collector.add(file.getAbsolutePath());
            }
        }

        for (ResolvedDependency child : dep.getChildren()) {
            collectArtifacts(child, collector, classpathJars);
        }
    }

    public static void writeMapToJson(File outputFile, Map<String, Set<String>> exportMap) {
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
        } catch (Exception e) {
            throw new RuntimeException("Failed to write export map to JSON", e);
        }
    }
}