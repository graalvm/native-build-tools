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

package org.graalvm.buildtools.gradle;

import org.graalvm.buildtools.VersionInfo;
import org.graalvm.buildtools.gradle.dsl.AgentConfiguration;
import org.graalvm.buildtools.gradle.dsl.GraalVMExtension;
import org.graalvm.buildtools.gradle.dsl.NativeImageOptions;
import org.graalvm.buildtools.gradle.internal.AgentCommandLineProvider;
import org.graalvm.buildtools.gradle.internal.BaseNativeImageOptions;
import org.graalvm.buildtools.gradle.internal.DefaultGraalVmExtension;
import org.graalvm.buildtools.gradle.internal.DefaultTestBinaryConfig;
import org.graalvm.buildtools.gradle.internal.DeprecatedNativeImageOptions;
import org.graalvm.buildtools.gradle.internal.GraalVMLogger;
import org.graalvm.buildtools.gradle.internal.GradleUtils;
import org.graalvm.buildtools.gradle.internal.NativeConfigurations;
import org.graalvm.buildtools.gradle.internal.ProcessGeneratedGraalResourceFiles;
import org.graalvm.buildtools.gradle.tasks.BuildNativeImageTask;
import org.graalvm.buildtools.gradle.tasks.GenerateResourcesConfigFile;
import org.graalvm.buildtools.gradle.tasks.NativeRunTask;
import org.graalvm.buildtools.utils.SharedConstants;
import org.gradle.api.Action;
import org.gradle.api.NamedDomainObjectContainer;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.Task;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.artifacts.ConfigurationContainer;
import org.gradle.api.attributes.Attribute;
import org.gradle.api.attributes.AttributeContainer;
import org.gradle.api.file.ArchiveOperations;
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.file.Directory;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.file.DuplicatesStrategy;
import org.gradle.api.file.FileCollection;
import org.gradle.api.file.FileSystemLocation;
import org.gradle.api.file.FileSystemOperations;
import org.gradle.api.model.ObjectFactory;
import org.gradle.api.plugins.ApplicationPlugin;
import org.gradle.api.plugins.JavaApplication;
import org.gradle.api.plugins.JavaPlugin;
import org.gradle.api.plugins.JavaPluginConvention;
import org.gradle.api.provider.ListProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.provider.Provider;
import org.gradle.api.tasks.JavaExec;
import org.gradle.api.tasks.OutputDirectory;
import org.gradle.api.tasks.SourceSet;
import org.gradle.api.tasks.SourceSetContainer;
import org.gradle.api.tasks.TaskContainer;
import org.gradle.api.tasks.TaskProvider;
import org.gradle.api.tasks.bundling.AbstractArchiveTask;
import org.gradle.api.tasks.bundling.Jar;
import org.gradle.api.tasks.testing.Test;
import org.gradle.jvm.toolchain.JavaToolchainService;
import org.gradle.language.base.plugins.LifecycleBasePlugin;
import org.gradle.process.CommandLineArgumentProvider;
import org.gradle.process.ExecOperations;
import org.gradle.process.JavaForkOptions;
import org.gradle.util.GFileUtils;

import javax.inject.Inject;
import java.io.File;
import java.util.Arrays;
import java.util.Collections;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import static org.graalvm.buildtools.gradle.internal.GradleUtils.transitiveProjectArtifacts;
import static org.graalvm.buildtools.gradle.internal.NativeImageExecutableLocator.graalvmHomeProvider;
import static org.graalvm.buildtools.utils.SharedConstants.AGENT_OUTPUT_FOLDER;
import static org.graalvm.buildtools.utils.SharedConstants.AGENT_PROPERTY;

/**
 * Gradle plugin for GraalVM Native Image.
 */
