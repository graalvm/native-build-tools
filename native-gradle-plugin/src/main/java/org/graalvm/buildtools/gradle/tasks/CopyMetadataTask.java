/*
 * Copyright (c) 2022, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.buildtools.gradle.tasks;

import org.graalvm.buildtools.gradle.internal.GraalVMLogger;
import org.graalvm.buildtools.gradle.internal.agent.AgentConfigurationFactory;
import org.gradle.api.DefaultTask;
import org.gradle.api.GradleException;
import org.gradle.api.provider.ListProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.Internal;
import org.gradle.api.tasks.TaskAction;
import org.gradle.api.tasks.options.Option;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.List;

public abstract class CopyMetadataTask extends DefaultTask {

    private final GraalVMLogger logger;

    public CopyMetadataTask() {
        this.logger = GraalVMLogger.of(getLogger());
    }

    @Internal
    public abstract ListProperty<String> getInputTaskNames();

    @Internal
    public abstract ListProperty<String> getOutputDirectories();

    @Internal
    public abstract Property<Boolean> getMergeWithExisting();

    @Option(option = "task", description = "Executed task previously instrumented with the agent whose metadata should be copied.")
    public void overrideInputTaskNames(List<String> inputTaskNames) {
        getInputTaskNames().set(inputTaskNames);
    }

    @Option(option = "dir", description = "")
    public void overrideOutputDirectories(List<String> outputDirectories) {
        getOutputDirectories().set(outputDirectories);
    }

    @TaskAction
    public void exec() {
        StringBuilder builder = new StringBuilder();
        for (String taskName : getInputTaskNames().get()) {
            File dir = AgentConfigurationFactory.getAgentOutputDirectoryForTask(getProject(), taskName).get().getAsFile();
            if (!dir.exists()) {
                builder.append("Could not find configuration for task: ").append(taskName).append(". Please run the task with the agent.");
            } else if (!dir.isDirectory()) {
                builder.append("Expected a directory with configuration for task: ").append(taskName).append(" but found a regular file at ").append(dir.getAbsolutePath()).append(". Was the output directory manually modified?");
            }
        }
        String errorString = builder.toString();
        if (!errorString.isEmpty()) {
            throw new GradleException(errorString);
        }

        for (String dirName : getOutputDirectories().get()) {
            File dir = getProject().getLayout().dir(getProject().provider(() -> new File(dirName))).get().getAsFile();
            if (dir.exists()) {
                if (!dir.isDirectory()) {
                    builder.append("Specified output path must either not exist or be a directory: ").append(dirName);
                }
            } else {
                try {
                    logger.log("Creating output directory: " + dirName);
                    Files.createDirectories(dir.toPath());
                } catch (IOException e) {
                    throw new GradleException("Could not create output directory: " + dirName, e);
                }
            }
        }
    }
}
