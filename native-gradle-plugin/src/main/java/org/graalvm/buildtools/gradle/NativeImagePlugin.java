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

package org.graalvm.buildtools.gradle;

import org.graalvm.buildtools.VersionInfo;
import org.graalvm.buildtools.agent.AgentConfiguration;
import org.graalvm.buildtools.agent.AgentMode;
import org.graalvm.buildtools.gradle.dsl.GraalVMExtension;
import org.graalvm.buildtools.gradle.dsl.GraalVMReachabilityMetadataRepositoryExtension;
import org.graalvm.buildtools.gradle.dsl.NativeImageOptions;
import org.graalvm.buildtools.gradle.dsl.agent.AgentOptions;
import org.graalvm.buildtools.gradle.internal.AgentCommandLineProvider;
import org.graalvm.buildtools.gradle.internal.BaseNativeImageOptions;
import org.graalvm.buildtools.gradle.internal.DefaultGraalVmExtension;
import org.graalvm.buildtools.gradle.internal.DefaultTestBinaryConfig;
import org.graalvm.buildtools.gradle.internal.GraalVMLogger;
import org.graalvm.buildtools.gradle.internal.GraalVMReachabilityMetadataService;
import org.graalvm.buildtools.gradle.internal.GradleUtils;
import org.graalvm.buildtools.gradle.internal.NativeConfigurations;
import org.graalvm.buildtools.gradle.internal.agent.AgentConfigurationFactory;
import org.graalvm.buildtools.gradle.tasks.BuildNativeImageTask;
import org.graalvm.buildtools.gradle.tasks.CollectReachabilityMetadata;
import org.graalvm.buildtools.gradle.tasks.GenerateResourcesConfigFile;
import org.graalvm.buildtools.gradle.tasks.MetadataCopyTask;
import org.graalvm.buildtools.gradle.tasks.NativeRunTask;
import org.graalvm.buildtools.gradle.tasks.actions.CleanupAgentFilesAction;
import org.graalvm.buildtools.gradle.tasks.actions.MergeAgentFilesAction;
import org.graalvm.buildtools.gradle.tasks.actions.ProcessGeneratedGraalResourceFilesAction;
import org.graalvm.buildtools.utils.SharedConstants;
import org.graalvm.reachability.DirectoryConfiguration;
import org.gradle.api.Action;
import org.gradle.api.NamedDomainObjectContainer;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.Task;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.artifacts.ConfigurationContainer;
import org.gradle.api.artifacts.Dependency;
import org.gradle.api.artifacts.ModuleVersionIdentifier;
import org.gradle.api.artifacts.component.ModuleComponentIdentifier;
import org.gradle.api.artifacts.result.DependencyResult;
import org.gradle.api.artifacts.result.ResolvedComponentResult;
import org.gradle.api.artifacts.result.ResolvedDependencyResult;
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
import org.gradle.api.logging.LogLevel;
import org.gradle.api.plugins.ExtensionAware;
import org.gradle.api.plugins.JavaApplication;
import org.gradle.api.plugins.JavaLibraryPlugin;
import org.gradle.api.plugins.JavaPlugin;
import org.gradle.api.plugins.JavaPluginConvention;
import org.gradle.api.provider.ListProperty;
import org.gradle.api.provider.MapProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.provider.Provider;
import org.gradle.api.provider.ProviderFactory;
import org.gradle.api.provider.SetProperty;
import org.gradle.api.tasks.OutputDirectory;
import org.gradle.api.tasks.SourceSet;
import org.gradle.api.tasks.SourceSetContainer;
import org.gradle.api.tasks.TaskCollection;
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

import javax.annotation.Nonnull;
import javax.inject.Inject;
import java.io.File;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static org.graalvm.buildtools.gradle.internal.ConfigurationCacheSupport.serializableBiFunctionOf;
import static org.graalvm.buildtools.gradle.internal.ConfigurationCacheSupport.serializableFunctionOf;
import static org.graalvm.buildtools.gradle.internal.ConfigurationCacheSupport.serializablePredicateOf;
import static org.graalvm.buildtools.gradle.internal.ConfigurationCacheSupport.serializableSupplierOf;
import static org.graalvm.buildtools.gradle.internal.ConfigurationCacheSupport.serializableTransformerOf;
import static org.graalvm.buildtools.gradle.internal.GradleUtils.transitiveProjectArtifacts;
import static org.graalvm.buildtools.gradle.internal.NativeImageExecutableLocator.graalvmHomeProvider;
import static org.graalvm.buildtools.utils.SharedConstants.AGENT_PROPERTY;
import static org.graalvm.buildtools.utils.SharedConstants.IS_WINDOWS;
import static org.graalvm.buildtools.utils.SharedConstants.METADATA_REPO_URL_TEMPLATE;

