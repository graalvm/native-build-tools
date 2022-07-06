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

import org.apache.maven.artifact.Artifact;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecution;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.descriptor.PluginDescriptor;
import org.apache.maven.plugins.annotations.Component;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.MavenProject;
import org.apache.maven.toolchain.ToolchainManager;
import org.codehaus.plexus.logging.Logger;
import org.graalvm.buildtools.Utils;
import org.graalvm.buildtools.maven.config.ExcludeConfigConfiguration;
import org.graalvm.buildtools.maven.config.MetadataRepositoryConfiguration;
import org.graalvm.buildtools.utils.FileUtils;
import org.graalvm.buildtools.utils.NativeImageUtils;
import org.graalvm.buildtools.utils.SharedConstants;
import org.graalvm.reachability.GraalVMReachabilityMetadataRepository;
import org.graalvm.reachability.internal.FileSystemRepository;

import javax.inject.Inject;
import java.io.File;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.graalvm.buildtools.utils.SharedConstants.METADATA_REPO_URL_TEMPLATE;

/**
 * @author Sebastien Deleuze
 */

public abstract class AbstractNativeMojo extends AbstractMojo {
    protected static final String NATIVE_IMAGE_META_INF = "META-INF/native-image";
    protected static final String NATIVE_IMAGE_PROPERTIES_FILENAME = "native-image.properties";
    protected static final String NATIVE_IMAGE_DRY_RUN = "nativeDryRun";

    @Parameter(defaultValue = "${plugin}", readonly = true) // Maven 3 only
    protected PluginDescriptor plugin;

    @Parameter(defaultValue = "${session}", readonly = true)
    protected MavenSession session;

    @Parameter(defaultValue = "${project}", readonly = true, required = true)
    protected MavenProject project;

    @Parameter(defaultValue = "${mojoExecution}")
    protected MojoExecution mojoExecution;

    @Parameter(property = "plugin.artifacts", required = true, readonly = true)
    protected List<Artifact> pluginArtifacts;

    @Parameter(defaultValue = "${project.build.directory}", property = "outputDir", required = true)
    protected File outputDirectory;

    @Parameter(property = "mainClass")
    protected String mainClass;

    @Parameter(property = "imageName", defaultValue = "${project.artifactId}")
    protected String imageName;

    @Parameter(property = "classpath")
    protected List<String> classpath;

    @Parameter(property = "classesDirectory")
    protected File classesDirectory;

    @Parameter(defaultValue = "${project.build.outputDirectory}", readonly = true, required = true)
    protected File defaultClassesDirectory;

    protected final List<Path> imageClasspath;

    protected final Set<Path> metadataRepositoryPaths;

    @Parameter(property = "debug", defaultValue = "false")
    protected boolean debug;

    @Parameter(property = "fallback", defaultValue = "false")
    protected boolean fallback;

    @Parameter(property = "verbose", defaultValue = "false")
    protected boolean verbose;

    @Parameter(property = "sharedLibrary", defaultValue = "false")
    protected boolean sharedLibrary;

    @Parameter(property = "quickBuild", defaultValue = "false")
    protected boolean quickBuild;

    @Parameter(property = "useArgFile")
    protected Boolean useArgFile;

    @Parameter(property = "buildArgs")
    protected List<String> buildArgs;

    @Parameter(defaultValue = "${project.build.directory}/native/generated", property = "resourcesConfigDirectory", required = true)
    protected File resourcesConfigDirectory;

    @Parameter(property = "agentResourceDirectory")
    protected File agentResourceDirectory;

    @Parameter(property = "excludeConfig")
    protected List<ExcludeConfigConfiguration> excludeConfig;

    @Parameter(property = "environmentVariables")
    protected Map<String, String> environment;

    @Parameter(property = "systemPropertyVariables")
    protected Map<String, String> systemProperties;

    @Parameter(property = "configurationFileDirectories")
    protected List<String> configFiles;

    @Parameter(property = "jvmArgs")
    protected List<String> jvmArgs;

    @Parameter(alias = "metadataRepository")
    protected MetadataRepositoryConfiguration metadataRepositoryConfiguration;

    @Parameter(property = NATIVE_IMAGE_DRY_RUN, defaultValue = "false")
    protected boolean dryRun;