@SuppressWarnings("unused")
public class NativeImagePlugin implements Plugin<Project> {
    public static final String NATIVE_COMPILE_TASK_NAME = "nativeCompile";
    public static final String NATIVE_TEST_COMPILE_TASK_NAME = "nativeTestCompile";
    public static final String NATIVE_TEST_TASK_NAME = "nativeTest";
    public static final String NATIVE_TEST_EXTENSION = "test";
    public static final String NATIVE_MAIN_EXTENSION = "main";
    public static final String PROCESS_AGENT_RESOURCES_TASK_NAME_PREFIX = "filterAgent";
    public static final String PROCESS_AGENT_RESOURCES_TASK_NAME_SUFFIX = "Resources";
    public static final String GENERATE_RESOURCES_CONFIG_FILE_TASK_NAME = "generateResourcesConfigFile";
    public static final String GENERATE_TEST_RESOURCES_CONFIG_FILE_TASK_NAME = "generateTestResourcesConfigFile";

    public static final String DEPRECATED_NATIVE_BUILD_EXTENSION = "nativeBuild";
    public static final String DEPRECATED_NATIVE_TEST_EXTENSION = "nativeTest";
    public static final String DEPRECATED_NATIVE_BUILD_TASK = "nativeBuild";
    public static final String DEPRECATED_NATIVE_TEST_BUILD_TASK = "nativeTestBuild";

    /**
     * This looks strange, but it is used to force the configuration of a dependent
     * task during the configuration of another one. This is a workaround for a bug
     * when applying the Kotlin plugin, where the test task is configured too late
     * for some reason.
     */
    private static final Consumer<Object> FORCE_CONFIG = t -> {
    };
    private static final String JUNIT_PLATFORM_LISTENERS_UID_TRACKING_ENABLED = "junit.platform.listeners.uid.tracking.enabled";
    private static final String JUNIT_PLATFORM_LISTENERS_UID_TRACKING_OUTPUT_DIR = "junit.platform.listeners.uid.tracking.output.dir";

    private GraalVMLogger logger;

    @Inject
    public ArchiveOperations getArchiveOperations() {
        throw new UnsupportedOperationException();
    }

    @Inject
    public ExecOperations getExecOperations() {
        throw new UnsupportedOperationException();
    }

    @Inject
    public FileSystemOperations getFileOperations() {
        throw new UnsupportedOperationException();
    }


    @Override
    public void apply(Project project) {
        Provider<NativeImageService> nativeImageServiceProvider = NativeImageService.registerOn(project);

        logger = GraalVMLogger.of(project.getLogger());
        DefaultGraalVmExtension graalExtension = (DefaultGraalVmExtension) registerGraalVMExtension(project);
        graalExtension.getUseArgFile().convention(true);
        project.getPlugins()
                .withType(JavaPlugin.class, javaPlugin -> configureJavaProject(project, nativeImageServiceProvider, graalExtension));
        project.afterEvaluate(p -> {
            Map<String, Provider<Boolean>> agents = graalExtension.getAgentProperties();
            graalExtension.getBinaries().all(options -> {
                AgentConfiguration agentConfiguration = options.getAgent();
                if (agentConfiguration.getInstrumentedTask().isPresent()) {
                    configureAgent(p, agents, graalExtension.getToolchainDetection().map(b -> !b), options, getExecOperations(), getFileOperations());
                }
            });
        });
    }

    private static String deriveTaskName(String name, String prefix, String suffix) {
        if ("main".equals(name)) {
            return prefix + suffix;
        }
        return prefix + capitalize(name) + suffix;
    }