/**
 * Gradle plugin for GraalVM Native Image.
 */
public class NativeImagePlugin implements Plugin<Project> {
    public static final String NATIVE_COMPILE_TASK_NAME = "nativeCompile";
    public static final String NATIVE_TEST_COMPILE_TASK_NAME = "nativeTestCompile";
    public static final String NATIVE_TEST_TASK_NAME = "nativeTest";
    public static final String NATIVE_MAIN_EXTENSION = "main";
    public static final String NATIVE_TEST_EXTENSION = "test";

    public static final String DEPRECATED_NATIVE_BUILD_EXTENSION = "nativeBuild";
    public static final String DEPRECATED_NATIVE_TEST_EXTENSION = "nativeTest";
    public static final String DEPRECATED_NATIVE_BUILD_TASK = "nativeBuild";
    public static final String DEPRECATED_NATIVE_TEST_BUILD_TASK = "nativeTestBuild";

    public static final String CONFIG_REPO_LOGLEVEL = "org.graalvm.internal.gradle.configrepo.logging";

    private static final String JUNIT_PLATFORM_LISTENERS_UID_TRACKING_ENABLED = "junit.platform.listeners.uid.tracking.enabled";
    private static final String JUNIT_PLATFORM_LISTENERS_UID_TRACKING_OUTPUT_DIR = "junit.platform.listeners.uid.tracking.output.dir";
    private static final String REPOSITORY_COORDINATES = "org.graalvm.buildtools:graalvm-reachability-metadata:" + VersionInfo.NBT_VERSION + ":repository@zip";
    private static final String DEFAULT_URI = String.format(METADATA_REPO_URL_TEMPLATE, VersionInfo.METADATA_REPO_VERSION);

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
    public void apply(@Nonnull Project project) {
        Provider<NativeImageService> nativeImageServiceProvider = NativeImageService.registerOn(project);

        logger = GraalVMLogger.of(project.getLogger());
        DefaultGraalVmExtension graalExtension = (DefaultGraalVmExtension) registerGraalVMExtension(project);
        graalExtension.getUseArgFile().convention(IS_WINDOWS);
        project.getPlugins()
                .withType(JavaPlugin.class, javaPlugin -> configureJavaProject(project, nativeImageServiceProvider, graalExtension));

        project.getPlugins().withType(JavaLibraryPlugin.class, javaLibraryPlugin -> graalExtension.getAgent().getDefaultMode().convention("conditional"));

        project.afterEvaluate(p -> {
            instrumentTasksWithAgent(project, graalExtension);
            for (NativeImageOptions options : graalExtension.getBinaries()) {
                if (options.getAgent().getOptions().get().size() > 0 || options.getAgent().getEnabled().isPresent()) {
                    logger.warn("The agent block in binary configuration '" + options.getName() + "' is deprecated.");
                    logger.warn("Such agent configuration has no effect and will become an error in the future.");
                }
            }
        });
    }

    private void instrumentTasksWithAgent(Project project, DefaultGraalVmExtension graalExtension) {
        Provider<String> agentMode = agentProperty(project, graalExtension.getAgent());
        Predicate<? super Task> taskPredicate = graalExtension.getAgent().getTasksToInstrumentPredicate().getOrElse(serializablePredicateOf(t -> true));
        project.getTasks().configureEach(t -> {
            if (isTaskInstrumentableByAgent(t) && taskPredicate.test(t)) {
                configureAgent(project, agentMode, graalExtension, getExecOperations(), getFileOperations(), t, (JavaForkOptions) t);
            } else {
                String reason;
                if (isTaskInstrumentableByAgent(t)) {
                    reason = "Not instrumentable by agent as it does not extend JavaForkOptions.";
                } else {
                    reason = "Task does not match user predicate";
                }
                logger.log("Not instrumenting task with native-image-agent: " + t.getName() + ". Reason: " + reason);
            }
        });
    }