    protected GraalVMReachabilityMetadataRepository metadataRepository;

    @Component
    protected Logger logger;

    @Component
    protected ToolchainManager toolchainManager;

    @Inject
    protected AbstractNativeMojo() {
        imageClasspath = new ArrayList<>();
        metadataRepositoryPaths = new HashSet<>();
        useArgFile = SharedConstants.IS_WINDOWS;
    }

    protected List<String> getBuildArgs() throws MojoExecutionException {
        final List<String> cliArgs = new ArrayList<>();

        if (excludeConfig != null) {
            excludeConfig.forEach(entry -> {
                cliArgs.add("--exclude-config");
                cliArgs.add(entry.getJarPath());
                cliArgs.add(String.format("\"%s\"", entry.getResourcePattern()));
            });
        }

        cliArgs.add("-cp");
        cliArgs.add(getClasspath());

        if (debug) {
            cliArgs.add("-g");
        }
        if (!fallback) {
            cliArgs.add("--no-fallback");
        }
        if (verbose) {
            cliArgs.add("--verbose");
        }
        if (sharedLibrary) {
            cliArgs.add("--shared");
        }

        // Let's allow user to specify environment option to toggle quick build.
        String quickBuildEnv = System.getenv("GRAALVM_QUICK_BUILD");
        if (quickBuildEnv != null) {
            logger.warn("Quick build environment variable is set.");
            quickBuild = quickBuildEnv.isEmpty() || Boolean.parseBoolean(quickBuildEnv);
        }

        if (quickBuild) {
            cliArgs.add("-Ob");
        }

        cliArgs.add("-H:Path=" + outputDirectory.toPath().toAbsolutePath());
        cliArgs.add("-H:Name=" + imageName);

        if (systemProperties != null) {
            for (Map.Entry<String, String> entry : systemProperties.entrySet()) {
                cliArgs.add("-D" + entry.getKey() + "=" + entry.getValue());
            }
        }

        if (jvmArgs != null) {
            jvmArgs.forEach(jvmArg -> cliArgs.add("-J" + jvmArg));
        }

        maybeAddGeneratedResourcesConfig(buildArgs);
        maybeAddReachabilityMetadata(configFiles);

        if (configFiles != null && !configFiles.isEmpty()) {
            cliArgs.add("-H:ConfigurationFileDirectories=" +
                    configFiles.stream()
                    .map(Paths::get)
                    .map(Path::toAbsolutePath)
                    .map(Path::toString)
                    .collect(Collectors.joining(","))
            );
        }

        if (mainClass != null && !mainClass.equals(".")) {
            cliArgs.add("-H:Class=" + mainClass);
        }

        if (buildArgs != null && !buildArgs.isEmpty()) {
            for (String buildArg : buildArgs) {
                cliArgs.addAll(Arrays.asList(buildArg.split("\\s+")));
            }
        }

        if (useArgFile) {
            return NativeImageUtils.convertToArgsFile(cliArgs);
        }
        return Collections.unmodifiableList(cliArgs);
    }

    protected Path processArtifact(Artifact artifact, String artifactType) throws MojoExecutionException {
        File artifactFile = artifact.getFile();
        if (!artifactType.equals(artifact.getType())) {
            logger.warn("Ignoring non-jar type ImageClasspath Entry " + artifact);
            return null;
        }
        if (!artifactFile.exists()) {
            throw new MojoExecutionException("Missing jar-file for " + artifact + ". " +
                    "Ensure that " + plugin.getArtifactId() + " runs in package phase.");
        }

        Path jarFilePath = artifactFile.toPath();
        logger.info("ImageClasspath Entry: " + artifact + " (" + jarFilePath.toUri() + ")");

        warnIfWrongMetaInfLayout(jarFilePath, artifact);
        return jarFilePath;
    }

    protected void addArtifactToClasspath(Artifact artifact) throws MojoExecutionException {
        Optional.ofNullable(processArtifact(artifact, "jar")).ifPresent(imageClasspath::add);
    }

