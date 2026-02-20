/*
 * Copyright (c) 2020, 2022 Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.reachability.internal.index.modules;

import com.github.openjson.JSONArray;
import com.github.openjson.JSONObject;
import org.graalvm.reachability.internal.UncheckedIOException;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Module-to-config index which:
 * - Resolves the primary module directory by conventional layout (groupId/artifactId),
 * - Reads requires from the inner metadata/group/artifact/index.json and adds their conventional directories.
 */
public class FileSystemModuleToConfigDirectoryIndex implements ModuleToConfigDirectoryIndex {
    private final Path rootPath;

    public FileSystemModuleToConfigDirectoryIndex(Path rootPath) {
        this.rootPath = rootPath;
    }

    /**
     * Returns the directories containing the candidate configurations for the given module.
     * <p>
     * - Always includes the conventional module directory if present: rootPath/groupId/artifactId
     * - Additionally includes conventional directories of any modules listed in "requires" of the inner index.json
     * - Only a single-level requires expansion is performed
     */
    @Override
    public Set<Path> findConfigurationDirectories(String groupId, String artifactId) {
        Path base = rootPath.resolve(groupId + "/" + artifactId);
        if (!Files.isDirectory(base)) {
            return Collections.emptySet();
        }

        Path indexFile = base.resolve("index.json");
        if (Files.isRegularFile(indexFile)) {
            Set<Path> result = new LinkedHashSet<>();
            // Always include the base directory so its index.json is parsed,
            // even if it doesn't contain configuration files itself.
            result.add(base);
            try {
                String content = Files.readString(indexFile);
                JSONArray entries = new JSONArray(content);
                for (int i = 0; i < entries.length(); i++) {
                    JSONObject entry = entries.getJSONObject(i);
                    JSONArray requires = entry.optJSONArray("requires");
                    if (requires == null) {
                        continue;
                    }
                    for (int j = 0; j < requires.length(); j++) {
                        String req = requires.getString(j);
                        int sep = req.indexOf(':');
                        if (sep > 0) {
                            String reqGroup = req.substring(0, sep);
                            String reqArtifact = req.substring(sep + 1);
                            Path reqDir = rootPath.resolve(reqGroup + "/" + reqArtifact);
                            if (Files.isDirectory(reqDir)) {
                                result.add(reqDir);
                            }
                        }
                    }
                }
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
            return result;
        }

        return Collections.singleton(base);
    }
}
