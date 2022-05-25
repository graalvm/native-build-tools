/*
 * Copyright (c) 2020, 2021, Oracle and/or its affiliates. All rights reserved.
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
import org.apache.maven.model.ConfigurationContainer;
import org.apache.maven.model.Dependency;
import org.apache.maven.model.Plugin;
import org.apache.maven.model.PluginExecution;
import org.apache.maven.plugin.MojoExecution;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.PluginParameterExpressionEvaluator;
import org.apache.maven.plugin.descriptor.PluginDescriptor;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.codehaus.plexus.component.configurator.expression.ExpressionEvaluationException;
import org.codehaus.plexus.util.xml.Xpp3Dom;
import org.graalvm.buildtools.Utils;
import org.graalvm.buildtools.maven.config.MetadataRepositoryConfiguration;
import org.graalvm.buildtools.utils.NativeImageUtils;
import org.graalvm.reachability.JvmReachabilityMetadataRepository;
import org.graalvm.reachability.internal.FileSystemRepository;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.function.BiFunction;
import java.util.function.Supplier;
import java.util.stream.Collectors;

@Mojo(name = "build", defaultPhase = LifecyclePhase.PACKAGE)
public class NativeBuildMojo extends AbstractNativeMojo {

    private static final String NATIVE_IMAGE_META_INF = "META-INF/native-image";
    private static final String NATIVE_IMAGE_PROPERTIES_FILENAME = "native-image.properties";

    @Parameter(defaultValue = "${plugin}", readonly = true) // Maven 3 only
    private PluginDescriptor plugin;

    @Parameter(defaultValue = "${project.build.directory}", property = "outputDir", required = true)//
    private File outputDirectory;

    @Parameter(property = "mainClass")
    private String mainClass;

    @Parameter(property = "imageName")
    private String imageName;

    @Parameter(property = "skipNativeBuild", defaultValue = "false")
    private boolean skip;

    @Parameter(defaultValue = "${mojoExecution}")
    private MojoExecution mojoExecution;

    @Parameter(property = "classpath")
    private List<String> classpath;

    @Parameter(property = "useArgFile", defaultValue = "true")
    private boolean useArgFile;

    @Parameter(alias = "metadataRepository")
    private MetadataRepositoryConfiguration metadataRepositoryConfiguration;

    private final List<Path> imageClasspath;

    private PluginParameterExpressionEvaluator evaluator;

    private JvmReachabilityMetadataRepository metadataRepository;

    public NativeBuildMojo() {
        this.imageClasspath = new ArrayList<>();
    }

    @Override
    public void execute() throws MojoExecutionException {
        if (skip) {
            getLog().info("Skipping native-image generation (parameter 'skipNativeBuild' is true).");
            return;
        }
        evaluator = new PluginParameterExpressionEvaluator(session, mojoExecution);

        //TODO refactor to a method
        if (isMetadataRepositoryEnabled()) {
            Path repoPath = null;
            if (metadataRepositoryConfiguration.getVersion() != null) {
                getLog().warn("The official JVM reachability metadata repository is not released yet. Only local repositories are supported");
            }
            if (metadataRepositoryConfiguration.getLocalPath() != null) {
                repoPath = metadataRepositoryConfiguration.getLocalPath().toPath();
            }

            if (repoPath == null) {
                getLog().warn("JVM reachability metadata repository is enabled, but no repository has been configured");
            } else {
                if (!Files.exists(repoPath)) {
                    getLog().error("JVM reachability metadata repository path does not exist: " + repoPath);
                } else {
                    metadataRepository = new FileSystemRepository(repoPath, new FileSystemRepository.Logger() {
                        @Override
                        public void log(String groupId, String artifactId, String version, Supplier<String> message) {
                            getLog().info(String.format("[jvm reachability metadata repository for %s:%s:%s]: %s", groupId, artifactId, version, message.get()));
                        }
                    });
                }
            }
        }
        Set<Path> metadataRepositoryPaths = new HashSet<>();

        imageClasspath.clear();
        if (classpath != null && !classpath.isEmpty()) {
            imageClasspath.addAll(classpath.stream().map(Paths::get).collect(Collectors.toList()));
        } else {
            List<String> imageClasspathScopes = Arrays.asList(Artifact.SCOPE_COMPILE, Artifact.SCOPE_RUNTIME);
            project.setArtifactFilter(artifact -> imageClasspathScopes.contains(artifact.getScope()));
            for (Artifact dependency : project.getArtifacts()) {
                addClasspath(dependency);
                if (isMetadataRepositoryEnabled() && metadataRepository != null && !isExcluded(dependency)) {
                    metadataRepositoryPaths.addAll(metadataRepository.findConfigurationDirectoriesFor(q -> {
                        q.useLatestConfigWhenVersionIsUntested();
                        q.forArtifact(artifact -> artifact.gav(String.join(":", dependency.getGroupId(), dependency.getArtifactId(), dependency.getVersion())));
                    }));
                }
            }
            addClasspath(project.getArtifact(), project.getPackaging());
        }
        String classpathStr = imageClasspath.stream().map(Path::toString).collect(Collectors.joining(File.pathSeparator));

        Path nativeImageExecutable = Utils.getNativeImage();

        maybeAddGeneratedResourcesConfig(buildArgs);
        maybeAddReachabilityMetadata(buildArgs, metadataRepositoryPaths);

        try {
            List<String> cliArgs = new ArrayList<>();
            cliArgs.add("-cp");
            cliArgs.add(classpathStr);
            cliArgs.addAll(getBuildArgs());
            if (useArgFile) {
                cliArgs = NativeImageUtils.convertToArgsFile(cliArgs);
            }
            ProcessBuilder processBuilder = new ProcessBuilder(nativeImageExecutable.toString());
            processBuilder.command().addAll(cliArgs);
            processBuilder.directory(getWorkingDirectory().toFile());
            processBuilder.inheritIO();

            String commandString = String.join(" ", processBuilder.command());
            getLog().info("Executing: " + commandString);
            Process imageBuildProcess = processBuilder.start();
            if (imageBuildProcess.waitFor() != 0) {
                throw new MojoExecutionException("Execution of " + commandString + " returned non-zero result");
            }
        } catch (IOException | InterruptedException e) {
            throw new MojoExecutionException("Building image with " + nativeImageExecutable + " failed", e);
        }
    }

    private boolean isExcluded(Artifact dependency) {
        List<Dependency> excludes = metadataRepositoryConfiguration.getExcludes();
        if (excludes == null || excludes.isEmpty()) {
            return false;
        } else {
            return excludes.stream().anyMatch(e -> e.getGroupId().equals(dependency.getGroupId()) && e.getArtifactId().equals(dependency.getArtifactId()));
        }
    }

    private void maybeAddReachabilityMetadata(List<String> buildArgs, Set<Path> paths) {
        if (isMetadataRepositoryEnabled() && !paths.isEmpty()) {
            String arg = paths.stream()
                    .map(Path::toAbsolutePath)
                    .map(Path::toFile)
                    .map(File::getAbsolutePath)
                    .collect(Collectors.joining(","));

            if (!arg.isEmpty()) {
                buildArgs.add("-H:ConfigurationFileDirectories=" + arg);
            }

        }
    }

    private boolean isMetadataRepositoryEnabled() {
        return metadataRepositoryConfiguration != null && metadataRepositoryConfiguration.isEnabled();
    }

    private void addClasspath(Artifact artifact) throws MojoExecutionException {
        addClasspath(artifact, "jar");
    }

    private void addClasspath(Artifact artifact, String artifactType) throws MojoExecutionException {
        if (!artifactType.equals(artifact.getType())) {
            getLog().warn("Ignoring non-jar type ImageClasspath Entry " + artifact);
            return;
        }
        File artifactFile = artifact.getFile();
        if (artifactFile == null) {
            throw new MojoExecutionException("Missing jar-file for " + artifact + ". Ensure that" + plugin.getArtifactId() + " runs in package phase.");
        }
        Path jarFilePath = artifactFile.toPath();
        getLog().info("ImageClasspath Entry: " + artifact + " (" + jarFilePath.toUri() + ")");

        URI jarFileURI = URI.create("jar:" + jarFilePath.toUri());
        try (FileSystem jarFS = FileSystems.newFileSystem(jarFileURI, Collections.emptyMap())) {
            Path nativeImageMetaInfBase = jarFS.getPath("/" + NATIVE_IMAGE_META_INF);
            if (Files.isDirectory(nativeImageMetaInfBase)) {
                List<Path> nativeImageProperties = Files.walk(nativeImageMetaInfBase)
                        .filter(p -> p.endsWith(NATIVE_IMAGE_PROPERTIES_FILENAME))
                        .collect(Collectors.toList());

                for (Path nativeImageProperty : nativeImageProperties) {
                    Path relativeSubDir = nativeImageMetaInfBase.relativize(nativeImageProperty).getParent();
                    boolean valid = relativeSubDir != null && (relativeSubDir.getNameCount() == 2);
                    valid = valid && relativeSubDir.getName(0).toString().equals(artifact.getGroupId());
                    valid = valid && relativeSubDir.getName(1).toString().equals(artifact.getArtifactId());
                    if (!valid) {
                        String example = NATIVE_IMAGE_META_INF + "/${groupId}/${artifactId}/" + NATIVE_IMAGE_PROPERTIES_FILENAME;
                        getLog().warn(nativeImageProperty.toUri() + " does not match recommended " + example + " layout.");
                    }
                }
            }
        } catch (IOException e) {
            throw new MojoExecutionException("Artifact " + artifact + "cannot be added to image classpath", e);
        }

        imageClasspath.add(jarFilePath);
    }

    private Path getWorkingDirectory() {
        outputDirectory.mkdirs();
        return outputDirectory.toPath();
    }

    private String consumeConfigurationNodeValue(String pluginKey, String... nodeNames) {
        Plugin selectedPlugin = project.getPlugin(pluginKey);
        if (selectedPlugin == null) {
            return null;
        }
        return getConfigurationNodeValue(selectedPlugin, nodeNames);
    }

    private String consumeExecutionsNodeValue(String pluginKey, String... nodeNames) {
        Plugin selectedPlugin = project.getPlugin(pluginKey);
        if (selectedPlugin == null) {
            return null;
        }
        for (PluginExecution execution : selectedPlugin.getExecutions()) {
            String value = getConfigurationNodeValue(execution, nodeNames);
            if (value != null) {
                return value;
            }
        }
        return null;
    }

    private String getConfigurationNodeValue(ConfigurationContainer container, String... nodeNames) {
        if (container != null && container.getConfiguration() instanceof Xpp3Dom) {
            Xpp3Dom node = (Xpp3Dom) container.getConfiguration();
            for (String nodeName : nodeNames) {
                node = node.getChild(nodeName);
                if (node == null) {
                    return null;
                }
            }
            String value = node.getValue();
            return evaluateValue(value);
        }
        return null;
    }

    private String evaluateValue(String value) {
        if (value != null) {
            try {
                Object evaluatedValue = evaluator.evaluate(value);
                if (evaluatedValue instanceof String) {
                    return (String) evaluatedValue;
                }
            } catch (ExpressionEvaluationException exception) {
            }
        }

        return null;
    }

    private void maybeSetMainClassFromPlugin(BiFunction<String, String[], String> mainClassProvider, String pluginName, String... nodeNames) {
        if (mainClass == null) {
            mainClass = mainClassProvider.apply(pluginName, nodeNames);

            if (mainClass != null) {
                getLog().info("Obtained main class from plugin " + pluginName + " with the following path: " + String.join(" -> ", nodeNames));
            }
        }
    }

    private List<String> getBuildArgs() {
        maybeSetMainClassFromPlugin(this::consumeExecutionsNodeValue, "org.apache.maven.plugins:maven-shade-plugin", "transformers", "transformer", "mainClass");
        maybeSetMainClassFromPlugin(this::consumeConfigurationNodeValue, "org.apache.maven.plugins:maven-assembly-plugin", "archive", "manifest", "mainClass");
        maybeSetMainClassFromPlugin(this::consumeConfigurationNodeValue, "org.apache.maven.plugins:maven-jar-plugin", "archive", "manifest", "mainClass");

        List<String> list = new ArrayList<>();
        if (buildArgs != null && !buildArgs.isEmpty()) {
            for (String buildArg : buildArgs) {
                list.addAll(Arrays.asList(buildArg.split("\\s+")));
            }
        }
        if (mainClass != null && !mainClass.equals(".")) {
            list.add("-H:Class=" + mainClass);
        }
        if (imageName == null) {
            imageName = project.getArtifactId();
        }
        list.add("-H:Name=" + imageName);
        return list;
    }
}
