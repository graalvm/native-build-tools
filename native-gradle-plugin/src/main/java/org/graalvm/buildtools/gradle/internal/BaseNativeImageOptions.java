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
import org.graalvm.buildtools.gradle.dsl.NativeImageLayer;
import org.graalvm.buildtools.gradle.dsl.NativeResourcesOptions;
import org.graalvm.buildtools.gradle.dsl.agent.DeprecatedAgentOptions;
import org.graalvm.buildtools.gradle.tasks.BuildNativeImageTask;
import org.graalvm.buildtools.gradle.tasks.CreateLayerOptions;
import org.graalvm.buildtools.gradle.tasks.LayerOptions;
import org.graalvm.buildtools.gradle.tasks.UseLayerOptions;
import org.graalvm.buildtools.utils.SharedConstants;
import org.graalvm.buildtools.utils.NativeImageLayerArguments;
import org.gradle.api.Action;
import org.gradle.api.DomainObjectSet;
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.file.ProjectLayout;
import org.gradle.api.logging.Logging;
import org.gradle.api.model.ObjectFactory;
import org.gradle.api.provider.ListProperty;
import org.gradle.api.provider.MapProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.provider.Provider;
import org.gradle.api.provider.ProviderFactory;
import org.gradle.api.tasks.Classpath;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.InputFiles;
import org.gradle.api.tasks.Internal;
import org.gradle.api.tasks.Nested;
import org.gradle.api.tasks.Optional;
import org.gradle.api.tasks.TaskContainer;
import org.gradle.jvm.toolchain.JavaLauncher;
import org.gradle.jvm.toolchain.JavaToolchainService;

import javax.inject.Inject;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

import static org.graalvm.buildtools.gradle.NativeImagePlugin.compileTaskNameForBinary;


/**
 * Class that declares native image options.
 *
 * @author gkrocher
 */
@SuppressWarnings({"unused", "UnusedReturnValue"})
public abstract class BaseNativeImageOptions implements NativeImageOptions {
    private static final GraalVMLogger LOGGER = GraalVMLogger.of(Logging.getLogger(BaseNativeImageOptions.class));
    private final DomainObjectSet<LayerOptions> layers;
    private final ConfigurableFileCollection layerCompatibilityClasspath;
    private transient NativeImageLayer assignedLayer;

    private final String name;
    private final transient TaskContainer tasks;
    private final ObjectFactory objects;
    private final ProviderFactory providers;
    private final transient NativeImageLayerRegistry layerRegistry;

    @Override
    @Internal
    public String getName() {
        return name;
    }

    /**
     * Gets the name of the native executable to be generated.
     *
     * @return The image name property.
     */
    @Input
    public abstract Property<String> getImageName();

    /**
     * Returns the fully qualified name of the Main class to be executed.
     * <p>
     * This does not need to be set if using an <a href="https://docs.oracle.com/javase/tutorial/deployment/jar/appman.html">Executable Jar</a> with a {@code Main-Class} attribute.
     * </p>
     *
     * @return mainClass The main class.
     */
    @Input
    @Optional
    public abstract Property<String> getMainClass();

    /**
     * Returns the arguments for the native-image invocation.
     *
     * @return Arguments for the native-image invocation.
     */
    @Input
    public abstract ListProperty<String> getBuildArgs();

    /**
     * Returns the system properties which will be used by the native-image builder process.
     *
     * @return The system properties. Returns an empty map when there are no system properties.
     */
    @Input
    public abstract MapProperty<String, Object> getSystemProperties();

    /**
     * Returns the environment variables which will be used by the native-image builder process.
     * @return the environment variables. Returns an empty map when there are no environment variables.
     *
     * @since 0.9.14
     */
    @Override
    public abstract MapProperty<String, Object> getEnvironmentVariables();

    /**
     * Returns the classpath for the native-image building.
     *
     * @return classpath The classpath for the native-image building.
     */
    @InputFiles
    @Classpath
    public abstract ConfigurableFileCollection getClasspath();

