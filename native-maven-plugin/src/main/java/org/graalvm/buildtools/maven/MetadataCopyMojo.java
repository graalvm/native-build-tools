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
import org.graalvm.buildtools.maven.config.AbstractMergeAgentFilesMojo;
import org.graalvm.buildtools.maven.config.agent.AgentConfiguration;
import org.graalvm.buildtools.maven.config.agent.MetadataCopyConfiguration;
import org.graalvm.buildtools.utils.NativeImageConfigurationUtils;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

@Mojo(name = "metadata-copy", defaultPhase = LifecyclePhase.PREPARE_PACKAGE)
public class MetadataCopyMojo extends AbstractMergeAgentFilesMojo {

    public static final String DEFAULT_OUTPUT_DIRECTORY = "/META-INF/native-image";

    @Parameter(alias = "agent")
    private AgentConfiguration agentConfiguration;

    @Parameter(defaultValue = "${project}", readonly = true, required = true)
    private MavenProject project;

    @Override
    public void execute() throws MojoExecutionException {
        if (agentConfiguration != null && agentConfiguration.isEnabled()) {
            MetadataCopyConfiguration config = agentConfiguration.getMetadataCopyConfiguration();
            if (config == null) {
                getLog().info("Metadata copy config not provided. Skipping this task.");
                return;
            }

            String buildDirectory = project.getBuild().getDirectory() + "/native/agent-output/";
            String destinationDir = config.getOutputDirectory();

            if (destinationDir == null) {
                destinationDir = project.getBuild().getOutputDirectory() + DEFAULT_OUTPUT_DIRECTORY;
            }

            if (!Files.isDirectory(Paths.get(destinationDir))) {
                throw new MojoExecutionException("Directory specified in metadata copy configuration dose not exists.");
            }

            Path nativeImageExecutable = NativeImageConfigurationUtils.getNativeImage(logger);
            tryInstallMergeExecutable(nativeImageExecutable);

            // if we have some disabled stages we don't have all files necessary for merge, so we don't want to merge files in case some stage is disabled
            if (config.shouldMerge()) {
                if (config.getDisabledStages().isEmpty()) {
                    executeMerge(buildDirectory);
                } else {
                    logger.warn("Configuration is inconsistent.");
                }
            }

            executeCopy(buildDirectory, destinationDir);
            getLog().info("Metadata copy process finished.");
        }
    }

    private void executeCopy(String buildDirectory, String destinationDir) throws MojoExecutionException {
        MetadataCopyConfiguration config = agentConfiguration.getMetadataCopyConfiguration();

        List<String> sourceDirectories = new ArrayList<>(2);
        List<String> disabledStages = config.getDisabledStages();
        if (disabledStages.isEmpty()) {
            sourceDirectories.add(buildDirectory);
        } else {
            sourceDirectories.add(buildDirectory + NativeExtension.Context.main.name());
            sourceDirectories.add(buildDirectory + NativeExtension.Context.test.name());

            for (String disabledStage : disabledStages) {
                sourceDirectories.remove(buildDirectory + disabledStage);
            }
        }

        if (sourceDirectories.isEmpty()) {
            logger.warn("Skipping metadata copy task. Both main and test stages are disabled in metadata copy configuration.");
            return;
        }

        logger.info("Copying files from:" + sourceDirectories);
        List<String> nativeImageConfigureOptions = agentConfiguration.getAgentMode().getNativeImageConfigureOptions(sourceDirectories, Collections.singletonList(destinationDir));
        nativeImageConfigureOptions.add(0, mergerExecutable.getAbsolutePath());
        ProcessBuilder processBuilder = new ProcessBuilder(nativeImageConfigureOptions);

        try {
            Process start = processBuilder.start();
            int retCode = start.waitFor();
            if (retCode != 0) {
                getLog().error("Metadata copy process failed with code: " + retCode);
                throw new MojoExecutionException("Metadata copy process failed.");
            }
        } catch (IOException | InterruptedException e) {
            throw new RuntimeException(e);
        }
    }

    private void executeMerge(String buildDirectory) throws MojoExecutionException {
        File baseDir = new File(buildDirectory);
        if (baseDir.exists()) {
            File[] mergingFiles = baseDir.listFiles();
            if (mergingFiles != null) {
                invokeMerge(mergerExecutable, Arrays.asList(mergingFiles), baseDir);
            }
        } else {
            getLog().debug("Agent output directory " + baseDir + " doesn't exist. Skipping merge.");
        }
    }
}
