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

package org.graalvm.buildtools.gradle.tasks;

import org.graalvm.buildtools.gradle.NativeImagePlugin;
import org.graalvm.buildtools.gradle.dsl.NativeImageCompileOptions;
import org.graalvm.buildtools.gradle.dsl.NativeImageOptions;
import org.graalvm.buildtools.gradle.internal.GraalVMLogger;
import org.graalvm.buildtools.gradle.internal.NativeImageCommandLineProvider;
import org.graalvm.buildtools.gradle.internal.NativeImageExecutableLocator;
import org.graalvm.buildtools.utils.NativeImageUtils;
import org.gradle.api.DefaultTask;
import org.gradle.api.file.Directory;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.file.RegularFile;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.model.ObjectFactory;
import org.gradle.api.plugins.JavaBasePlugin;
import org.gradle.api.provider.MapProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.provider.Provider;
import org.gradle.api.provider.ProviderFactory;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.InputDirectory;
import org.gradle.api.tasks.InputFile;
import org.gradle.api.tasks.Internal;
import org.gradle.api.tasks.Nested;
import org.gradle.api.tasks.Optional;
import org.gradle.api.tasks.OutputDirectory;
import org.gradle.api.tasks.TaskAction;
import org.gradle.api.tasks.options.Option;
import org.gradle.process.ExecOperations;
import org.gradle.process.ExecResult;

import javax.inject.Inject;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.graalvm.buildtools.gradle.internal.ConfigurationCacheSupport.serializableBiFunctionOf;
import static org.graalvm.buildtools.gradle.internal.NativeImageExecutableLocator.graalvmHomeProvider;
import static org.graalvm.buildtools.utils.SharedConstants.EXECUTABLE_EXTENSION;

/**
 * This task is responsible for generating a native image by
 * calling the corresponding tool in the GraalVM toolchain.
 */
public abstract class BuildNativeImageTask extends DefaultTask {
    private final Provider<String> graalvmHomeProvider;
    private final NativeImageExecutableLocator.Diagnostics diagnostics;

    @Internal
    public abstract Property<NativeImageOptions> getOptions();

    @Nested
    protected NativeImageCompileOptions getCompileOptions() {
        getOptions().finalizeValue();
        return getOptions().get().asCompileOptions();
    }

    @Option(option = "quick-build-native", description = "Enables quick build mode")
    public void overrideQuickBuild() {
        getOptions().get().getQuickBuild().set(true);
    }

    @Option(option = "debug-native", description = "Enables debug mode")
    public void overrideDebugBuild() {
        getOptions().get().getDebug().set(true);
    }

    @Option(option = "pgo-instrument", description = "Enables PGO instrumentation")
    public void overridePgoInstrument() {
        getOptions().get().getPgoInstrument().set(true);
    }

    @Inject
    protected abstract ExecOperations getExecOperations();

    @Internal
    protected abstract DirectoryProperty getWorkingDirectory();

    @OutputDirectory
    public abstract DirectoryProperty getOutputDirectory();

    @InputDirectory
    @Optional
    public abstract DirectoryProperty getTestListDirectory();

    @Optional
    @Input
    protected Provider<String> getGraalVMHome() {
        return graalvmHomeProvider;
    }

    @Internal
    public Provider<String> getExecutableShortName() {
        return getOptions().flatMap(options ->
                options.getImageName().zip(options.getPgoInstrument(), serializableBiFunctionOf((name, pgo) -> name + (Boolean.TRUE.equals(pgo) ? "-instrumented" : "")))
        );
    }

    @Internal
    public Provider<String> getExecutableName() {
        return getExecutableShortName().map(name -> name + EXECUTABLE_EXTENSION);
    }

    @Internal
    public Provider<RegularFile> getOutputFile() {
        return getOutputDirectory().zip(getExecutableName(), Directory::file);
    }

    @Input
    public abstract Property<Boolean> getDisableToolchainDetection();

    @Inject
    protected abstract ProviderFactory getProviders();

    @Inject
    protected abstract ObjectFactory getObjects();

    @InputFile
    @Optional
    public abstract RegularFileProperty getClasspathJar();