    /**
     * Returns the extra arguments to use when launching the JVM for the native-image building process.
     * Does not include system properties and the minimum/maximum heap size.
     *
     * @return The arguments. Returns an empty list if there are no arguments.
     */
    @Input
    public abstract ListProperty<String> getJvmArgs();

    /**
     * Returns the arguments to use when launching the built image.
     *
     * @return The arguments. Returns an empty list if there are no arguments.
     */
    @Input
    public abstract ListProperty<String> getRuntimeArgs();

    /**
     * Gets the value which toggles native-image debug symbol output.
     *
     * @return Is debug enabled
     */
    @Input
    public abstract Property<Boolean> getDebug();

    /**
     * @return Whether to enable fallbacks (defaults to false).
     */
    @Input
    public abstract Property<Boolean> getFallback();

    /**
     * Gets the value which toggles native-image verbose output.
     *
     * @return Is verbose output
     */
    @Input
    public abstract Property<Boolean> getVerbose();


    /**
     * Gets the value which determines if shared library is being built.
     *
     * @return The value which determines if shared library is being built.
     */
    @Input
    public abstract Property<Boolean> getSharedLibrary();

    /**
     * Gets the value which determines if image is being built in quick build mode.
     *
     * @return The value which determines if image is being built in quick build mode.
     */
    @Input
    public abstract Property<Boolean> getQuickBuild();

    /**
     * Gets the value which determines if image is being built with rich output.
     *
     * @return The value which determines if image is being built with rich output.
     */
    @Input
    public abstract Property<Boolean> getRichOutput();

    /**
     * Returns the toolchain used to invoke native-image. Currently, pointing
     * to a Java launcher due to Gradle limitations.
     */
    @Nested
    @Optional
    public abstract Property<JavaLauncher> getJavaLauncher();

    /**
     * Returns the list of configuration file directories (e.g. resource-config.json, ...) which need
     * to be passed to native-image.
     *
     * @return a collection of directories
     */
    @InputFiles
    public abstract ConfigurableFileCollection getConfigurationFileDirectories();

    /**
     * Returns the MapProperty that contains information about configuration that should be excluded
     * during image building. It consists of dependency coordinates and a list of
     * regular expressions that match resources that should be excluded.
     *
     * @return a map of filters for configuration exclusion
     */
    @Input
    public abstract MapProperty<Object, List<String>> getExcludeConfig();

    @Nested
    public abstract NativeResourcesOptions getResources();

    public void resources(Action<? super NativeResourcesOptions> spec) {
        spec.execute(getResources());
    }

    @Inject
    public BaseNativeImageOptions(String name,
                                  ProjectLayout layout,
                                  ObjectFactory objectFactory,
                                  ProviderFactory providers,
                                  JavaToolchainService toolchains,
                                  TaskContainer tasks,
                                  NativeImageLayerRegistry layerRegistry,
                                  String defaultImageName) {
        this.name = name;
        getDebug().convention(false);
        getFallback().convention(false);
        getVerbose().convention(false);
        getQuickBuild().convention(providers.environmentVariable("GRAALVM_QUICK_BUILD").map(env -> {
            LOGGER.logOnce("Quick build environment variable is set.");
            if (env.isEmpty()) {
                return true;
            }
            return Boolean.parseBoolean(env);
        }).orElse(false));
        getRichOutput().convention(!SharedConstants.IS_CI && !SharedConstants.IS_WINDOWS && !SharedConstants.IS_DUMB_TERM && !SharedConstants.NO_COLOR);
        getSharedLibrary().convention(false);
        getImageName().convention(defaultImageName);
        getUseFatJar().convention(false);
        getPgoInstrument().convention(false);
        getLayerNames().convention(List.of());
        DirectoryProperty pgoProfileDir = objectFactory.directoryProperty();
        pgoProfileDir.convention(layout.getProjectDirectory().dir("src/pgo-profiles/" + name));
        getPgoProfilesDirectory().convention(pgoProfileDir.map(d -> d.getAsFile().exists() ? d : null));
        this.layers = objectFactory.domainObjectSet(LayerOptions.class);
        this.layerCompatibilityClasspath = objectFactory.fileCollection();
        getClasspath().from(layerCompatibilityClasspath);
        this.tasks = tasks;
        this.objects = objectFactory;
        this.providers = providers;
        this.layerRegistry = layerRegistry;
    }

