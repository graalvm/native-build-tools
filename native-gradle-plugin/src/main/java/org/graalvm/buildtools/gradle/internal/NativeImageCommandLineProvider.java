/*
 * Copyright (c) 2021, 2022, Oracle and/or its affiliates. All rights reserved.
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
import org.graalvm.buildtools.utils.NativeImageUtils;
import org.gradle.api.Transformer;
import org.gradle.api.file.FileSystemLocation;
import org.gradle.api.file.FileTree;
import org.gradle.api.file.RegularFile;
import org.gradle.api.provider.Provider;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.InputFile;
import org.gradle.api.tasks.Nested;
import org.gradle.process.CommandLineArgumentProvider;

import java.io.File;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

public class NativeImageCommandLineProvider implements CommandLineArgumentProvider {
    private static final Transformer<Boolean, Boolean> NEGATE = b -> !b;

    private final Provider<NativeImageOptions> options;
    private final Provider<String> executableName;
    private final Provider<String> workingDirectory;
    private final Provider<String> outputDirectory;
    private final Provider<RegularFile> classpathJar;
    private final Provider<Boolean> useArgFile;
    private final Provider<Integer> majorJDKVersion;
    private final Provider<Boolean> useColors;

    public NativeImageCommandLineProvider(Provider<NativeImageOptions> options,
                                          Provider<String> executableName,
                                          Provider<String> workingDirectory,
                                          Provider<String> outputDirectory,
                                          Provider<RegularFile> classpathJar,
                                          Provider<Boolean> useArgFile,
                                          Provider<Integer> majorJDKVersion,
                                          Provider<Boolean> useColors) {
        this.options = options;
        this.executableName = executableName;
        this.workingDirectory = workingDirectory;
        this.outputDirectory = outputDirectory;
        this.classpathJar = classpathJar;
        this.useArgFile = useArgFile;
        this.majorJDKVersion = majorJDKVersion;
        this.useColors = useColors;
    }

    @Nested
    public Provider<NativeImageOptions> getOptions() {
        return options;
    }

    @Input
    public Provider<String> getExecutableName() {
        return executableName;
    }

    @Input
    public Provider<String> getOutputDirectory() {
        return outputDirectory;
    }

    @InputFile
    public Provider<RegularFile> getClasspathJar() {
        return classpathJar;
    }

    @Override
    public List<String> asArguments() {
        NativeImageOptions options = getOptions().get();
        List<String> cliArgs = new ArrayList<>(20);
        cliArgs.addAll(options.getExcludeConfigArgs().get());
        cliArgs.add("-cp");
        String classpathString = buildClasspathString(options);
        cliArgs.add(classpathString);
        appendBooleanOption(cliArgs, options.getDebug(), "-g");
        appendBooleanOption(cliArgs, options.getFallback().map(NEGATE), "--no-fallback");
        appendBooleanOption(cliArgs, options.getVerbose(), "--verbose");
        appendBooleanOption(cliArgs, options.getSharedLibrary(), "--shared");
        appendBooleanOption(cliArgs, options.getQuickBuild(), "-Ob");
        if (useColors.get()) {
            appendBooleanOption(cliArgs, options.getRichOutput(), majorJDKVersion.getOrElse(-1) >= 21 ? "--color" : "-H:+BuildOutputColorful");
        }
        appendBooleanOption(cliArgs, options.getPgoInstrument(), "--pgo-instrument");

        String targetOutputPath = getExecutableName().get();
        if (getOutputDirectory().isPresent()) {
            targetOutputPath = getOutputDirectory().get() + File.separator + targetOutputPath;
        }
        cliArgs.add("-o");
        cliArgs.add(targetOutputPath);

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
        if (Boolean.FALSE.equals(options.getPgoInstrument().get()) && options.getPgoProfilesDirectory().isPresent()) {
            FileTree files = options.getPgoProfilesDirectory().get().getAsFileTree();
            Set<File> profiles = files.filter(f -> f.getName().endsWith(".iprof")).getFiles();
            for (File profile : profiles) {
                cliArgs.add("--pgo=" + profile);
            }
        }
        cliArgs.addAll(options.getBuildArgs().get());

        List<String> actualCliArgs;
        if (useArgFile.getOrElse(true)) {
            Path argFileDir = Paths.get(workingDirectory.get());
            actualCliArgs = new ArrayList<>(NativeImageUtils.convertToArgsFile(cliArgs, argFileDir, argFileDir));
        } else {
            actualCliArgs = cliArgs;
        }

        /* Main class comes last. It is kept outside argument files as GraalVM releases before JDK 21 fail to detect the mainClass in these files. */
        if (options.getMainClass().isPresent()) {
            actualCliArgs.add(options.getMainClass().get());
        }
        return Collections.unmodifiableList(actualCliArgs);
    }

    /**
     * Builds a classpath string from the given classpath elements.
     * This can be overridden by subclasses for special needs. For
     * example, the Micronaut plugin requires this because it's going
     * to build images within a docker container, which makes it so
     * that the paths in the options are invalid (they would be prefixed
     * by a Windows path).
     *
     * @param options the native options
     * @return the classpath string
     */
    protected String buildClasspathString(NativeImageOptions options) {
        String classpathString;
        if (classpathJar.isPresent()) {
            classpathString = classpathJar.get().getAsFile().getAbsolutePath();
        } else {
            classpathString = options.getClasspath().getAsPath();
        }
        return classpathString;
    }

    private static void appendBooleanOption(List<String> cliArgs, Provider<Boolean> provider, String whenTrue) {
        if (provider.get()) {
            cliArgs.add(whenTrue);
        }
    }

}
