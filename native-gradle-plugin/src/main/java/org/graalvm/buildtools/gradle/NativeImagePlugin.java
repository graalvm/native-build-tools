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
package org.graalvm.buildtools.gradle;

import org.graalvm.buildtools.VersionInfo;
import org.graalvm.buildtools.gradle.dsl.NativeImageOptions;
import org.graalvm.buildtools.gradle.internal.AgentCommandLineProvider;
import org.graalvm.buildtools.gradle.internal.CopyClasspathResourceTask;
import org.graalvm.buildtools.gradle.internal.GraalVMLogger;
import org.graalvm.buildtools.gradle.internal.GradleUtils;
import org.graalvm.buildtools.gradle.internal.Utils;
import org.graalvm.buildtools.gradle.tasks.BuildNativeImageTask;
import org.graalvm.buildtools.gradle.tasks.NativeRunTask;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.plugins.ApplicationPlugin;
import org.gradle.api.plugins.JavaApplication;
import org.gradle.api.plugins.JavaPlugin;
import org.gradle.api.provider.ListProperty;
import org.gradle.api.provider.Provider;
import org.gradle.api.tasks.JavaExec;
import org.gradle.api.tasks.SourceSet;
import org.gradle.api.tasks.TaskCollection;
import org.gradle.api.tasks.TaskContainer;
import org.gradle.api.tasks.TaskProvider;
import org.gradle.api.tasks.testing.Test;
import org.gradle.process.JavaForkOptions;

import java.io.File;
import java.util.function.Consumer;

import static org.graalvm.buildtools.gradle.internal.Utils.AGENT_FILTER;
import static org.graalvm.buildtools.gradle.internal.Utils.AGENT_OUTPUT_FOLDER;
import static org.graalvm.buildtools.gradle.internal.Utils.AGENT_PROPERTY;

/**
 * Gradle plugin for GraalVM Native Image.
 */
@SuppressWarnings("unused")
public class NativeImagePlugin implements Plugin<Project> {
    public static final String NATIVE_BUILD_TASK_NAME = "nativeBuild";
    public static final String NATIVE_TEST_TASK_NAME = "nativeTest";
    public static final String NATIVE_TEST_BUILD_TASK_NAME = "nativeTestBuild";
    public static final String NATIVE_TEST_EXTENSION = "nativeTest";
    public static final String NATIVE_BUILD_EXTENSION = "nativeBuild";
    public static final String COPY_AGENT_FILTER_TASK_NAME = "copyAgentFilter";

    /**
     * This looks strange, but it is used to force the configuration of a dependent
     * task during the configuration of another one. This is a workaround for a bug
     * when applying the Kotlin plugin, where the test task is configured too late
     * for some reason.
     */
    private static final Consumer<Object> FORCE_CONFIG = t -> { };

    private GraalVMLogger logger;

    public void apply(Project project) {
        Provider<NativeImageService> nativeImageServiceProvider = NativeImageService.registerOn(project);

        logger = new GraalVMLogger(project.getLogger());

        project.getPlugins()
                .withType(JavaPlugin.class, javaPlugin -> configureJavaProject(project, nativeImageServiceProvider));
    }

