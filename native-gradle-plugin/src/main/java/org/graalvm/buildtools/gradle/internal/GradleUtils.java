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

package org.graalvm.buildtools.gradle.internal;

import org.gradle.api.Project;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.artifacts.component.ProjectComponentIdentifier;
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.file.FileCollection;
import org.gradle.api.plugins.JavaPlugin;
import org.gradle.api.plugins.JavaPluginExtension;
import org.gradle.api.provider.Provider;
import org.gradle.api.provider.ProviderFactory;
import org.gradle.api.tasks.SourceSet;
import org.gradle.api.tasks.SourceSetContainer;
import org.gradle.util.GradleVersion;

/**
 * Utility class containing various gradle related methods.
 */
@SuppressWarnings("unused")
public class GradleUtils {
    private static final GradleVersion MINIMAL_GRADLE_VERSION = GradleVersion.version("7.4");

    public static SourceSet findSourceSet(Project project, String sourceSetName) {
        SourceSetContainer sourceSetContainer = getJavaPluginConvention(project).getSourceSets();
        return sourceSetContainer.findByName(sourceSetName);
    }

    public static JavaPluginExtension getJavaPluginConvention(Project project) {
        return project.getExtensions().getByType(JavaPluginExtension.class);
    }


    public static Configuration findConfiguration(Project project, String name) {
        return project.getConfigurations().getByName(name);
    }

    public static FileCollection transitiveProjectArtifacts(Project project, String name) {
        ConfigurableFileCollection transitiveProjectArtifacts = project.getObjects().fileCollection();
        transitiveProjectArtifacts.from(findMainArtifacts(project));
        transitiveProjectArtifacts.from(findConfiguration(project, name)
            .getIncoming()
            .artifactView(view -> view.componentFilter(ProjectComponentIdentifier.class::isInstance))
            .getFiles());
        return transitiveProjectArtifacts;
    }

    public static FileCollection findMainArtifacts(Project project) {
        return findConfiguration(project, JavaPlugin.RUNTIME_ELEMENTS_CONFIGURATION_NAME)
            .getOutgoing()
            .getArtifacts()
            .getFiles();
    }

    public static Provider<Integer> intProperty(ProviderFactory providers, String propertyName, int defaultValue) {
        return stringProperty(providers, propertyName)
            .map(Integer::parseInt)
            .orElse(defaultValue);
    }

    private static Provider<String> stringProperty(ProviderFactory providers, String propertyName) {
        return providers.systemProperty(propertyName)
            .orElse(providers.gradleProperty(propertyName))
            .orElse(providers.environmentVariable(propertyName.replace('.', '_').toUpperCase()));
    }
}
