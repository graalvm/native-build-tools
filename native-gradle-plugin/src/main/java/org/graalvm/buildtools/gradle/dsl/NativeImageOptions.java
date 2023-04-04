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
import org.graalvm.buildtools.gradle.internal.DelegatingCompileOptions;
import org.gradle.api.Action;
import org.gradle.api.Named;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.Internal;
import org.gradle.api.tasks.Optional;

import java.util.Map;


/**
 * Class that declares native image options.
 * This object is a domain object which can be configured via
 * the Gradle DSL. Multiple instances of this object can be created,
 * in which case it means we have multiple native binaries.
 * 
 * The DSL combines the compiler options (building a native binary)
 * and the runtime options (executing a native binary).
 *
 * @author gkrocher
 */
@SuppressWarnings({"unused", "UnusedReturnValue"})
public interface NativeImageOptions extends Named, NativeImageCompileOptions, NativeImageRuntimeOptions {
    @Override
    @Internal
    String getName();

    void resources(Action<? super NativeResourcesOptions> spec);

    /**
     * Adds arguments for the native-image invocation.
     *
     * @param buildArgs Arguments for the native-image invocation.
     * @return this
     */
    NativeImageOptions buildArgs(Object... buildArgs);

    /**
     * Adds arguments for the native-image invocation.
     *
     * @param buildArgs Arguments for the native-image invocation.
     * @return this
     */
    NativeImageOptions buildArgs(Iterable<?> buildArgs);

    /**
     * Adds some system properties to be used by the native-image builder process.
     *
     * @param properties The system properties. Must not be null.
     * @return this
     */
    @SuppressWarnings("unused")
    NativeImageOptions systemProperties(Map<String, ?> properties);

    /**
     * Adds a system property to be used by the native-image builder process.
     *
     * @param name The name of the property
     * @param value The value for the property. May be null.
     * @return this
     */
    NativeImageOptions systemProperty(String name, Object value);

    /**
     * Adds elements to the classpath for the native-image building.
     *
     * @param paths The classpath elements.
     * @return this
     */
    NativeImageOptions classpath(Object... paths);

    /**
     * Adds some arguments to use when launching the JVM for the native-image building process.
     *
     * @param arguments The arguments.
     * @return this
     */
    NativeImageOptions jvmArgs(Object... arguments);

    /**
     * Adds some arguments to use when launching the JVM for the native-image building process.
     *
     * @param arguments The arguments. Must not be null.
     * @return this
     */
    NativeImageOptions jvmArgs(Iterable<?> arguments);

    /**
     * Adds some arguments to use when launching the built image.
     *
     * @param arguments The arguments.
     * @return this
     */
    NativeImageOptions runtimeArgs(Object... arguments);

    /**
     * Adds some arguments to use when launching the built image.
     *
     * @param arguments The arguments. Must not be null.
     * @return this
     */
    NativeImageOptions runtimeArgs(Iterable<?> arguments);

    void agent(Action<? super DeprecatedAgentOptions> spec);

    /**
     * Specify the minimal GraalVM version, can be {@code MAJOR}, {@code MAJOR.MINOR} or {@code MAJOR.MINOR.PATCH}.
     *
     * @deprecated deprecated without replacement.
     * @return the required version property.
     */
    @Input
    @Optional
    @Deprecated
    Property<String> getRequiredVersion();

    /**
     * Restricts this object to the list of options which are
     * required for compilation. This is required so that Gradle
     * only considers the inputs from that type instead of the
     * full set of properties when building a native binary.
     *
     * @return the compilation options.
     */
    default NativeImageCompileOptions asCompileOptions() {
        return new DelegatingCompileOptions(this);
    }

}
