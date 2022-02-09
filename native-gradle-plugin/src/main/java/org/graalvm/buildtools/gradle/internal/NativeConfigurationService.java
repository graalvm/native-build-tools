/*
 * Copyright 2003-2021 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.graalvm.buildtools.gradle.internal;

import org.graalvm.nativeconfig.NativeConfigurationRepository;
import org.graalvm.nativeconfig.Query;
import org.graalvm.nativeconfig.internal.FileSystemRepository;
import org.gradle.api.file.ArchiveOperations;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.file.FileSystemOperations;
import org.gradle.api.logging.Logger;
import org.gradle.api.logging.Logging;
import org.gradle.api.provider.Property;
import org.gradle.api.services.BuildService;
import org.gradle.api.services.BuildServiceParameters;

import javax.inject.Inject;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.math.BigInteger;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.channels.Channels;
import java.nio.channels.ReadableByteChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Collection;
import java.util.Set;
import java.util.function.Consumer;

public abstract class NativeConfigurationService implements BuildService<NativeConfigurationService.Params>, NativeConfigurationRepository {
    private static final Logger LOGGER = Logging.getLogger(NativeConfigurationService.class);

    private final NativeConfigurationRepository repository;

    @Inject
    protected abstract ArchiveOperations getArchiveOperations();

    @Inject
    protected abstract FileSystemOperations getFileOperations();

    public interface Params extends BuildServiceParameters {
        Property<URI> getUri();

        DirectoryProperty getCacheDir();
    }

    public NativeConfigurationService() throws URISyntaxException {
        URI uri = getParameters().getUri().get();
        this.repository = newRepository(uri);
    }

    private static String hashFor(URI uri) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-1");
            byte[] messageDigest = md.digest(md.digest(uri.toString().getBytes("utf-8")));
            BigInteger no = new BigInteger(1, messageDigest);
            StringBuilder digest = new StringBuilder(no.toString(16));
            while (digest.length() < 32) {
                digest.insert(0, "0");
            }
            return digest.toString();
        } catch (NoSuchAlgorithmException | UnsupportedEncodingException e) {
            throw new UnsupportedOperationException(e);
        }
    }

    private NativeConfigurationRepository newRepository(URI uri) throws URISyntaxException {
        String cacheKey = hashFor(uri);
        String path = uri.getPath();
        if (uri.getScheme().equals("file")) {
            File localFile = new File(uri);
            if (isSupportedZipFormat(path)) {
                return newRepositoryFromZipFile(cacheKey, localFile);
            }
            return newRepositoryFromDirectory(localFile.toPath());
        }
        if (isSupportedZipFormat(path)) {
            File zipped = getParameters().getCacheDir().file(cacheKey + "/archive").get().getAsFile();
            if (!zipped.exists()) {
                try (ReadableByteChannel readableByteChannel = Channels.newChannel(uri.toURL().openStream())) {
                    try (FileOutputStream fileOutputStream = new FileOutputStream(zipped)) {
                        fileOutputStream.getChannel().transferFrom(readableByteChannel, 0, Long.MAX_VALUE);
                    }
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            }
            return newRepositoryFromZipFile(cacheKey, zipped);
        }
        throw new UnsupportedOperationException("Remote URI must point to a zip, a tar.gz or tar.bz2 file");
    }

    private static boolean isSupportedZipFormat(String path) {
        return path.endsWith(".zip") || path.endsWith(".tar.gz") || path.endsWith(".tar.bz2");
    }

    private FileSystemRepository newRepositoryFromZipFile(String cacheKey, File localFile) {
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
        return newRepositoryFromDirectory(explodedEntry.toPath());
    }

    private FileSystemRepository newRepositoryFromDirectory(Path path) {
        if (Files.isDirectory(path)) {
            return new FileSystemRepository(path);
        } else {
            throw new IllegalArgumentException("Native configuration repository URI must point to a directory");
        }
    }

    /**
     * Performs a generic query on the repository, returning a list of
     * configuration directories. The query may be parameterized with
     * a number of artifacts, and can be used to refine behavior, for
     * example if a configuration directory isn't available for a
     * particular artifact version.
     *
     * @param queryBuilder the query builder
     * @return the set of configuration directories matching the query
     */
    @Override
    public Set<Path> findConfigurationDirectoriesFor(Consumer<? super Query> queryBuilder) {
        return repository.findConfigurationDirectoriesFor(queryBuilder);
    }

    /**
     * Returns a list of configuration directories for the specified artifact.
     * There may be more than one configuration directory for a given artifact,
     * but the list may also be empty if the repository doesn't contain any.
     * Never null.
     *
     * @param gavCoordinates the artifact GAV coordinates (group:artifact:version)
     * @return a list of configuration directories
     */
    @Override
    public Set<Path> findConfigurationDirectoriesFor(String gavCoordinates) {
        return repository.findConfigurationDirectoriesFor(gavCoordinates);
    }

    /**
     * Returns the set of configuration directories for all the modules supplied
     * as an argument.
     *
     * @param modules the list of modules
     * @return the set of configuration directories
     */
    @Override
    public Set<Path> findConfigurationDirectoriesFor(Collection<String> modules) {
        return repository.findConfigurationDirectoriesFor(modules);
    }
}
