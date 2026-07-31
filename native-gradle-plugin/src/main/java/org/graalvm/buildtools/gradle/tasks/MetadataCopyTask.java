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

import org.graalvm.buildtools.agent.AgentMode;
import org.graalvm.buildtools.agent.StandardAgentMode;
import org.graalvm.buildtools.gradle.internal.GraalVMLogger;
import org.graalvm.buildtools.gradle.internal.JavaLauncherProperty;
import org.graalvm.buildtools.gradle.internal.agent.AgentConfigurationFactory;
import org.graalvm.buildtools.gradle.tasks.actions.MergeAgentFilesAction;
import org.gradle.api.DefaultTask;
import org.gradle.api.GradleException;
import org.gradle.api.file.ProjectLayout;
import org.gradle.api.model.ObjectFactory;
import org.gradle.api.provider.ListProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.provider.Provider;
import org.gradle.api.provider.ProviderFactory;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.Internal;
import org.gradle.api.tasks.Nested;
import org.gradle.api.tasks.Optional;
import org.gradle.api.tasks.TaskAction;
import org.gradle.api.tasks.options.Option;
import org.gradle.jvm.toolchain.JavaLauncher;
import org.gradle.process.ExecOperations;

import javax.inject.Inject;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

import static org.graalvm.buildtools.gradle.internal.ConfigurationCacheSupport.serializableTransformerOf;
import static org.graalvm.buildtools.gradle.internal.NativeImageExecutableLocator.graalvmHomeProvider;

/**
 * Copies or merges tracing-agent metadata collected by Gradle tasks. §FS-tracing-agent.5.
 */
public abstract class MetadataCopyTask extends DefaultTask {

    private final GraalVMLogger logger;

    private final ProjectLayout layout;
    private final ProviderFactory providerFactory;
    private final ObjectFactory objectFactory;
    private final ExecOperations execOperations;
    private final Provider<String> graalvmHomeEnv;
    private final Provider<String> javaHomeEnv;
    private final Provider<String> gradleJvmHome;
    private final JavaLauncherProperty javaLauncher;

    @Inject
    public MetadataCopyTask(ProjectLayout layout,
                            ProviderFactory providerFactory,
                            ObjectFactory objectFactory,
                            ExecOperations execOperations) {
        this.logger = GraalVMLogger.of(getLogger());
        this.layout = layout;
        this.providerFactory = providerFactory;
        this.objectFactory = objectFactory;
        this.execOperations = execOperations;
        this.graalvmHomeEnv = providerFactory.environmentVariable("GRAALVM_HOME");
        this.javaHomeEnv = providerFactory.environmentVariable("JAVA_HOME");
        this.gradleJvmHome = providerFactory.systemProperty("java.home");
        this.javaLauncher = new JavaLauncherProperty(objectFactory.property(JavaLauncher.class));
    }

    @Internal
    public abstract ListProperty<String> getInputTaskNames();

    @Internal
    public abstract ListProperty<String> getOutputDirectories();

    @Internal
    public abstract Property<Boolean> getMergeWithExisting();

    @Internal
    public abstract Property<Boolean> getToolchainDetection();

    /**
     * Launcher used to locate Native Image while merging metadata. §FS-tracing-agent.5.
     */
    @Nested
    @Optional
    public Property<JavaLauncher> getJavaLauncher() {
        return javaLauncher;
    }

    // The environment sources that can supply native-image through the fallback candidates
    // (§FS-native-invocation.1.3) are task inputs in a 3-state sentinel form: "set:<value>",
    // "set:" for an empty value, and "unset". Distinguishing the states makes a change to
    // any source re-run the task instead of leaving it UP-TO-DATE with the previous executable.
    @Input
    protected Provider<String> getGraalvmHomeEnvInput() {
        return graalvmHomeEnv.map(serializableTransformerOf(value -> "set:" + value)).orElse("unset");
    }

    @Input
    protected Provider<String> getJavaHomeEnvInput() {
        return javaHomeEnv.map(serializableTransformerOf(value -> "set:" + value)).orElse("unset");
    }

    @Input
    protected Provider<String> getGradleJvmHomeInput() {
        return gradleJvmHome.map(serializableTransformerOf(value -> "set:" + value)).orElse("unset");
    }

    @Option(option = "task", description = "Executed task previously instrumented with the agent whose metadata should be copied.")
    public void overrideInputTaskNames(List<String> inputTaskNames) {
        getInputTaskNames().set(inputTaskNames);
    }

    @Option(option = "dir", description = "Directory to which the metadata will be copied.")
    public void overrideOutputDirectories(List<String> outputDirectories) {
        getOutputDirectories().set(outputDirectories);
    }

    @TaskAction
    public void exec() {
        StringBuilder builder = new StringBuilder();
        List<String> inputDirectories = new ArrayList<>();

        for (String taskName : getInputTaskNames().get()) {
            File dir = AgentConfigurationFactory.getAgentOutputDirectoryForTask(layout, taskName).get().getAsFile();
            if (!dir.exists()) {
                builder.append("Could not find configuration for task: ").append(taskName).append(". Please run the task with the agent.");
            } else if (!dir.isDirectory()) {
                builder.append("Expected a directory with configuration for task: ").append(taskName).append(" but found a regular file at ").append(dir.getAbsolutePath()).append(". Was the output directory manually modified?");
            }
            inputDirectories.add(dir.getAbsolutePath());
        }
        String errorString = builder.toString();
        if (!errorString.isEmpty()) {
            throw new GradleException(errorString);
        }

        List<String> outputDirectories = new ArrayList<>();
        for (String dirName : getOutputDirectories().get()) {
            File dir = layout.dir(providerFactory.provider(() -> new File(dirName))).get().getAsFile();
            outputDirectories.add(dir.getAbsolutePath());
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

        Provider<Boolean> isMergeEnabled = providerFactory.provider(() -> true);
        Provider<AgentMode> agentModeProvider = providerFactory.provider(StandardAgentMode::new);

        JavaLauncher resolvedLauncher = javaLauncher.getOrNull();
        boolean isExplicit = javaLauncher.isExplicit();

        Property<JavaLauncher> resolvedLauncherProperty = objectFactory.property(JavaLauncher.class);
        if (resolvedLauncher != null) {
            resolvedLauncherProperty.set(resolvedLauncher);
        }

        new MergeAgentFilesAction(
                isMergeEnabled,
                agentModeProvider,
                getMergeWithExisting(),
                objectFactory,
                resolvedLauncherProperty,
                graalvmHomeProvider(providerFactory),
                () -> inputDirectories,
                () -> outputDirectories,
                getToolchainDetection().map(enabled -> !enabled),
                providerFactory.provider(() -> isExplicit),
                execOperations,
                graalvmHomeEnv,
                javaHomeEnv,
                gradleJvmHome).execute(this);
    }
}
