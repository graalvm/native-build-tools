/*
 * Copyright (c) 2021, 2021 Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.buildtools.model.resources;

import com.github.openjson.JSONArray;
import com.github.openjson.JSONObject;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;

public class ResourcesConfigModelSerializer {
    public static void serialize(ResourcesConfigModel model,  File outputFile) throws IOException {
        JSONObject json = toJson(model);
        String pretty = json.toString(4);
        File outputDir = outputFile.getParentFile();
        if (outputDir.isDirectory() || outputDir.mkdirs()) {
            try (OutputStreamWriter out = new OutputStreamWriter(new FileOutputStream(outputFile), StandardCharsets.UTF_8)) {
                out.append(pretty);
            }
        }
    }

    private static JSONObject toJson(ResourcesConfigModel model) {
        JSONObject json = new JSONObject();
        json.put("resources", toJson(model.getResources()));
        JSONArray namedValues = new JSONArray();
        model.getBundles().forEach(namedValue -> namedValues.put(toJson(namedValue)));
        json.put("bundles", namedValues);
        return json;
    }

    private static JSONObject toJson(ResourcesModel model) {
        JSONObject json = new JSONObject();
        JSONArray includes = new JSONArray();
        model.getIncludes().forEach(includes::put);
        json.put("includes", includes);
        JSONArray excludes = new JSONArray();
        model.getExcludes().forEach(excludes::put);
        json.put("excludes", excludes);
        return json;
    }

    private static JSONObject toJson(NamedValue namedValue) {
        JSONObject json = new JSONObject();
        json.put("name", namedValue.getName());
        return json;
    }

}