    @Input
    @Optional
    public abstract Property<Boolean> getUseArgFile();

    public BuildNativeImageTask() {
        DirectoryProperty buildDir = getProject().getLayout().getBuildDirectory();
        Provider<Directory> outputDir = buildDir.dir("native/" + getName());
        getWorkingDirectory().set(outputDir);
        setDescription("Builds a native image.");
        setGroup(JavaBasePlugin.VERIFICATION_GROUP);
        getOutputDirectory().convention(outputDir);
        ProviderFactory providers = getProject().getProviders();
        this.diagnostics = new NativeImageExecutableLocator.Diagnostics();
        this.graalvmHomeProvider = graalvmHomeProvider(providers, diagnostics);
        getDisableToolchainDetection().convention(false);
    }

    private List<String> buildActualCommandLineArgs(int majorJDKVersion) {
        getOptions().finalizeValue();
        return new NativeImageCommandLineProvider(
                getOptions(),
                getExecutableShortName(),
                getProviders().provider(() -> getWorkingDirectory().get().getAsFile().getAbsolutePath()),
                // Can't use getOutputDirectory().map(...) because Gradle would complain that we use
                // a mapped value before the task was called, when we are actually calling it...
                getProviders().provider(() -> getOutputDirectory().getAsFile().get().getAbsolutePath()),
                getClasspathJar(),
                getUseArgFile(),
                getProviders().provider(() -> majorJDKVersion)).asArguments();
    }

    // This property provides access to the service instance
    // It should be Property<NativeImageService> but because of a bug in Gradle
    // we have to use a more generic type, see https://github.com/gradle/gradle/issues/17559
    @Internal
    public abstract Property<Object> getService();

    @TaskAction
    public void exec() {
        NativeImageOptions options = getOptions().get();
        GraalVMLogger logger = GraalVMLogger.of(getLogger());

        File executablePath = NativeImageExecutableLocator.findNativeImageExecutable(
                options.getJavaLauncher(),
                getDisableToolchainDetection(),
                getGraalVMHome(),
                getExecOperations(),
                logger,
                diagnostics);
        String versionString = getVersionString(getExecOperations(), executablePath);
        if (options.getRequiredVersion().isPresent()) {
            NativeImageUtils.checkVersion(options.getRequiredVersion().get(), versionString);
        }
        int majorJDKVersion = NativeImageUtils.getMajorJDKVersion(versionString);
        List<String> args = buildActualCommandLineArgs(majorJDKVersion);
        if (options.getVerbose().get()) {
            logger.lifecycle("Args are: " + args);
        }
        for (String diagnostic : diagnostics.getDiagnostics()) {
            logger.lifecycle(diagnostic);
        }
        String executable = executablePath.getAbsolutePath();
        File outputDir = getOutputDirectory().getAsFile().get();
        if (outputDir.isDirectory() || outputDir.mkdirs()) {
            getExecOperations().exec(spec -> {
                MapProperty<String, Object> environmentVariables = options.getEnvironmentVariables();
                if (environmentVariables.isPresent() && !environmentVariables.get().isEmpty()) {
                    spec.environment(environmentVariables.get());
                }
                spec.setWorkingDir(getWorkingDirectory());
                if (getTestListDirectory().isPresent()) {
                    NativeImagePlugin.TrackingDirectorySystemPropertyProvider directoryProvider = getObjects().newInstance(NativeImagePlugin.TrackingDirectorySystemPropertyProvider.class);
                    directoryProvider.getDirectory().set(getTestListDirectory());
                    spec.getArgumentProviders().add(directoryProvider);
                }
                spec.args(args);
                getService().get();
                spec.setExecutable(executable);
            });
            logger.lifecycle("Native Image written to: " + outputDir);
        }
    }

    public static String getVersionString(ExecOperations execOperations, File executablePath) {
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        ExecResult execResult = execOperations.exec(spec -> {
            spec.setStandardOutput(outputStream);
            spec.args("--version");
            spec.setExecutable(executablePath.getAbsolutePath());
        });
        execResult.assertNormalExitValue();
        return new String(outputStream.toByteArray(), StandardCharsets.UTF_8);
    }
}
