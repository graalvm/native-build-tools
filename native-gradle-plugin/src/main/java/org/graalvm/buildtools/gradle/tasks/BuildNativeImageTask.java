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
import org.graalvm.buildtools.utils.SchemaValidationUtils;
import org.gradle.api.DefaultTask;
import org.gradle.api.file.Directory;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.file.FileSystemOperations;
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
import java.nio.file.Path;
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
    private final boolean useColors;

    @Internal
    public abstract Property<NativeImageOptions> getOptions();

    @Nested
    protected NativeImageCompileOptions getCompileOptions() {
        getOptions().finalizeValue();
        return getOptions().get().asCompileOptions();
    }

    @Option(option = "quick-build-native", description = "Enables quick build mode")
    public void overrideQuickBuild(boolean quickBuild) {
        getOptions().get().getQuickBuild().set(quickBuild);
    }

    @Option(option = "debug-native", description = "Enables debug mode")
    public void overrideDebugBuild(boolean debug) {
        getOptions().get().getDebug().set(debug);
    }

    @Option(option = "verbose", description = "Enables verbose mode")
    public void overrideVerboseBuild(boolean verbose) {
        getOptions().get().getVerbose().set(verbose);
    }

    @Option(option = "fallback", description = "Enables fallback mode")
    public void overrideFallbackBuild(boolean fallback) {
        getOptions().get().getFallback().set(fallback);
    }

    @Option(option = "pgo-instrument", description = "Enables PGO instrumentation")
    public void overridePgoInstrument(boolean pgo) {
        getOptions().get().getPgoInstrument().set(pgo);
    }

    @Option(option = "main-class", description = "The fully qualified name of the entry point for native image compilation")
    public void overrideMainClass(String mainClass) {
        getCompileOptions().getMainClass().set(mainClass);
    }

    @Option(option = "build-args", description = "Adds arguments for the native-image compilation")
    public void appendBuildArgs(List<String> buildArgs) {
        getOptions().get().buildArgs(buildArgs);
    }

    @Option(option = "force-build-args", description = "Adds arguments for the native-image compilation")
    public void overrideBuildArgs(List<String> buildArgs) {
        getOptions().get().getBuildArgs().set(buildArgs);
    }

    @Option(option = "rich-output", description = "Enables rich output")
    public void overrideRichOutput(boolean richOutput) {
        getOptions().get().getRichOutput().set(richOutput);
    }

    @Option(option = "image-name", description = "The name of the generated native image")
    public void overrideImageName(String imageName) {
        getOptions().get().getImageName().set(imageName);
    }

    @Option(option = "fatjar", description = "Uses a fat jar as an input, instead of exploded classpath")
    public void overrideFatJar(boolean fatJar) {
        getOptions().get().getUseFatJar().set(fatJar);
    }

    @Option(option = "sysprop-native", description = "Adds a system property to the native image build (format key=value)")
    public void addSystemProperty(String property) {
        String[] parts = property.split("=", 2);
        if (parts.length == 2) {
            getOptions().get().systemProperty(parts[0], parts[1]);
        } else {
            getLogger().warn("Ignoring invalid system property: " + property);
        }
    }

    @Option(option = "env-native", description = "Adds a environment variable to the native image build (format key=value)")
    public void addEnvVar(String property) {
        String[] parts = property.split("=", 2);
        if (parts.length == 2) {
            getOptions().get().getEnvironmentVariables().put(parts[0], parts[1]);
        } else {
            getLogger().warn("Ignoring invalid environment variable: " + property);
        }
    }

    @Option(option = "jvm-args-native", description = "Adds arguments to the JVM used to build the native image")
    public void appendJvmArgs(List<String> jvmArgs) {
        getOptions().get().jvmArgs(jvmArgs);
    }

    @Option(option = "force-jvm-args-native", description = "Overrides arguments passed to the JVM used to build the native image")
    public void overrideJvmArgs(List<String> jvmArgs) {
        getOptions().get().getJvmArgs().set(jvmArgs);
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
    public Provider<RegularFile> getCreatedLayerFile() {
        return getOptions().zip(getOutputDirectory(), (options, dir) -> dir.file(options.getLayers().stream()
            .filter(CreateLayerOptions.class::isInstance)
            .map(cl -> cl.getLayerName().get() + ".nil")
            .findFirst()
            .orElseThrow()));
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

    @Input
    @Optional
    public abstract Property<Boolean> getMetadataRepositoryEnabled();

    @Input
    @Optional
    public abstract Property<String> getMetadataRepositoryRootPath();

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
        this.useColors = "plain".equals(getProject().getGradle().getStartParameter().getConsoleOutput());
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
            getProviders().provider(() -> majorJDKVersion),
            getProviders().provider(() -> useColors))
            .asArguments();
    }

    // This property provides access to the service instance
    // It should be Property<NativeImageService> but because of a bug in Gradle
    // we have to use a more generic type, see https://github.com/gradle/gradle/issues/17559
    @Internal
    public abstract Property<Object> getService();

    @Inject
    protected abstract FileSystemOperations getFileSystemOperations();

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
        Boolean metadataEnabled = getMetadataRepositoryEnabled().getOrNull();
        String metadataRoot = getMetadataRepositoryRootPath().getOrNull();
        if (Boolean.TRUE.equals(metadataEnabled) && metadataRoot != null) {
            SchemaValidationUtils.validateReachabilityMetadataSchema(Path.of(metadataRoot), NativeImageUtils.getMajorJDKVersion(versionString), executablePath.toPath());
        }
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
        getFileSystemOperations().delete(d -> d.delete(outputDir));
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
