/*
 * Copyright (c) 2020, 2021 Oracle and/or its affiliates. All rights reserved.
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
import org.graalvm.buildtools.gradle.dsl.NativeResourcesOptions;
import org.graalvm.buildtools.gradle.dsl.agent.DeprecatedAgentOptions;
import org.gradle.api.Action;
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.provider.ListProperty;
import org.gradle.api.provider.MapProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.Classpath;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.InputFiles;
import org.gradle.api.tasks.Internal;
import org.gradle.api.tasks.Nested;
import org.gradle.api.tasks.Optional;
import org.gradle.jvm.toolchain.JavaLauncher;
import org.slf4j.Logger;

import javax.inject.Inject;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Supplier;

@SuppressWarnings("DeprecatedIsStillUsed")
@Deprecated
public abstract class DeprecatedNativeImageOptions implements NativeImageOptions {
    private final String name;
    private final NativeImageOptions delegate;
    private final String replacedWith;
    private final Logger logger;
    private final AtomicBoolean warned = new AtomicBoolean();

    @Inject
    public DeprecatedNativeImageOptions(String name,
                                        NativeImageOptions delegate,
                                        String replacedWith,
                                        Logger logger) {
        this.name = name;
        this.delegate = delegate;
        this.replacedWith = replacedWith;
        this.logger = logger;
    }

    private <T> T warnAboutDeprecation(Supplier<T> action) {
        issueWarning();
        return action.get();
    }

    private void warnAboutDeprecation(Runnable action) {
        issueWarning();
        action.run();
    }

    private void issueWarning() {
        if (warned.compareAndSet(false, true)) {
            logger.warn("The " + name + " extension is deprecated and will be removed. Please use the 'graalvmNative.binaries." + replacedWith + "' extension to configure the native image instead.");
        }
    }

    @Override
    @Internal
    public String getName() {
        return warnAboutDeprecation(delegate::getName);
    }

    @Override
    @Input
    public Property<String> getImageName() {
        return warnAboutDeprecation(delegate::getImageName);
    }

    @Override
    @Optional
    @Input
    public Property<String> getMainClass() {
        return warnAboutDeprecation(delegate::getMainClass);
    }

    @Override
    @Input
    public ListProperty<String> getBuildArgs() {
        return warnAboutDeprecation(delegate::getBuildArgs);
    }

    @Override
    @Input
    public MapProperty<String, Object> getSystemProperties() {
        return warnAboutDeprecation(delegate::getSystemProperties);
    }

    @Override
    @Classpath
    @InputFiles
    public ConfigurableFileCollection getClasspath() {
        return warnAboutDeprecation(delegate::getClasspath);
    }

    @Override
    @Input
    public ListProperty<String> getJvmArgs() {
        return warnAboutDeprecation(delegate::getJvmArgs);
    }

    @Override
    @Input
    public ListProperty<String> getRuntimeArgs() {
        return warnAboutDeprecation(delegate::getRuntimeArgs);
    }

    @Override
    @Input
    public Property<Boolean> getDebug() {
        return warnAboutDeprecation(delegate::getDebug);
    }

    @Override
    @Input
    public Property<Boolean> getFallback() {
        return warnAboutDeprecation(delegate::getFallback);
    }

    @Override
    @Input
    public Property<Boolean> getVerbose() {
        return warnAboutDeprecation(delegate::getVerbose);
    }

    @Override
    @Input
    public Property<Boolean> getSharedLibrary() {
        return warnAboutDeprecation(delegate::getSharedLibrary);
    }

    @Override
    @Nested
    public Property<JavaLauncher> getJavaLauncher() {
        return warnAboutDeprecation(delegate::getJavaLauncher);
    }

    @Override
    @InputFiles
    public ConfigurableFileCollection getConfigurationFileDirectories() {
        return warnAboutDeprecation(delegate::getConfigurationFileDirectories);
    }

    @Override
    @Nested
    public NativeResourcesOptions getResources() {
        return warnAboutDeprecation(delegate::getResources);
    }

    @Override
    public void resources(Action<? super NativeResourcesOptions> spec) {
        warnAboutDeprecation(() -> delegate.resources(spec));
    }

    @Override
    public NativeImageOptions buildArgs(Object... buildArgs) {
        return warnAboutDeprecation(() -> delegate.buildArgs(buildArgs));
    }

    @Override
    public NativeImageOptions buildArgs(Iterable<?> buildArgs) {
        return warnAboutDeprecation(() -> delegate.buildArgs(buildArgs));
    }

    @Override
    public NativeImageOptions systemProperties(Map<String, ?> properties) {
        return warnAboutDeprecation(() -> delegate.systemProperties(properties));
    }

    @Override
    public NativeImageOptions systemProperty(String name, Object value) {
        return warnAboutDeprecation(() -> delegate.systemProperty(name, value));
    }

    @Override
    public NativeImageOptions classpath(Object... paths) {
        return warnAboutDeprecation(() -> delegate.classpath(paths));
    }

    @Override
    public NativeImageOptions jvmArgs(Object... arguments) {
        return warnAboutDeprecation(() -> delegate.jvmArgs(arguments));
    }

    @Override
    public NativeImageOptions jvmArgs(Iterable<?> arguments) {
        return warnAboutDeprecation(() -> delegate.jvmArgs(arguments));
    }

    @Override
    public NativeImageOptions runtimeArgs(Object... arguments) {
        return warnAboutDeprecation(() -> delegate.runtimeArgs(arguments));
    }

    @Override
    public NativeImageOptions runtimeArgs(Iterable<?> arguments) {
        return warnAboutDeprecation(() -> delegate.runtimeArgs(arguments));
    }

    @Override
    public DeprecatedAgentOptions getAgent() {
        return warnAboutDeprecation(delegate::getAgent);
    }

    @Override
    public void agent(Action<? super DeprecatedAgentOptions> spec) {
        warnAboutDeprecation(() -> delegate.agent(spec));
    }
}