    private static boolean isTaskInstrumentableByAgent(Task task) {
        return task instanceof JavaForkOptions;
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

        // Add DSL extensions for building and testing
        NativeImageOptions mainOptions = createMainOptions(graalExtension, project);

        project.getPlugins().withId("application", p -> mainOptions.getMainClass().convention(
                Objects.requireNonNull(project.getExtensions().findByType(JavaApplication.class)).getMainClass()
        ));

        project.getPlugins().withId("java-library", p -> mainOptions.getSharedLibrary().convention(true));

        registerServiceProvider(project, nativeImageServiceProvider);

        // Register Native Image tasks
        TaskContainer tasks = project.getTasks();
        JavaPluginConvention javaConvention = project.getConvention().getPlugin(JavaPluginConvention.class);
        configureAutomaticTaskCreation(project, graalExtension, tasks, javaConvention.getSourceSets());

        TaskProvider<BuildNativeImageTask> imageBuilder = tasks.named(NATIVE_COMPILE_TASK_NAME, BuildNativeImageTask.class);
        tasks.register(DEPRECATED_NATIVE_BUILD_TASK, t -> {
            t.dependsOn(imageBuilder);
            t.doFirst("Warn about deprecation", task -> task.getLogger().warn("Task " + DEPRECATED_NATIVE_BUILD_TASK + " is deprecated. Use " + NATIVE_COMPILE_TASK_NAME + " instead."));
        });

        graalExtension.registerTestBinary(NATIVE_TEST_EXTENSION, config -> {
            config.forTestTask(tasks.named("test", Test.class));
            config.usingSourceSet(GradleUtils.findSourceSet(project, SourceSet.TEST_SOURCE_SET_NAME));
        });

        project.getTasks().register("metadataCopy", MetadataCopyTask.class, task -> {
            task.setGroup(LifecycleBasePlugin.BUILD_GROUP);
            task.setDescription("Copies metadata collected from tasks instrumented with the agent into target directories");
            task.getInputTaskNames().set(graalExtension.getAgent().getMetadataCopy().getInputTaskNames());
            task.getOutputDirectories().set(graalExtension.getAgent().getMetadataCopy().getOutputDirectories());
            task.getMergeWithExisting().set(graalExtension.getAgent().getMetadataCopy().getMergeWithExisting());
            task.getToolchainDetection().set(graalExtension.getToolchainDetection());
        });

        project.getTasks().register("collectReachabilityMetadata", CollectReachabilityMetadata.class, task -> {
            task.setGroup(LifecycleBasePlugin.BUILD_GROUP);
            task.setDescription("Obtains native reachability metadata for the runtime classpath configuration");
            task.setClasspath(project.getConfigurations().getByName(JavaPlugin.RUNTIME_CLASSPATH_CONFIGURATION_NAME));
        });

        GraalVMReachabilityMetadataRepositoryExtension metadataRepositoryExtension = reachabilityExtensionOn(graalExtension);
        TaskCollection<CollectReachabilityMetadata> reachabilityMetadataCopyTasks = project.getTasks()
                .withType(CollectReachabilityMetadata.class);
        reachabilityMetadataCopyTasks.configureEach(task -> {
            Provider<GraalVMReachabilityMetadataService> reachabilityMetadataService = graalVMReachabilityMetadataService(
                    project, metadataRepositoryExtension);
            task.getMetadataService().set(reachabilityMetadataService);
            task.usesService(reachabilityMetadataService);
            task.getUri().convention(task.getVersion().map(serializableTransformerOf(this::getReachabilityMetadataRepositoryUrlForVersion))
                    .orElse(metadataRepositoryExtension.getUri()));
            task.getExcludedModules().convention(metadataRepositoryExtension.getExcludedModules());
            task.getModuleToConfigVersion().convention(metadataRepositoryExtension.getModuleToConfigVersion());
            task.getInto().convention(project.getLayout().getBuildDirectory().dir("native-reachability-metadata"));
        });
    }

