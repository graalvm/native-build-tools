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

package org.graalvm.buildtools.gradle.dsl;

import org.graalvm.buildtools.gradle.internal.GradleUtils;
import org.gradle.api.Action;
import org.gradle.api.JavaVersion;
import org.gradle.api.Project;
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.model.ObjectFactory;
import org.gradle.api.provider.ListProperty;
import org.gradle.api.provider.MapProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.provider.Provider;
import org.gradle.api.provider.ProviderFactory;
import org.gradle.api.tasks.Classpath;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.InputFiles;
import org.gradle.api.tasks.Nested;
import org.gradle.api.tasks.Optional;
import org.gradle.jvm.toolchain.JavaLanguageVersion;
import org.gradle.jvm.toolchain.JavaLauncher;
import org.gradle.jvm.toolchain.JavaToolchainService;
import org.gradle.jvm.toolchain.JvmVendorSpec;

import javax.inject.Inject;
import java.util.Arrays;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;


/**
 * Class that declares native image options.
 *
 * @author gkrocher
 */
@SuppressWarnings({"unused", "UnusedReturnValue"})
public abstract class NativeImageOptions {
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
     * Returns the server property, used to determine if the native image
     * build server should be used.
     *
     * @return the server property
     */
    @Input
    public abstract Property<Boolean> getServer();

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
     * Gets the value which toggles the native-image-agent usage.
     *
     * @return The value which toggles the native-image-agent usage.
     */
    @Input
    public abstract Property<Boolean> getAgent();

    /**
     * Returns the toolchain used to invoke native-image. Currently pointing
     * to a Java launcher due to Gradle limitations.
     */
    @Nested
    public abstract Property<JavaLauncher> getJavaLauncher();

    /**
     * Returns the list of configuration file directories (e.g resource-config.json, ...) which need
     * to be passed to native-image.
     *
     * @return a collection of directories
     */
    @InputFiles
    public abstract ConfigurableFileCollection getConfigurationFileDirectories();

    @Nested
    public abstract NativeResourcesOptions getResources();

    public void resources(Action<? super NativeResourcesOptions> spec) {
        spec.execute(getResources());
    }

    @Inject
    public NativeImageOptions(ObjectFactory objectFactory,
                              ProviderFactory providers,
                              JavaToolchainService toolchains,
                              String defaultImageName) {
        getDebug().convention(false);
        getServer().convention(false);
        getFallback().convention(false);
        getVerbose().convention(false);
        getAgent().convention(false);
        getImageName().convention(defaultImageName);
        getJavaLauncher().convention(
                toolchains.launcherFor(spec -> {
                    spec.getLanguageVersion().set(JavaLanguageVersion.of(JavaVersion.current().getMajorVersion()));
                    if (GradleUtils.isAtLeastGradle7()) {
                        spec.getVendor().set(JvmVendorSpec.matching("GraalVM"));
                    }
                })
        );
    }

    private static Provider<Boolean> property(ProviderFactory providers, String name) {
        return providers.gradleProperty(name)
                .forUseAtConfigurationTime()
                .map(Boolean::valueOf)
                .orElse(false);
    }

    public static NativeImageOptions register(Project project, String extensionName) {
        return project.getExtensions().create(extensionName,
                NativeImageOptions.class,
                project.getObjects(),
                project.getProviders(),
                project.getExtensions().findByType(JavaToolchainService.class),
                project.getName()
        );
    }

    /**
     * Adds arguments for the native-image invocation.
     *
     * @param buildArgs Arguments for the native-image invocation.
     * @return this
     */
    public NativeImageOptions buildArgs(Object... buildArgs) {
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
    public NativeImageOptions buildArgs(Iterable<?> buildArgs) {
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
    public NativeImageOptions systemProperties(Map<String, ?> properties) {
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
    public NativeImageOptions systemProperty(String name, Object value) {
        getSystemProperties().put(name, value == null ? null : String.valueOf(value));
        return this;
    }

    /**
     * Adds elements to the classpath for the native-image building.
     *
     * @param paths The classpath elements.
     * @return this
     */
    public NativeImageOptions classpath(Object... paths) {
        getClasspath().from(paths);
        return this;
    }

    /**
     * Adds some arguments to use when launching the JVM for the native-image building process.
     *
     * @param arguments The arguments.
     * @return this
     */
    public NativeImageOptions jvmArgs(Object... arguments) {
        getJvmArgs().addAll(Arrays.stream(arguments).map(String::valueOf).collect(Collectors.toList()));
        return this;
    }

    /**
     * Adds some arguments to use when launching the JVM for the native-image building process.
     *
     * @param arguments The arguments. Must not be null.
     * @return this
     */
    public NativeImageOptions jvmArgs(Iterable<?> arguments) {
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
    public NativeImageOptions runtimeArgs(Object... arguments) {
        getRuntimeArgs().addAll(Arrays.stream(arguments).map(String::valueOf).collect(Collectors.toList()));
        return this;
    }

    /**
     * Adds some arguments to use when launching the built image.
     *
     * @param arguments The arguments. Must not be null.
     * @return this
     */
    public NativeImageOptions runtimeArgs(Iterable<?> arguments) {
        getRuntimeArgs().addAll(
                StreamSupport.stream(arguments.spliterator(), false)
                        .map(String::valueOf)
                        .collect(Collectors.toList())
        );
        return this;
    }

    /**
     * Enables server build. Server build is disabled by default.
     *
     * @param enabled Value which controls whether the server build is enabled.
     * @return this
     */
    public NativeImageOptions enableServerBuild(boolean enabled) {
        getServer().set(enabled);
        return this;
    }

}
