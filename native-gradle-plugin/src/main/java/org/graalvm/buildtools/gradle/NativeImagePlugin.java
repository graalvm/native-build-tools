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
import org.graalvm.buildtools.gradle.internal.GraalVMLogger;
import org.graalvm.buildtools.gradle.internal.GradleUtils;
import org.graalvm.buildtools.gradle.internal.Utils;
import org.graalvm.buildtools.gradle.tasks.BuildNativeImageTask;
import org.graalvm.buildtools.gradle.tasks.NativeRunTask;
import org.gradle.api.GradleException;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.Task;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.file.FileCollection;
import org.gradle.api.plugins.ApplicationPlugin;
import org.gradle.api.plugins.JavaApplication;
import org.gradle.api.plugins.JavaPlugin;
import org.gradle.api.provider.ListProperty;
import org.gradle.api.provider.Provider;
import org.gradle.api.tasks.SourceSet;
import org.gradle.api.tasks.TaskCollection;
import org.gradle.api.tasks.TaskProvider;
import org.gradle.api.tasks.testing.Test;
import org.gradle.process.JavaForkOptions;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.Arrays;
import java.util.Objects;

import static org.graalvm.buildtools.gradle.internal.Utils.AGENT_FILTER;
import static org.graalvm.buildtools.gradle.internal.Utils.AGENT_OUTPUT_FOLDER;

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

    private GraalVMLogger logger;

    @SuppressWarnings("UnstableApiUsage")
    public void apply(Project project) {
        Provider<NativeImageService> nativeImageServiceProvider = registerNativeImageService(project);

        logger = new GraalVMLogger(project.getLogger());

        project.getPlugins().withType(JavaPlugin.class, javaPlugin -> {
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
            TaskProvider<BuildNativeImageTask> imageBuilder = project.getTasks().register(NATIVE_BUILD_TASK_NAME, BuildNativeImageTask.class);

            project.getTasks().register(NativeRunTask.TASK_NAME, NativeRunTask.class, task -> {
                task.getImage().convention(imageBuilder.map(t -> t.getOutputFile().get()));
                task.getRuntimeArgs().convention(buildExtension.getRuntimeArgs());
            });

            if (project.hasProperty(Utils.AGENT_PROPERTY) || buildExtension.getAgent().get()) {
                // We want to add agent invocation to "run" task, but it is only available when
                // Application Plugin is initialized.
                project.getPlugins().withType(ApplicationPlugin.class, applicationPlugin -> {
                    Task run = project.getTasksByName(ApplicationPlugin.TASK_RUN_NAME, false).stream().findFirst()
                            .orElse(null);
                    assert run != null : "Application plugin didn't register 'run' task";

                    boolean persistConfig = System.getProperty(Utils.PERSIST_CONFIG_PROPERTY) != null
                            || buildExtension.getPersistConfig().get();
                    setAgentArgs(project, SourceSet.MAIN_SOURCE_SET_NAME, run, persistConfig);
                });
            }

            // In future Gradle releases this becomes a proper DirectoryProperty
            File testResultsDir = GradleUtils.getJavaPluginConvention(project).getTestResultsDir();
            DirectoryProperty testListDirectory = project.getObjects().directoryProperty();

            // Testing part begins here.
            TaskCollection<Test> testTask = findTestTask(project);
            testTask.configureEach(test -> {
                testListDirectory.set(new File(testResultsDir, test.getName() + "/testlist"));
                test.getOutputs().dir(testResultsDir);
                test.systemProperty("graalvm.testids.outputdir", testListDirectory.getAsFile().get());
            });

            // Following ensures that required feature jar is on classpath for every project
            injectTestPluginDependencies(project);

            // If `test` task was found we should add `nativeTestBuild` and `nativeTest`
            // tasks to this project as well.
            TaskProvider<BuildNativeImageTask> testImageBuilder = project.getTasks().register(NATIVE_TEST_BUILD_TASK_NAME, BuildNativeImageTask.class, task -> {
                task.setDescription("Builds native image with tests.");
                task.getOptions().set(testExtension);
                ConfigurableFileCollection testList = project.getObjects().fileCollection();
                // Later this will be replaced by a dedicated task not requiring execution of tests
                testList.from(testListDirectory).builtBy(testTask);
                testExtension.getClasspath().from(testList);
            });

            project.getTasks().register(NATIVE_TEST_TASK_NAME, NativeRunTask.class, task -> {
                task.setDescription("Runs native-image compiled tests.");
                task.getImage().convention(testImageBuilder.map(t -> t.getOutputFile().get()));
                task.getRuntimeArgs().convention(testExtension.getRuntimeArgs());
            });

            if (project.hasProperty(Utils.AGENT_PROPERTY) || testExtension.getAgent().get()) {
                // Add agent invocation to test task.
                Boolean persistConfig = System.getProperty(Utils.PERSIST_CONFIG_PROPERTY) != null
                        || testExtension.getPersistConfig().get();
            //    setAgentArgs(project, SourceSet.TEST_SOURCE_SET_NAME, test, persistConfig);
            }

        });
    }

    private TaskCollection<Test> findTestTask(Project project) {
        return project.getTasks().withType(Test.class).matching(task -> JavaPlugin.TEST_TASK_NAME.equals(task.getName()));
    }

    private static void registerServiceProvider(Project project, Provider<NativeImageService> nativeImageServiceProvider) {
        project.getTasks()
                .withType(BuildNativeImageTask.class)
                .configureEach(task -> {
                    task.usesService(nativeImageServiceProvider);
                    task.getService().set(nativeImageServiceProvider);
                });
    }

    private NativeImageOptions createMainExtension(Project project) {
        NativeImageOptions buildExtension = NativeImageOptions.register(project, NATIVE_BUILD_EXTENSION);
        buildExtension.getClasspath().from(findMainArtifacts(project));
        buildExtension.getClasspath().from(findConfiguration(project, JavaPlugin.RUNTIME_CLASSPATH_CONFIGURATION_NAME));
        return buildExtension;
    }

    private static Configuration findConfiguration(Project project, String name) {
        return project.getConfigurations().getByName(name);
    }

    private static FileCollection findMainArtifacts(Project project) {
        return findConfiguration(project, JavaPlugin.RUNTIME_ELEMENTS_CONFIGURATION_NAME)
                .getOutgoing()
                .getArtifacts()
                .getFiles();
    }

    private NativeImageOptions createTestExtension(Project project, NativeImageOptions mainExtension) {
        NativeImageOptions testExtension = NativeImageOptions.register(project, NATIVE_TEST_EXTENSION);
        testExtension.getMainClass().set("org.graalvm.junit.platform.NativeImageJUnitLauncher");
        testExtension.getMainClass().finalizeValue();
        testExtension.getImageName().convention(mainExtension.getImageName().map(name -> name + Utils.NATIVE_TESTS_SUFFIX));
        ListProperty<String> runtimeArgs = testExtension.getRuntimeArgs();
        runtimeArgs.add("--xml-output-dir");
        runtimeArgs.add(project.getLayout().getBuildDirectory().dir("test-results/test-native").map(d -> d.getAsFile().getAbsolutePath()));
        testExtension.buildArgs("--features=org.graalvm.junit.platform.JUnitPlatformFeature");
        ConfigurableFileCollection classpath = testExtension.getClasspath();
        classpath.from(findMainArtifacts(project));
        classpath.from(findConfiguration(project, JavaPlugin.TEST_RUNTIME_CLASSPATH_CONFIGURATION_NAME));
        classpath.from(GradleUtils.findSourceSet(project, SourceSet.TEST_SOURCE_SET_NAME).getOutput().getClassesDirs());
        classpath.from(GradleUtils.findSourceSet(project, SourceSet.TEST_SOURCE_SET_NAME).getOutput().getResourcesDir());
        return testExtension;
    }

    private Provider<NativeImageService> registerNativeImageService(Project project) {
        return project.getGradle()
                .getSharedServices()
                .registerIfAbsent("nativeImage", NativeImageService.class,
                        spec -> spec.getMaxParallelUsages().set(1 + Runtime.getRuntime().availableProcessors() / 16));
    }

    private void setAgentArgs(Project project, String sourceSetName, Task task, Boolean persistConfig) {
        Path buildFolder = project.getBuildDir().toPath();
        Path accessFilter = buildFolder.resolve("tmp").resolve(AGENT_FILTER);

        task.doFirst((__) -> {
            try {
                // Before JVM execution (during `run` or `test` task), we want to copy
                // access-filter file so that native-image-agent run doesn't catch internal
                // gradle classes.
                Files.copy(Objects.requireNonNull(this.getClass().getResourceAsStream("/" + AGENT_FILTER)),
                        accessFilter, StandardCopyOption.REPLACE_EXISTING);
            } catch (IOException | NullPointerException e) {
                throw new GradleException("Error while copying access-filter file.", e);
            }
        });

        Path agentOutput;
        if (persistConfig) { // If user chooses, we can persist native-image-agent generated configuration
            // into the codebase.
            agentOutput = Paths.get(project.getProjectDir().getAbsolutePath(), "src", sourceSetName, "resources",
                    "META-INF", "native-image");
            logger.log("Persist config option was set.");
        } else {
            agentOutput = buildFolder.resolve(AGENT_OUTPUT_FOLDER).resolve(sourceSetName).toAbsolutePath();
        }

        ((JavaForkOptions) task)
                .setJvmArgs(Arrays.asList(
                        "-agentlib:native-image-agent=experimental-class-loader-support," + "config-output-dir="
                                + agentOutput + "," + "access-filter-file=" + accessFilter,
                        "-Dorg.graalvm.nativeimage.imagecode=agent"));
    }

    private void injectTestPluginDependencies(Project project) {
        project.getDependencies().add("implementation", Utils.MAVEN_GROUP_ID + ":junit-platform-native:"
                + VersionInfo.JUNIT_PLATFORM_NATIVE_VERSION);
    }
}