    private void configureJavaProject(Project project, Provider<NativeImageService> nativeImageServiceProvider, DefaultGraalVmExtension graalExtension) {
        logger.log("====================");
        logger.log("Initializing project: " + project.getName());
        logger.log("====================");

        Map<String, Provider<Boolean>> agents = graalExtension.getAgentProperties();

        // Add DSL extensions for building and testing
        NativeImageOptions mainOptions = createMainOptions(graalExtension, project);
        deprecateExtension(project, mainOptions, DEPRECATED_NATIVE_BUILD_EXTENSION, "main");

        project.getPlugins().withId("application", p -> mainOptions.getMainClass().convention(
                project.getExtensions().findByType(JavaApplication.class).getMainClass()
        ));

        project.getPlugins().withId("java-library", p -> mainOptions.getSharedLibrary().convention(true));

        registerServiceProvider(project, nativeImageServiceProvider);

        // Register Native Image tasks
        TaskContainer tasks = project.getTasks();
        JavaPluginConvention javaConvention = project.getConvention().getPlugin(JavaPluginConvention.class);
        configureAutomaticTaskCreation(project, graalExtension, agents, tasks, javaConvention.getSourceSets());

        TaskProvider<BuildNativeImageTask> imageBuilder = tasks.named(NATIVE_COMPILE_TASK_NAME, BuildNativeImageTask.class);
        TaskProvider<Task> deprecatedTask = tasks.register(DEPRECATED_NATIVE_BUILD_TASK, t -> {
            t.dependsOn(imageBuilder);
            t.doFirst("Warn about deprecation", task -> task.getLogger().warn("Task " + DEPRECATED_NATIVE_BUILD_TASK + " is deprecated. Use " + NATIVE_COMPILE_TASK_NAME + " instead."));
        });

        // We want to add agent invocation to "run" task, but it is only available when
        // Application Plugin is initialized.
        project.getPlugins().withType(ApplicationPlugin.class, applicationPlugin -> {
            TaskProvider<? extends JavaForkOptions> runTask = tasks.withType(JavaExec.class).named(ApplicationPlugin.TASK_RUN_NAME);
            mainOptions.getAgent().getInstrumentedTask().convention(runTask);
        });

        graalExtension.registerTestBinary("test", config -> {
            config.forTestTask(tasks.named("test", Test.class));
            config.usingSourceSet(GradleUtils.findSourceSet(project, SourceSet.TEST_SOURCE_SET_NAME));
        });
    }

    private void configureAutomaticTaskCreation(Project project,
                                                GraalVMExtension graalExtension,
                                                Map<String, Provider<Boolean>> agents,
                                                TaskContainer tasks,
                                                SourceSetContainer sourceSets) {
        graalExtension.getBinaries().configureEach(options -> {
            String binaryName = options.getName();
            String compileTaskName = deriveTaskName(binaryName, "native", "Compile");
            if ("main".equals(binaryName)) {
                compileTaskName = NATIVE_COMPILE_TASK_NAME;
            }
            Provider<Boolean> agent = agentPropertyOverride(project, options);
            agents.put(binaryName, agent);
            TaskProvider<BuildNativeImageTask> imageBuilder = tasks.register(compileTaskName,
                    BuildNativeImageTask.class, builder -> {
                        builder.setDescription("Compiles a native image for the " + options.getName() + " binary");
                        builder.setGroup(LifecycleBasePlugin.BUILD_GROUP);
                        builder.getOptions().convention(options);
                        builder.getAgentEnabled().set(agent);
                        builder.getUseArgFile().convention(graalExtension.getUseArgFile());
                    });
            String runTaskName = deriveTaskName(binaryName, "native", "Run");
            if ("main".equals(binaryName)) {
                runTaskName = NativeRunTask.TASK_NAME;
            } else if (binaryName.toLowerCase(Locale.US).endsWith("test")) {
                runTaskName = "native" + capitalize(binaryName);
            }
            tasks.register(runTaskName, NativeRunTask.class, task -> {
                task.setGroup(LifecycleBasePlugin.BUILD_GROUP);
                task.setDescription("Executes the " + options.getName() + " native binary");
                task.getImage().convention(imageBuilder.map(t -> t.getOutputFile().get()));
                task.getRuntimeArgs().convention(options.getRuntimeArgs());
            });
            configureClasspathJarFor(tasks, options, imageBuilder);
            SourceSet sourceSet = "test".equals(binaryName) ? sourceSets.getByName(SourceSet.TEST_SOURCE_SET_NAME) : sourceSets.getByName(SourceSet.MAIN_SOURCE_SET_NAME);
            TaskProvider<GenerateResourcesConfigFile> generateResourcesConfig = registerResourcesConfigTask(
                    graalExtension.getGeneratedResourcesDirectory(),
                    options,
                    tasks,
                    transitiveProjectArtifacts(project, sourceSet.getRuntimeClasspathConfigurationName()),
                    deriveTaskName(binaryName, "generate", "ResourcesConfigFile"));
            options.getConfigurationFileDirectories().from(generateResourcesConfig.map(t ->
                    t.getOutputFile().map(f -> f.getAsFile().getParentFile())
            ));
        });
    }

