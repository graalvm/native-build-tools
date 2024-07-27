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

import org.graalvm.buildtools.gradle.dsl.agent.AgentOptions;
import org.gradle.api.Action;
import org.gradle.api.NamedDomainObjectContainer;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.Nested;
import org.gradle.api.tasks.SourceSet;
import org.gradle.api.tasks.TaskProvider;
import org.gradle.api.tasks.testing.Test;

/**
 * This is the entry point for configuring GraalVM relative features
 * provided by this plugin.
 */
public interface GraalVMExtension {

    /**
     * Determines if test support is active. This can be used
     * to disable automatic test support, especially in cases
     * where the test framework doesn't allow testing within
     * a native image.
     *
     * @return is the test support active
     */
    Property<Boolean> getTestSupport();

    @Nested
    AgentOptions getAgent();

    void agent(Action<? super AgentOptions> spec);

    DirectoryProperty getGeneratedResourcesDirectory();

    /**
     * Returns the native image configurations used to generate images.
     * By default, this plugin creates two images, one called "main" for
     * the main application and another one called "test" for tests.
     *
     * @return configuration for binaries
     */
    NamedDomainObjectContainer<NativeImageOptions> getBinaries();

    /**
     * Configures the native image options.
     *
     * @param spec specification for binary
     */
    void binaries(Action<? super NamedDomainObjectContainer<NativeImageOptions>> spec);

    /**
     * Registers a new native image binary with testing support.
     *
     * @param name the name of the binary
     * @param spec the test image configuration
     */
    void registerTestBinary(String name, Action<? super TestBinaryConfig> spec);

    /**
     * Property driving the detection of toolchains which support building native images.
     * The default is false.
     *
     * @return is toolchain detection on
     */
    Property<Boolean> getToolchainDetection();

    /**
     * Property driving the use of @-arg files when invoking native image.
     * This is enabled by default on Windows. For older native-image versions, this
     * needs to be disabled.
     *
     * @return the argument file property
     */
    Property<Boolean> getUseArgFile();


    interface TestBinaryConfig {
        /**
         * Sets the JVM test task which corresponds to the
         * native test that we're configuring.
         *
         * @param jvmTestTask an existing JVM test task
         */
        void forTestTask(TaskProvider<Test> jvmTestTask);

        void usingSourceSet(SourceSet testSourceSet);
    }
}