    private void configureAutomaticTaskCreation(Project project,
                                                GraalVMExtension graalExtension,
                                                TaskContainer tasks,
                                                SourceSetContainer sourceSets) {
        graalExtension.getBinaries().configureEach(options -> {
            String binaryName = options.getName();
            String compileTaskName = deriveTaskName(binaryName, "native", "Compile");
            if ("main".equals(binaryName)) {
                compileTaskName = NATIVE_COMPILE_TASK_NAME;
            }
            TaskProvider<BuildNativeImageTask> imageBuilder = tasks.register(compileTaskName,
                    BuildNativeImageTask.class, builder -> {
                        builder.setDescription("Compiles a native image for the " + options.getName() + " binary");
                        builder.setGroup(LifecycleBasePlugin.BUILD_GROUP);
                        builder.getOptions().convention(options);
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
                task.getImage().convention(imageBuilder.flatMap(BuildNativeImageTask::getOutputFile));
                task.getRuntimeArgs().convention(options.getRuntimeArgs());
                task.getInternalRuntimeArgs().convention(
                        imageBuilder.zip(options.getPgoInstrument(), serializableBiFunctionOf((builder, pgo) -> {
                                    if (Boolean.TRUE.equals(pgo)) {
                                        File outputDir = builder.getOutputDirectory().get().getAsFile();
                                        return Collections.singletonList("-XX:ProfilesDumpFile=" + new File(outputDir, "default.iprof").getAbsolutePath());
                                    }
                                    return Collections.emptyList();
                                })
                        ));
            });
            configureClasspathJarFor(tasks, options, imageBuilder);
            SourceSet sourceSet = "test".equals(binaryName) ? sourceSets.getByName(SourceSet.TEST_SOURCE_SET_NAME) : sourceSets.getByName(SourceSet.MAIN_SOURCE_SET_NAME);
            TaskProvider<GenerateResourcesConfigFile> generateResourcesConfig = registerResourcesConfigTask(
                    graalExtension.getGeneratedResourcesDirectory(),
                    options,
                    tasks,
                    transitiveProjectArtifacts(project, sourceSet.getRuntimeClasspathConfigurationName()),
                    deriveTaskName(binaryName, "generate", "ResourcesConfigFile"));
            options.getConfigurationFileDirectories().from(generateResourcesConfig.map(serializableTransformerOf(t ->
                    t.getOutputFile().map(serializableTransformerOf(f -> f.getAsFile().getParentFile()))
            )));
            configureJvmReachabilityConfigurationDirectories(project, graalExtension, options, sourceSet);
            configureJvmReachabilityExcludeConfigArgs(project, graalExtension, options, sourceSet);
        });
    }

    private void configureJvmReachabilityConfigurationDirectories(Project project,
                                                                  GraalVMExtension graalExtension,
                                                                  NativeImageOptions options,
                                                                  SourceSet sourceSet) {
        options.getConfigurationFileDirectories().from(
                graalVMReachabilityQueryForConfigDirectories(project,
                        graalExtension,
                        sourceSet,
                        configuration -> true)
        );
    }

    private static File getConfigurationDirectory(DirectoryConfiguration configuration) {
        return configuration.getDirectory().toAbsolutePath().toFile();
    }

    private Provider<List<File>> graalVMReachabilityQueryForConfigDirectories(Project project, GraalVMExtension graalExtension,
                                                 SourceSet sourceSet, Predicate<DirectoryConfiguration> filter) {
        GraalVMReachabilityMetadataRepositoryExtension extension = reachabilityExtensionOn(graalExtension);
        Provider<GraalVMReachabilityMetadataService> metadataServiceProvider = graalVMReachabilityMetadataService(project, extension);
        Provider<ResolvedComponentResult> rootComponent = project.getConfigurations()
                .getByName(sourceSet.getRuntimeClasspathConfigurationName())
                .getIncoming()
                .getResolutionResult()
                .getRootComponent();
        SetProperty<String> excludedModulesProperty = extension.getExcludedModules();
        MapProperty<String, String> moduleToConfigVersion = extension.getModuleToConfigVersion();
        Property<URI> uri = extension.getUri();
        ProviderFactory providers = project.getProviders();
        return extension.getEnabled().flatMap(serializableTransformerOf(enabled -> {
                if (Boolean.TRUE.equals(enabled) && uri.isPresent()) {
                    Set<String> excludedModules = excludedModulesProperty.getOrElse(Collections.emptySet());
                    Map<String, String> forcedVersions = moduleToConfigVersion.getOrElse(Collections.emptyMap());
                    return metadataServiceProvider.map(serializableTransformerOf(service -> {
                        Set<ResolvedComponentResult> components = findAllComponentsFrom(rootComponent.get());
                        return components.stream().flatMap(serializableFunctionOf(component -> {
                            ModuleVersionIdentifier moduleVersion = component.getModuleVersion();
                            Set<DirectoryConfiguration> configurations = service.findConfigurationsFor(excludedModules, forcedVersions, moduleVersion);
                            return configurations.stream()
                                    .filter(filter)
                                    .map(NativeImagePlugin::getConfigurationDirectory);
                        })).collect(Collectors.<File>toList());
                    }));
                }
                return providers.provider(Collections::emptyList);
            }));
    }

