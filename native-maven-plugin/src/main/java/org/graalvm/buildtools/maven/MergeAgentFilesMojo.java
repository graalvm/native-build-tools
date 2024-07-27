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

import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.MavenProject;
import org.codehaus.plexus.util.FileUtils;
import org.graalvm.buildtools.maven.config.AbstractMergeAgentFilesMojo;
import org.graalvm.buildtools.maven.config.agent.AgentConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.Collections;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Mojo(name = "merge-agent-files", defaultPhase = LifecyclePhase.TEST)
public class MergeAgentFilesMojo extends AbstractMergeAgentFilesMojo {
    @Parameter(defaultValue = "${project}", readonly = true, required = true)
    protected MavenProject project;

    @Parameter(defaultValue = "${project.build.directory}", readonly = true, required = true)
    protected String target;

    @Parameter(property = "native.agent.merge.context", required = true)
    protected String context;

    @Parameter(alias = "agent")
    private AgentConfiguration agentConfiguration;

    private static int numberOfExecutions = 0;

    @Override
    public void execute() throws MojoExecutionException {
        // we need this mojo to be executed only once
        numberOfExecutions++;
        if (numberOfExecutions > 1) {
            return;
        }

        // if we reached here and agent config is null, agent is enabled but there is no configuration in pom.xml
        // that means that we enabled agent from command line, so we are using default agent configuration
        if (agentConfiguration == null) {
            agentConfiguration = new AgentConfiguration();
        }


        if (agentConfiguration.getDefaultMode().equalsIgnoreCase("direct")) {
            logger.info("Skipping files merge mojo since we are in direct mode");
            return;
        }

        List<String> disabledPhases = agentConfiguration.getMetadataCopyConfiguration().getDisabledStages();
        if (disabledPhases.size() == 2) {
            logger.info("Both phases are skipped.");
            return;
        }

        Set<String> dirs = new HashSet(2);
        dirs.addAll(Arrays.asList("main", "test"));
        dirs.removeAll(disabledPhases);

        for (String dir : dirs) {
            String agentOutputDirectory = (target + "/native/agent-output/" + dir).replace('/', File.separatorChar);
            mergeForGivenDir(agentOutputDirectory);
        }
    }

    private void mergeForGivenDir(String agentOutputDirectory) throws MojoExecutionException {
        File baseDir = new File(agentOutputDirectory);
        if (baseDir.exists()) {
            List<File> sessionDirectories = sessionDirectoriesFrom(baseDir.listFiles()).collect(Collectors.toList());
            if (sessionDirectories.size() == 0) {
                sessionDirectories = Collections.singletonList(baseDir);
            }

            invokeMerge(sessionDirectories, baseDir);
        } else {
            getLog().debug("Agent output directory " + baseDir + " doesn't exist. Skipping merge.");
        }
    }

    private static Stream<File> sessionDirectoriesFrom(File[] files) {
        return Arrays.stream(files)
                .filter(File::isDirectory)
                .filter(f -> f.getName().startsWith("session-"));
    }

    private void invokeMerge(List<File> inputDirectories, File outputDirectory) throws MojoExecutionException {
        File mergerExecutable = getMergerExecutable();
        try {
            if (inputDirectories.isEmpty()) {
                getLog().warn("Skipping merging of agent files since there are no input directories.");
                return;
            }

            getLog().info("Merging agent " + inputDirectories.size() + " files into " + outputDirectory);
            List<String> optionsInputDirs = inputDirectories.stream().map(File::getAbsolutePath).collect(Collectors.toList());
            List<String> optionsOutputDirs = Collections.singletonList(outputDirectory.getAbsolutePath());
            List<String> args = agentConfiguration.getAgentMode().getNativeImageConfigureOptions(optionsInputDirs, optionsOutputDirs);

            ProcessBuilder processBuilder = new ProcessBuilder(mergerExecutable.toString());
            processBuilder.command().addAll(args);
            processBuilder.inheritIO();
            String commandString = String.join(" ", processBuilder.command());
            Process imageBuildProcess = processBuilder.start();
            if (imageBuildProcess.waitFor() != 0) {
                throw new MojoExecutionException("Execution of " + commandString + " returned non-zero result");
            }

            // in case inputDirectories has only one value which is the same as outputDirectory
            // we shouldn't delete that directory, because we will delete outputDirectory
            if (!(inputDirectories.size() == 1 && inputDirectories.get(0).equals(outputDirectory))) {
                for (File inputDirectory : inputDirectories) {
                    FileUtils.deleteDirectory(inputDirectory);
                }
            }

            getLog().debug("Agent output: " + Arrays.toString(outputDirectory.listFiles()));
        } catch (IOException | InterruptedException e) {
            throw new MojoExecutionException("Merging agent files with " + mergerExecutable + " failed", e);
        }
    }

}
