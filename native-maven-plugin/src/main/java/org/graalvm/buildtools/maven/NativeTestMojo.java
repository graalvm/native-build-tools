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
import org.apache.maven.model.FileSet;
import org.apache.maven.model.Plugin;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.plugins.annotations.ResolutionScope;
import org.codehaus.plexus.util.xml.Xpp3Dom;
import org.eclipse.aether.DefaultRepositorySystemSession;
import org.eclipse.aether.RepositorySystemSession;
import org.eclipse.aether.artifact.DefaultArtifact;
import org.eclipse.aether.collection.CollectRequest;
import org.eclipse.aether.graph.Dependency;
import org.eclipse.aether.repository.RemoteRepository;
import org.eclipse.aether.resolution.ArtifactResult;
import org.eclipse.aether.resolution.DependencyRequest;
import org.eclipse.aether.resolution.DependencyResolutionException;
import org.eclipse.aether.resolution.DependencyResult;
import org.graalvm.buildtools.utils.NativeImageConfigurationUtils;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.graalvm.buildtools.utils.NativeImageConfigurationUtils.NATIVE_TESTS_EXE;

/**
 * This goal builds and runs native tests.
 *
 * @author Sebastien Deleuze
 */
@Mojo(name = "test", defaultPhase = LifecyclePhase.TEST, threadSafe = true,
    requiresDependencyResolution = ResolutionScope.TEST,
    requiresDependencyCollection = ResolutionScope.TEST)
public class NativeTestMojo extends AbstractNativeImageMojo {

    @Parameter(property = "skipTests", defaultValue = "false")
    private boolean skipTests;

    @Parameter(property = "skipNativeTests", defaultValue = "false")
    private boolean skipNativeTests;

    @Override
    protected void populateApplicationClasspath() throws MojoExecutionException {
        super.populateApplicationClasspath();
        imageClasspath.add(Paths.get(project.getBuild().getTestOutputDirectory()));
        project.getBuild()
            .getTestResources()
            .stream()
            .map(FileSet::getDirectory)
            .map(Paths::get)
            .forEach(imageClasspath::add);
    }

    @Override
    protected List<String> getDependencyScopes() {
        return Arrays.asList(
            Artifact.SCOPE_COMPILE,
            Artifact.SCOPE_RUNTIME,
            Artifact.SCOPE_TEST,
            Artifact.SCOPE_COMPILE_PLUS_RUNTIME
        );
    }

    @Override
    protected void addDependenciesToClasspath() throws MojoExecutionException {
        super.addDependenciesToClasspath();
        Set<Module> modules = new HashSet<>();
        //noinspection SimplifyStreamApiCallChains
        pluginArtifacts.stream()
            // do not use peek as Stream implementations are free to discard it
            .map(a -> {
                modules.add(new Module(a.getGroupId(), a.getArtifactId()));
                return a;
            })
            .filter(it -> it.getGroupId().startsWith(NativeImageConfigurationUtils.MAVEN_GROUP_ID) || it.getGroupId().startsWith("org.junit"))
            .map(it -> it.getFile().toPath())
            .forEach(imageClasspath::add);
        var jars = findJunitPlatformNativeJars(modules);
        imageClasspath.addAll(jars);
    }

    @Override
    public void execute() throws MojoExecutionException {
        if (skipTests || skipNativeTests) {
            logger.info("Skipping native-image tests (parameter 'skipTests' or 'skipNativeTests' is true).");
            return;
        }
        if (!hasTests()) {
            logger.info("Skipped native-image tests since there are no test classes.");
            return;
        }
        if (!hasTestIds()) {
            logger.error("Test configuration file wasn't found. Make sure that test execution wasn't skipped.");
            throw new IllegalStateException("Test configuration file wasn't found.");
        }

        logger.info("====================");
        logger.info("Initializing project: " + project.getName());
        logger.info("====================");

        configureEnvironment();
        buildArgs.add("--features=org.graalvm.junit.platform.JUnitPlatformFeature");

        if (systemProperties == null) {
            systemProperties = new HashMap<>();
        }
        systemProperties.put("junit.platform.listeners.uid.tracking.output.dir",
            NativeExtension.testIdsDirectory(outputDirectory.getAbsolutePath()));

        imageName = NATIVE_TESTS_EXE;
        mainClass = "org.graalvm.junit.platform.NativeImageJUnitLauncher";

        buildImage();
        runNativeTests(outputDirectory.toPath().resolve(NATIVE_TESTS_EXE));
    }

    private void configureEnvironment() {
        // inherit from surefire mojo
        Plugin plugin = project.getPlugin("org.apache.maven.plugins:maven-surefire-plugin");
        if (plugin != null) {
            Object configuration = plugin.getConfiguration();
            if (configuration instanceof Xpp3Dom) {
                Xpp3Dom dom = (Xpp3Dom) configuration;
                Xpp3Dom environmentVariables = dom.getChild("environmentVariables");
                if (environmentVariables != null) {
                    Xpp3Dom[] children = environmentVariables.getChildren();
                    if (environment == null) {
                        environment = new HashMap<>(children.length);
                    }
                    for (Xpp3Dom child : children) {
                        environment.put(child.getName(), child.getValue());
                    }
                }
                Xpp3Dom systemProps = dom.getChild("systemPropertyVariables");
                if (systemProps != null) {
                    Xpp3Dom[] children = systemProps.getChildren();
                    if (systemProperties == null) {
                        systemProperties = new HashMap<>(children.length);
                    }
                    for (Xpp3Dom child : children) {
                        systemProperties.put(child.getName(), child.getValue());
                    }
                }
            }
        }
    }

