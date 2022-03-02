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
package org.graalvm.nativeconfig.internal;

import org.graalvm.nativeconfig.NativeConfigurationRepository;
import org.graalvm.nativeconfig.Query;
import org.graalvm.nativeconfig.internal.index.artifacts.SingleModuleJsonVersionToConfigDirectoryIndex;
import org.graalvm.nativeconfig.internal.index.artifacts.VersionToConfigDirectoryIndex;
import org.graalvm.nativeconfig.internal.index.modules.FileSystemModuleToConfigDirectoryIndex;

import java.nio.file.Path;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.stream.Collectors;

public class FileSystemRepository implements NativeConfigurationRepository {
    private final FileSystemModuleToConfigDirectoryIndex moduleIndex;
    private final Logger logger;
    private final Map<Path, VersionToConfigDirectoryIndex> artifactIndexes;
    private final Path rootDirectory;

    public FileSystemRepository(Path rootDirectory) {
        this(rootDirectory, new Logger() {});
    }

    public FileSystemRepository(Path rootDirectory, Logger logger) {
        this.moduleIndex = new FileSystemModuleToConfigDirectoryIndex(rootDirectory);
        this.logger = logger;
        this.artifactIndexes = new ConcurrentHashMap<>();
        this.rootDirectory = rootDirectory;
    }

    @Override
    public Set<Path> findConfigurationDirectoriesFor(Consumer<? super Query> queryBuilder) {
        DefaultQuery query = new DefaultQuery();
        queryBuilder.accept(query);
        return query.getArtifacts()
                .stream()
                .flatMap(artifactQuery -> {
                    String groupId = artifactQuery.getGroupId();
                    String artifactId = artifactQuery.getArtifactId();
                    String version = artifactQuery.getVersion();
                    return moduleIndex.findConfigurationDirectories(groupId, artifactId)
                            .stream()
                            .map(dir -> {
                                VersionToConfigDirectoryIndex index = artifactIndexes.computeIfAbsent(dir, SingleModuleJsonVersionToConfigDirectoryIndex::new);
                                if (artifactQuery.getForcedConfig().isPresent()) {
                                    String configVersion = artifactQuery.getForcedConfig().get();
                                    logger.log(groupId, artifactId, version, "Configuration is forced to version " + configVersion);
                                    return index.findForcedConfiguration(configVersion);
                                }
                                Optional<Path> configurationDirectory = index.findConfigurationDirectory(groupId, artifactId, version);
                                if (!configurationDirectory.isPresent() && artifactQuery.isUseLatestVersion()) {
                                    logger.log(groupId, artifactId, version, "Configuration directory not found. Trying latest version.");
                                    configurationDirectory = index.findLatestConfigurationFor(groupId, artifactId);
                                    if (!configurationDirectory.isPresent()) {
                                        logger.log(groupId, artifactId, version, "Latest version not found!");
                                    }
                                }
                                Optional<Path> finalConfigurationDirectory = configurationDirectory;
                                logger.log(groupId, artifactId, version, () -> {
                                    if (finalConfigurationDirectory.isPresent()) {
                                        Path path = finalConfigurationDirectory.get();
                                        return "Configuration directory is " + rootDirectory.relativize(path);
                                    }
                                    return "missing.";
                                });
                                return configurationDirectory;
                            })
                            .filter(Optional::isPresent)
                            .map(Optional::get);
                })
                .collect(Collectors.toSet());
    }

    /**
     * Allows getting insights about how configuration is picked.
     */
    public interface Logger {
        default void log(String groupId, String artifactId, String version, String message) {
            log(groupId, artifactId, version, () -> message);
        }

        default void log(String groupId, String artifactId, String version, Supplier<String> message) {

        }
    }
}