    private static Provider<Boolean> property(ProviderFactory providers, String name) {
        return providers.gradleProperty(name)
                .map(Boolean::valueOf)
                .orElse(false);
    }

    /**
     * Adds arguments for the native-image invocation.
     *
     * @param buildArgs Arguments for the native-image invocation.
     * @return this
     */
    public BaseNativeImageOptions buildArgs(Object... buildArgs) {
        getBuildArgs().addAll(
                Arrays.stream(buildArgs)
                        .map(String::valueOf)
                        .collect(Collectors.toList())
        );
        return this;
    }

    /**
     * Adds arguments for the native-image invocation.
     *
     * @param buildArgs Arguments for the native-image invocation.
     * @return this
     */
    public BaseNativeImageOptions buildArgs(Iterable<?> buildArgs) {
        getBuildArgs().addAll(
                StreamSupport.stream(buildArgs.spliterator(), false)
                        .map(String::valueOf)
                        .collect(Collectors.toList())
        );
        return this;
    }

    /**
     * Adds some system properties to be used by the native-image builder process.
     *
     * @param properties The system properties. Must not be null.
     * @return this
     */
    @SuppressWarnings("unused")
    public BaseNativeImageOptions systemProperties(Map<String, ?> properties) {
        MapProperty<String, Object> map = getSystemProperties();
        properties.forEach((key, value) -> map.put(key, value == null ? null : String.valueOf(value)));
        return this;
    }

    /**
     * Adds a system property to be used by the native-image builder process.
     *
     * @param name The name of the property
     * @param value The value for the property. May be null.
     * @return this
     */
    public BaseNativeImageOptions systemProperty(String name, Object value) {
        getSystemProperties().put(name, value == null ? null : String.valueOf(value));
        return this;
    }

    /**
     * Adds elements to the classpath for the native-image building.
     *
     * @param paths The classpath elements.
     * @return this
     */
    public BaseNativeImageOptions classpath(Object... paths) {
        getClasspath().from(paths);
        return this;
    }

    /**
     * Adds some arguments to use when launching the JVM for the native-image building process.
     *
     * @param arguments The arguments.
     * @return this
     */
    public BaseNativeImageOptions jvmArgs(Object... arguments) {
        getJvmArgs().addAll(Arrays.stream(arguments).map(String::valueOf).collect(Collectors.toList()));
        return this;
    }

    /**
     * Adds some arguments to use when launching the JVM for the native-image building process.
     *
     * @param arguments The arguments. Must not be null.
     * @return this
     */
    public BaseNativeImageOptions jvmArgs(Iterable<?> arguments) {
        getJvmArgs().addAll(
                StreamSupport.stream(arguments.spliterator(), false)
                        .map(String::valueOf)
                        .collect(Collectors.toList())
        );
        return this;
    }

    /**
     * Adds some arguments to use when launching the built image.
     *
     * @param arguments The arguments.
     * @return this
     */
    public BaseNativeImageOptions runtimeArgs(Object... arguments) {
        getRuntimeArgs().addAll(Arrays.stream(arguments).map(String::valueOf).collect(Collectors.toList()));
        return this;
    }

    /**
     * Adds some arguments to use when launching the built image.
     *
     * @param arguments The arguments. Must not be null.
     * @return this
     */
    public BaseNativeImageOptions runtimeArgs(Iterable<?> arguments) {
        getRuntimeArgs().addAll(
                StreamSupport.stream(arguments.spliterator(), false)
                        .map(String::valueOf)
                        .collect(Collectors.toList())
        );
        return this;
    }

