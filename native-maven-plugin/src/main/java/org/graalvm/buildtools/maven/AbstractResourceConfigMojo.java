/*
 * Copyright (c) 2020, 2021, Oracle and/or its affiliates. All rights reserved.
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

package org.graalvm.buildtools.maven;

import groovy.json.JsonGenerator;
import groovy.json.JsonOutput;
import org.apache.maven.artifact.Artifact;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.MavenProject;
import org.graalvm.buildtools.model.resources.ClassPathEntryAnalyzer;
import org.graalvm.buildtools.model.resources.NamedValue;
import org.graalvm.buildtools.model.resources.PatternValue;
import org.graalvm.buildtools.model.resources.ResourceFilter;
import org.graalvm.buildtools.model.resources.ResourcesConfigModel;
import org.graalvm.buildtools.model.resources.ResourcesModel;
import org.graalvm.buildtools.utils.SharedConstants;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static java.util.stream.Collectors.toCollection;
import static org.graalvm.buildtools.model.resources.Helper.asNamedValues;
import static org.graalvm.buildtools.model.resources.Helper.asPatternValues;

public abstract class AbstractResourceConfigMojo extends AbstractMojo {

    @Parameter(defaultValue = "${project}", readonly = true)
    protected MavenProject mavenProject;

    @Parameter(defaultValue = "${session}", readonly = true, required = true)
    protected MavenSession session;

    // Below are the parameters that the user actually cares about

    @Parameter(defaultValue = "${project.build.directory}/native/generated", property = "resources.outputDir", required = true)
    private File outputDirectory;

    abstract String getConfigurationKind();

    @Parameter(property = "resources.bundles")
    private List<String> resourceBundles;

    @Parameter(property = "resources.includedPatterns")
    private List<String> resourceIncludedPatterns;

    @Parameter(property = "resources.excludedPatterns")
    private List<String> resourceExcludedPatterns;

    @Parameter(property = "resources.inference.enabled", defaultValue = "false")
    private boolean isInferenceEnabled;

    @Parameter(property = "resources.inference.restrictToModuleDependencies", defaultValue = "true")
    private boolean isInferenceRestrictedToModuleDependencies;

    @Parameter(property = "resources.inference.inferenceExclusionPatterns")
    private List<String> inferenceExclusionPatterns = new ArrayList<>(SharedConstants.DEFAULT_EXCLUDES_FOR_RESOURCE_INFERENCE);

    @Override
    public void execute() throws MojoExecutionException {
        Set<PatternValue> includes = asPatternValues(resourceIncludedPatterns);
        Set<PatternValue> excludes = asPatternValues(resourceExcludedPatterns);
        Set<NamedValue> bundles = asNamedValues(resourceBundles);
        if (isInferenceEnabled) {
            try {
                inferResourcesFromClasspath(includes);
            } catch (IOException e) {
                throw new MojoExecutionException("Unable to infer resources", e);
            }
        }
        try {
            ResourcesConfigModel model = new ResourcesConfigModel(
                    new ResourcesModel(
                            includes,
                            excludes
                    ), bundles
            );
            serializeModel(model, new File(outputDirectory, getConfigurationKind() + "/resource-config.json"));
        } catch (IOException e) {
            throw new MojoExecutionException("Unable to write resource-config.json file", e);
        }
    }

    private Set<File> findAllProjectArtifacts() {
        Set<File> all = new LinkedHashSet<>();
        all.add(mavenProject.getArtifact().getFile());
        all.addAll(transitiveProjectsArtifacts());
        Collection<? extends File> extraProjectArtifacts = getExtraProjectArtifacts();
        getLog().info("extraProjectArtifacts = " + extraProjectArtifacts);
        all.addAll(extraProjectArtifacts);
        return all;
    }

    protected Collection<? extends File> getExtraProjectArtifacts() {
        return Collections.emptySet();
    }

    private Set<File> findAllExternalArtifacts() {
        Set<File> all = new LinkedHashSet<>(allTransitiveArtifacts());
        all.removeAll(transitiveProjectsArtifacts());
        return all;
    }

    private Set<File> allTransitiveArtifacts() {
        return mavenProject.getArtifacts().stream()
                .map(Artifact::getFile)
                .filter(Objects::nonNull)
                .collect(toCollection(LinkedHashSet::new));
    }

    private Set<File> transitiveProjectsArtifacts() {
        return session.getProjectDependencyGraph()
                .getUpstreamProjects(mavenProject, true)
                .stream()
                .map(p -> p.getArtifact().getFile())
                .filter(Objects::nonNull)
                .collect(toCollection(LinkedHashSet::new));
    }

    private static <T> Set<T> safeAsSet(Collection<T> elements) {
        if (elements == null) {
            return Collections.emptySet();
        }
        return new LinkedHashSet<>(elements);
    }

    private void inferResourcesFromClasspath(Set<PatternValue> output) throws IOException {
        ResourceFilter filter = new ResourceFilter(safeAsSet(inferenceExclusionPatterns));
        Set<String> inferredResources = new LinkedHashSet<>();
        Set<File> artifacts = findAllProjectArtifacts();
        if (!isInferenceRestrictedToModuleDependencies) {
            artifacts.addAll(findAllExternalArtifacts());
        }
        for (File file : artifacts) {
            inferResourcesFromClasspathEntry(filter, inferredResources, file);
        }
        if (!inferredResources.isEmpty()) {
            output.addAll(
                    inferredResources.stream()
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
    private void inferResourcesFromClasspathEntry(ResourceFilter filter, Set<String> inferredResources, File file) throws IOException {
        ClassPathEntryAnalyzer analyzer = ClassPathEntryAnalyzer.of(file, filter::shouldIncludeResource);
        List<String> resources = analyzer.getResources();
        getLog().info(String.format("Inferred resources for %s are %s", file, resources));
        inferredResources.addAll(resources);
    }

    private void serializeModel(ResourcesConfigModel model, File outputFile) throws IOException {
        JsonGenerator builder = new JsonGenerator.Options()
                .build();
        String json = builder.toJson(model);
        String pretty = JsonOutput.prettyPrint(json);
        File outputDir = outputFile.getParentFile();
        if (outputDir.isDirectory() || outputDir.mkdirs()) {
            try (OutputStreamWriter out = new OutputStreamWriter(new FileOutputStream(outputFile), StandardCharsets.UTF_8)) {
                out.append(pretty);
            }
        }
        getLog().info("Resources configuration written into " + outputFile);
    }
}