    private void deprecateExtension(Project project,
                                    NativeImageOptions delegate,
                                    String name,
                                    String substitute) {
        JavaToolchainService toolchains = project.getExtensions().findByType(JavaToolchainService.class);
        ObjectFactory objects = project.getObjects();
        project.getExtensions().add(name, objects.newInstance(DeprecatedNativeImageOptions.class,
                name,
                delegate,
                substitute,
                project.getLogger()));
    }

    private void configureClasspathJarFor(TaskContainer tasks, NativeImageOptions options, TaskProvider<BuildNativeImageTask> imageBuilder) {
        String baseName = imageBuilder.getName();
        TaskProvider<Jar> classpathJar = tasks.register(baseName + "ClasspathJar", Jar.class, jar -> {
            jar.setDescription("Builds a pathing jar for the " + options.getName() + " native binary");
            jar.from(
                    options.getClasspath()
                            .getElements()
                            .map(elems -> elems.stream()
                                    .map(e -> {
                                        if (isJar(e)) {
                                            return getArchiveOperations().zipTree(e);
                                        }
                                        return e;
                                    })
                                    .collect(Collectors.toList()))
            );
            jar.setDuplicatesStrategy(DuplicatesStrategy.WARN);
            jar.getArchiveBaseName().set(baseName.toLowerCase(Locale.ENGLISH) + "-classpath");
        });
        imageBuilder.configure(nit -> {
            if (options.getUseFatJar().get()) {
                nit.getClasspathJar().set(classpathJar.flatMap(AbstractArchiveTask::getArchiveFile));
            }
        });
    }

    private static boolean isJar(FileSystemLocation location) {
        return location.getAsFile().getName().toLowerCase(Locale.US).endsWith(".jar");
    }

    private GraalVMExtension registerGraalVMExtension(Project project) {
        NamedDomainObjectContainer<NativeImageOptions> nativeImages = project.getObjects()
                .domainObjectContainer(NativeImageOptions.class, name ->
                        project.getObjects().newInstance(BaseNativeImageOptions.class,
                                name,
                                project.getObjects(),
                                project.getProviders(),
                                project.getExtensions().findByType(JavaToolchainService.class),
                                project.getName())
                );
        GraalVMExtension graalvmNative = project.getExtensions().create(GraalVMExtension.class, "graalvmNative",
                DefaultGraalVmExtension.class, nativeImages, this, project);
        graalvmNative.getGeneratedResourcesDirectory().set(project.getLayout()
                .getBuildDirectory()
                .dir("native/generated/"));
        return graalvmNative;
    }

    private TaskProvider<GenerateResourcesConfigFile> registerResourcesConfigTask(Provider<Directory> generatedDir,
                                                                                  NativeImageOptions options,
                                                                                  TaskContainer tasks,
                                                                                  FileCollection transitiveProjectArtifacts,
                                                                                  String name) {
        return tasks.register(name, GenerateResourcesConfigFile.class, task -> {
            task.setDescription("Generates a GraalVM resource-config.json file");
            task.getOptions().convention(options.getResources());
            task.getClasspath().from(options.getClasspath());
            task.getTransitiveProjectArtifacts().from(transitiveProjectArtifacts);
            task.getOutputFile().convention(generatedDir.map(d -> d.file(name + "/resource-config.json")));
        });
    }