    @Override
    @Nested
    public abstract DeprecatedAgentOptions getAgent();

    @Override
    public void agent(Action<? super DeprecatedAgentOptions> spec) {
        spec.execute(getAgent());
    }

    @Override
    public DomainObjectSet<LayerOptions> getLayers() {
        return layers;
    }

    @Override
    public void layers(Action<? super DomainObjectSet<LayerOptions>> spec) {
        spec.execute(layers);
    }

    @Override
    public void useLayer(String name) {
        // Keep the deprecated string adapter operational while directing callers to typed layer objects. §FS-plugin-model.2.
        LOGGER.warn("useLayer(String) is deprecated; configure graalvmNative.layers and pass the typed layer object instead.");
        addLayerName(name);
        var taskName = compileTaskNameForBinary(name);
        var layer = objects.newInstance(UseLayerOptions.class);
        layer.getLayerName().convention(name);
        layer.getLayerFile().convention(tasks.named(taskName, BuildNativeImageTask.class).flatMap(BuildNativeImageTask::getCreatedLayerFile));
        getLayerFiles().from(layer.getLayerFile());
        layers(options -> options.add(layer));
    }

    @Override
    public void useLayer(NativeImageLayer layer) {
        // Typed layer consumption records the producing layer output on the binary. §FS-plugin-model.2.
        addLayerName(layer.getName());
        addLayerReference(layerRegistry.reference(layer.getName()));
    }

    @Override
    public void useLayer(Provider<? extends NativeImageLayer> layer) {
        // Provider-backed layer consumption preserves the typed, lazy extension surface. §FS-plugin-model.2.
        getLayerNames().add(layer.map(NativeImageLayer::getName));
        getLayerFiles().from(layer.flatMap(NativeImageLayer::getOutputFile));
        layerCompatibilityClasspath.from(layer.map(NativeImageLayer::getCompatibilityClasspath));
    }

    @Override
    public void usesLayer(String name) {
        // Resolve normalized properties without retaining the layer container in task state. §FS-plugin-model.2.
        NativeImageLayerArguments.validateLayerName(name);
        addLayerName(name);
        addLayerReference(layerRegistry.reference(name));
    }

    @Override
    public NativeImageLayer getLayer() {
        return assignedLayer;
    }

    @Override
    public void setLayer(NativeImageLayer layer) {
        // Singular property assignment replaces any prior layer selection. §FS-plugin-model.2.
        assignedLayer = layer;
        getLayerNames().set(List.of(layer.getName()));
        NativeImageLayerRegistry.LayerReference reference = layerRegistry.reference(layer.getName());
        getLayerFiles().setFrom(reference.getOutputFile());
        layerCompatibilityClasspath.setFrom(reference.getCompatibilityClasspath());
    }

    private void addLayerReference(NativeImageLayerRegistry.LayerReference reference) {
        getLayerFiles().from(reference.getOutputFile());
        layerCompatibilityClasspath.from(reference.getCompatibilityClasspath());
    }

    private void addLayerName(String layerName) {
        if (getLayerNames().getOrElse(List.of()).contains(layerName)) {
            throw new IllegalArgumentException("Layer '" + layerName + "' is already assigned to binary '" + getName() + "'");
        }
        assignedLayer = null;
        getLayerNames().add(layerName);
    }

    @Override
    public void createLayer(Action<? super CreateLayerOptions> spec) {
        LOGGER.warn("createLayer is deprecated; configure a named layer in graalvmNative.layers instead.");
        var layer = objects.newInstance(CreateLayerOptions.class);
        var binaryName = getName();
        if (!binaryName.startsWith("lib")) {
            throw new IllegalArgumentException("Binary name for a layer must start with 'lib'");
        }
        layer.getLayerName().convention(binaryName);
        getImageName().set(binaryName);
        layer.getAll().convention(false);
        spec.execute(layer);
        getLayerCreate().set(layer);
        layers(options -> options.add(layer));
    }
}