    protected void warnIfWrongMetaInfLayout(Path jarFilePath, Artifact artifact) throws MojoExecutionException {
        if (jarFilePath.toFile().isDirectory()) {
            logger.warn("Artifact `" + jarFilePath + "` is a directory.");
            return;
        }
        URI jarFileURI = URI.create("jar:" + jarFilePath.toUri());
        try (FileSystem jarFS = FileSystems.newFileSystem(jarFileURI, Collections.emptyMap())) {
            Path nativeImageMetaInfBase = jarFS.getPath("/" + NATIVE_IMAGE_META_INF);
            if (Files.isDirectory(nativeImageMetaInfBase)) {
                try (Stream<Path> stream = Files.walk(nativeImageMetaInfBase)) {
                    List<Path> nativeImageProperties = stream
                            .filter(p -> p.endsWith(NATIVE_IMAGE_PROPERTIES_FILENAME)).collect(Collectors.toList());
                    for (Path nativeImageProperty : nativeImageProperties) {
                        Path relativeSubDir = nativeImageMetaInfBase.relativize(nativeImageProperty).getParent();
                        boolean valid = relativeSubDir != null && (relativeSubDir.getNameCount() == 2);
                        valid = valid && relativeSubDir.getName(0).toString().equals(artifact.getGroupId());
                        valid = valid && relativeSubDir.getName(1).toString().equals(artifact.getArtifactId());
                        if (!valid) {
                            String example = NATIVE_IMAGE_META_INF + "/${groupId}/${artifactId}/" + NATIVE_IMAGE_PROPERTIES_FILENAME;
                            logger.warn(nativeImageProperty.toUri() + " does not match recommended " + example + " layout.");
                        }
                    }
                }
            }
        } catch (IOException e) {
            throw new MojoExecutionException("Artifact " + artifact + "cannot be added to image classpath", e);
        }
    }

    protected abstract List<String> getDependencyScopes();

    protected void addDependenciesToClasspath() throws MojoExecutionException {
        configureMetadataRepository();
        for (Artifact dependency : project.getArtifacts().stream()
                .filter(artifact -> getDependencyScopes().contains(artifact.getScope()))
                .collect(Collectors.toSet())) {
            addArtifactToClasspath(dependency);
            maybeAddDependencyMetadata(dependency);
        }
    }

    /**
     * Returns path to where application classes are stored, or jar artifact if it is produced.
     * @return Path to application classes
     * @throws MojoExecutionException failed getting main build path
     */
    protected Path getMainBuildPath() throws MojoExecutionException {
        if (classesDirectory != null) {
            return classesDirectory.toPath();
        } else {
            Path artifactPath = processArtifact(project.getArtifact(), project.getPackaging());
            if (artifactPath != null) {
                return artifactPath;
            } else {
                return defaultClassesDirectory.toPath();
            }
        }
    }

    protected void populateApplicationClasspath() throws MojoExecutionException {
        imageClasspath.add(getMainBuildPath());
    }

    protected void populateClasspath() throws MojoExecutionException {
        if (classpath != null && !classpath.isEmpty()) {
            imageClasspath.addAll(classpath.stream()
                    .map(Paths::get)
                    .map(Path::toAbsolutePath)
                    .collect(Collectors.toSet())
            );
        } else {
            populateApplicationClasspath();
            addDependenciesToClasspath();
        }
    }

    protected String getClasspath() throws MojoExecutionException {
        populateClasspath();
        if (imageClasspath.isEmpty()) {
            throw new MojoExecutionException("Image classpath is empty. " +
                    "Check if your classpath configuration is correct.");
        }
        return imageClasspath.stream()
                .map(Path::toString)
                .collect(Collectors.joining(File.pathSeparator));
    }

    protected void buildImage() throws MojoExecutionException {
        Path nativeImageExecutable = Utils.getNativeImage(logger);

        try {
            ProcessBuilder processBuilder = new ProcessBuilder(nativeImageExecutable.toString());
            processBuilder.command().addAll(getBuildArgs());

            if (environment != null) {
                processBuilder.environment().putAll(environment);
            }

            if (!outputDirectory.exists() && !outputDirectory.mkdirs()) {
                throw new MojoExecutionException("Failed creating output directory");
            }
            processBuilder.directory(outputDirectory);
            processBuilder.inheritIO();

            String commandString = String.join(" ", processBuilder.command());
            logger.info("Executing: " + commandString);

            if (dryRun) {
                logger.warn("Skipped native-image building due to `" + NATIVE_IMAGE_DRY_RUN + "` being specified.");
                return;
            }

            Process imageBuildProcess = processBuilder.start();
            if (imageBuildProcess.waitFor() != 0) {
                throw new MojoExecutionException("Execution of " + commandString + " returned non-zero result");
            }
        } catch (IOException | InterruptedException e) {
            throw new MojoExecutionException("Building image with " + nativeImageExecutable + " failed", e);
        }
    }