    private void configureJavaProject(Project project, Provider<NativeImageService> nativeImageServiceProvider) {
        logger.log("====================");
        logger.log("Initializing project: " + project.getName());
        logger.log("====================");

        // Add DSL extensions for building and testing
        NativeImageOptions buildExtension = createMainExtension(project);
        NativeImageOptions testExtension = createTestExtension(project, buildExtension);

        project.getPlugins().withId("application", p -> buildExtension.getMainClass().convention(
                project.getExtensions().findByType(JavaApplication.class).getMainClass()
        ));

        registerServiceProvider(project, nativeImageServiceProvider);

        // Register Native Image tasks
        TaskContainer tasks = project.getTasks();

        Provider<Boolean> agent = agentPropertyOverride(project, buildExtension);
        TaskProvider<BuildNativeImageTask> imageBuilder = tasks.register(NATIVE_BUILD_TASK_NAME,
                BuildNativeImageTask.class, builder -> builder.getAgentEnabled().set(agent));
        tasks.register(NativeRunTask.TASK_NAME, NativeRunTask.class, task -> {
            task.getImage().convention(imageBuilder.map(t -> t.getOutputFile().get()));
            task.getRuntimeArgs().convention(buildExtension.getRuntimeArgs());
        });

        TaskProvider<CopyClasspathResourceTask> copyAgentFilterTask = registerCopyAgentFilterTask(project);

        // We want to add agent invocation to "run" task, but it is only available when
        // Application Plugin is initialized.
        project.getPlugins().withType(ApplicationPlugin.class, applicationPlugin ->
                tasks.withType(JavaExec.class).named(ApplicationPlugin.TASK_RUN_NAME, run -> {
                    configureAgent(project, run, copyAgentFilterTask, agent, buildExtension, run.getName());
                }));

        // In future Gradle releases this becomes a proper DirectoryProperty
        File testResultsDir = GradleUtils.getJavaPluginConvention(project).getTestResultsDir();
        DirectoryProperty testListDirectory = project.getObjects().directoryProperty();

        // Testing part begins here.
        TaskCollection<Test> testTask = findTestTask(project);
        Provider<Boolean> testAgent = agentPropertyOverride(project, testExtension);

        testTask.configureEach(test -> {
            testListDirectory.set(new File(testResultsDir, test.getName() + "/testlist"));
            test.getOutputs().dir(testResultsDir);
            test.systemProperty("graalvm.testids.outputdir", testListDirectory.getAsFile().get());
            configureAgent(project, test, copyAgentFilterTask, testAgent, testExtension, test.getName());
        });

        // Following ensures that required feature jar is on classpath for every project
        injectTestPluginDependencies(project);

        TaskProvider<BuildNativeImageTask> testImageBuilder = tasks.register(NATIVE_TEST_BUILD_TASK_NAME, BuildNativeImageTask.class, task -> {
            task.setDescription("Builds native image with tests.");
            task.getOptions().set(testExtension);
            testTask.forEach(FORCE_CONFIG);
            ConfigurableFileCollection testList = project.getObjects().fileCollection();
            // Later this will be replaced by a dedicated task not requiring execution of tests
            testList.from(testListDirectory).builtBy(testTask);
            testExtension.getClasspath().from(testList);
            task.getAgentEnabled().set(testAgent);
        });

        tasks.register(NATIVE_TEST_TASK_NAME, NativeRunTask.class, task -> {
            task.setDescription("Runs native-image compiled tests.");
            task.getImage().convention(testImageBuilder.map(t -> t.getOutputFile().get()));
            task.getRuntimeArgs().convention(testExtension.getRuntimeArgs());
        });
    }

    /**
     * Returns a provider which prefers the CLI arguments over the configured
     * extension value.
     */
    private static Provider<Boolean> agentPropertyOverride(Project project, NativeImageOptions extension) {
        return project.getProviders()
                .gradleProperty(AGENT_PROPERTY)
                .forUseAtConfigurationTime()
                .map(v -> {
                    if (!v.isEmpty()) {
                        return Boolean.valueOf(v);
                    }
                    return true;
                })
                .orElse(extension.getAgent());
    }

    private static TaskProvider<CopyClasspathResourceTask> registerCopyAgentFilterTask(Project project) {
        return project.getTasks().register(COPY_AGENT_FILTER_TASK_NAME, CopyClasspathResourceTask.class, task -> {
            task.getClasspathResource().set("/" + AGENT_FILTER);
            task.getOutputFile().set(project.getLayout().getBuildDirectory().file("native/agent-filter/" + AGENT_FILTER));
        });
    }

    private static TaskCollection<Test> findTestTask(Project project) {
        return project.getTasks().withType(Test.class).matching(task -> JavaPlugin.TEST_TASK_NAME.equals(task.getName()));
    }

