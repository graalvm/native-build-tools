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
package org.graalvm.buildtools.gradle.tasks.actions;

import org.graalvm.buildtools.agent.AgentMode;
import org.graalvm.buildtools.gradle.internal.GraalVMLogger;
import org.graalvm.buildtools.gradle.internal.NativeImageExecutableLocator;
import org.graalvm.buildtools.utils.NativeImageUtils;
import org.gradle.api.Action;
import org.gradle.api.Task;
import org.gradle.api.model.ObjectFactory;
import org.gradle.api.provider.Property;
import org.gradle.api.provider.Provider;
import org.gradle.jvm.toolchain.JavaLauncher;
import org.gradle.process.ExecOperations;
import org.gradle.process.ExecResult;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.function.Supplier;

import static org.graalvm.buildtools.gradle.internal.NativeImageExecutableLocator.findNativeImageExecutable;
import static org.graalvm.buildtools.utils.NativeImageUtils.nativeImageConfigureFileName;

public class MergeAgentFilesAction implements Action<Task> {
    private final Provider<Boolean> isMergingEnabled;
    private final Provider<AgentMode> agentMode;
    private final Provider<Boolean> mergeWithOutputs;
    private final Provider<String> graalvmHomeProvider;
    private final Supplier<List<String>> inputDirs;
    private final Supplier<List<String>> outputDirs;
    private final Provider<Boolean> disableToolchainDetection;
    private final Property<JavaLauncher> noLauncherProperty;
    private final ExecOperations execOperations;

    public MergeAgentFilesAction(Provider<Boolean> isMergingEnabled,
                                 Provider<AgentMode> agentMode,
                                 Provider<Boolean> mergeWithOutputs,
                                 ObjectFactory objectFactory,
                                 Provider<String> graalvmHomeProvider,
                                 Supplier<List<String>> inputDirs,
                                 Supplier<List<String>> outputDirs,
                                 Provider<Boolean> disableToolchainDetection,
                                 ExecOperations execOperations) {
        this.isMergingEnabled = isMergingEnabled;
        this.agentMode = agentMode;
        this.mergeWithOutputs = mergeWithOutputs;
        this.graalvmHomeProvider = graalvmHomeProvider;
        this.inputDirs = inputDirs;
        this.outputDirs = outputDirs;
        this.disableToolchainDetection = disableToolchainDetection;
        this.execOperations = execOperations;
        this.noLauncherProperty = objectFactory.property(JavaLauncher.class);
    }

    private static final Set<String> METADATA_FILES = Set.of("reflect-config.json", "jni-config.json", "proxy-config.json", "resource-config.json", "reachability-metadata.json");

    private static boolean isConfigDir(String dir) {
        return Arrays.stream(new File(dir).listFiles())
           .anyMatch(file -> METADATA_FILES.contains(file.getName()));
    }

    @Override
    public void execute(Task task) {
        if (isMergingEnabled.get()) {
            File nativeImage = findNativeImageExecutable(noLauncherProperty, disableToolchainDetection, graalvmHomeProvider, execOperations, GraalVMLogger.of(task.getLogger()), new NativeImageExecutableLocator.Diagnostics());
            File workingDir = nativeImage.getParentFile();
            File launcher = new File(workingDir, nativeImageConfigureFileName());
            if (!launcher.exists()) {
                task.getLogger().info("Installing native-image-configure");
                execOperations.exec(spec -> {
                    spec.executable(nativeImage);
                    spec.args("--macro:native-image-configure-launcher");
                });
                NativeImageUtils.maybeCreateConfigureUtilSymlink(launcher, nativeImage.toPath());
            }
            if (launcher.exists()) {
                if (mergeWithOutputs.get()) {
                    List<String> inputs = inputDirs.get();
                    List<String> leftoverOutputDirs = new ArrayList<>();
                    for (String outputDir : outputDirs.get()) {
                        if (isConfigDir(outputDir)) {
                            List<String> newInputs = new ArrayList<>(inputs.size() + 1);
                            newInputs.addAll(inputs);
                            newInputs.add(outputDir);
                            mergeAgentFiles(launcher, newInputs, Collections.singletonList(outputDir));
                        } else {
                            leftoverOutputDirs.add(outputDir);
                        }
                    }

                    if (leftoverOutputDirs.size() > 0) {
                        mergeAgentFiles(launcher, inputs, leftoverOutputDirs);
                    }
                } else {
                    mergeAgentFiles(launcher, inputDirs.get(), outputDirs.get());
                }
            } else {
                task.getLogger().warn("Cannot merge agent files because native-image-configure is not installed. Please upgrade to a newer version of GraalVM.");
            }
        }
    }

    private void mergeAgentFiles(File launcher, List<String> inputDirs, List<String> outputDirs) {
        List<String> nicCommandLine = agentMode.get().getNativeImageConfigureOptions(inputDirs, outputDirs);

        if (nicCommandLine.size() > 0) {
            ExecResult exec = execOperations.exec(spec -> {
                spec.executable(launcher);
                spec.args(nicCommandLine);
                spec.setStandardOutput(System.out);
                spec.setErrorOutput(System.err);
            });
            if (exec.getExitValue() != 0) {
                exec.rethrowFailure();
            }
        }
    }
}
