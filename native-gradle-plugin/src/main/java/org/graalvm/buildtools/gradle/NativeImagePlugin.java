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

import org.graalvm.buildtools.Utils;
import org.graalvm.buildtools.gradle.dsl.JUnitPlatformOptions;
import org.graalvm.buildtools.gradle.dsl.NativeImageOptions;
import org.graalvm.buildtools.gradle.tasks.NativeBuildTask;
import org.graalvm.buildtools.gradle.tasks.NativeRunTask;
import org.graalvm.buildtools.gradle.tasks.TestNativeBuildTask;
import org.graalvm.buildtools.gradle.tasks.TestNativeRunTask;
import org.gradle.api.Action;
import org.gradle.api.GradleException;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.Task;
import org.gradle.api.plugins.ApplicationPlugin;
import org.gradle.api.plugins.JavaPlugin;
import org.gradle.api.provider.Provider;
import org.gradle.api.tasks.SourceSet;
import org.gradle.process.JavaForkOptions;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.Arrays;
import java.util.Objects;

import static org.graalvm.buildtools.gradle.GradleUtils.initLogger;
import static org.graalvm.buildtools.gradle.GradleUtils.log;
import static org.graalvm.buildtools.Utils.AGENT_FILTER;
import static org.graalvm.buildtools.Utils.AGENT_OUTPUT_FOLDER;

/**
 * Gradle plugin for GraalVM Native Image.
 */
@SuppressWarnings("unused")
public class NativeImagePlugin implements Plugin<Project> {

    @SuppressWarnings("UnstableApiUsage")
    public void apply(Project project) {
        Provider<NativeImageService> nativeImageServiceProvider = project.getGradle().getSharedServices()
                .registerIfAbsent("nativeImage", NativeImageService.class,
                        spec -> spec.getMaxParallelUsages().set(1 + Runtime.getRuntime().availableProcessors() / 16));

        initLogger(project);

        project.getPlugins().withType(JavaPlugin.class, javaPlugin -> {
            log("====================");
            log("Initializing project: " + project.getName());
            log("====================");

            // Add DSL extensions for building and testing
            NativeImageOptions buildExtension = NativeImageOptions.register(project);

            JUnitPlatformOptions testExtension = JUnitPlatformOptions.register(project);

            // Register Native Image tasks
            project.getTasks().register(NativeBuildTask.TASK_NAME, NativeBuildTask.class, task -> {
                task.usesService(nativeImageServiceProvider);
                task.getServer().set(nativeImageServiceProvider);
            });

            Task oldTask = project.getTasks().create("nativeImage");
            oldTask.dependsOn(NativeBuildTask.TASK_NAME);
            oldTask.doLast(new Action<Task>() {
                @SuppressWarnings("NullableProblems")
                @Override
                public void execute(Task task) {
                    GradleUtils.error("[WARNING] Task 'nativeImage' is deprecated. "
                            + String.format("Use '%s' instead.", NativeBuildTask.TASK_NAME));
                }
            });

            project.getTasks().register(NativeRunTask.TASK_NAME, NativeRunTask.class);

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

            // Testing part begins here.
            Task test = project.getTasksByName(JavaPlugin.TEST_TASK_NAME, false).stream().findFirst().orElse(null);
            if (test != null) {
                // Following ensures that required feature jar is on classpath for every project
                injectTestPluginDependencies(project);

                // If `test` task was found we should add `nativeTestBuild` and `nativeTest`
                // tasks to this project as well.
                project.getTasks().register(TestNativeBuildTask.TASK_NAME, TestNativeBuildTask.class, task -> {
                    task.usesService(nativeImageServiceProvider);
                    task.getServer().set(nativeImageServiceProvider);
                });
                Task nativeTest = project.getTasksByName(TestNativeBuildTask.TASK_NAME, false).stream().findFirst().orElse(null);
                nativeTest.dependsOn(test);

                project.getTasks().register(TestNativeRunTask.TASK_NAME, TestNativeRunTask.class);

                if (project.hasProperty(Utils.AGENT_PROPERTY) || testExtension.getAgent().get()) {
                    // Add agent invocation to test task.
                    Boolean persistConfig = System.getProperty(Utils.PERSIST_CONFIG_PROPERTY) != null
                            || testExtension.getPersistConfig().get();
                    setAgentArgs(project, SourceSet.TEST_SOURCE_SET_NAME, test, persistConfig);
                }
            }
        });
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
            log("Persist config option was set.");
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
        project.getDependencies().add("implementation", "org.graalvm.nativeimage:junit-platform-native:+");
    }
}
