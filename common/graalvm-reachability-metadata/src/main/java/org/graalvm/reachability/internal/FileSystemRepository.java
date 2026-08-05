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
package org.graalvm.reachability.internal;

import org.graalvm.buildtools.utils.SchemaValidationUtils;
import org.graalvm.reachability.DirectoryConfiguration;
import org.graalvm.reachability.GraalVMReachabilityMetadataRepository;
import org.graalvm.reachability.Query;
import org.graalvm.reachability.internal.index.artifacts.SingleModuleJsonVersionToConfigDirectoryIndex;
import org.graalvm.reachability.internal.index.artifacts.VersionToConfigDirectoryIndex;
import org.graalvm.reachability.internal.index.modules.FileSystemModuleToConfigDirectoryIndex;
import org.graalvm.reachability.internal.index.modules.ModuleConfigurationDirectory;

import java.nio.file.Path;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * Queries an unpacked reachability metadata repository. §FS-common-libraries.5.
 */
public class FileSystemRepository implements GraalVMReachabilityMetadataRepository {

    private final FileSystemModuleToConfigDirectoryIndex moduleIndex;
    private final Logger logger;
    private final Map<Path, VersionToConfigDirectoryIndex> artifactIndexes;
    private final Path rootDirectory;

    public FileSystemRepository(Path rootDirectory) {
        this(rootDirectory, new Logger() {});
    }

    public FileSystemRepository(Path rootDirectory, Logger logger) {
        SchemaValidationUtils.validateSchemas(rootDirectory);
        this.moduleIndex = new FileSystemModuleToConfigDirectoryIndex(rootDirectory);
        this.logger = logger;
        this.artifactIndexes = new ConcurrentHashMap<>();
        this.rootDirectory = rootDirectory;
    }

    private static final String[] SUPPORTED_FORMATS = {".zip", ".tar.gz", ".tar.bz2"};

    public static String getArchiveFormat(String path) {
        String normalizedPath = path.toLowerCase();
        for (String format : SUPPORTED_FORMATS) {
            if (normalizedPath.endsWith(format)) {
                return format;
            }
        }
        return null;
    }

    public static boolean isSupportedArchiveFormat(String path) {
        return getArchiveFormat(path) != null;
    }

    @Override
    public Set<DirectoryConfiguration> findConfigurationsFor(Consumer<? super Query> queryBuilder) {
        DefaultQuery query = new DefaultQuery();
        queryBuilder.accept(query);
        List<DefaultArtifactQuery> artifacts = query.getArtifacts();
        Set<String> directlyQueriedModules = directlyQueriedModules(artifacts);
        Map<String, DirectoryConfiguration> configurations = new LinkedHashMap<>();
        // Select the complete query as one graph so requires never preempts a direct module query. §FS-common-libraries.5.1.
        for (DefaultArtifactQuery artifactQuery : artifacts) {
            for (ModuleConfigurationDirectory candidate : moduleIndex.findConfigurationDirectories(
                    artifactQuery.getGroupId(), artifactQuery.getArtifactId())) {
                boolean direct = isSameModule(candidate, artifactQuery);
                if (!direct && directlyQueriedModules.contains(moduleKey(candidate.getGroupId(), candidate.getArtifactId()))) {
                    continue;
                }
                Optional<String> forcedConfig = direct ? artifactQuery.getForcedConfig() : Optional.empty();
                selectConfiguration(candidate, artifactQuery.getVersion(), artifactQuery.isUseLatestVersion(), forcedConfig)
                        .ifPresent(configuration -> configurations.putIfAbsent(configurationKey(configuration), configuration));
            }
        }
        return Collections.unmodifiableSet(new LinkedHashSet<>(configurations.values()));
    }

