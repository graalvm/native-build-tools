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
package org.graalvm.buildtools.gradle.internal;

import org.graalvm.buildtools.utils.ExponentialBackoff;
import org.graalvm.buildtools.utils.FileUtils;
import org.graalvm.reachability.DirectoryConfiguration;
import org.graalvm.reachability.GraalVMReachabilityMetadataRepository;
import org.graalvm.reachability.Query;
import org.graalvm.reachability.internal.FileSystemRepository;
import org.gradle.api.artifacts.ModuleVersionIdentifier;
import org.gradle.api.file.ArchiveOperations;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.file.FileSystemOperations;
import org.gradle.api.logging.LogLevel;
import org.gradle.api.logging.Logger;
import org.gradle.api.logging.Logging;
import org.gradle.api.provider.Property;
import org.gradle.api.services.BuildService;
import org.gradle.api.services.BuildServiceParameters;

import javax.inject.Inject;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.channels.Channels;
import java.nio.channels.ReadableByteChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Collection;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Supplier;

public abstract class GraalVMReachabilityMetadataService implements BuildService<GraalVMReachabilityMetadataService.Params>, GraalVMReachabilityMetadataRepository {
    private static final Logger LOGGER = Logging.getLogger(GraalVMReachabilityMetadataService.class);

    private final GraalVMReachabilityMetadataRepository repository;

    @Inject
    protected abstract ArchiveOperations getArchiveOperations();

    @Inject
    protected abstract FileSystemOperations getFileOperations();

    public interface Params extends BuildServiceParameters {
        Property<Integer> getBackoffMaxRetries();

        Property<Integer> getInitialBackoffMillis();

        Property<LogLevel> getLogLevel();

        Property<URI> getUri();

        DirectoryProperty getCacheDir();
    }

    public GraalVMReachabilityMetadataService() throws URISyntaxException {
        URI uri = getParameters().getUri().get();
        this.repository = newRepository(uri);
    }

    private GraalVMReachabilityMetadataRepository newRepository(URI uri) throws URISyntaxException {
        String cacheKey = FileUtils.hashFor(uri);
        String path = uri.getPath();
        LogLevel logLevel = getParameters().getLogLevel().get();
        if (uri.getScheme().equals("file")) {
            File localFile = new File(uri);
            if (FileSystemRepository.isSupportedArchiveFormat(path)) {
                return newRepositoryFromZipFile(cacheKey, localFile, logLevel);
            }
            return newRepositoryFromDirectory(localFile.toPath(), logLevel);
        }
        String format = FileSystemRepository.getArchiveFormat(path);
        if (format != null) {
            File zipped = getParameters().getCacheDir().file(cacheKey + "/archive" + format).get().getAsFile();
            if (!zipped.exists()) {
                File cacheDirParent = zipped.getParentFile();
                if (cacheDirParent.exists()) {
                    if (!cacheDirParent.isDirectory()) {
                        throw new RuntimeException("Cache directory path must not exist or must be a directory: " + cacheDirParent.getAbsolutePath());
                    }
                } else {
                    try {
                        Files.createDirectories(cacheDirParent.toPath());
                    } catch (IOException e) {
                        throw new RuntimeException(e);
                    }
                }
                ExponentialBackoff.get()
                    .withMaxRetries(getParameters().getBackoffMaxRetries().get())
                    .withInitialWaitPeriod(Duration.ofMillis(getParameters().getInitialBackoffMillis().get()))
                    .execute(() -> {
                        try (ReadableByteChannel readableByteChannel = Channels.newChannel(uri.toURL().openStream())) {
                            try (FileOutputStream fileOutputStream = new FileOutputStream(zipped)) {
                                fileOutputStream.getChannel().transferFrom(readableByteChannel, 0, Long.MAX_VALUE);
                            }
                        }
                    });
            }
            return newRepositoryFromZipFile(cacheKey, zipped, logLevel);
        }
        throw new UnsupportedOperationException("Remote URI must point to a zip, a tar.gz or tar.bz2 file");
    }

