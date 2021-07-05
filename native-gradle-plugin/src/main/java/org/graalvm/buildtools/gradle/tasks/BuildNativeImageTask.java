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
package org.graalvm.buildtools.gradle.tasks;

import org.graalvm.buildtools.gradle.dsl.NativeImageOptions;
import org.graalvm.buildtools.gradle.internal.GraalVMLogger;
import org.gradle.api.DefaultTask;
import org.gradle.api.GradleException;
import org.gradle.api.Transformer;
import org.gradle.api.file.Directory;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.file.FileSystemLocation;
import org.gradle.api.file.RegularFile;
import org.gradle.api.plugins.JavaBasePlugin;
import org.gradle.api.provider.Property;
import org.gradle.api.provider.Provider;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.Internal;
import org.gradle.api.tasks.Nested;
import org.gradle.api.tasks.Optional;
import org.gradle.api.tasks.OutputDirectory;
import org.gradle.api.tasks.TaskAction;
import org.gradle.jvm.toolchain.JavaInstallationMetadata;
import org.gradle.process.ExecOperations;

import javax.inject.Inject;
import java.io.File;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

import static org.graalvm.buildtools.gradle.internal.Utils.NATIVE_IMAGE_EXE;

/**
 * This task is responsible for generating a native image by
 * calling the corresponding tool in the GraalVM toolchain.
 */
public abstract class BuildNativeImageTask extends DefaultTask {
    private static final Transformer<Boolean, Boolean> NEGATE = b -> !b;

    private final Provider<String> graalvmHomeProvider;

    @Nested
    public abstract Property<NativeImageOptions> getOptions();

    @Inject
    protected abstract ExecOperations getExecOperations();

    @Internal
    protected abstract DirectoryProperty getWorkingDirectory();

    @OutputDirectory
    public abstract DirectoryProperty getOutputDirectory();

    @Optional
    @Input
    protected Provider<String> getGraalVMHome() {
        return graalvmHomeProvider;
    }

    @Internal
    public Provider<String> getExecutableName() {
        return getOptions().flatMap(NativeImageOptions::getImageName);
    }

    @Internal
    public Provider<RegularFile> getOutputFile() {
        return getOutputDirectory().map(dir -> dir.file(getExecutableName()).get());
    }

    @Input
    public abstract Property<Boolean> getAgentEnabled();

    public BuildNativeImageTask() {
        DirectoryProperty buildDir = getProject().getLayout().getBuildDirectory();
        Provider<Directory> outputDir = buildDir.dir("native/" + getName());
        getWorkingDirectory().set(outputDir);
        setDescription("Builds a native image.");
        setGroup(JavaBasePlugin.VERIFICATION_GROUP);

        getOptions().convention(getProject().getExtensions().findByType(NativeImageOptions.class));
        getOutputDirectory().convention(outputDir);
        this.graalvmHomeProvider = getProject().getProviders().environmentVariable("GRAALVM_HOME");
    }

    private List<String> buildActualCommandLineArgs() {
        getOptions().finalizeValue();
        NativeImageOptions options = getOptions().get();
        List<String> cliArgs = new ArrayList<>(20);

        cliArgs.add("-cp");
        cliArgs.add(options.getClasspath().getAsPath());

        appendBooleanOption(cliArgs, options.getDebug(), "-H:GenerateDebugInfo=1");
        appendBooleanOption(cliArgs, options.getFallback().map(NEGATE), "--no-fallback");
        appendBooleanOption(cliArgs, options.getVerbose(), "--verbose");
        appendBooleanOption(cliArgs, options.getServer(), "-Dcom.oracle.graalvm.isaot=true");

        cliArgs.add("-H:Path=" + getOutputDirectory().getAsFile().get().getAbsolutePath());
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


    // This property provides access to the service instance
    // It should be Property<NativeImageService> but because of a bug in Gradle
    // we have to use a more generic type, see https://github.com/gradle/gradle/issues/17559
    @Internal
    public abstract Property<Object> getService();

    @TaskAction
    @SuppressWarnings("ConstantConditions")
    public void exec() {
        List<String> args = buildActualCommandLineArgs();
        NativeImageOptions options = getOptions().get();
        GraalVMLogger logger = new GraalVMLogger(getLogger());
        if (options.getVerbose().get()) {
            logger.lifecycle("Args are: " + args);
        }
        JavaInstallationMetadata metadata = options.getJavaLauncher().get().getMetadata();
        File executablePath = metadata.getInstallationPath().file("bin/" + NATIVE_IMAGE_EXE).getAsFile();
        if (!executablePath.exists() && getGraalVMHome().isPresent()) {
            executablePath = Paths.get(getGraalVMHome().get()).resolve("bin").resolve(NATIVE_IMAGE_EXE).toFile();
        }
        if (executablePath.exists()) {
            logger.log("Using executable path: " + executablePath);
            String executable = executablePath.getAbsolutePath();
            File outputDir = getOutputDirectory().getAsFile().get();
            if (outputDir.isDirectory() || outputDir.mkdirs()) {
                getExecOperations().exec(spec -> {
                    spec.setWorkingDir(getWorkingDirectory());
                    spec.args(args);
                    getService().get();
                    spec.setExecutable(executable);
                });
                logger.lifecycle("Native Image written to: " + outputDir);
            }
            return;
        }
        throw new GradleException("Expected to find " + NATIVE_IMAGE_EXE + " executable but it wasn't found. " +
                "Make sure to declare the GRAALVM_HOME environment variable or install GraalVM with native-image in a standard location recognized by Gradle Java toolchain support");
    }
}
