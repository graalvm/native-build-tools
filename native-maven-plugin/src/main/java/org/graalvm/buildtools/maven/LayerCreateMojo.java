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
package org.graalvm.buildtools.maven;

import org.apache.maven.artifact.Artifact;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.Component;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.plugins.annotations.ResolutionScope;
import org.apache.maven.project.MavenProjectHelper;
import org.graalvm.buildtools.maven.config.LayerConfiguration;
import org.graalvm.buildtools.maven.config.LayerDependencyConfiguration;
import org.graalvm.buildtools.model.resources.NativeImageFlags;
import org.graalvm.buildtools.utils.ArtifactSelection;
import org.graalvm.buildtools.utils.NativeImageLayerArguments;
import org.graalvm.buildtools.utils.NativeImageLayerRuntime;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Creates and attaches one Maven-resolvable Native Image layer. §FS-goal-surface.6.
 */
@Mojo(name = "layer-create", threadSafe = true,
        requiresDependencyResolution = ResolutionScope.RUNTIME,
        requiresDependencyCollection = ResolutionScope.RUNTIME)
public class LayerCreateMojo extends AbstractNativeImageMojo {
    @Parameter(required = false)
    private LayerConfiguration layer;

    @Component
    private MavenProjectHelper projectHelper;

    @Override
    protected List<String> getDependencyScopes() {
        return Arrays.asList(Artifact.SCOPE_COMPILE, Artifact.SCOPE_RUNTIME, Artifact.SCOPE_COMPILE_PLUS_RUNTIME);
    }

    @Override
    protected void populateClasspath() {
        // Layer creation always sets an explicit selection, including an intentionally empty modules-only classpath. §FS-native-builds.3.
        for (String entry : classpath) {
            imageClasspath.add(Path.of(entry).toAbsolutePath());
        }
        imageClasspath.removeIf(entry -> !entry.toFile().exists());
    }

    @Override
    protected void executeInternal() throws MojoExecutionException {
        if (layer == null || layer.getName() == null || layer.getName().isBlank()) {
            throw new MojoExecutionException("The layer-create goal requires a non-blank layer name");
        }
        try {
            NativeImageLayerArguments.validateLayerName(layer.getName());
        } catch (IllegalArgumentException ex) {
            throw new MojoExecutionException(ex.getMessage(), ex);
        }
        String includeDependencies = layer.getIncludeDependencies();
        if (includeDependencies != null && !includeDependencies.isBlank()
                && !"all".equalsIgnoreCase(includeDependencies)) {
            throw new MojoExecutionException(
                "Layer includeDependencies accepts only 'all', but was '" + includeDependencies + "'");
        }
        outputDirectory = new File(project.getBuild().getDirectory(), "native/layers/" + layer.getName());
        // Native Image requires the shared library inside a layer bundle to use a library name;
        // the public layer and attached artifact remain <name>.nil. §FS-goal-surface.6.
        imageName = "lib" + layer.getName();
        mainClass = null;
        List<Path> selectedPaths = resolveSelectedPaths();
        classpath = selectedPaths.stream().map(Path::toString).toList();
        if (layer.isAll() || !layer.getPackages().isEmpty()) {
            List<Path> invocationClasspath = new ArrayList<>(runtimeArtifactPaths());
            if (!layer.getPackages().isEmpty() && defaultClassesDirectory != null) {
                invocationClasspath.add(defaultClassesDirectory.toPath().toAbsolutePath());
            }
            classpath = invocationClasspath.stream().map(Path::toString).toList();
        }
        ArtifactSelection selection = new ArtifactSelection(
            layer.isAll(),
            layer.getModules(),
            layer.getPackages(),
            layer.isAll() ? List.of() : selectedPaths
        );
        if (selection.isEmpty()) {
            throw new MojoExecutionException(
                "Layer '" + layer.getName() + "' has no contents; configure all, modules, packages, paths, or dependencies.");
        }
        if (buildArgs == null) {
            buildArgs = new ArrayList<>();
        }
        buildArgs.add(0, NativeImageLayerArguments.renderLayerCreate(layer.getName(), selection));
        buildArgs.add(0, NativeImageFlags.UNLOCK_EXPERIMENTAL_VMOPTIONS);
        buildImage();

        File layerFile = new File(outputDirectory, layer.getName() + ".nil");
        if (!dryRun && !layerFile.isFile()) {
            throw new MojoExecutionException("Native Image did not produce the expected layer " + layerFile);
        }
        if (!dryRun) {
            projectHelper.attachArtifact(project, "nil", null, layerFile);
            attachRuntimeArchive();
        }
    }