    private FileSystemRepository newRepositoryFromZipFile(String cacheKey, File localFile, LogLevel logLevel) {
        File explodedEntry = getParameters().getCacheDir().file(cacheKey + "/exploded").get().getAsFile();
        if (!explodedEntry.exists()) {
            if (explodedEntry.getParentFile().isDirectory() || explodedEntry.getParentFile().mkdirs()) {
                LOGGER.info("Extracting {} to {}", localFile, explodedEntry);
                getFileOperations().copy(spec -> {
                    if (localFile.getName().endsWith(".zip")) {
                        spec.from(getArchiveOperations().zipTree(localFile));
                    } else if (localFile.getName().endsWith(".tar.gz")) {
                        spec.from(getArchiveOperations().tarTree(localFile));
                    } else if (localFile.getName().endsWith(".tar.bz2")) {
                        spec.from(getArchiveOperations().tarTree(localFile));
                    }
                    spec.into(explodedEntry);
                });
            }
        }
        return newRepositoryFromDirectory(explodedEntry.toPath(), logLevel);
    }

    private FileSystemRepository newRepositoryFromDirectory(Path path, LogLevel logLevel) {
        if (Files.isDirectory(path)) {
            return new FileSystemRepository(path, new FileSystemRepository.Logger() {
                @Override
                public void log(String groupId, String artifactId, String version, Supplier<String> message) {
                    LOGGER.log(logLevel, "[graalvm reachability metadata repository for {}:{}:{}]: {}", groupId, artifactId, version, message.get());
                }
            });
        } else {
            throw new IllegalArgumentException("GraalVM reachability metadata repository URI must point to a directory");
        }
    }

    /**
     * Performs a generic query on the repository, returning a list of
     * configurations. The query may be parameterized with
     * a number of artifacts, and can be used to refine behavior, for
     * example if a configuration directory isn't available for a
     * particular artifact version.
     *
     * @param queryBuilder the query builder
     * @return the set of configurations matching the query
     */
    @Override
    public Set<DirectoryConfiguration> findConfigurationsFor(Consumer<? super Query> queryBuilder) {
        return repository.findConfigurationsFor(queryBuilder);
    }

    /**
     * Returns a list of configuration directories for the specified artifact.
     * There may be more than one configuration directory for a given artifact,
     * but the list may also be empty if the repository doesn't contain any.
     * Never null.
     *
     * @param gavCoordinates the artifact GAV coordinates (group:artifact:version)
     * @return a list of configurations
     */
    @Override
    public Set<DirectoryConfiguration> findConfigurationsFor(String gavCoordinates) {
        return repository.findConfigurationsFor(gavCoordinates);
    }

    /**
     * Returns the set of configuration directories for all the modules supplied
     * as an argument.
     *
     * @param modules the list of modules
     * @return the set of configurations
     */
    @Override
    public Set<DirectoryConfiguration> findConfigurationsFor(Collection<String> modules) {
        return repository.findConfigurationsFor(modules);
    }

    public Set<DirectoryConfiguration> findConfigurationsFor(Set<String> excludedModules, Map<String, String> forcedVersions, ModuleVersionIdentifier moduleVersion) {
        Objects.requireNonNull(moduleVersion);
        String groupAndArtifact = moduleVersion.getGroup() + ":" + moduleVersion.getName();
        return findConfigurationsFor(query -> {
            if (!excludedModules.contains(groupAndArtifact)) {
                query.forArtifact(artifact -> {
                    artifact.gav(groupAndArtifact + ":" + moduleVersion.getVersion());
                    if (forcedVersions.containsKey(groupAndArtifact)) {
                        artifact.forceConfigVersion(forcedVersions.get(groupAndArtifact));
                    }
                });
            }
            query.useLatestConfigWhenVersionIsUntested();
        });
    }
}
