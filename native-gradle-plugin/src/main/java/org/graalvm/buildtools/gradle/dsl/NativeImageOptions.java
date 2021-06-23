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
package org.graalvm.buildtools.gradle.dsl;

import org.graalvm.buildtools.Utils;
import org.graalvm.buildtools.gradle.GradleUtils;
import org.gradle.api.GradleException;
import org.gradle.api.Project;
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.file.FileCollection;
import org.gradle.api.model.ObjectFactory;
import org.gradle.api.plugins.JavaApplication;
import org.gradle.api.provider.ListProperty;
import org.gradle.api.provider.MapProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.SourceSet;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BooleanSupplier;
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
    public abstract Property<String> getImageName();

    /**
     * Returns the fully qualified name of the Main class to be executed.
     * <p>
     * This does not need to be set if using an <a href="https://docs.oracle.com/javase/tutorial/deployment/jar/appman.html">Executable Jar</a> with a {@code Main-Class} attribute.
     * </p>
     *
     * @return mainClass The main class.
     */
    public abstract Property<String> getMainClass();

    /**
     * Returns the arguments for the native-image invocation.
     *
     * @return Arguments for the native-image invocation.
     */
    public abstract ListProperty<String> getBuildArgs();

    /**
     * Returns the system properties which will be used by the native-image builder process.
     *
     * @return The system properties. Returns an empty map when there are no system properties.
     */
    public abstract MapProperty<String, Object> getSystemProperties();

    /**
     * Returns the classpath for the native-image building.
     *
     * @return classpath The classpath for the native-image building.
     */
    public abstract ConfigurableFileCollection getClasspath();

    /**
     * Returns the extra arguments to use when launching the JVM for the native-image building process.
     * Does not include system properties and the minimum/maximum heap size.
     *
     * @return The arguments. Returns an empty list if there are no arguments.
     */
    public abstract ListProperty<String> getJvmArgs();

    /**
     * Returns the arguments to use when launching the built image.
     *
     * @return The arguments. Returns an empty list if there are no arguments.
     */
    public abstract ListProperty<String> getRuntimeArgs();

    /**
     * Gets the value which toggles native-image debug symbol output.
     *
     * @return Is debug enabled
     */
    public abstract Property<Boolean> getDebug();

    /**
     * Returns the server property, used to determine if the native image
     * build server should be used.
     *
     * @return the server property
     */
    public abstract Property<Boolean> getServer();

    /**
     * @return Whether to enable fallbacks (defaults to false).
     */
    public abstract Property<Boolean> getFallback();

    /**
     * Gets the value which toggles native-image verbose output.
     *
     * @return Is verbose output
     */
    public abstract Property<Boolean> getVerbose();

    /**
     * Gets the value which toggles the native-image-agent usage.
     *
     * @return The value which toggles the native-image-agent usage.
     */
    public abstract Property<Boolean> getAgent();

    /**
     * Gets the value which toggles persisting of agent config to META-INF.
     *
     * @return The value which toggles persisting of agent config to META-INF.
     */
    public abstract Property<Boolean> getPersistConfig();

    // internal state
    private boolean configured;
    private final Map<BooleanSupplier, String> booleanCmds;

    public NativeImageOptions(ObjectFactory objectFactory) {
        getDebug().convention(false);
        getServer().convention(false);
        getFallback().convention(false);
        getVerbose().convention(false);
        getAgent().convention(false);
        getPersistConfig().convention(false);

        this.booleanCmds = new LinkedHashMap<>(3);
        this.booleanCmds.put(getDebug()::get, "-H:GenerateDebugInfo=1");
        this.booleanCmds.put(() -> !getFallback().get(), "--no-fallback");
        this.booleanCmds.put(getVerbose()::get, "--verbose");
        this.booleanCmds.put(getServer()::get, "-Dcom.oracle.graalvm.isaot=true");

        this.configured = false;
    }

    public static NativeImageOptions register(Project project) {
        return project.getExtensions().create("nativeBuild", NativeImageOptions.class, project.getObjects());
    }

    public void configure(Project project) {
        configure(project, SourceSet.MAIN_SOURCE_SET_NAME);
    }

    /**
     * Configures the arguments for the native-image invocation based on user supplied options.
     *
     * @param project The current project.
     * @param sourceSetName Used source set name.
     */
    protected void configure(Project project, String sourceSetName) {
        if (configured) {
            return;
        }
        this.configured = true;

        // User arguments should be at the end of the native-image invocation
        // but at this moment they are already set. So we remove them here
        // and add them back later.
        List<String> userArgs = getBuildArgs().get();
        getBuildArgs().set(Collections.emptyList());

        FileCollection classpath = GradleUtils.getClassPath(project);
        FileCollection userClasspath = getClasspath();
        if (userClasspath != null) {
            classpath = classpath.plus(userClasspath);
        }

        String cp = classpath.getAsPath();
        if (cp.length() > 0) {
            buildArgs("-cp", cp);
        }

        booleanCmds.forEach((property, cmd) -> {
            if (property.getAsBoolean()) {
                buildArgs(cmd);
            }
        });

        buildArgs("-H:Path=" + Utils.NATIVE_IMAGE_OUTPUT_FOLDER);

        if (!getImageName().isPresent()) {
            getImageName().set(project.getName().toLowerCase());
        }
        buildArgs("-H:Name=" + getImageName().get());

        Map<String, Object> sysProps = getSystemProperties().get();
        sysProps.forEach((n, v) -> {
            if (v != null) {
                buildArgs("-D" + n + "=\"" + v + "\"");
            }
        });

        List<String> jvmArgs = getJvmArgs().get();
        for (String jvmArg : jvmArgs) {
            buildArgs("-J" + jvmArg);
        }

        if (project.hasProperty(Utils.AGENT_PROPERTY) || getAgent().get()) {
            Path agentOutput = project.getBuildDir().toPath()
                    .resolve(Utils.AGENT_OUTPUT_FOLDER).resolve(sourceSetName).toAbsolutePath();

            if (!agentOutput.toFile().exists()) {
                // Maybe user chose to persist configuration into the codebase, so lets also check if that folder exists.
                agentOutput = Paths.get(project.getProjectDir().getAbsolutePath(), "src", sourceSetName, "resources", "META-INF", "native-image");
                if (!agentOutput.toFile().exists()) {
                    throw new GradleException("Agent output missing while `agent` option is set.\n" +
                            "Did you run the corresponding task in JVM mode before with the `-Pagent` option enabled?");
                }
            }
            buildArgs("-H:ConfigurationFileDirectories=" + agentOutput);
            buildArgs("--allow-incomplete-classpath");
        }

        if (!getMainClass().isPresent()) {
            JavaApplication app = project.getExtensions().findByType(JavaApplication.class);
            if (app != null && app.getMainClass().isPresent()) {
                getMainClass().set(app.getMainClass().get());
            }
        }

        if (getMainClass().isPresent()) {
            buildArgs("-H:Class=" + getMainClass().get());
        }

        getBuildArgs().addAll(userArgs);
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