    @Override
    public boolean isCoveredByRepository(Consumer<? super Query> queryBuilder) {
        DefaultQuery query = new DefaultQuery();
        queryBuilder.accept(query);
        List<DefaultArtifactQuery> artifacts = query.getArtifacts();
        Set<String> directlyQueriedModules = directlyQueriedModules(artifacts);
        // Check the complete query as one graph so requires never preempts a direct module query. §FS-common-libraries.5.1.
        for (DefaultArtifactQuery artifactQuery : artifacts) {
            for (ModuleConfigurationDirectory candidate : moduleIndex.findConfigurationDirectories(
                    artifactQuery.getGroupId(), artifactQuery.getArtifactId())) {
                boolean direct = isSameModule(candidate, artifactQuery);
                if (!direct && directlyQueriedModules.contains(moduleKey(candidate.getGroupId(), candidate.getArtifactId()))) {
                    continue;
                }
                Optional<String> forcedConfig = direct ? artifactQuery.getForcedConfig() : Optional.empty();
                if (isCovered(candidate, artifactQuery.getVersion(), artifactQuery.isUseLatestVersion(), forcedConfig)) {
                    return true;
                }
            }
        }
        return false;
    }

    private Optional<DirectoryConfiguration> selectConfiguration(ModuleConfigurationDirectory candidate,
            String version, boolean useLatestVersion, Optional<String> forcedConfig) {
        String groupId = candidate.getGroupId();
        String artifactId = candidate.getArtifactId();
        VersionToConfigDirectoryIndex index = artifactIndexes.computeIfAbsent(candidate.getDirectory(),
                SingleModuleJsonVersionToConfigDirectoryIndex::new);
        Optional<DirectoryConfiguration> configuration;
        if (forcedConfig.isPresent()) {
            String configVersion = forcedConfig.get();
            logger.log(groupId, artifactId, version, "Configuration is forced to version " + configVersion);
            configuration = index.findConfiguration(groupId, artifactId, configVersion);
        } else {
            configuration = index.findConfiguration(groupId, artifactId, version);
            if (!configuration.isPresent() && useLatestVersion) {
                logger.log(groupId, artifactId, version, "Configuration directory not found. Trying latest version.");
                configuration = index.findLatestConfigurationFor(groupId, artifactId, version);
                if (!configuration.isPresent()) {
                    logger.log(groupId, artifactId, version, "Latest version not found!");
                }
            }
        }
        Optional<DirectoryConfiguration> result = configuration;
        logger.log(groupId, artifactId, version, () -> result
                .map(value -> "Configuration directory is " + rootDirectory.relativize(value.getDirectory()))
                .orElse("missing."));
        return configuration;
    }

    private boolean isCovered(ModuleConfigurationDirectory candidate, String version, boolean useLatestVersion,
            Optional<String> forcedConfig) {
        Optional<DirectoryConfiguration> configuration = selectConfiguration(candidate, version, useLatestVersion, forcedConfig);
        if (configuration.isPresent()) {
            return true;
        }
        VersionToConfigDirectoryIndex index = artifactIndexes.get(candidate.getDirectory());
        if (index.isNotForNativeImage(candidate.getGroupId(), candidate.getArtifactId(), version)) {
            logger.log(candidate.getGroupId(), candidate.getArtifactId(), version, "Artifact is marked as not for native-image.");
            return true;
        }
        return false;
    }

    private static Set<String> directlyQueriedModules(List<DefaultArtifactQuery> artifacts) {
        Set<String> modules = new LinkedHashSet<>();
        for (DefaultArtifactQuery artifact : artifacts) {
            modules.add(moduleKey(artifact.getGroupId(), artifact.getArtifactId()));
        }
        return modules;
    }

    private static boolean isSameModule(ModuleConfigurationDirectory candidate, DefaultArtifactQuery artifact) {
        return candidate.getGroupId().equals(artifact.getGroupId()) && candidate.getArtifactId().equals(artifact.getArtifactId());
    }

    private static String moduleKey(String groupId, String artifactId) {
        return groupId + ':' + artifactId;
    }

    private static String configurationKey(DirectoryConfiguration configuration) {
        return moduleKey(configuration.getGroupId(), configuration.getArtifactId()) + ':' + configuration.getVersion()
                + ':' + configuration.getDirectory().normalize() + ':' + configuration.isOverride();
    }

    public Path getRootDirectory() {
        return rootDirectory;
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
