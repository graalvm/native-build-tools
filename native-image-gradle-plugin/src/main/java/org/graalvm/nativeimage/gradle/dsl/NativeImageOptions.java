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
package org.graalvm.nativeimage.gradle.dsl;

import org.graalvm.nativeimage.gradle.GradleUtils;
import org.graalvm.nativeimage.gradle.Utils;
import org.gradle.api.GradleException;
import org.gradle.api.Project;
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.file.FileCollection;
import org.gradle.api.model.ObjectFactory;
import org.gradle.api.plugins.JavaApplication;
import org.gradle.api.provider.ListProperty;
import org.gradle.api.provider.MapProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.Classpath;
import org.gradle.api.tasks.SourceSet;

import javax.annotation.Nullable;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.BooleanSupplier;


/**
 * Class that declares native image options.
 *
 * @author gkrocher
 */
@SuppressWarnings({"UnstableApiUsage", "unused", "UnusedReturnValue"})
public class NativeImageOptions {
    public static final List<String> EXTENSION_NAMES = Arrays.asList("nativeImage", "graal");

    private final Property<String> imageName;
    private final Property<String> mainClass;
    private final ListProperty<String> buildArgs;
    private final MapProperty<String, Object> systemProperties;
    private @Nullable FileCollection classpath;
    private final ListProperty<String> jvmArgs;
    private final ListProperty<String> runtimeArgs;
    private final Property<Boolean> debug;
    private final Property<Boolean> server;
    private final Property<Boolean> fallback;
    private final Property<Boolean> verbose;
    private final Map<BooleanSupplier, String> booleanCmds;
    private final Property<Boolean> agent;
    private final Property<Boolean> persistConfig;

    public NativeImageOptions(ObjectFactory objectFactory) {
        this.imageName = objectFactory.property(String.class)
                .convention("application");
        this.mainClass = objectFactory.property(String.class);
        this.buildArgs = objectFactory.listProperty(String.class)
                .convention(new ArrayList<>(5));
        this.systemProperties = objectFactory.mapProperty(String.class, Object.class)
                .convention(new LinkedHashMap<>(5));
        this.classpath = objectFactory.fileCollection();
        this.jvmArgs = objectFactory.listProperty(String.class)
                .convention(new ArrayList<>(5));
        this.runtimeArgs = objectFactory.listProperty(String.class)
                .convention(new ArrayList<>(5));
        this.debug = objectFactory.property(Boolean.class).convention(false);
        this.server = objectFactory.property(Boolean.class).convention(false);
        this.fallback = objectFactory.property(Boolean.class).convention(false);
        this.verbose = objectFactory.property(Boolean.class).convention(false);

        this.booleanCmds = new LinkedHashMap<>(3);
        this.booleanCmds.put(debug::get, "-H:GenerateDebugInfo=1");
        this.booleanCmds.put(() -> !fallback.get(), "--no-fallback");
        this.booleanCmds.put(verbose::get, "--verbose");
        this.booleanCmds.put(server::get, "-Dcom.oracle.graalvm.isaot=true");
        this.agent = objectFactory.property(Boolean.class).convention(false);
        this.persistConfig = objectFactory.property(Boolean.class).convention(false);
    }

    public static NativeImageOptions register(Project project) {
        EXTENSION_NAMES.forEach(name -> {
            project.getExtensions().create(name,
                    NativeImageOptions.class, project.getObjects());
        });
        return project.getExtensions().findByType(NativeImageOptions.class);
    }

    public void configure(Project project) {
        configure(project, SourceSet.MAIN_SOURCE_SET_NAME);
    }