    protected void maybeAddGeneratedResourcesConfig(List<String> into) {
        if (resourcesConfigDirectory.exists() || agentResourceDirectory != null) {
            File[] dirs = resourcesConfigDirectory.listFiles();
            Stream<File> configDirs =
                    Stream.concat(dirs == null ? Stream.empty() : Arrays.stream(dirs),
                            agentResourceDirectory == null ? Stream.empty() : Stream.of(agentResourceDirectory).filter(File::isDirectory));

            String value = configDirs.map(File::getAbsolutePath).collect(Collectors.joining(","));
            if (!value.isEmpty()) {
                into.add("-H:ConfigurationFileDirectories=" + value);
                if (agentResourceDirectory != null && agentResourceDirectory.isDirectory()) {
                    // The generated reflect config file contains references to java.*
                    // and org.apache.maven.surefire that we'd need to remove using
                    // a proper JSON parser/writer instead
                    into.add("-H:+AllowIncompleteClasspath");
                }
            }
        }
    }

    protected boolean isMetadataRepositoryEnabled() {
        return metadataRepositoryConfiguration != null && metadataRepositoryConfiguration.isEnabled();
    }

    protected void configureMetadataRepository() {
        if (isMetadataRepositoryEnabled()) {
            Path repoPath = null;
            if (metadataRepositoryConfiguration.getLocalPath() != null) {
                Path localPath = metadataRepositoryConfiguration.getLocalPath().toPath();
                repoPath = unzipLocalMetadata(localPath);
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
                Optional<Path> download = downloadMetadata(metadataRepositoryConfiguration.getUrl());
                if (download.isPresent()) {
                    logger.info("Downloaded GraalVM reachability metadata repository from " + metadataRepositoryConfiguration.getUrl());
                    repoPath = unzipLocalMetadata(download.get());
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

    protected void maybeAddReachabilityMetadata(List<String> configDirs) {
        if (isMetadataRepositoryEnabled() && !metadataRepositoryPaths.isEmpty()) {
            String arg = metadataRepositoryPaths.stream()
                    .map(Path::toAbsolutePath)
                    .map(Path::toFile)
                    .map(File::getAbsolutePath)
                    .collect(Collectors.joining(","));
            if (!arg.isEmpty()) {
                configDirs.add(arg);
            }
        }
    }

    protected void maybeAddDependencyMetadata(Artifact dependency) {
        if (isMetadataRepositoryEnabled() && metadataRepository != null && !isArtifactExcludedFromMetadataRepository(dependency)) {
            metadataRepositoryPaths.addAll(metadataRepository.findConfigurationDirectoriesFor(q -> {
                q.useLatestConfigWhenVersionIsUntested();
                q.forArtifact(artifact -> {
                    artifact.gav(String.join(":",
                            dependency.getGroupId(),
                            dependency.getArtifactId(),
                            dependency.getVersion()));
                    getMetadataVersion(dependency).ifPresent(artifact::forceConfigVersion);
                });
            }));
        }
    }

    protected Optional<String> getMetadataVersion(Artifact dependency) {
        if (metadataRepositoryConfiguration == null) {
            return Optional.empty();
        } else {
            return metadataRepositoryConfiguration.getMetadataVersion(dependency);
        }
    }

    protected Optional<Path> downloadMetadata(URL url) {
        Path destination = outputDirectory.toPath().resolve("graalvm-reachability-metadata");
        return FileUtils.download(url, destination, logger::error);
    }

    protected Path unzipLocalMetadata(Path localPath) {
        if (Files.exists(localPath)) {
            if (FileUtils.isZip(localPath)) {
                Path destination = outputDirectory.toPath().resolve("graalvm-reachability-metadata");
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
