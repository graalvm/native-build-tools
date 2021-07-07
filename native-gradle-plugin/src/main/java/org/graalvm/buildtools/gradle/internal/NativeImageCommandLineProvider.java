/*
 * Copyright (c) 2021, Oracle and/or its affiliates. All rights reserved.
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

package org.graalvm.buildtools.gradle.internal;

import org.graalvm.buildtools.gradle.dsl.NativeImageOptions;
import org.gradle.api.Transformer;
import org.gradle.api.file.FileSystemLocation;
import org.gradle.api.provider.Provider;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.Nested;
import org.gradle.process.CommandLineArgumentProvider;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

public class NativeImageCommandLineProvider implements CommandLineArgumentProvider {
    private static final Transformer<Boolean, Boolean> NEGATE = b -> !b;

    private final Provider<NativeImageOptions> options;
    private final Provider<Boolean> agentEnabled;
    private final Provider<String> executableName;
    private final Provider<String> outputDirectory;

    public NativeImageCommandLineProvider(Provider<NativeImageOptions> options,
                                          Provider<Boolean> agentEnabled,
                                          Provider<String> executableName,
                                          Provider<String> outputDirectory) {
        this.options = options;
        this.agentEnabled = agentEnabled;
        this.executableName = executableName;
        this.outputDirectory = outputDirectory;
    }

    @Nested
    public Provider<NativeImageOptions> getOptions() {
        return options;
    }

    @Input
    public Provider<Boolean> getAgentEnabled() {
        return agentEnabled;
    }

    @Input
    public Provider<String> getExecutableName() {
        return executableName;
    }

    @Input
    public Provider<String> getOutputDirectory() {
        return outputDirectory;
    }

    @Override
    public List<String> asArguments() {
        NativeImageOptions options = getOptions().get();
        List<String> cliArgs = new ArrayList<>(20);

        cliArgs.add("-cp");
        cliArgs.add(options.getClasspath().getAsPath());

        appendBooleanOption(cliArgs, options.getDebug(), "-H:GenerateDebugInfo=1");
        appendBooleanOption(cliArgs, options.getFallback().map(NEGATE), "--no-fallback");
        appendBooleanOption(cliArgs, options.getVerbose(), "--verbose");
        appendBooleanOption(cliArgs, options.getServer(), "-Dcom.oracle.graalvm.isaot=true");
        if (getOutputDirectory().isPresent()) {
            cliArgs.add("-H:Path=" + getOutputDirectory().get());
        }
        cliArgs.add("-H:Name=" + getExecutableName().get());

        options.getSystemProperties().get().forEach((n, v) -> {
            if (v != null) {
                cliArgs.add("-D" + n + "=\"" + v + "\"");
            }
        });

        options.getJvmArgs().get().forEach(jvmArg -> cliArgs.add("-J" + jvmArg));

        String configFiles = options.getConfigurationFileDirectories()
                .getElements()
                .get()
                .stream()
                .map(FileSystemLocation::getAsFile)
                .map(File::getAbsolutePath)
                .collect(Collectors.joining(","));
        if (!configFiles.isEmpty()) {
            cliArgs.add("-H:ConfigurationFileDirectories=" + configFiles);
        }
        if (getAgentEnabled().get()) {
            cliArgs.add("--allow-incomplete-classpath");
        }
        if (options.getMainClass().isPresent()) {
            cliArgs.add("-H:Class=" + options.getMainClass().get());
        }
        cliArgs.addAll(options.getBuildArgs().get());
        return Collections.unmodifiableList(cliArgs);
    }

    private static void appendBooleanOption(List<String> cliArgs, Provider<Boolean> provider, String whenTrue) {
        if (provider.get()) {
            cliArgs.add(whenTrue);
        }
    }

}