    protected void configure(Project project, String sourceSetName) {
        FileCollection classpath = GradleUtils.getClassPath(project);
        FileCollection userClasspath = getClasspath();
        if (userClasspath != null) {
            classpath = classpath.plus(userClasspath);
        }

        String cp = classpath.getAsPath();
        if (cp.length() > 0) {
            args("-cp", cp);
        }

        booleanCmds.forEach((property, cmd) -> {
            if (property.getAsBoolean()) {
                args(cmd);
            }
        });

        args("-H:Path=native-image");
        args("-H:Name=" + getImageName().get());

        Map<String, Object> sysProps = getSystemProperties().get();
        sysProps.forEach((n, v) -> {
            if (v != null) {
                args("-D" + n + "=\"" + v + "\"");
            }
        });

        List<String> jvmArgs = getJvmArgs().get();
        for (String jvmArg : jvmArgs) {
            args("-J" + jvmArg);
        }

        if (project.hasProperty(Utils.AGENT_PROPERTY) || getAgent().get()) {
            Path agentOutput = GradleUtils.getTargetDir(project)
                    .resolve(Utils.AGENT_OUTPUT_FOLDER).toAbsolutePath();

            if (!agentOutput.toFile().exists()) {
                // Maybe user chose to persist configuration into the codebase, so lets also check if that folder exists.
                agentOutput = Paths.get(project.getProjectDir().getAbsolutePath(), "src", sourceSetName, "resources", "META-INF", "native-image");
                if (!agentOutput.toFile().exists()) {
                    throw new GradleException("Agent output missing while `agent` option is set.\n" +
                            "Did you run the test task before with `-Pagent` enabled?");
                }
            }
            args("-H:ConfigurationFileDirectories=" + agentOutput);
            args("--allow-incomplete-classpath");
        }

        String mainClass = ".";
        if (getMain().isPresent()) {
            mainClass = getMain().get();
        } else {
            JavaApplication app = project.getExtensions().findByType(JavaApplication.class);
            if (app != null && app.getMainClass().isPresent()) {
                mainClass = app.getMainClass().get();
            }
        }

        if (!mainClass.equals(".")) {
            args("-H:Class=" + mainClass);
        }
    }

    /**
     * Gets the name of the native executable to be generated.
     */
    public Property<String> getImageName() {
        return imageName;
    }

    /**
     * Sets the name of the native executable to be generated.
     *
     * @param name The name.
     * @return this
     */
    public NativeImageOptions setImageName(@Nullable String name) {
        imageName.set(name);
        return this;
    }

    /**
     * Gets the name of the native executable to be generated.
     */
    public Property<String> getOutputName() {
        return imageName;
    }

    /**
     * Sets the name of the native executable to be generated.
     *
     * @param name The name.
     * @return this
     */
    public NativeImageOptions setOutputName(@Nullable String name) {
        imageName.set(name);
        return this;
    }

    /**
     * Returns the fully qualified name of the Main class to be executed.
     * <p>
     * This does not need to be set if using an <a href="https://docs.oracle.com/javase/tutorial/deployment/jar/appman.html">Executable Jar</a> with a {@code Main-Class} attribute.
     * </p>
     */
    public Property<String> getMain() {
        return mainClass;
    }

    /**
     * Returns the fully qualified name of the Main class to be executed.
     * <p>
     * This does not need to be set if using an <a href="https://docs.oracle.com/javase/tutorial/deployment/jar/appman.html">Executable Jar</a> with a {@code Main-Class} attribute.
     * </p>
     */
    public Property<String> getMainClass() {
        return mainClass;
    }

    /**
     * Sets the fully qualified name of the main class to be executed.
     *
     * @param mainClass the fully qualified name of the main class to be executed.
     * @return this
     */
    public NativeImageOptions setMain(@Nullable String mainClass) {
        this.mainClass.set(mainClass);
        return this;
    }

    /**
     * Sets the fully qualified name of the main class to be executed.
     *
     * @param mainClass the fully qualified name of the main class to be executed.
     * @return this
     */
    public NativeImageOptions setMainClass(@Nullable String mainClass) {
        this.mainClass.set(mainClass);
        return this;
    }

    /**
     * Adds args for the native-image invocation.
     *
     * @param args Args for native-image invocation.
     * @return this
     */
    public NativeImageOptions args(Object... args) {
        setArgs(Arrays.asList(args));
        return this;
    }

    /**
     * Adds args for the native-image invocation.
     *
     * @param args Args for the native-image invocation.
     * @return this
     */
    public NativeImageOptions buildArgs(Object... args) {
        return this.args(args);
    }

    /**
     * Adds args for the native-image invocation.
     *
     * @param args Args for the native-image invocation.
     * @return this
     */
    public NativeImageOptions option(Object... args) {
        return this.args(args);
    }

    /**
     * Adds args for the native-image invocation.
     *
     * @param args Args for the native-image invocation.
     * @return this
     */
    public NativeImageOptions args(Iterable<?> args) {
        setArgs(args);
        return this;
    }

    /**
     * Adds args for the native-image invocation.
     *
     * @param args Args for the native-image invocation.
     * @return this
     */
    public NativeImageOptions buildArgs(Iterable<?> args) {
        return this.args(args);
    }

    /**
     * Adds args for the native-image invocation.
     *
     * @param args Args for the native-image invocation.
     * @return this
     */
    public NativeImageOptions option(Iterable<?> args) {
        return this.args(args);
    }

