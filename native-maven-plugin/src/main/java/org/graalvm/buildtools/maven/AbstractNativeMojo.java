/*
 * Copyright (c) 2020, 2022, Oracle and/or its affiliates. All rights reserved.
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

package org.graalvm.buildtools.maven;

import static org.graalvm.buildtools.utils.SharedConstants.METADATA_REPO_URL_TEMPLATE;

import java.io.File;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Supplier;

import javax.inject.Inject;

import org.apache.maven.artifact.Artifact;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugins.annotations.Component;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.MavenProject;
import org.codehaus.plexus.logging.Logger;
import org.graalvm.buildtools.maven.config.MetadataRepositoryConfiguration;
import org.graalvm.buildtools.utils.FileUtils;
import org.graalvm.buildtools.utils.SharedConstants;
import org.graalvm.reachability.DirectoryConfiguration;
import org.graalvm.reachability.GraalVMReachabilityMetadataRepository;
import org.graalvm.reachability.internal.FileSystemRepository;

/**
 * @author Sebastien Deleuze
 */
public abstract class AbstractNativeMojo extends AbstractMojo {

    @Parameter(defaultValue = "${project}", readonly = true, required = true)
    protected MavenProject project;

    @Parameter(defaultValue = "${project.build.directory}/graalvm-reachability-metadata", required = true)
    protected File reachabilityMetadataOutputDirectory;

    @Parameter(alias = "metadataRepository")
    protected MetadataRepositoryConfiguration metadataRepositoryConfiguration;

    protected final Set<DirectoryConfiguration> metadataRepositoryConfigurations;

    protected GraalVMReachabilityMetadataRepository metadataRepository;

    @Component
    protected Logger logger;

    @Inject
    protected AbstractNativeMojo() {
        metadataRepositoryConfigurations = new HashSet<>();
    }

    protected boolean isMetadataRepositoryEnabled() {
        return metadataRepositoryConfiguration != null && metadataRepositoryConfiguration.isEnabled();
    }

    protected void configureMetadataRepository() {
        if (isMetadataRepositoryEnabled()) {
            Path repoPath = null;
            Path destinationRoot = reachabilityMetadataOutputDirectory.toPath();
            if (Files.exists(destinationRoot) && !Files.isDirectory(destinationRoot)) {
                throw new RuntimeException("Metadata repository must be a directory, please remove regular file at: " + destinationRoot);
            }
            try {
                Files.createDirectories(destinationRoot);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }

            if (metadataRepositoryConfiguration.getLocalPath() != null) {
                Path localPath = metadataRepositoryConfiguration.getLocalPath().toPath();
                Path destination = destinationRoot.resolve(FileUtils.hashFor(localPath.toUri()));
                repoPath = unzipLocalMetadata(localPath, destination);
            } else {
                URL targetUrl = metadataRepositoryConfiguration.getUrl();
                if (targetUrl == null) {
                    String version = metadataRepositoryConfiguration.getVersion();
                    if (version == null) {
                        version = SharedConstants.METADATA_REPO_DEFAULT_VERSION;
                    }
                    String metadataUrl = String.format(METADATA_REPO_URL_TEMPLATE, version);
                    try {
                        targetUrl = new URI(metadataUrl).toURL();
                        metadataRepositoryConfiguration.setUrl(targetUrl);
                    } catch (URISyntaxException | MalformedURLException e) {
                        throw new RuntimeException(e);
                    }
                }
                Path destination;
                try {
                    destination = destinationRoot.resolve(FileUtils.hashFor(targetUrl.toURI()));
                } catch (URISyntaxException e) {
                    throw new RuntimeException(e);
                }
                if (Files.exists(destination)) {
                    repoPath = destination;
                } else {
                    Optional<Path> download = downloadMetadata(targetUrl, destination);
                    if (download.isPresent()) {
                        logger.info("Downloaded GraalVM reachability metadata repository from " + targetUrl);
                        repoPath = unzipLocalMetadata(download.get(), destination);
                    }
                }
            }

            if (repoPath == null) {
                logger.warn("GraalVM reachability metadata repository is enabled, but no repository has been configured");
            } else {
                metadataRepository = new FileSystemRepository(repoPath, new FileSystemRepository.Logger() {
                    @Override
                    public void log(String groupId, String artifactId, String version, Supplier<String> message) {
                        logger.info(String.format("[graalvm reachability metadata repository for %s:%s:%s]: %s", groupId, artifactId, version, message.get()));
                    }
                });
            }
        }
    }

    public boolean isArtifactExcludedFromMetadataRepository(Artifact dependency) {
        if (metadataRepositoryConfiguration == null) {
            return false;
        } else {
            return metadataRepositoryConfiguration.isArtifactExcluded(dependency);
        }
    }

    protected void maybeAddDependencyMetadata(Artifact dependency, Consumer<File> excludeAction) {
        if (isMetadataRepositoryEnabled() && metadataRepository != null && !isArtifactExcludedFromMetadataRepository(dependency)) {
            Set<DirectoryConfiguration> configurations = metadataRepository.findConfigurationsFor(q -> {
                q.useLatestConfigWhenVersionIsUntested();
                q.forArtifact(artifact -> {
                    artifact.gav(String.join(":",
                            dependency.getGroupId(),
                            dependency.getArtifactId(),
                            dependency.getVersion()));
                    getMetadataVersion(dependency).ifPresent(artifact::forceConfigVersion);
                });
            });
            metadataRepositoryConfigurations.addAll(configurations);
            if (excludeAction != null && configurations.stream().anyMatch(DirectoryConfiguration::isOverride)) {
                excludeAction.accept(dependency.getFile());
            }
        }
    }

    protected Optional<String> getMetadataVersion(Artifact dependency) {
        if (metadataRepositoryConfiguration == null) {
            return Optional.empty();
        } else {
            return metadataRepositoryConfiguration.getMetadataVersion(dependency);
        }
    }

    protected Optional<Path> downloadMetadata(URL url, Path destination) {
        return FileUtils.download(url, destination, logger::error);
    }

    protected Path unzipLocalMetadata(Path localPath, Path destination) {
        if (Files.exists(localPath)) {
            if (FileUtils.isZip(localPath)) {
                if (!Files.exists(destination) && !destination.toFile().mkdirs()) {
                    throw new RuntimeException("Failed creating destination directory");
                }
                FileUtils.extract(localPath, destination, logger::error);
                return destination;
            } else if (Files.isDirectory(localPath)) {
                return localPath;
            } else {
                logger.warn("Unable to extract metadata repository from " + localPath + ". " +
                        "It needs to be either a ZIP file or an exploded directory");
            }
        } else {
            logger.error("GraalVM reachability metadata repository path does not exist: " + localPath);
        }
        return null;
    }
}
