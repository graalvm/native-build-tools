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
package org.graalvm.buildtools.gradle.dsl;

import org.graalvm.buildtools.gradle.dsl.agent.DeprecatedAgentOptions;
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.provider.ListProperty;
import org.gradle.api.provider.MapProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.Classpath;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.InputFiles;
import org.gradle.api.tasks.Nested;
import org.gradle.api.tasks.Optional;
import org.gradle.jvm.toolchain.JavaLauncher;

import java.util.List;

/**
 * Configuration options for compiling a native binary.
 */
public interface NativeImageCompileOptions {
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
    Property<String> getMainClass();

    /**
     * Returns the arguments for the native-image invocation.
     *
     * @return Arguments for the native-image invocation.
     */
    @Input
    ListProperty<String> getBuildArgs();

    /**
     * Returns the system properties which will be used by the native-image builder process.
     *
     * @return The system properties. Returns an empty map when there are no system properties.
     */
    @Input
    MapProperty<String, Object> getSystemProperties();

    /**
     * Returns the environment variables which will be used by the native-image builder process.
     * @return the environment variables. Returns an empty map when there are no environment variables.
     *
     * @since 0.9.14
     */
    @Input
    MapProperty<String, Object> getEnvironmentVariables();

    /**
     * Returns the classpath for the native-image building.
     *
     * @return classpath The classpath for the native-image building.
     */
    @InputFiles
    @Classpath
    ConfigurableFileCollection getClasspath();

    /**
     * Returns the extra arguments to use when launching the JVM for the native-image building process.
     * Does not include system properties and the minimum/maximum heap size.
     *
     * @return The arguments. Returns an empty list if there are no arguments.
     */
    @Input
    ListProperty<String> getJvmArgs();

    /**
     * Gets the value which toggles native-image debug symbol output.
     *
     * @return Is debug enabled
     */
    @Input
    Property<Boolean> getDebug();

    /**
     * @return Whether to enable fallbacks (defaults to false).
     */
    @Input
    Property<Boolean> getFallback();

    /**
     * Gets the value which toggles native-image verbose output.
     *
     * @return Is verbose output
     */
    @Input
    Property<Boolean> getVerbose();

    /**
     * Gets the value which determines if shared library is being built.
     *
     * @return The value which determines if shared library is being built.
     */
    @Input
    Property<Boolean> getSharedLibrary();

    /**
     * Gets the value which determines if image is being built in quick build mode.
     *
     * @return The value which determines if image is being built in quick build mode.
     */
    @Input
    Property<Boolean> getQuickBuild();

    /**
     * Gets the value which determines if image is being built with rich output.
     *
     * @return The value which determines if image is being built with rich output.
     */
    @Input
    Property<Boolean> getRichOutput();

    /**
     * Returns the MapProperty that contains information about configuration that should be excluded
     * during image building. It consists of a dependency coordinates and a list of
     * regular expressions that match resources that should be excluded as a value.
     *
     * @return a map of filters for configuration exclusion
     */
    @Input
    MapProperty<Object, List<String>> getExcludeConfig();

    @Nested
    NativeResourcesOptions getResources();

    /**
     * Returns the list of configuration file directories (e.g. resource-config.json, ...) which need
     * to be passed to native-image.
     *
     * @return a collection of directories
     */
    @InputFiles
    ConfigurableFileCollection getConfigurationFileDirectories();

    @Input
    ListProperty<String> getExcludeConfigArgs();

    /**
     * Gets the name of the native executable to be generated.
     *
     * @return The image name property.
     */
    @Input
    Property<String> getImageName();

    /**
     * Returns the toolchain used to invoke native-image. Currently pointing
     * to a Java launcher due to Gradle limitations.
     *
     * @return the detected java launcher
     */
    @Nested
    @Optional
    Property<JavaLauncher> getJavaLauncher();

    /**
     * If set to true, this will build a fat jar of the image classpath
     * instead of passing each jar individually to the classpath. This
     * option can be used in case the classpath is too long and that
     * invoking native image fails, which can happen on Windows.
     *
     * @return true if a fatjar should be used. Defaults to true for Windows, and false otherwise.
     */
    @Input
    Property<Boolean> getUseFatJar();

    @Nested
    DeprecatedAgentOptions getAgent();

    /**
     * When set to true, the compiled binaries will be generated with PGO instrumentation
     * support.
     * @return the PGO instrumentation flag
     */
    @Input
    Property<Boolean> getPgoInstrument();
}