    /**
     * Returns the args for the native-image invocation.
     *
     * @return Args for native-image invocation.
     */
    public ListProperty<String> getArgs() {
        return this.buildArgs;
    }

    /**
     * Returns the args for the native-image invocation.
     *
     * @return Args for native-image invocation.
     */
    public ListProperty<String> getBuildArgs() {
        return this.buildArgs;
    }

    /**
     * Returns the args for the native-image invocation.
     *
     * @return Args for native-image invocation.
     */
    public ListProperty<String> getOption() {
        return this.buildArgs;
    }

    /**
     * Sets the args for the native-image invocation.
     *
     * @param buildArgs Args for native-image invocation.
     * @return this
     */
    public NativeImageOptions setArgs(@Nullable List<String> buildArgs) {
        if (buildArgs == null) {
            this.buildArgs.set(new ArrayList<>(5));
        } else {
            this.buildArgs.addAll(buildArgs);
        }
        return this;
    }

    /**
     * Sets the args for the native-image invocation.
     *
     * @param args Args for native-image invocation.
     * @return this
     */
    public NativeImageOptions setBuildArgs(@Nullable List<String> args) {
        return this.setArgs(args);
    }

    /**
     * Sets the args for the native-image invocation.
     *
     * @param args Args for native-image invocation.
     * @return this
     */
    public NativeImageOptions setOption(@Nullable List<String> args) {
        return this.setArgs(args);
    }

    /**
     * Sets the args for the native-image invocation.
     *
     * @param args Args for native-image invocation.
     * @return this
     */
    public NativeImageOptions setArgs(@Nullable Iterable<?> args) {
        if (args == null) {
            this.buildArgs.set(Collections.emptyList());
        } else {
            for (Object argument : args) {
                if (argument != null) {
                    this.buildArgs.add(argument.toString());
                }
            }
        }
        return this;
    }

    /**
     * Sets the args for the native-image invocation.
     *
     * @param args Args for native-image invocation.
     * @return this
     */
    public NativeImageOptions setBuildArgs(@Nullable Iterable<?> args) {
        return this.setArgs(args);
    }

    /**
     * Sets the args for the native-image invocation.
     *
     * @param args Args for native-image invocation.
     * @return this
     */
    public NativeImageOptions setOption(@Nullable Iterable<?> args) {
        return this.setArgs(args);
    }

    /**
     * Returns the system properties which will be used for the process.
     *
     * @return The system properties. Returns an empty map when there are no system properties.
     */
    public MapProperty<String, Object> getSystemProperties() {
        return systemProperties;
    }

    /**
     * Sets the system properties to use for the process.
     *
     * @param properties The system properties. Must not be null.
     */
    public void setSystemProperties(Map<String, ?> properties) {
        if (properties == null) {
            this.systemProperties.set(new LinkedHashMap<>());
        } else {
            this.systemProperties.set(properties);
        }
    }

    /**
     * Adds some system properties to use for the process.
     *
     * @param properties The system properties. Must not be null.
     * @return this
     */
    public NativeImageOptions systemProperties(Map<String, ?> properties) {
        setSystemProperties(properties);
        return this;
    }

    /**
     * Adds a system property to use for the process.
     *
     * @param name  The name of the property
     * @param value The value for the property. May be null.
     * @return this
     */
    public NativeImageOptions systemProperty(String name, Object value) {
        if (name != null && value != null) {
            this.systemProperties.put(name, value.toString());
        }
        return this;
    }

    /**
     * Adds elements to the classpath for executing the main class.
     *
     * @param paths classpath elements
     * @return this
     */
    public NativeImageOptions classpath(Object... paths) {
        classpath = ((ConfigurableFileCollection) Objects.requireNonNull(classpath)).from(paths);
        return this;
    }

    /**
     * Returns the classpath for executing the main class.
     */
    @Classpath
    @Nullable
    public FileCollection getClasspath() {
        return this.classpath;
    }

    /**
     * Sets the classpath for executing the main class.
     *
     * @param classpath the classpath
     * @return this
     */
    public NativeImageOptions setClasspath(FileCollection classpath) {
        this.classpath = classpath;
        return this;
    }

    /**
     * Returns the extra arguments to use to launch the JVM for the process. Does not include system properties and the
     * minimum/maximum heap size.
     *
     * @return The arguments. Returns an empty list if there are no arguments.
     */
    public ListProperty<String> getJvmArgs() {
        return this.jvmArgs;
    }

