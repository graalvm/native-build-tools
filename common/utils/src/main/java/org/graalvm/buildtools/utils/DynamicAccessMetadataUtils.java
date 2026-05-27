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

    /**
     * Serializes dynamic access metadata to JSON.
     * <p>
     * The output follows the schema defined at:
     * <a href="https://github.com/oracle/graal/blob/master/docs/reference-manual/native-image/assets/dynamic-access-metadata-schema-v1.0.0.json">
     *     dynamic-access-metadata-schema-v1.0.0.json
     * </a>
     */
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
