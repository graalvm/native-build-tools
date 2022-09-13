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
package org.graalvm.reachability.internal.index.artifacts;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.type.TypeFactory;
import org.graalvm.reachability.DirectoryConfiguration;
import org.graalvm.reachability.internal.UncheckedIOException;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Predicate;
import java.util.stream.Collectors;

public class SingleModuleJsonVersionToConfigDirectoryIndex implements VersionToConfigDirectoryIndex {
    private final Path moduleRoot;
    private final Map<String, List<Artifact>> index;

    public SingleModuleJsonVersionToConfigDirectoryIndex(Path moduleRoot) {
        this.moduleRoot = moduleRoot;
        this.index = parseIndexFile(moduleRoot);
    }

    private Map<String, List<Artifact>> parseIndexFile(Path rootPath) {
        Path indexFile = rootPath.resolve("index.json");
        ObjectMapper objectMapper = new ObjectMapper();
        TypeFactory typeFactory = objectMapper.getTypeFactory();
        try (BufferedReader reader = Files.newBufferedReader(indexFile)) {
            List<Artifact> entries = objectMapper.readValue(
                    reader,
                    typeFactory.constructCollectionType(List.class, Artifact.class)
            );
            return entries.stream()
                    .collect(Collectors.groupingBy(Artifact::getModule));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }

    }

    /**
     * Returns the configuration directory for the requested artifact.
     *
     * @param groupId the group ID of the artifact
     * @param artifactId the artifact ID of the artifact
     * @param version the version of the artifact
     * @return a configuration directory, or empty if no configuration directory is available
     */
    @Override
    public Optional<DirectoryConfiguration> findConfiguration(String groupId, String artifactId, String version) {
        return findConfigurationFor(groupId, artifactId, artifact -> artifact.getVersions().contains(version));
    }

    /**
     * Returns the latest configuration directory for the requested artifact.
     *
     * @param groupId the group ID of the artifact
     * @param artifactId the artifact ID of the artifact
     * @return a configuration directory, or empty if no configuration directory is available
     */
    @Override
    public Optional<DirectoryConfiguration> findLatestConfigurationFor(String groupId, String artifactId) {
        return findConfigurationFor(groupId, artifactId, Artifact::isLatest);
    }

    private Optional<DirectoryConfiguration> findConfigurationFor(String groupId, String artifactId, Predicate<? super Artifact> predicate) {
        String module = groupId + ":" + artifactId;
        List<Artifact> artifacts = index.get(module);
        if (artifacts == null) {
            return Optional.empty();
        }
        return artifacts.stream()
                .filter(artifact -> artifact.getModule().equals(module))
                .filter(predicate)
                .findFirst()
                .map(artifact -> new DirectoryConfiguration(moduleRoot.resolve(artifact.getDirectory()), artifact.isOverride()));
    }

}
