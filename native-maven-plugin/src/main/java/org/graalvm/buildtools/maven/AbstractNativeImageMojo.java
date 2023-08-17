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
import org.apache.maven.plugin.MojoExecution;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.descriptor.PluginDescriptor;
import org.apache.maven.plugins.annotations.Component;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.toolchain.ToolchainManager;
import org.graalvm.buildtools.maven.config.ExcludeConfigConfiguration;
import org.graalvm.buildtools.utils.NativeImageConfigurationUtils;
import org.graalvm.buildtools.utils.NativeImageUtils;
import org.graalvm.buildtools.utils.SharedConstants;

import javax.inject.Inject;
import java.io.File;
import java.io.InputStream;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Collections;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * @author Sebastien Deleuze
 */
public abstract class AbstractNativeImageMojo extends AbstractNativeMojo {
    protected static final String NATIVE_IMAGE_META_INF = "META-INF/native-image";
    protected static final String NATIVE_IMAGE_PROPERTIES_FILENAME = "native-image.properties";
    protected static final String NATIVE_IMAGE_DRY_RUN = "nativeDryRun";

    @Parameter(defaultValue = "${plugin}", readonly = true) // Maven 3 only
    protected PluginDescriptor plugin;

    @Parameter(defaultValue = "${session}", readonly = true)
    protected MavenSession session;

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

    @Parameter(property = NATIVE_IMAGE_DRY_RUN, defaultValue = "false")
    protected boolean dryRun;

    @Parameter(property = "requiredVersion")
    protected String requiredVersion;

    @Component
    protected ToolchainManager toolchainManager;

    @Inject
    protected AbstractNativeImageMojo() {
        imageClasspath = new ArrayList<>();
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

        cliArgs.add("-o");
        cliArgs.add(outputDirectory.toPath().toAbsolutePath() + File.separator + imageName);

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

        if (buildArgs != null && !buildArgs.isEmpty()) {
            for (String buildArg : buildArgs) {
                cliArgs.addAll(Arrays.asList(buildArg.split("\\s+")));
            }
        }

        List<String> actualCliArgs;
        if (useArgFile) {
            Path tmpDir = Paths.get("target", "tmp");
            actualCliArgs = new ArrayList<>(NativeImageUtils.convertToArgsFile(cliArgs, tmpDir));
        } else {
            actualCliArgs = cliArgs;
        }

        /* Main class comes last. It is kept outside argument files as GraalVM releases before JDK 21 fail to detect the mainClass in these files. */
        if (mainClass != null && !mainClass.equals(".")) {
            actualCliArgs.add(mainClass);
        }
        return Collections.unmodifiableList(actualCliArgs);
    }

    protected Path processSupportedArtifacts(Artifact artifact) throws MojoExecutionException {
        return processArtifact(artifact, "jar", "test-jar", "war");
    }

    protected Path processArtifact(Artifact artifact, String... artifactTypes) throws MojoExecutionException {
        File artifactFile = artifact.getFile();

        if (artifactFile == null) {
            logger.debug("Missing artifact file for artifact " + artifact + " (type: " + artifact.getType() + ")");
            return null;
        }

        if (Arrays.stream(artifactTypes).noneMatch(a -> a.equals(artifact.getType()))) {
            logger.warn("Ignoring ImageClasspath Entry '" + artifact + "' with unsupported type '" + artifact.getType() + "'");
            return null;
        }
        if (!artifactFile.exists()) {
            throw new MojoExecutionException("Missing jar-file for " + artifact + ". " +
                    "Ensure that " + plugin.getArtifactId() + " runs in package phase.");
        }

        Path jarFilePath = artifactFile.toPath();
        logger.debug("ImageClasspath Entry: " + artifact + " (" + jarFilePath.toUri() + ")");

        warnIfWrongMetaInfLayout(jarFilePath, artifact);
        return jarFilePath;
    }

    protected void addArtifactToClasspath(Artifact artifact) throws MojoExecutionException {
        Optional.ofNullable(processSupportedArtifacts(artifact)).ifPresent(imageClasspath::add);
    }

    protected void warnIfWrongMetaInfLayout(Path jarFilePath, Artifact artifact) throws MojoExecutionException {
        if (jarFilePath.toFile().isDirectory()) {
            logger.debug("Artifact `" + jarFilePath + "` is a directory.");
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
                            String example = NATIVE_IMAGE_META_INF + "/%s/%s/" + NATIVE_IMAGE_PROPERTIES_FILENAME;
                            example = String.format(example, artifact.getGroupId(), artifact.getArtifactId());
                            logger.warn("Properties file at '" + nativeImageProperty.toUri() + "' does not match the recommended '" + example + "' layout.");
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
        Set<Artifact> collected = new HashSet<>();
        // Must keep classpath order is the same with surefire test
        for (Artifact dependency : project.getArtifacts()) {
            if (getDependencyScopes().contains(dependency.getScope()) && collected.add(dependency)) {
                addArtifactToClasspath(dependency);
                maybeAddDependencyMetadata(dependency, file -> {
                    buildArgs.add("--exclude-config");
                    buildArgs.add(Pattern.quote(dependency.getFile().getAbsolutePath()));
                    buildArgs.add("^/META-INF/native-image/");
                });
            }
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
        imageClasspath.removeIf(entry -> !entry.toFile().exists());
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
        checkRequiredVersionIfNeeded();
        Path nativeImageExecutable = NativeImageConfigurationUtils.getNativeImage(logger);

        try {
            ProcessBuilder processBuilder = new ProcessBuilder(nativeImageExecutable.toString());
            processBuilder.command().addAll(getBuildArgs());

            if (environment != null) {
                processBuilder.environment().putAll(environment);
            }

            if (!outputDirectory.exists() && !outputDirectory.mkdirs()) {
                throw new MojoExecutionException("Failed creating output directory");
            }
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

    protected void checkRequiredVersionIfNeeded() throws MojoExecutionException {
        if (requiredVersion == null) {
            return;
        }
        Path nativeImageExecutable = NativeImageConfigurationUtils.getNativeImage(logger);
        try {
            ProcessBuilder processBuilder = new ProcessBuilder(nativeImageExecutable.toString());
            processBuilder.command().add("--version");
            Process versionCheckProcess = processBuilder.start();
            if (versionCheckProcess.waitFor() != 0) {
                String commandString = String.join(" ", processBuilder.command());
                throw new MojoExecutionException("Execution of " + commandString + " returned non-zero result");
            }
            InputStream inputStream = versionCheckProcess.getInputStream();
            String versionToCheck = new BufferedReader(
                    new InputStreamReader(inputStream, StandardCharsets.UTF_8))
                    .lines()
                    .collect(Collectors.joining("\n"));
            NativeImageUtils.checkVersion(requiredVersion, versionToCheck);

        } catch (IOException | InterruptedException e) {
            throw new MojoExecutionException("Checking GraalVM version with " + nativeImageExecutable + " failed", e);
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
            }
        }
    }



    protected void maybeAddReachabilityMetadata(List<String> configDirs) {
        if (isMetadataRepositoryEnabled() && !metadataRepositoryConfigurations.isEmpty()) {
            metadataRepositoryConfigurations.stream()
                    .map(configuration -> configuration.getDirectory().toAbsolutePath())
                    .map(Path::toFile)
                    .map(File::getAbsolutePath)
                    .forEach(configDirs::add);
        }
    }
}