    public void registerTestBinary(Project project,
                                   DefaultGraalVmExtension graalExtension,
                                   DefaultTestBinaryConfig config) {
        NativeImageOptions mainOptions = graalExtension.getBinaries().getByName("main");
        String name = config.getName();
        boolean isPrimaryTest = "test".equals(name);
        TaskContainer tasks = project.getTasks();

        // Testing part begins here. -------------------------------------------

        // In future Gradle releases this becomes a proper DirectoryProperty
        File testResultsDir = GradleUtils.getJavaPluginConvention(project).getTestResultsDir();
        DirectoryProperty testListDirectory = project.getObjects().directoryProperty();

        // Add DSL extension for testing
        NativeImageOptions testOptions = createTestOptions(graalExtension, name, project, mainOptions, testListDirectory, config.getSourceSet());
        if (isPrimaryTest) {
            deprecateExtension(project, testOptions, DEPRECATED_NATIVE_TEST_EXTENSION, "test");
        }

        TaskProvider<Test> testTask = config.validate().getTestTask();
        testOptions.getAgent().getInstrumentedTask().set(testTask);
        testTask.configure(test -> {
            File testList = new File(testResultsDir, test.getName() + "/testlist");
            testListDirectory.set(testList);
            test.getOutputs().dir(testList);
            // Set system property read by the UniqueIdTrackingListener.
            test.systemProperty(JUNIT_PLATFORM_LISTENERS_UID_TRACKING_ENABLED, true);
            TrackingDirectorySystemPropertyProvider directoryProvider = project.getObjects().newInstance(TrackingDirectorySystemPropertyProvider.class);
            directoryProvider.getDirectory().set(testListDirectory);
            test.getJvmArgumentProviders().add(directoryProvider);
            test.doFirst("cleanup test ids", new CleanupTestIdsDirectory(testListDirectory));
        });

        // Following ensures that required feature jar is on classpath for every project
        injectTestPluginDependencies(project, graalExtension.getTestSupport());

        TaskProvider<BuildNativeImageTask> testImageBuilder = tasks.named(deriveTaskName(name, "native", "Compile"), BuildNativeImageTask.class, task -> {
            task.setOnlyIf(t -> graalExtension.getTestSupport().get());
            task.getTestListDirectory().set(testListDirectory);
            testTask.get();
            ConfigurableFileCollection testList = project.getObjects().fileCollection();
            // Later this will be replaced by a dedicated task not requiring execution of tests
            testList.from(testListDirectory).builtBy(testTask);
            testOptions.getClasspath().from(testList);
        });
        if (isPrimaryTest) {
            tasks.register(DEPRECATED_NATIVE_TEST_BUILD_TASK, t -> {
                t.dependsOn(testImageBuilder);
                t.doFirst("Warn about deprecation", task -> task.getLogger().warn("Task " + DEPRECATED_NATIVE_TEST_BUILD_TASK + " is deprecated. Use " + NATIVE_TEST_COMPILE_TASK_NAME + " instead."));
            });
        }

        tasks.named(isPrimaryTest ? NATIVE_TEST_TASK_NAME : "native" + capitalize(name), NativeRunTask.class, task -> {
            task.setGroup(LifecycleBasePlugin.VERIFICATION_GROUP);
            task.setOnlyIf(t -> graalExtension.getTestSupport().get());
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
                .orElse(extension.getAgent().getEnabled());
    }

    private static TaskProvider<ProcessGeneratedGraalResourceFiles> registerProcessAgentFilesTask(Project project, String name) {
        return project.getTasks().register(name, ProcessGeneratedGraalResourceFiles.class, task -> {
            task.getFilterableEntries().convention(Arrays.asList("org.gradle.", "java."));
            task.getOutputDirectory().convention(project.getLayout().getBuildDirectory().dir("native/processed/agent/" + name));
        });
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

    private static String capitalize(String name) {
        if (name.length() > 0) {
            return name.substring(0, 1).toUpperCase(Locale.US) + name.substring(1);
        }
        return name;
    }

    @SuppressWarnings("unchecked")
    private static NativeConfigurations createNativeConfigurations(Project project, String binaryName, String baseClasspathConfigurationName) {
        ConfigurationContainer configurations = project.getConfigurations();
        String prefix = "main".equals(binaryName) ? "" : capitalize(binaryName);
        Configuration baseClasspath = configurations.getByName(baseClasspathConfigurationName);
        Configuration compileOnly = configurations.create("nativeImage" + prefix + "CompileOnly", c -> {
            c.setCanBeResolved(false);
            c.setCanBeConsumed(false);
        });
        Configuration classpath = configurations.create("nativeImage" + prefix + "Classpath", c -> {
            c.setCanBeConsumed(false);
            c.setCanBeResolved(true);
            c.extendsFrom(compileOnly);
            baseClasspath.getExtendsFrom().forEach(c::extendsFrom);
            c.attributes(attrs -> {
                AttributeContainer baseAttributes = baseClasspath.getAttributes();
                for (Attribute<?> attribute : baseAttributes.keySet()) {
                    Attribute<Object> attr = (Attribute<Object>) attribute;
                    Object value = baseAttributes.getAttribute(attr);
                    attrs.attribute(attr, value);
                }
            });
        });
        compileOnly.getDependencies().add(project.getDependencies().create(project));
        return new NativeConfigurations(compileOnly, classpath);
    }

    private static NativeImageOptions createMainOptions(GraalVMExtension graalExtension, Project project) {
        NativeImageOptions buildExtension = graalExtension.getBinaries().create(NATIVE_MAIN_EXTENSION);
        NativeConfigurations configs = createNativeConfigurations(
                project,
                "main",
                JavaPlugin.RUNTIME_CLASSPATH_CONFIGURATION_NAME
        );
        buildExtension.getClasspath().from(configs.getImageClasspathConfiguration());
        return buildExtension;
    }

    private static NativeImageOptions createTestOptions(GraalVMExtension graalExtension,
                                                        String binaryName,
                                                        Project project,
                                                        NativeImageOptions mainExtension,
                                                        DirectoryProperty testListDirectory,
                                                        SourceSet sourceSet) {
        NativeImageOptions testExtension = graalExtension.getBinaries().create(binaryName);
        NativeConfigurations configs = createNativeConfigurations(
                project,
                binaryName,
                JavaPlugin.TEST_RUNTIME_CLASSPATH_CONFIGURATION_NAME
        );
        testExtension.getMainClass().set("org.graalvm.junit.platform.NativeImageJUnitLauncher");
        testExtension.getMainClass().finalizeValue();
        testExtension.getImageName().convention(mainExtension.getImageName().map(name -> name + SharedConstants.NATIVE_TESTS_SUFFIX));
        ListProperty<String> runtimeArgs = testExtension.getRuntimeArgs();
        runtimeArgs.add("--xml-output-dir");
        runtimeArgs.add(project.getLayout().getBuildDirectory().dir("test-results/" + binaryName + "-native").map(d -> d.getAsFile().getAbsolutePath()));
        testExtension.buildArgs("--features=org.graalvm.junit.platform.JUnitPlatformFeature");
        ConfigurableFileCollection classpath = testExtension.getClasspath();
        classpath.from(configs.getImageClasspathConfiguration());
        classpath.from(sourceSet.getOutput().getClassesDirs());
        classpath.from(sourceSet.getOutput().getResourcesDir());
        return testExtension;
    }

    private static void configureAgent(Project project,
                                       Map<String, Provider<Boolean>> agents,
                                       Provider<Boolean> disableToolchainDetection,
                                       NativeImageOptions nativeImageOptions,
                                       ExecOperations execOperations,
                                       FileSystemOperations fileOperations) {
        String postProcessTaskName = PROCESS_AGENT_RESOURCES_TASK_NAME_PREFIX + capitalize(nativeImageOptions.getName()) + PROCESS_AGENT_RESOURCES_TASK_NAME_SUFFIX;
        TaskProvider<ProcessGeneratedGraalResourceFiles> postProcessingTask = registerProcessAgentFilesTask(project, postProcessTaskName);
        TaskProvider<? extends JavaForkOptions> instrumentedTask = nativeImageOptions.getAgent().getInstrumentedTask().get();
        AgentCommandLineProvider cliProvider = project.getObjects().newInstance(AgentCommandLineProvider.class);
        Provider<Boolean> agent = agents.get(nativeImageOptions.getName());
        cliProvider.getEnabled().set(agent);
        Provider<Directory> outputDir = project.getLayout().getBuildDirectory().dir(AGENT_OUTPUT_FOLDER + "/" + instrumentedTask.getName());
        cliProvider.getOutputDirectory().set(outputDir);
        cliProvider.getAgentOptions().set(nativeImageOptions.getAgent().getOptions());
        instrumentedTask.get().getJvmArgumentProviders().add(cliProvider);
        instrumentedTask.configure(task -> task.doLast(new MergeAgentFiles(
                agent,
                graalvmHomeProvider(project.getProviders()),
                outputDir,
                disableToolchainDetection,
                nativeImageOptions,
                execOperations,
                fileOperations,
                project.getLogger())));
        // Gradle won't let us configure from configure so we have to eagerly create the post-processing task :(
        postProcessingTask.get().getGeneratedFilesDir().set(
                instrumentedTask.map(t -> outputDir.get())
        );
        // We can't set from(postProcessingTask) directly, otherwise a task
        // dependency would be introduced even if the agent is not enabled.
        // We should be able to write this:
        // nativeImageOptions.getConfigurationFileDirectories().from(
        //     agent.map(enabled -> enabled ? postProcessingTask : project.files())
        // )
        // but Gradle won't track the postProcessingTask dependency so we have to write this:
        ConfigurableFileCollection files = project.getObjects().fileCollection();
        files.from(agent.map(enabled -> enabled ? postProcessingTask : project.files()));
        files.builtBy((Callable<Task>) () -> agent.get() ? postProcessingTask.get() : null);
        nativeImageOptions.getConfigurationFileDirectories().from(files);
    }

    private static void injectTestPluginDependencies(Project project, Property<Boolean> testSupportEnabled) {
        project.afterEvaluate(p -> {
            if (testSupportEnabled.get()) {
                project.getDependencies().add("testImplementation", "org.graalvm.buildtools:junit-platform-native:"
                        + VersionInfo.JUNIT_PLATFORM_NATIVE_VERSION);
            }
        });
    }

    private static final class CleanupTestIdsDirectory implements Action<Task> {
        private final DirectoryProperty directory;

        private CleanupTestIdsDirectory(DirectoryProperty directory) {
            this.directory = directory;
        }

        @Override
        public void execute(Task task) {
            File dir = directory.getAsFile().get();
            if (dir.exists()) {
                GFileUtils.deleteDirectory(dir);
            }
        }
    }

    public abstract static class TrackingDirectorySystemPropertyProvider implements CommandLineArgumentProvider {
        @OutputDirectory
        public abstract DirectoryProperty getDirectory();

        /**
         * The arguments which will be provided to the process.
         */
        @Override
        public Iterable<String> asArguments() {
            return Collections.singleton("-D" + JUNIT_PLATFORM_LISTENERS_UID_TRACKING_OUTPUT_DIR + "=" + getDirectory().getAsFile().get().getAbsolutePath());
        }
    }
}
