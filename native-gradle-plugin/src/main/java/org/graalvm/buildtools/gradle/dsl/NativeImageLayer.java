/*
 * Copyright (c) 2026, Oracle and/or its affiliates. All rights reserved.
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

import org.gradle.api.Action;
import org.gradle.api.Named;
import org.gradle.api.Project;
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.model.ObjectFactory;
import org.gradle.api.provider.ListProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.provider.Provider;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.Internal;
import org.gradle.api.tasks.Nested;
import org.graalvm.buildtools.utils.NativeImageLayerArguments;

import javax.inject.Inject;
import java.util.HashSet;
import java.util.Set;

/**
 * First-class named Native Image layer. §FS-plugin-model.2.
 */
public abstract class NativeImageLayer implements Named {
    private final String name;
    private final LayerContents contents;
    private final Project project;
    private final ConfigurableFileCollection layerFiles;
    private final ConfigurableFileCollection inheritedCompatibilityClasspath;
    private final ConfigurableFileCollection compatibilityClasspath;
    private final Set<String> configuredLayerNames = new HashSet<>();

    @Inject
    public NativeImageLayer(String name, ObjectFactory objects, Project project) {
        this.name = name;
        this.contents = objects.newInstance(LayerContents.class, project);
        this.project = project;
        this.layerFiles = project.files();
        this.inheritedCompatibilityClasspath = project.files();
        this.compatibilityClasspath = project.files();
    }

    @Override
    @Internal
    public String getName() {
        return name;
    }

    @Nested
    public LayerContents getContents() {
        return contents;
    }

    public void contents(Action<? super LayerContents> action) {
        action.execute(contents);
    }

    @Input
    public abstract ListProperty<String> getBuildArgs();

    @Input
    public abstract Property<Boolean> getVerbose();

    public void buildArgs(String... arguments) {
        getBuildArgs().addAll(arguments);
    }

    /**
     * Adds a lazily resolved named producer layer to this layer. §FS-plugin-model.2.
     */
    public void usesLayer(String name) {
        NativeImageLayerArguments.validateLayerName(name);
        if (!configuredLayerNames.add(name)) {
            throw new IllegalArgumentException("Layer '" + name + "' is selected more than once");
        }
        getUseLayerNames().add(name);
        Provider<NativeImageLayer> layer = project.provider(() -> project.getExtensions()
            .getByType(GraalVMExtension.class)
            .getLayers()
            .getByName(name));
        layerFiles.from(layer.flatMap(NativeImageLayer::getOutputFile));
        inheritedCompatibilityClasspath.from(layer.map(NativeImageLayer::getCompatibilityClasspath));
    }

    @Internal
    public abstract ListProperty<String> getUseLayerNames();

    @Internal
    public ConfigurableFileCollection getUseLayerFiles() {
        return layerFiles;
    }

    /**
     * Classpath entries inherited from layers consumed by this layer.
     * §FS-native-invocation.3.
     */
    @Internal
    public ConfigurableFileCollection getInheritedCompatibilityClasspath() {
        return inheritedCompatibilityClasspath;
    }

    /**
     * Classpath entries that must accompany this layer in every dependent layer or binary.
     * §FS-native-invocation.3.
     */
    @Internal
    public ConfigurableFileCollection getCompatibilityClasspath() {
        return compatibilityClasspath;
    }

    @Internal
    public abstract RegularFileProperty getOutputFile();
}