    private Provider<Map<String, List<String>>> graalVMReachabilityQueryForExcludeList(Project project, GraalVMExtension graalExtension,
                                                 SourceSet sourceSet, Predicate<DirectoryConfiguration> filter) {
        GraalVMReachabilityMetadataRepositoryExtension extension = reachabilityExtensionOn(graalExtension);
        Provider<GraalVMReachabilityMetadataService> metadataServiceProvider = graalVMReachabilityMetadataService(project, extension);
        Provider<ResolvedComponentResult> rootComponent = project.getConfigurations()
                .getByName(sourceSet.getRuntimeClasspathConfigurationName())
                .getIncoming()
                .getResolutionResult()
                .getRootComponent();
        SetProperty<String> excludedModulesProperty = extension.getExcludedModules();
        MapProperty<String, String> moduleToConfigVersion = extension.getModuleToConfigVersion();
        Property<URI> uri = extension.getUri();
        ProviderFactory providers = project.getProviders();
        return extension.getEnabled().flatMap(serializableTransformerOf(enabled -> {
                if (Boolean.TRUE.equals(enabled) && uri.isPresent()) {
                    Set<String> excludedModules = excludedModulesProperty.getOrElse(Collections.emptySet());
                    Map<String, String> forcedVersions = moduleToConfigVersion.getOrElse(Collections.emptyMap());
                    return metadataServiceProvider.map(serializableTransformerOf(service -> {
                        Set<ResolvedComponentResult> components = findAllComponentsFrom(rootComponent.get());
                        return components.stream().flatMap(serializableFunctionOf(component -> {
                            ModuleVersionIdentifier moduleVersion = component.getModuleVersion();
                            Set<DirectoryConfiguration> configurations = service.findConfigurationsFor(excludedModules, forcedVersions, moduleVersion);
                            return configurations.stream()
                                    .filter(filter)
                                    .map(e -> getExclusionConfig(moduleVersion));
                        })).collect(Collectors.toMap(ExcludeEntry::getGav, ExcludeEntry::getExcludes));
                    }));
                }
                return providers.provider(Collections::emptyMap);
            }));
    }

    private static Set<ResolvedComponentResult> findAllComponentsFrom(ResolvedComponentResult resolvedComponentResult) {
        Set<ResolvedComponentResult> all = new LinkedHashSet<>();
        findAllComponentsFrom(resolvedComponentResult, all);
        return Collections.unmodifiableSet(all);
    }

    private static void findAllComponentsFrom(ResolvedComponentResult resolvedComponentResult, Set<ResolvedComponentResult> all) {
        if (all.add(resolvedComponentResult)) {
            Set<? extends DependencyResult> dependencies = resolvedComponentResult.getDependencies();
            for (DependencyResult dependencyResult : dependencies) {
                if (dependencyResult instanceof ResolvedDependencyResult) {
                    findAllComponentsFrom(((ResolvedDependencyResult) dependencyResult).getSelected(), all);
                }
            }
        }
    }

    private Provider<GraalVMReachabilityMetadataService> graalVMReachabilityMetadataService(Project project,
                                                                                            GraalVMReachabilityMetadataRepositoryExtension repositoryExtension) {
        return project.getGradle()
                .getSharedServices()
                .registerIfAbsent("nativeConfigurationService", GraalVMReachabilityMetadataService.class, spec -> {
                    LogLevel logLevel = determineLogLevel();
                    spec.getMaxParallelUsages().set(1);
                    spec.getParameters().getLogLevel().set(logLevel);
                    spec.getParameters().getUri().set(repositoryExtension.getUri().map(serializableTransformerOf(configuredUri -> computeMetadataRepositoryUri(project, repositoryExtension, m -> logFallbackToDefaultUri(m, logger)))));
                    spec.getParameters().getCacheDir().set(
                            new File(project.getGradle().getGradleUserHomeDir(), "native-build-tools/repositories"));
                });
    }

    private static void logFallbackToDefaultUri(URI defaultUri, GraalVMLogger logger) {
        logger.warn("Unable to find the GraalVM reachability metadata repository in Maven repository. " +
                    "Falling back to the default repository at " + defaultUri);
    }

