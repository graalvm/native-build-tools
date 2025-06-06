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

import org.graalvm.buildtools.gradle.dsl.NativeImageCompileOptions;
import org.graalvm.buildtools.gradle.dsl.NativeResourcesOptions;
import org.graalvm.buildtools.gradle.dsl.agent.DeprecatedAgentOptions;
import org.graalvm.buildtools.gradle.tasks.CreateLayerOptions;
import org.graalvm.buildtools.gradle.tasks.LayerOptions;
import org.gradle.api.Action;
import org.gradle.api.DomainObjectSet;
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.provider.ListProperty;
import org.gradle.api.provider.MapProperty;
import org.gradle.api.provider.Property;
import org.gradle.jvm.toolchain.JavaLauncher;

import java.util.List;

/**
 * Configuration options for compiling a native binary.
 */
public class DelegatingCompileOptions implements NativeImageCompileOptions {
    private final NativeImageCompileOptions options;

    public DelegatingCompileOptions(NativeImageCompileOptions options) {
        this.options = options;
    }

    @Override
    public Property<String> getImageName() {
        return options.getImageName();
    }

    @Override
    public Property<JavaLauncher> getJavaLauncher() {
        return options.getJavaLauncher();
    }

    @Override
    public Property<String> getMainClass() {
        return options.getMainClass();
    }

    @Override
    public ListProperty<String> getBuildArgs() {
        return options.getBuildArgs();
    }

    @Override
    public MapProperty<String, Object> getSystemProperties() {
        return options.getSystemProperties();
    }

    @Override
    public MapProperty<String, Object> getEnvironmentVariables() {
        return options.getEnvironmentVariables();
    }

    @Override
    public ConfigurableFileCollection getClasspath() {
        return options.getClasspath();
    }

    @Override
    public ListProperty<String> getJvmArgs() {
        return options.getJvmArgs();
    }

    @Override
    public Property<Boolean> getDebug() {
        return options.getDebug();
    }

    @Override
    public Property<Boolean> getFallback() {
        return options.getFallback();
    }

    @Override
    public Property<Boolean> getVerbose() {
        return options.getVerbose();
    }

    @Override
    public Property<Boolean> getSharedLibrary() {
        return options.getSharedLibrary();
    }

    @Override
    public Property<Boolean> getQuickBuild() {
        return options.getQuickBuild();
    }

    @Override
    public Property<Boolean> getRichOutput() {
        return options.getRichOutput();
    }

    @Override
    public MapProperty<Object, List<String>> getExcludeConfig() {
        return options.getExcludeConfig();
    }

    @Override
    public NativeResourcesOptions getResources() {
        return options.getResources();
    }

    @Override
    public ConfigurableFileCollection getConfigurationFileDirectories() {
        return options.getConfigurationFileDirectories();
    }

    @Override
    public ListProperty<String> getExcludeConfigArgs() {
        return options.getExcludeConfigArgs();
    }

    @Override
    public Property<Boolean> getUseFatJar() {
        return options.getUseFatJar();
    }

    @Override
    public DeprecatedAgentOptions getAgent() {
        return options.getAgent();
    }

    @Override
    public Property<Boolean> getPgoInstrument() {
        return options.getPgoInstrument();
    }

    @Override
    public DirectoryProperty getPgoProfilesDirectory() {
        return options.getPgoProfilesDirectory();
    }

    @Override
    public DomainObjectSet<LayerOptions> getLayers() {
        return options.getLayers();
    }

    @Override
    public void layers(Action<? super DomainObjectSet<LayerOptions>> spec) {
        options.layers(spec);
    }

    @Override
    public void useLayer(String name) {
        options.useLayer(name);
    }

    @Override
    public void createLayer(Action<? super CreateLayerOptions> spec) {
        options.createLayer(spec);
    }
}
