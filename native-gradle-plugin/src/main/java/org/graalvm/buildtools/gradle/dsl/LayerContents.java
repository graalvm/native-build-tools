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
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.artifacts.Dependency;
import org.gradle.api.artifacts.MinimalExternalModuleDependency;
import org.gradle.api.artifacts.ModuleDependency;
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.provider.ListProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.provider.Provider;
import org.gradle.api.tasks.Classpath;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.Nested;
import org.gradle.api.Project;

import javax.inject.Inject;
import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Lazy contents of a named Native Image layer. §FS-plugin-model.2.
 */
public abstract class LayerContents {
    private final transient Project project;

    @Input
    public abstract Property<Boolean> getAll();

    @Input
    public abstract ListProperty<String> getModules();

    @Input
    public abstract ListProperty<String> getPackages();

    @Classpath
    public abstract ConfigurableFileCollection getFiles();

    @Nested
    public abstract ListProperty<LayerDependency> getDependencies();

    @Inject
    public LayerContents(Project project) {
        this.project = project;
        getAll().convention(false);
        getModules().convention(List.of());
        getPackages().convention(List.of());
        getDependencies().convention(List.of());
    }

    public void modules(String... modules) {
        getModules().addAll(Arrays.asList(modules));
    }

    public void packages(String... packages) {
        getPackages().addAll(Arrays.asList(packages));
    }

    public void from(Object... paths) {
        getFiles().from(paths);
    }

    public void fromConfiguration(Configuration configuration) {
        getFiles().from(resolvedArtifactsOf(configuration));
    }

    public void fromConfiguration(Provider<Configuration> configuration) {
        getFiles().from(configuration.flatMap(this::resolvedArtifactsOf));
    }

    public void dependencies(String notation) {
        addDependency(notation, true);
    }

    public void dependencies(String notation, Action<? super LayerDependencySpec> action) {
        LayerDependencySpec spec = new LayerDependencySpec();
        action.execute(spec);
        addDependency(notation, spec.isTransitive());
    }

    public void dependencies(Provider<? extends MinimalExternalModuleDependency> dependency) {
        dependencies(dependency, spec -> {
        });
    }

    public void dependencies(Provider<? extends MinimalExternalModuleDependency> dependency,
                             Action<? super LayerDependencySpec> action) {
        LayerDependencySpec spec = new LayerDependencySpec();
        action.execute(spec);
        Provider<String> notation = dependency.map(value ->
            value.getModule().toString() + ":" + value.getVersionConstraint().getRequiredVersion());
        getDependencies().add(notation.map(value -> new LayerDependency(value, spec.isTransitive())));
        getFiles().from(notation.flatMap(value -> resolvedArtifactsOf(detachedConfiguration(value, spec.isTransitive()))));
    }

    private void addDependency(String notation, boolean transitive) {
        LayerDependency selection = new LayerDependency(notation, transitive);
        getDependencies().add(selection);
        Dependency dependency = project.getDependencies().create(notation);
        if (dependency instanceof ModuleDependency) {
            ((ModuleDependency) dependency).setTransitive(transitive);
        }
        getFiles().from(resolvedArtifactsOf(project.getConfigurations().detachedConfiguration(dependency)));
    }

    private Configuration detachedConfiguration(String notation, boolean transitive) {
        Dependency dependency = project.getDependencies().create(notation);
        if (dependency instanceof ModuleDependency) {
            ((ModuleDependency) dependency).setTransitive(transitive);
        }
        return project.getConfigurations().detachedConfiguration(dependency);
    }

    private Provider<List<File>> resolvedArtifactsOf(Configuration configuration) {
        return configuration.getIncoming()
            .artifactView(view -> {
                view.setLenient(false);
            })
            .getArtifacts()
            .getResolvedArtifacts()
            .map(artifacts -> {
                List<File> files = new ArrayList<>();
                for (var artifact : artifacts) {
                    files.add(artifact.getFile());
                }
                return files;
            });
    }
}