    private boolean hasTests() {
        Path testOutputPath = Paths.get(project.getBuild().getTestOutputDirectory());
        if (Files.exists(testOutputPath) && Files.isDirectory(testOutputPath)) {
            try (Stream<Path> testClasses = Files.walk(testOutputPath)) {
                return testClasses.anyMatch(p -> p.getFileName().toString().endsWith(".class"));
            } catch (IOException ex) {
                throw new IllegalStateException(ex);
            }
        }
        return false;
    }

    private void runNativeTests(Path executable) throws MojoExecutionException {
        Path xmlLocation = outputDirectory.toPath().resolve("native-test-reports");
        if (!xmlLocation.toFile().exists() && !xmlLocation.toFile().mkdirs()) {
            throw new MojoExecutionException("Failed creating xml output directory");
        }
        try {
            ProcessBuilder processBuilder = new ProcessBuilder(executable.toAbsolutePath().toString());
            processBuilder.inheritIO();
            processBuilder.directory(session.getCurrentProject().getBasedir());

            List<String> command = new ArrayList<>();
            command.add("--xml-output-dir");
            command.add(xmlLocation.toString());
            systemProperties.forEach((key, value) -> command.add("-D" + key + "=" + value));
            processBuilder.command().addAll(command);
            processBuilder.environment().putAll(environment);

            String commandString = String.join(" ", processBuilder.command());
            getLog().info("Executing: " + commandString);
            Process imageBuildProcess = processBuilder.start();
            if (imageBuildProcess.waitFor() != 0) {
                throw new MojoExecutionException("Execution of " + commandString + " returned non-zero result");
            }
        } catch (IOException | InterruptedException e) {
            throw new MojoExecutionException("native-image test run failed");
        }
    }

    private boolean hasTestIds() {
        try {
            Path buildDir = Paths.get(project.getBuild().getDirectory());
            // See org.graalvm.junit.platform.UniqueIdTrackingListener.DEFAULT_OUTPUT_FILE_PREFIX
            return readAllFiles(buildDir, "junit-platform-unique-ids").anyMatch(contents -> !contents.isEmpty());
        } catch (Exception ex) {
            return false;
        }
    }

    @SuppressWarnings("SameParameterValue")
    private Stream<String> readAllFiles(Path dir, String prefix) throws IOException {
        return findFiles(dir, prefix).map(outputFile -> {
            try {
                return Files.readAllLines(outputFile);
            } catch (IOException ex) {
                throw new UncheckedIOException(ex);
            }
        }).flatMap(List::stream);
    }

    private static Stream<Path> findFiles(Path dir, String prefix) throws IOException {
        if (!Files.exists(dir)) {
            return Stream.empty();
        }
        return Files.find(dir, Integer.MAX_VALUE,
            (path, basicFileAttributes) -> (basicFileAttributes.isRegularFile()
                                            && path.getFileName().toString().startsWith(prefix)));
    }

    private List<Path> findJunitPlatformNativeJars(Set<Module> modulesAlreadyOnClasspath) {
        RepositorySystemSession repositorySession = mavenSession.getRepositorySession();
        DefaultRepositorySystemSession newSession = new DefaultRepositorySystemSession(repositorySession);
        CollectRequest collectRequest = new CollectRequest();
        List<RemoteRepository> repositories = project.getRemoteProjectRepositories();
        collectRequest.setRepositories(repositories);
        DefaultArtifact artifact = new DefaultArtifact(
            RuntimeMetadata.GROUP_ID,
            RuntimeMetadata.JUNIT_PLATFORM_NATIVE_ARTIFACT_ID,
            null,
            "jar",
            RuntimeMetadata.VERSION
        );
        Dependency dependency = new Dependency(artifact, "runtime");
        collectRequest.addDependency(dependency);
        DependencyRequest dependencyRequest = new DependencyRequest(collectRequest, null);
        DependencyResult dependencyResult;
        try {
            dependencyResult = repositorySystem.resolveDependencies(newSession, dependencyRequest);
        } catch (DependencyResolutionException e) {
            return Collections.emptyList();
        }
        return dependencyResult.getArtifactResults()
            .stream()
            .map(ArtifactResult::getArtifact)
            .filter(a -> !modulesAlreadyOnClasspath.contains(new Module(a.getGroupId(), a.getArtifactId())))
            .map(a -> a.getFile().toPath())
            .collect(Collectors.toList());
    }

    private static final class Module {
        private final String groupId;
        private final String artifactId;

        private Module(String groupId, String artifactId) {
            this.groupId = groupId;
            this.artifactId = artifactId;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }

            Module module = (Module) o;

            if (!groupId.equals(module.groupId)) {
                return false;
            }
            return artifactId.equals(module.artifactId);
        }

        @Override
        public int hashCode() {
            int result = groupId.hashCode();
            result = 31 * result + artifactId.hashCode();
            return result;
        }
    }
}
