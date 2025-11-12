package org.graalvm.buildtools.utils;

import com.github.openjson.JSONArray;
import com.github.openjson.JSONObject;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

public final class DynamicAccessMetadataUtils {
    /**
     * Collects all versionless artifact coordinates ({@code groupId:artifactId}) from each
     * entry in the {@code library-and-framework-list.json} file.
     */
    public static Set<String> readArtifacts(File inputFile) throws IOException {
        Set<String> artifacts = new LinkedHashSet<>();
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

    public static void serialize(File outputFile, Map<String, Set<String>> exportMap) throws IOException {
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
    }
}
