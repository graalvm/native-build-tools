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
package org.graalvm.buildtools.gradle.tasks;

import org.graalvm.buildtools.gradle.dsl.NativeResourcesOptions;
import org.graalvm.buildtools.gradle.dsl.ResourceDetectionOptions;
import org.graalvm.buildtools.gradle.internal.GraalVMLogger;
import org.graalvm.buildtools.model.resources.ClassPathEntryAnalyzer;
import org.graalvm.buildtools.model.resources.Helper;
import org.graalvm.buildtools.model.resources.NamedValue;
import org.graalvm.buildtools.model.resources.PatternValue;
import org.graalvm.buildtools.model.resources.ResourceFilter;
import org.graalvm.buildtools.model.resources.ResourcesConfigModel;
import org.graalvm.buildtools.model.resources.ResourcesModel;
import org.graalvm.buildtools.model.resources.ResourcesConfigModelSerializer;
import org.gradle.api.DefaultTask;
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.CacheableTask;
import org.gradle.api.tasks.Classpath;
import org.gradle.api.tasks.InputFiles;
import org.gradle.api.tasks.Nested;
import org.gradle.api.tasks.OutputFile;
import org.gradle.api.tasks.TaskAction;

import java.io.File;
import java.io.IOException;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@CacheableTask
public abstract class GenerateResourcesConfigFile extends DefaultTask {

    @Nested
    public abstract Property<NativeResourcesOptions> getOptions();

    @InputFiles
    @Classpath
    public abstract ConfigurableFileCollection getClasspath();

    @InputFiles
    @Classpath
    public abstract ConfigurableFileCollection getTransitiveProjectArtifacts();

    @OutputFile
    public abstract RegularFileProperty getOutputFile();

    @TaskAction
    public void generate() throws IOException {
        NativeResourcesOptions nativeResourcesOptions = getOptions().get();
        ResourceDetectionOptions detectionOptions = nativeResourcesOptions.getDetectionOptions();
        Set<NamedValue> bundles = Helper.asNamedValues(nativeResourcesOptions.getBundles().get());
        Set<PatternValue> includes = Helper.asPatternValues(nativeResourcesOptions.getIncludedPatterns().get());
        Set<PatternValue> excludes = Helper.asPatternValues(nativeResourcesOptions.getExcludedPatterns().get());
        if (detectionOptions.getEnabled().get()) {
            detectResourcesFromClasspath(detectionOptions, includes);
        }
        ResourcesConfigModel model = new ResourcesConfigModel(new ResourcesModel(includes, excludes), bundles);
        serializeModel(model, getOutputFile().getAsFile().get());
    }

    private void detectResourcesFromClasspath(ResourceDetectionOptions detectionOptions,
                                              Set<PatternValue> output) throws IOException {
        Set<File> classpath = getClasspath().getFiles();
        ResourceFilter filter = new ResourceFilter(detectionOptions.getDetectionExclusionPatterns().get());
        Set<String> detectedResources = new LinkedHashSet<>();
        boolean projectLocalOnly = detectionOptions.getRestrictToProjectDependencies().get();
        Set<File> projectsArtifacts = getTransitiveProjectArtifacts().getFiles();
        for (File file : classpath) {
            if (projectLocalOnly && file.getName().endsWith(".jar") && !projectsArtifacts.contains(file)) {
                continue;
            }
            // todo: ideally we should try to find a way for Gradle to cache the analysis per jar
            // which should be doable via artifact transforms instead
            detectResourcesFromClasspathEntry(filter, detectedResources, file, detectionOptions.getIgnoreExistingResourcesConfigFile().get());
        }
        if (!detectedResources.isEmpty()) {
            output.addAll(
                    detectedResources.stream()
                            .map(Pattern::quote)
                            .map(PatternValue::new)
                            .collect(Collectors.toList())
            );
        }
    }

    /**
     * Infers the resources to add to resource-config.json for a single classpath entry.
     * If it's a directory, we will walk the directory and collect resources found in
     * the directory. If it's a jar we do the same but with jar entries instead.
     */
    private void detectResourcesFromClasspathEntry(ResourceFilter filter,
                                                   Set<String> detectedResources,
                                                   File file,
                                                   boolean ignoreExistingResourcesConfig) throws IOException {
        ClassPathEntryAnalyzer analyzer = ClassPathEntryAnalyzer.of(file, filter::shouldIncludeResource, ignoreExistingResourcesConfig);
        List<String> resources = analyzer.getResources();
        GraalVMLogger.of(getLogger()).log("Detected resources for {} are {}", file, resources);
        detectedResources.addAll(resources);
    }

    private void serializeModel(ResourcesConfigModel model, File outputFile) throws IOException {
        ResourcesConfigModelSerializer.serialize(model, outputFile);
        GraalVMLogger.of(getLogger()).lifecycle("Resources configuration written into " + outputFile);
    }

}