    @SuppressWarnings("UnstableApiUsage")
    private static void registerServiceProvider(Project project, Provider<NativeImageService> nativeImageServiceProvider) {
        project.getTasks()
                .withType(BuildNativeImageTask.class)
                .configureEach(task -> {
                    task.usesService(nativeImageServiceProvider);
                    task.getService().set(nativeImageServiceProvider);
                });
    }

    private static NativeImageOptions createMainExtension(Project project) {
        NativeImageOptions buildExtension = NativeImageOptions.register(project, NATIVE_BUILD_EXTENSION);
        buildExtension.getClasspath().from(GradleUtils.findMainArtifacts(project));
        buildExtension.getClasspath().from(GradleUtils.findConfiguration(project, JavaPlugin.RUNTIME_CLASSPATH_CONFIGURATION_NAME));
        return buildExtension;
    }

    private static NativeImageOptions createTestExtension(Project project, NativeImageOptions mainExtension) {
        NativeImageOptions testExtension = NativeImageOptions.register(project, NATIVE_TEST_EXTENSION);
        testExtension.getMainClass().set("org.graalvm.junit.platform.NativeImageJUnitLauncher");
        testExtension.getMainClass().finalizeValue();
        testExtension.getImageName().convention(mainExtension.getImageName().map(name -> name + Utils.NATIVE_TESTS_SUFFIX));
        ListProperty<String> runtimeArgs = testExtension.getRuntimeArgs();
        runtimeArgs.add("--xml-output-dir");
        runtimeArgs.add(project.getLayout().getBuildDirectory().dir("test-results/test-native").map(d -> d.getAsFile().getAbsolutePath()));
        testExtension.buildArgs("--features=org.graalvm.junit.platform.JUnitPlatformFeature");
        ConfigurableFileCollection classpath = testExtension.getClasspath();
        classpath.from(GradleUtils.findMainArtifacts(project));
        classpath.from(GradleUtils.findConfiguration(project, JavaPlugin.TEST_RUNTIME_CLASSPATH_CONFIGURATION_NAME));
        classpath.from(GradleUtils.findSourceSet(project, SourceSet.TEST_SOURCE_SET_NAME).getOutput().getClassesDirs());
        classpath.from(GradleUtils.findSourceSet(project, SourceSet.TEST_SOURCE_SET_NAME).getOutput().getResourcesDir());
        return testExtension;
    }

    private static void configureAgent(Project project,
                                                 JavaForkOptions javaForkOptions,
                                                 TaskProvider<CopyClasspathResourceTask> filterProvider,
                                                 Provider<Boolean> agent,
                                                 NativeImageOptions nativeImageOptions,
                                                 String context) {
        AgentCommandLineProvider cliProvider = project.getObjects().newInstance(AgentCommandLineProvider.class);
        cliProvider.getEnabled().set(agent);
        cliProvider.getAccessFilter().set(filterProvider.flatMap(CopyClasspathResourceTask::getOutputFile));
        cliProvider.getOutputDirectory().set(project.getLayout().getBuildDirectory().dir(AGENT_OUTPUT_FOLDER + "/" + context));
        javaForkOptions.getJvmArgumentProviders().add(cliProvider);
        // We're "desugaring" the output intentionally to workaround a Gradle bug which absolutely
        // wants the file to track the input but since it's not from a task it would fail
        Provider<File> producer = project.getProviders().provider(() -> cliProvider.getOutputDirectory().get().getAsFile());
        nativeImageOptions.getConfigurationFileDirectories().from(
                agent.map(enabled -> enabled ? producer.get() : project.files())
        );
    }

    private static void injectTestPluginDependencies(Project project) {
        project.getDependencies().add("implementation", "org.graalvm.buildtools:junit-platform-native:"
                + VersionInfo.JUNIT_PLATFORM_NATIVE_VERSION);
    }

}