    static URI computeMetadataRepositoryUri(Project project,
                                            GraalVMReachabilityMetadataRepositoryExtension repositoryExtension,
                                            Consumer<? super URI> onFallback) {
        URI configuredUri = repositoryExtension.getUri().getOrNull();
        URI githubReleaseUri;
        URI defaultUri;
        try {
            defaultUri = new URI(DEFAULT_URI);
            githubReleaseUri = new URI(String.format(METADATA_REPO_URL_TEMPLATE, repositoryExtension.getVersion().get()));
        } catch (URISyntaxException e) {
            throw new RuntimeException("Unable to convert repository path to URI", e);
        }
        if (defaultUri.equals(configuredUri)) {
            // The user didn't configure any custom URI or repo version, so we're going to
            // use the Maven artifact instead
            Configuration configuration = project.getConfigurations().detachedConfiguration();
            Dependency e = project.getDependencies().create(REPOSITORY_COORDINATES);
            configuration.getDependencies().add(e);
            Set<File> files = configuration.getIncoming()
                    .artifactView(view -> view.setLenient(true))
                    .getFiles()
                    .getFiles();
            if (files.size() == 1) {
                return files.iterator().next().toURI();
            } else {
                onFallback.accept(githubReleaseUri);
            }
        }
        return configuredUri;
    }

    private void configureJvmReachabilityExcludeConfigArgs(Project project, GraalVMExtension graalExtension, NativeImageOptions options, SourceSet sourceSet) {
        options.getExcludeConfig().putAll(
                graalVMReachabilityQueryForExcludeList(project,
                        graalExtension,
                        sourceSet,
                        DirectoryConfiguration::isOverride)
        );
        GraalVMReachabilityMetadataRepositoryExtension repositoryExtension = reachabilityExtensionOn(graalExtension);
        graalVMReachabilityMetadataService(project, repositoryExtension);
    }

    private static ExcludeEntry getExclusionConfig(ModuleVersionIdentifier moduleVersion) {
        String gav = moduleVersion.getGroup() + ":" + moduleVersion.getName() + ":" + moduleVersion.getVersion();
        return new ExcludeEntry(gav, Arrays.asList("^/META-INF/native-image/.*"));
    }

    private static LogLevel determineLogLevel() {
        LogLevel logLevel = LogLevel.DEBUG;
        String loggingProperty = System.getProperty(CONFIG_REPO_LOGLEVEL);
        if (loggingProperty != null) {
            logLevel = LogLevel.valueOf(loggingProperty.toUpperCase(Locale.US));
        }
        return logLevel;
    }

    private static GraalVMReachabilityMetadataRepositoryExtension reachabilityExtensionOn(GraalVMExtension graalExtension) {
        return ((ExtensionAware) graalExtension).getExtensions().getByType(GraalVMReachabilityMetadataRepositoryExtension.class);
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
        configureNativeConfigurationRepo((ExtensionAware) graalvmNative);
        return graalvmNative;
    }

    private void configureNativeConfigurationRepo(ExtensionAware graalvmNative) {
        GraalVMReachabilityMetadataRepositoryExtension configurationRepository = graalvmNative.getExtensions().create("metadataRepository", GraalVMReachabilityMetadataRepositoryExtension.class);
        configurationRepository.getEnabled().convention(false);
        configurationRepository.getVersion().convention(VersionInfo.METADATA_REPO_VERSION);
        configurationRepository.getUri().convention(configurationRepository.getVersion().map(serializableTransformerOf(this::getReachabilityMetadataRepositoryUrlForVersion)));
        configurationRepository.getExcludedModules().convention(Collections.emptySet());
        configurationRepository.getModuleToConfigVersion().convention(Collections.emptyMap());
    }

