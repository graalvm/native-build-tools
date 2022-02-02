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
package org.graalvm.buildtools.gradle;

import org.graalvm.buildtools.gradle.dsl.NativeImageOptions;
import org.graalvm.buildtools.gradle.internal.GraalVMLogger;
import org.graalvm.buildtools.utils.NativeImageUtils;
import org.gradle.api.Action;
import org.gradle.api.Task;
import org.gradle.api.file.Directory;
import org.gradle.api.file.FileSystemOperations;
import org.gradle.api.logging.Logger;
import org.gradle.api.provider.Provider;
import org.gradle.process.ExecOperations;
import org.gradle.process.ExecResult;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Stream;

import static org.graalvm.buildtools.gradle.internal.NativeImageExecutableLocator.findNativeImageExecutable;
import static org.graalvm.buildtools.utils.NativeImageUtils.nativeImageConfigureFileName;

class MergeAgentFiles implements Action<Task> {
    private final Provider<Boolean> agent;
    private final Provider<String> graalvmHomeProvider;
    private final Provider<Directory> outputDir;
    private final Provider<Boolean> disableToolchainDetection;
    private final ExecOperations execOperations;
    private final NativeImageOptions options;
    private final FileSystemOperations fileOperations;
    private final Logger logger;

    MergeAgentFiles(Provider<Boolean> agent,
                    Provider<String> graalvmHomeProvider,
                    Provider<Directory> outputDir,
                    Provider<Boolean> disableToolchainDetection,
                    NativeImageOptions options,
                    ExecOperations execOperations,
                    FileSystemOperations fileOperations,
                    Logger logger) {
        this.agent = agent;
        this.graalvmHomeProvider = graalvmHomeProvider;
        this.outputDir = outputDir;
        this.disableToolchainDetection = disableToolchainDetection;
        this.options = options;
        this.execOperations = execOperations;
        this.fileOperations = fileOperations;
        this.logger = logger;
    }

    @Override
    public void execute(Task task) {
        if (agent.get()) {
            File nativeImage = findNativeImageExecutable(options, disableToolchainDetection, graalvmHomeProvider, execOperations, GraalVMLogger.of(logger));
            File workingDir = nativeImage.getParentFile();
            File launcher = new File(workingDir, nativeImageConfigureFileName());
            if (!launcher.exists()) {
                logger.info("Installing native-image-configure");
                execOperations.exec(spec -> {
                    spec.executable(nativeImage);
                    spec.args("--macro:native-image-configure-launcher");
                });
                NativeImageUtils.maybeCreateConfigureUtilSymlink(launcher, nativeImage.toPath());
            }
            if (launcher.exists()) {
                File[] files = outputDir.get().getAsFile().listFiles();
                List<String> args = new ArrayList<>(files.length + 2);
                args.add("generate");
                sessionDirectoriesFrom(files)
                        .map(f -> "--input-dir=" + f.getAbsolutePath())
                        .forEach(args::add);
                if (args.size() > 1) {
                    logger.info("Merging agent files");
                    args.add("--output-dir=" + outputDir.get().getAsFile().getAbsolutePath());
                    ExecResult exec = execOperations.exec(spec -> {
                        spec.executable(launcher);
                        spec.args(args);
                        spec.setStandardOutput(System.out);
                        spec.setErrorOutput(System.err);
                    });
                    if (exec.getExitValue() == 0) {
                        fileOperations.delete(spec -> sessionDirectoriesFrom(files).forEach(spec::delete));
                    } else {
                        exec.rethrowFailure();
                    }
                }
            } else {
                logger.warn("Cannot merge agent files because native-image-configure is not installed. Please upgrade to a newer version of GraalVM.");
            }
        }
    }

    private Stream<File> sessionDirectoriesFrom(File[] files) {
        return Arrays.stream(files)
                .filter(File::isDirectory)
                .filter(f -> f.getName().startsWith("session-"));
    }
}