    /**
     * Sets the extra arguments to use to launch the JVM for the process. System properties
     * and minimum/maximum heap size are updated.
     *
     * @param arguments The arguments. Must not be null.
     */
    public void setJvmArgs(@Nullable List<String> arguments) {
        if (arguments == null) {
            this.jvmArgs.set(new ArrayList<>(5));
        } else {
            this.jvmArgs.addAll(arguments);
        }
    }

    /**
     * Sets the extra arguments to use to launch the JVM for the process. System properties
     * and minimum/maximum heap size are updated.
     *
     * @param arguments The arguments. Must not be null.
     */
    public void setJvmArgs(@Nullable Iterable<?> arguments) {
        if (arguments == null) {
            this.jvmArgs.set(Collections.emptyList());
        } else {
            for (Object argument : arguments) {
                if (argument != null) {
                    this.jvmArgs.add(argument.toString());
                }
            }
        }
    }

    /**
     * Adds some arguments to use to launch the JVM for the process.
     *
     * @param arguments The arguments. Must not be null.
     * @return this
     */
    public NativeImageOptions jvmArgs(Iterable<?> arguments) {
        setJvmArgs(arguments);
        return this;
    }

    /**
     * Adds some arguments to use to launch the JVM for the process.
     *
     * @param arguments The arguments.
     * @return this
     */
    public NativeImageOptions jvmArgs(Object... arguments) {
        setJvmArgs(Arrays.asList(arguments));
        return this;
    }

    /**
     * Returns the arguments to use when launching built image.
     *
     * @return The arguments. Returns an empty list if there are no arguments.
     */
    public ListProperty<String> getRuntimeArgs() {
        return this.runtimeArgs;
    }

    /**
     * Sets the extra arguments to use when launching built image.
     *
     * @param arguments The arguments. Must not be null.
     */
    public void setRuntimeArgs(@Nullable List<String> arguments) {
        if (arguments == null) {
            this.runtimeArgs.set(new ArrayList<>(5));
        } else {
            this.runtimeArgs.addAll(arguments);
        }
    }

    /**
     * Sets the arguments to use when launching built image.
     *
     * @param arguments The arguments. Must not be null.
     */
    public void setRuntimeArgs(@Nullable Iterable<?> arguments) {
        if (arguments == null) {
            this.runtimeArgs.set(Collections.emptyList());
        } else {
            for (Object argument : arguments) {
                if (argument != null) {
                    this.runtimeArgs.add(argument.toString());
                }
            }
        }
    }

    /**
     * Adds some arguments to use when launching built image.
     *
     * @param arguments The arguments. Must not be null.
     * @return this
     */
    public NativeImageOptions runtimeArgs(Iterable<?> arguments) {
        setRuntimeArgs(arguments);
        return this;
    }

    /**
     * Adds some arguments to use when launching built image.
     *
     * @param arguments The arguments.
     * @return this
     */
    public NativeImageOptions runtimeArgs(Object... arguments) {
        setRuntimeArgs(Arrays.asList(arguments));
        return this;
    }

    /**
     * Sets the native image build to be verbose.
     *
     * @return this
     */
    public NativeImageOptions verbose(boolean verbose) {
        this.verbose.set(verbose);
        return this;
    }

    /**
     * Enables server build. Server build is disabled by default.
     *
     * @return this
     */
    public NativeImageOptions enableServerBuild(boolean enabled) {
        server.set(enabled);
        return this;
    }

    /**
     * @return Is debug enabled
     */
    public Property<Boolean> getDebug() {
        return debug;
    }

    /**
     * Builds a native image with debug symbols.
     *
     * @return this
     */
    public NativeImageOptions debug(boolean debug) {
        this.debug.set(debug);
        return this;
    }

    /**
     * @return Whether to enable fallbacks (defaults to false).
     */
    public Property<Boolean> getFallback() {
        return fallback;
    }

    /**
     * Sets whether to enable a fallback or not.
     *
     * @return this
     */
    public NativeImageOptions fallback(boolean fallback) {
        this.fallback.set(fallback);
        return this;
    }

    /**
     * @return Is verbose output
     */
    public Property<Boolean> getVerbose() {
        return verbose;
    }

    /**
     * @return Gets value which toggles agent usage
     */
    public Property<Boolean> getAgent() {
        return this.agent;
    }

    /**
     * @return Gets value which toggles persisting of agent config to META-INF
     */
    public Property<Boolean> getPersistConfig() {
        return this.persistConfig;
    }
}