    private URI getReachabilityMetadataRepositoryUrlForVersion(String version) {
        try {
            return new URI(String.format(METADATA_REPO_URL_TEMPLATE, version));
        } catch (URISyntaxException e) {
            throw new RuntimeException(e);
        }
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
        NativeImageOptions testOptions = createTestOptions(graalExtension, name, project, mainOptions, config.getSourceSet());

        TaskProvider<Test> testTask = config.validate().getTestTask();
        testTask.configure(test -> {
            File testList = new File(testResultsDir, test.getName() + "/testlist");
            testListDirectory.set(testList);
            test.getOutputs().dir(testList);
            // Set system property read by the UniqueIdTrackingListener.
            test.systemProperty(JUNIT_PLATFORM_LISTENERS_UID_TRACKING_ENABLED, true);
            TrackingDirectorySystemPropertyProvider directoryProvider = project.getObjects().newInstance(TrackingDirectorySystemPropertyProvider.class);
            directoryProvider.getDirectory().set(testListDirectory);
            test.getJvmArgumentProviders().add(directoryProvider);
            test.doFirst("cleanup test ids", new CleanupTestIdsDirectory(testListDirectory, getFileOperations()));
        });

        // Following ensures that required feature jar is on classpath for every project
        injectTestPluginDependencies(project, graalExtension.getTestSupport());

        TaskProvider<BuildNativeImageTask> testImageBuilder = tasks.named(deriveTaskName(name, "native", "Compile"), BuildNativeImageTask.class, task -> {
            task.setOnlyIf(t -> graalExtension.getTestSupport().get() && testListDirectory.getAsFile().get().exists());
            task.getTestListDirectory().set(testListDirectory);
            testTask.get();
            if (!agentProperty(project, graalExtension.getAgent()).get().equals("disabled")) {
                testOptions.getConfigurationFileDirectories().from(AgentConfigurationFactory.getAgentOutputDirectoryForTask(project.getLayout(), testTask.getName()));
            }
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
            task.setOnlyIf(t -> graalExtension.getTestSupport().get() && testListDirectory.getAsFile().get().exists());
        });
    }

