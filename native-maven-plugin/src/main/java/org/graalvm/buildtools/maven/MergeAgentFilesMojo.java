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
package org.graalvm.buildtools.maven;

import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.MavenProject;
import org.codehaus.plexus.util.FileUtils;
import org.graalvm.buildtools.Utils;
import org.graalvm.buildtools.utils.NativeImageUtils;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.graalvm.buildtools.maven.NativeExtension.agentOutputDirectoryFor;
import static org.graalvm.buildtools.utils.NativeImageUtils.nativeImageConfigureFileName;

@Mojo(name = "merge-agent-files", defaultPhase = LifecyclePhase.TEST)
public class MergeAgentFilesMojo extends AbstractMojo {
    @Parameter(defaultValue = "${project}", readonly = true, required = true)
    protected MavenProject project;

    @Parameter(defaultValue = "${project.build.directory}", readonly = true, required = true)
    protected String target;

    @Parameter(property = "native.agent.merge.context", required = true)
    protected String context;

    @Override
    public void execute() throws MojoExecutionException, MojoFailureException {
        String agentOutputDirectory = agentOutputDirectoryFor(target,  NativeExtension.Context.valueOf(context));
        File baseDir = new File(agentOutputDirectory);
        Path nativeImageExecutable = Utils.getNativeImage();
        File mergerExecutable = tryInstall(nativeImageExecutable);
        List<File> sessionDirectories = sessionDirectoriesFrom(baseDir.listFiles()).collect(Collectors.toList());
        invokeMerge(mergerExecutable, sessionDirectories, baseDir);
    }

    private File tryInstall(Path nativeImageExecutablePath) {
        File nativeImageExecutable = nativeImageExecutablePath.toAbsolutePath().toFile();
        File mergerExecutable = new File(nativeImageExecutable.getParentFile(), nativeImageConfigureFileName());
        if (!mergerExecutable.exists()) {
            getLog().info("Installing native image merger to " + mergerExecutable);
            ProcessBuilder processBuilder = new ProcessBuilder(nativeImageExecutable.toString());
            processBuilder.command().add("--macro:native-image-configure-launcher");
            processBuilder.directory(mergerExecutable.getParentFile());
            processBuilder.inheritIO();

            try {
                Process installProcess = processBuilder.start();
                if (installProcess.waitFor() != 0) {
                    getLog().warn("Installation of native image merging tool failed");
                }
                NativeImageUtils.maybeCreateConfigureUtilSymlink(mergerExecutable, nativeImageExecutablePath);
            } catch (IOException | InterruptedException e) {
                // ignore since we will handle that if the installer doesn't exist later
            }

        }
        return mergerExecutable;
    }

    private static Stream<File> sessionDirectoriesFrom(File[] files) {
        return Arrays.stream(files)
                .filter(File::isDirectory)
                .filter(f -> f.getName().startsWith("session-"));
    }

    private void invokeMerge(File mergerExecutable, List<File> inputDirectories, File outputDirectory) throws MojoExecutionException {
        if (!mergerExecutable.exists()) {
            getLog().warn("Cannot merge agent files because native-image-configure is not installed. Please upgrade to a newer version of GraalVM.");
            return;
        }
        try {
            getLog().info("Merging agent " + inputDirectories.size() + " files into " + outputDirectory);
            List<String> args = new ArrayList<>(inputDirectories.size() + 2);
            args.add("generate");
            inputDirectories.stream()
                    .map(f -> "--input-dir=" + f.getAbsolutePath())
                    .forEach(args::add);
            args.add("--output-dir=" + outputDirectory.getAbsolutePath());
            ProcessBuilder processBuilder = new ProcessBuilder(mergerExecutable.toString());
            processBuilder.command().addAll(args);
            processBuilder.inheritIO();

            String commandString = String.join(" ", processBuilder.command());
            Process imageBuildProcess = processBuilder.start();
            if (imageBuildProcess.waitFor() != 0) {
                throw new MojoExecutionException("Execution of " + commandString + " returned non-zero result");
            }
            for (File inputDirectory : inputDirectories) {
                FileUtils.deleteDirectory(inputDirectory);
            }
            getLog().debug("Agent output: " + Arrays.toString(outputDirectory.listFiles()));
        } catch (IOException | InterruptedException e) {
            throw new MojoExecutionException("Merging agent files with " + mergerExecutable + " failed", e);
        }
    }
}
