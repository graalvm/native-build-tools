/*
 * Copyright (c) 2021, 2021 Oracle and/or its affiliates. All rights reserved.
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
import org.apache.maven.artifact.DependencyResolutionRequiredException;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.plugins.annotations.ResolutionScope;
import org.graalvm.buildtools.Utils;
import org.graalvm.buildtools.VersionInfo;

import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.graalvm.buildtools.Utils.NATIVE_TESTS_EXE;

/**
 * @author Sebastien Deleuze
 */
@Mojo(name = "test", defaultPhase = LifecyclePhase.TEST, threadSafe = true,
        requiresDependencyResolution = ResolutionScope.TEST,
        requiresDependencyCollection = ResolutionScope.TEST)
public class NativeTestMojo extends AbstractNativeMojo {

    @Parameter(property = "skipTests", defaultValue = "false")
    private boolean skipTests;

    @Override
    public void execute() throws MojoExecutionException, MojoFailureException {
        if (skipTests) {
            logger.info("Tests are skipped.");
            return;
        }
        if (!hasTests()) {
            return;
        }

        String classpath = getClassPath();
        Path targetFolder = new File(project.getBuild().getDirectory()).toPath();
        targetFolder.toFile().mkdirs();

        logger.info("====================");
        logger.info("Initializing project: " + project.getName());
        logger.info("====================");

        if (!hasTestIds()) {
            logger.warn("Test configuration file wasn't found. Build will now fallback to test discovery mode.");
            logger.warn("Add following dependency to use the test listener mode:");
            logger.warn("<dependency>");
            logger.warn("    <groupId>org.graalvm.buildtools</groupId>");
            logger.warn("    <artifactId>junit-platform-native</artifactId>");
            logger.warn("    <version>" + VersionInfo.JUNIT_PLATFORM_NATIVE_VERSION + "</version>");
            logger.warn("    <scope>test</scope>");
            logger.warn("</dependency>");
        }

        logger.debug("Classpath: " + classpath);
        buildImage(classpath, targetFolder);

        runTests(targetFolder);
    }

    private boolean hasTests() {
        Path testOutputPath = Paths.get(project.getBuild().getTestOutputDirectory());
        if (Files.exists(testOutputPath) && Files.isDirectory(testOutputPath)) {
            try (DirectoryStream<Path> directory = Files.newDirectoryStream(testOutputPath)) {
                return directory.iterator().hasNext();
            } catch (IOException ex) {
                throw new IllegalStateException(ex);
            }
        }
        return false;
    }

    private void buildImage(String classpath, Path targetFolder) throws MojoExecutionException {
        Path nativeImageExecutable = Utils.getNativeImage();

        List<String> command = new ArrayList<>(Arrays.asList(
                nativeImageExecutable.toString(),
                "-cp", classpath,
                "--features=org.graalvm.junit.platform.JUnitPlatformFeature",
                "-H:Path=" + targetFolder.toAbsolutePath(),
                "-H:Name=" + NATIVE_TESTS_EXE));

        if (buildArgs != null) {
            command.addAll(buildArgs);
        }

        command.add("org.graalvm.junit.platform.NativeImageJUnitLauncher");

        try {
            ProcessBuilder processBuilder = new ProcessBuilder(command);
            processBuilder.directory(new File(project.getBuild().getDirectory()));
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

    private void runTests(Path targetFolder) throws MojoExecutionException {
        Path xmlLocation = targetFolder.resolve("native-test-reports");
        xmlLocation.toFile().mkdirs();

        try {
            ProcessBuilder processBuilder = new ProcessBuilder(
                    targetFolder.resolve(NATIVE_TESTS_EXE).toAbsolutePath().toString(),
                    "--xml-output-dir", xmlLocation.toString());
            processBuilder.inheritIO();

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

    private String getClassPath() throws MojoFailureException {
        try {
            List<Artifact> pluginDependencies = pluginArtifacts.stream()
                    .filter(it -> it.getGroupId().startsWith(Utils.MAVEN_GROUP_ID) || it.getGroupId().startsWith("org.junit"))
                    .collect(Collectors.toList());

            List<String> projectClassPath = new ArrayList<>(project
                    .getTestClasspathElements());

            return Stream.concat(projectClassPath.stream(), pluginDependencies.stream()
                    .map(it -> it.getFile().toString()))
                    .collect(Collectors.joining(File.pathSeparator));
        } catch (DependencyResolutionRequiredException e) {
            throw new MojoFailureException(e.getMessage(), e);
        }
    }

    private boolean hasTestIds() {
    	try {
    		Path buildDir = Paths.get(project.getBuild().getDirectory());
    		// See org.graalvm.junit.platform.UniqueIdTrackingListener.DEFAULT_OUTPUT_FILE_PREFIX
			return readAllFiles(buildDir, "junit-platform-unique-ids").anyMatch(contents -> !contents.isEmpty());
		}
		catch (Exception ex) {
			return false;
		}
    }

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

}