    /**
     * Returns a provider which prefers the CLI arguments over the configured
     * extension value.
     */
    private static Provider<String> agentProperty(Project project, AgentOptions options) {
        return project.getProviders()
                .gradleProperty(AGENT_PROPERTY)
                .map(serializableTransformerOf(v -> {
                    if (!v.isEmpty()) {
                        return v;
                    }
                    return options.getDefaultMode().get();
                }))
                .orElse(options.getEnabled().map(serializableTransformerOf(enabled -> enabled ? options.getDefaultMode().get() : "disabled")));
    }

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
        ProviderFactory providers = project.getProviders();
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
                    attrs.attributeProvider(attr, providers.provider(() -> baseAttributes.getAttribute(attr)));
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
        setupExtensionConfigExcludes(buildExtension, configs);
        buildExtension.getClasspath().from(configs.getImageClasspathConfiguration());
        return buildExtension;
    }


    private static NativeImageOptions createTestOptions(GraalVMExtension graalExtension,
                                                        String binaryName,
                                                        Project project,
                                                        NativeImageOptions mainExtension,
                                                        SourceSet sourceSet) {
        NativeImageOptions testExtension = graalExtension.getBinaries().create(binaryName);
        NativeConfigurations configs = createNativeConfigurations(
                project,
                binaryName,
                JavaPlugin.TEST_RUNTIME_CLASSPATH_CONFIGURATION_NAME
        );
        setupExtensionConfigExcludes(testExtension, configs);

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

    private static void addExcludeConfigArg(List<String> args, Path jarPath, List<String> listOfResourcePatterns) {
        listOfResourcePatterns.forEach(resourcePattern -> {
            args.add("--exclude-config");
            args.add(Pattern.quote(jarPath.toAbsolutePath().toString()));
            args.add(String.format("%s", resourcePattern));
        });
    }

    private static void setupExtensionConfigExcludes(NativeImageOptions options, NativeConfigurations configs) {
        options.getExcludeConfigArgs().set(options.getExcludeConfig().map(serializableTransformerOf(excludedConfig -> {
            List<String> args = new ArrayList<>();
            excludedConfig.forEach((entry, listOfResourcePatterns) -> {
                if (entry instanceof File) {
                    addExcludeConfigArg(args, ((File) entry).toPath(), listOfResourcePatterns);
                } else if (entry instanceof String) {
                    String dependency = (String) entry;
                    // Resolve jar for this dependency.
                    configs.getImageClasspathConfiguration().getIncoming().artifactView(view -> {
                                view.setLenient(true);
                                view.componentFilter(id -> {
                                    if (id instanceof ModuleComponentIdentifier) {
                                        ModuleComponentIdentifier mid = (ModuleComponentIdentifier) id;
                                        String gav = String.format("%s:%s:%s",
                                                mid.getGroup(),
                                                mid.getModule(),
                                                mid.getVersion()
                                        );
                                        return gav.startsWith(dependency);
                                    }
                                    return false;
                                });
                            }).getFiles().getFiles().stream()
                            .map(File::toPath)
                            .forEach(jarPath -> addExcludeConfigArg(args, jarPath, listOfResourcePatterns));
                } else {
                    throw new UnsupportedOperationException("Expected File or GAV coordinate for excludeConfig option, got " + entry.getClass());
                }
            });
            return args;
        })));
    }

    private static List<String> agentSessionDirectories(Directory outputDirectory) {
        return Arrays.stream(Objects.requireNonNull(
                                outputDirectory.getAsFile()
                                        .listFiles(file -> file.isDirectory() && file.getName().startsWith("session-"))
                        )
                ).map(File::getAbsolutePath)
                .collect(Collectors.toList());
    }

    private void configureAgent(Project project,
                                Provider<String> agentMode,
                                GraalVMExtension graalExtension,
                                ExecOperations execOperations,
                                FileSystemOperations fileOperations,
                                Task taskToInstrument,
                                JavaForkOptions javaForkOptions) {
        Provider<AgentConfiguration> agentConfiguration = AgentConfigurationFactory.getAgentConfiguration(agentMode, graalExtension.getAgent());
        //noinspection Convert2Lambda
        taskToInstrument.doFirst(new Action<Task>() {
            @Override
            public void execute(@Nonnull Task task) {
                if (agentConfiguration.get().isEnabled()) {
                    logger.logOnce("Instrumenting task with the native-image-agent: " + task.getName());
                }
            }
        });
        AgentCommandLineProvider cliProvider = project.getObjects().newInstance(AgentCommandLineProvider.class);
        cliProvider.getInputFiles().from(agentConfiguration.map(serializableTransformerOf(AgentConfiguration::getAgentFiles)));
        cliProvider.getEnabled().set(agentConfiguration.map(serializableTransformerOf(AgentConfiguration::isEnabled)));
        cliProvider.getFilterableEntries().set(graalExtension.getAgent().getFilterableEntries());
        cliProvider.getAgentMode().set(agentMode);

        Provider<Directory> outputDir = AgentConfigurationFactory.getAgentOutputDirectoryForTask(project.getLayout(), taskToInstrument.getName());
        Provider<Boolean> isMergingEnabled = agentConfiguration.map(serializableTransformerOf(AgentConfiguration::isEnabled));
        Provider<AgentMode> agentModeProvider = agentConfiguration.map(serializableTransformerOf(AgentConfiguration::getAgentMode));
        Supplier<List<String>> mergeInputDirs = serializableSupplierOf(() -> outputDir.map(serializableTransformerOf(NativeImagePlugin::agentSessionDirectories)).get());
        Supplier<List<String>> mergeOutputDirs = serializableSupplierOf(() -> outputDir.map(serializableTransformerOf(dir -> Collections.singletonList(dir.getAsFile().getAbsolutePath()))).get());
        cliProvider.getOutputDirectory().set(outputDir);
        cliProvider.getAgentOptions().set(agentConfiguration.map(serializableTransformerOf(AgentConfiguration::getAgentCommandLine)));
        javaForkOptions.getJvmArgumentProviders().add(cliProvider);

        taskToInstrument.doLast(new MergeAgentFilesAction(
                isMergingEnabled,
                agentModeProvider,
                project.provider(() -> false),
                project.getObjects(),
                graalvmHomeProvider(project.getProviders()),
                mergeInputDirs,
                mergeOutputDirs,
                graalExtension.getToolchainDetection(),
                execOperations));

        taskToInstrument.doLast(new CleanupAgentFilesAction(mergeInputDirs, fileOperations));

        taskToInstrument.doLast(new ProcessGeneratedGraalResourceFilesAction(
                outputDir,
                graalExtension.getAgent().getFilterableEntries()
        ));
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
        private final FileSystemOperations fsOperations;

        private CleanupTestIdsDirectory(DirectoryProperty directory, FileSystemOperations fsOperations) {
            this.directory = directory;
            this.fsOperations = fsOperations;
        }

        @Override
        public void execute(@Nonnull Task task) {
            File dir = directory.getAsFile().get();
            if (dir.exists()) {
                fsOperations.delete(s -> s.delete(dir));
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

    private static final class ExcludeEntry {
        private final String gav;
        private final List<String> excludes;

        private ExcludeEntry(String gav, List<String> excludes) {
            this.gav = gav;
            this.excludes = excludes;
        }

        public String getGav() {
            return gav;
        }

        public List<String> getExcludes() {
            return excludes;
        }
    }
}