    private void attachRuntimeArchive() throws MojoExecutionException {
        Path layerOutput = outputDirectory.toPath();
        try {
            List<Path> runtimeFiles = NativeImageLayerRuntime.discoverRuntimeFiles(layerOutput);
            if (!NativeImageLayerRuntime.containsPrimaryRuntimeLibrary(runtimeFiles, imageName)) {
                throw new MojoExecutionException("Native Image reported a successful layer build but did not produce "
                    + "the expected primary runtime library for '" + imageName + "' in " + layerOutput);
            }
            String classifier = NativeImageLayerRuntime.classifier(buildArgs);
            Path archive = layerOutput.resolve(layer.getName() + "-" + classifier + ".zip");
            NativeImageLayerRuntime.createArchive(layerOutput, runtimeFiles, archive);
            projectHelper.attachArtifact(project, NativeImageLayerRuntime.ARCHIVE_TYPE, classifier, archive.toFile());
        } catch (IOException ex) {
            throw new MojoExecutionException("Unable to package runtime files for layer '" + layer.getName() + "'", ex);
        }
    }

    List<Path> resolveSelectedPaths() throws MojoExecutionException {
        Set<Path> paths = new LinkedHashSet<>();
        for (File path : layer.getPaths()) {
            paths.add(path.toPath().toAbsolutePath());
        }
        for (LayerDependencyConfiguration dependency : layer.getDependencies()) {
            String selector = dependency.getArtifact();
            if (selector == null || selector.isBlank()) {
                throw new MojoExecutionException("Layer dependency coordinates must not be blank");
            }
            String[] coordinate = AbstractNativeImageMojo.parseLayerCoordinate(selector);
            boolean matched = false;
            for (Artifact artifact : project.getArtifacts()) {
                if (matches(artifact, coordinate, dependency.isTransitive())) {
                    if (artifact.getFile() != null) {
                        paths.add(artifact.getFile().toPath().toAbsolutePath());
                    }
                    matched = true;
                }
            }
            if (!matched) {
                throw new MojoExecutionException("Layer dependency '" + selector + "' was not found in the resolved project dependencies");
            }
        }
        return new ArrayList<>(paths);
    }

    private List<Path> runtimeArtifactPaths() {
        return project.getArtifacts().stream()
            .filter(artifact -> getDependencyScopes().contains(artifact.getScope()))
            .filter(artifact -> !"nil".equals(artifact.getType()))
            .filter(artifact -> artifact.getFile() != null)
            .map(artifact -> artifact.getFile().toPath().toAbsolutePath())
            .toList();
    }

    static boolean matches(Artifact artifact, String[] parts, boolean transitive) {
        boolean exact = artifact.getGroupId().equals(parts[0])
            && artifact.getArtifactId().equals(parts[1])
            && (parts.length == 2 || artifact.getVersion().equals(parts[2]));
        if (exact || !transitive || artifact.getDependencyTrail() == null) {
            return exact;
        }
        // A version-qualified root must match exactly before its transitive trail is selected. §FS-config-model.7.
        return artifact.getDependencyTrail().stream().anyMatch(entry -> matchesTrailCoordinate(entry, parts));
    }

    private static boolean matchesTrailCoordinate(String entry, String[] parts) {
        String[] trailParts = entry.split(":", -1);
        return trailParts.length >= 4
            && trailParts[0].equals(parts[0])
            && trailParts[1].equals(parts[1])
            && (parts.length == 2 || trailParts[trailParts.length - 1].equals(parts[2]));
    }
}
