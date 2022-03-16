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

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.type.TypeFactory;
import org.graalvm.reachability.internal.UncheckedIOException;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class JsonModuleToConfigDirectoryIndex implements ModuleToConfigDirectoryIndex {
    private final Path rootPath;
    private final Map<String, Set<Path>> index;

    public JsonModuleToConfigDirectoryIndex(Path rootPath) {
        this.rootPath = rootPath;
        this.index = parseIndexFile(rootPath);
    }

    private Map<String, Set<Path>> parseIndexFile(Path rootPath) {
        Path indexFile = rootPath.resolve("index.json");
        ObjectMapper objectMapper = new ObjectMapper();
        TypeFactory typeFactory = objectMapper.getTypeFactory();
        try (BufferedReader reader = Files.newBufferedReader(indexFile)) {
            List<ModuleEntry> entries = objectMapper.readValue(
                    reader,
                    typeFactory.constructCollectionType(List.class, ModuleEntry.class)
            );
            Map<String, List<ModuleEntry>> moduleToEntries = entries.stream()
                    .collect(Collectors.groupingBy(ModuleEntry::getModule));
            Map<String, Set<Path>> index = new HashMap<>(moduleToEntries.size());
            for (Map.Entry<String, List<ModuleEntry>> entry : moduleToEntries.entrySet()) {
                String key = entry.getKey();
                Set<Path> dirs = entry.getValue()
                        .stream()
                        .flatMap(module -> Stream.concat(
                                Stream.of(module.getModuleDirectory()),
                                module.getRequires().stream().flatMap(e -> {
                                    List<ModuleEntry> moduleEntries = moduleToEntries.get(e);
                                    if (moduleEntries == null) {
                                        throw new IllegalStateException("Module " + module.getModule() + " requires module " + e + " which is not found in index");
                                    }
                                    return moduleEntries.stream().map(ModuleEntry::getModuleDirectory);
                                })
                        ))
                        .filter(Objects::nonNull)
                        .map(rootPath::resolve)
                        .collect(Collectors.toSet());
                index.put(key, dirs);
            }
            return index;
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }

    }

    /**
     * Returns the directory containing the candidate configurations for the given module.
     *
     * @param groupId the group of the module
     * @param artifactId the artifact of the module
     * @return the configuration directory
     */
    @Override
    public Set<Path> findConfigurationDirectories(String groupId, String artifactId) {
        String key = groupId + ":" + artifactId;
        if (!index.containsKey(key)) {
            return Collections.emptySet();
        }
        return index.get(key);
    }
}
