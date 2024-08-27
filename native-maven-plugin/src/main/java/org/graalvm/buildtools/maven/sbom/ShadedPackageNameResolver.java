/*
 * Copyright (c) 2024, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.buildtools.maven.sbom;

import org.apache.maven.model.Plugin;
import org.apache.maven.model.PluginExecution;
import org.apache.maven.project.MavenProject;
import org.apache.maven.shared.utils.xml.Xpp3Dom;

import java.io.*;
import java.nio.file.FileSystem;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

class ShadedPackageNameResolver {
    private final MavenProject mavenProject;
    private final Optional<Plugin> optionalShadePlugin;
    /*
     * Set of possible directory paths containing class files in a jar file system. Examples of the keys are:
     * "org/json" and "org/apache/commons/collections/map".
     */
    private final Set<Path> pathToClassFilesDirectories;
    private final Set<Path> visitedPathToClassFileDirectories;
    private final String mainClass;
    private static final String mavenShadePluginName = "maven-shade-plugin";

    ShadedPackageNameResolver(MavenProject mavenProject, String mainClass) {
        this.mavenProject = mavenProject;
        this.optionalShadePlugin = getShadePluginIfUsed(mavenProject);
        this.pathToClassFilesDirectories = new HashSet<>();
        this.visitedPathToClassFileDirectories = new HashSet<>();
        this.mainClass = mainClass;
    }

    /**
     * Even if an artifact is packaged in a fat or shaded jar, its artifact source file path would not point
     * to the fat or shaded jar, but instead to the local repository. This method tries to return a path to
     * the directory containing the class files inside the fat or shaded jar. If the artifact is not part of
     * a fat or shaded jar, {@param jarPath} is returned.
     *
     * NOTE:
     * - To improve chances of successful resolution, it is important to call this method with the main
     * artifact last.
     * - Should not be called with the same artifact more than once.
     * - This only works if the main artifact is shaded, i.e. shaded dependencies are not handled. Currently,
     * we disable any pruning by Native Image of shaded dependencies since we cannot guarantee its correctness.
     *
     * @param jarPath  the jar path as reported by the original Artifact.
     * @param artifact the artifact with its class files inside the {@param jarPath}.
     * @return a new path to the directory containing the class file of this shaded artifact (if it is one).
     */
    Optional<ArtifactAdapter> resolvePackageNamesFromPossiblyShadedJar(Path jarPath, ArtifactAdapter artifact) throws IOException {
        if (!Files.exists(jarPath) || !jarPath.toString().endsWith(".jar")) {
            return Optional.empty();
        }

        /* If the shade plugin is not used, then we are not dealing with a fat or shaded jar. */
        if (optionalShadePlugin.isEmpty()) {
            return handleNonShadedCase(artifact, jarPath);
        }

        /* Recover the path to the shaded jar by querying the shade plugin object. */
        Plugin shadePlugin = optionalShadePlugin.get();
        Optional<Path> optionalShadedJarPath = getShadedJarPath(shadePlugin);
        if (optionalShadedJarPath.isEmpty()) {
            return handleNonShadedCase(artifact, jarPath);
        }

        /* Check if artifact is part of the shading. */
        Path shadedJarPath = optionalShadedJarPath.get();
        FileSystem jarFileSystem = getOrCreateFileSystem(shadedJarPath);
        if (!isPartOfJar(jarFileSystem, artifact)) {
            return handleNonShadedCase(artifact, jarPath);
        }

        /* Attempt to derive which shaded directory contains the class files of this artifact. */
        Optional<Set<Path>> optionalDirectories = resolveDirectoriesWithClasses(jarFileSystem, jarPath, artifact, shadePlugin);
        if (optionalDirectories.isPresent()) {
            Set<Path> containingDirectories = optionalDirectories.get();
            Set<String> packageNames = new HashSet<>();
            for (var directory : containingDirectories) {
                Set<String> newPackageNames = FileWalkerUtility.walkFileSystemAndCollectPackageNames(jarFileSystem, directory)
                        .orElse(Set.of());
                packageNames.addAll(newPackageNames);
            }
            artifact.setPackageNames(packageNames);
            artifact.setJarPath(shadedJarPath.toUri());
            return Optional.of(artifact);
        }
        return handleNonShadedCase(artifact, jarPath);
    }

    static void markShadedDependencies(Set<ArtifactAdapter> dependencies) throws IOException {
        for (ArtifactAdapter dependency : dependencies) {
            if (isShaded(dependency)) {
                dependency.prunable = false;
            }
        }
    }

    private static boolean isShaded(ArtifactAdapter artifact) throws IOException {
        FileSystem jarFileSystem = getOrCreateFileSystem(Paths.get(artifact.jarPath));
        Optional<Path> optionalMetaInfPath = getMetaInfArtifactPath(jarFileSystem, artifact);
        if (optionalMetaInfPath.isEmpty()) {
            return false;
        }

        Path metaInfPath = optionalMetaInfPath.get();
        Path pomPath = jarFileSystem.getPath(metaInfPath.toString(), "pom.xml");
        try (InputStream pomInputStream = Files.newInputStream(pomPath);
             BufferedReader reader = new BufferedReader(new InputStreamReader(pomInputStream))) {
            return reader.lines()
                    .anyMatch(line -> line.contains(String.format("<artifactId>%s</artifactId>", mavenShadePluginName)));
        } catch (IOException e) {
            return false;
        }
    }

    private Optional<ArtifactAdapter> handleNonShadedCase(ArtifactAdapter artifactAdapter, Path jarPath) throws IOException {
        FileSystem fileSystem = getOrCreateFileSystem(jarPath);
        Set<String> packageNames = FileWalkerUtility.walkFileTreeAndCollectPackageNames(fileSystem.getPath("/")).orElse(Set.of());
        artifactAdapter.setPackageNames(packageNames);
        artifactAdapter.setJarPath(jarPath.toUri());
        return Optional.of(artifactAdapter);
    }

    private Optional<Path> getShadedJarPath(Plugin shadePlugin) {
        Path targetDirectory = Paths.get(mavenProject.getBuild().getDirectory());

        Optional<String> outputFile = getParameterFromPlugin(shadePlugin, "outputFile");
        if (outputFile.isPresent()) {
            Path outputPath = Paths.get(outputFile.get());
            if (Files.exists(outputPath)) {
                return Optional.of(outputPath);
            }
        }

        Optional<String> finalName = getParameterFromPlugin(shadePlugin, "finalName");
        if (finalName.isPresent()) {
            Path finalJarPath = targetDirectory.resolve(finalName.get() + ".jar");
            if (Files.exists(finalJarPath)) {
                return Optional.of(finalJarPath);
            }
        }

        Path defaultJarPath = targetDirectory.resolve(mavenProject.getArtifactId() + "-" + mavenProject.getVersion() + ".jar");
        if (Files.exists(defaultJarPath)) {
            return Optional.of(defaultJarPath);
        }

        return Optional.empty();
    }

    private boolean isPartOfJar(FileSystem jarFileSystem, ArtifactAdapter artifact) throws IOException {
        Optional<Path> optionalMetaInfPath = getMetaInfArtifactPath(jarFileSystem, artifact);
        if (optionalMetaInfPath.isEmpty()) {
            return false;
        }
        Path metaInfPath = optionalMetaInfPath.get();

        /* Handle case where there are multiple versions under this artifact. */
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(metaInfPath)) {
            int versionCount = 0;
            for (Path path : stream) {
                if (Files.isDirectory(path)) {
                    versionCount++;
                }
            }

            if (versionCount > 1) {
                Path versionedPath = metaInfPath.resolve(artifact.version);
                return Files.isDirectory(versionedPath);
            } else {
                return true;
            }
        }
    }

    private static Optional<Path> getMetaInfArtifactPath(FileSystem jarFileSystem, ArtifactAdapter artifact) {
        Path path = jarFileSystem.getPath("META-INF", "maven", artifact.groupId, artifact.artifactId);
        if (!Files.isDirectory(path)) {
            return Optional.empty();
        }
        return Optional.of(path);
    }

    private Optional<Set<Path>> resolveDirectoriesWithClasses(FileSystem jarFileSystem, Path jarPath, ArtifactAdapter artifact, Plugin shadePlugin) throws IOException {
        // TODO: this only handles cases where there's no relocation or where ALL files are relocated. Thus, partial or multiple relocations are not supported.
        if (pathToClassFilesDirectories.isEmpty()) {
            Set<Path> potentialDirectories = collectPotentialDirectories(jarFileSystem.getPath("/"));
            if (potentialDirectories.isEmpty()) {
                return Optional.empty();
            }
            pathToClassFilesDirectories.addAll(potentialDirectories);
        }

        if (pathToClassFilesDirectories.size() == 1) {
            Path onlyPossiblePath = pathToClassFilesDirectories.stream().findFirst().get();
            visitedPathToClassFileDirectories.add(onlyPossiblePath);
            return Optional.of(Set.of(onlyPossiblePath));
        }

        /* If all but one path has been visited, then that path must be the correct one for this artifact. */
        Set<Path> difference = notVisitedPaths();
        if (difference.size() == 1) {
            Path onlyPossiblePath = difference.stream().findFirst().get();
            visitedPathToClassFileDirectories.add(onlyPossiblePath);
            return Optional.of(Set.of(onlyPossiblePath));
        }

        /*
         * If relocations are not used then matching directly with the GAV coordinates should work.
         */
        if (!areRelocationsUsed(shadePlugin)) {
            Optional<Path> resolvedPath = tryResolveUsingGAVCoordinates(jarFileSystem.getPath("/"), artifact);
            if (resolvedPath.isPresent()) return Optional.of(Set.of(resolvedPath.get()));
        }

        boolean isMainArtifact = artifact.equals(mavenProject.getArtifact());
        if (isMainArtifact) {
            Optional<Path> resolvedPath = findTopClassDirectory(jarFileSystem.getPath("/"), mainClass);
            if (resolvedPath.isPresent()) {
                return Optional.of(Set.of(resolvedPath.get()));
            }

            resolvedPath = tryResolveUsingGAVCoordinates(jarFileSystem.getPath("/"), artifact);
            return resolvedPath.map(Set::of);
        }

        /*
         * To derive the directory path when relocation is used we apply a matching strategy on the class names.
         * We collect the class file names of the original jar and searches the directories in the shaded/fat jar
         * and define a match to be when all class file names match the class files in the original jar.
         */
        FileSystem fileSystemOriginalJar = getOrCreateFileSystem(jarPath);
        Set<String> originalClassFiles = new HashSet<>();
        FileWalkerUtility.walkFileTreeWithExtensions(fileSystemOriginalJar.getPath("/"), Set.of(".class", ".java"), file -> {
            Path fileName = file.getFileName();
            if (fileName != null) {
                originalClassFiles.add(fileName.toString());
            }
        });
        Optional<Set<Path>> optionalPaths = resolveDirectoriesFromClassNameMatching(artifact, originalClassFiles);
        if (optionalPaths.isPresent()) {
            Set<Path> paths = optionalPaths.get();
            visitedPathToClassFileDirectories.addAll(paths);
            return Optional.of(paths);
        }
        return Optional.empty();
    }


    /**
     * Resolves the top directory containing class files by traversing backwards from the main class location.
     *
     * @param qualifiedName the qualified name of the class to start the search from.
     * @param rootPath      the root of the file system.
     * @return a path of the top directory containing class files.
     */
    private Optional<Path> findTopClassDirectory(Path rootPath, String qualifiedName) throws IOException {
        String mainClassPath = qualifiedName.replace('.', File.separatorChar) + ".class";
        Path classFilePath = rootPath.resolve(mainClassPath);
        Path currentPath = classFilePath.getParent();
        while (currentPath != null && !Files.isSameFile(currentPath, rootPath)) {
            if (FileWalkerUtility.containsClassFiles(currentPath)) {
                return Optional.of(currentPath);
            }
            currentPath = currentPath.getParent();
        }
        return Optional.empty();
    }

    /**
     * Helper method to resolve GAV coordinates and check if they exist in the class files directory.
     * Tries both with and without using the artifactId.
     */
    private Optional<Path> tryResolveUsingGAVCoordinates(Path rootPath, ArtifactAdapter artifact) throws IOException {
        Optional<Path> resolvedPath = resolveGAVCoordinates(rootPath, artifact, true);
        if (resolvedPath.isPresent()) return resolvedPath;
        return resolveGAVCoordinates(rootPath, artifact, false);
    }

    /**
     * Helper method to resolve GAV coordinates for a specific configuration (with or without artifactId).
     */
    private Optional<Path> resolveGAVCoordinates(Path rootPath, ArtifactAdapter artifact, boolean useArtifactId) throws IOException {
        Path gavPath = pathFromGAVCoordinates(rootPath, artifact, useArtifactId);
        var pathsAsStrings = pathToClassFilesDirectories.stream()
                .map(Path::toString)
                .collect(Collectors.toSet());
        if (pathsAsStrings.contains(gavPath.toString())) {
            visitedPathToClassFileDirectories.add(gavPath);
            return Optional.of(gavPath);
        }
        return Optional.empty();
    }

    private Set<Path> notVisitedPaths() {
        Set<Path> difference = new HashSet<>(pathToClassFilesDirectories);
        difference.removeAll(visitedPathToClassFileDirectories);
        return difference;
    }

    private Optional<Set<Path>> resolveDirectoriesFromClassNameMatching(ArtifactAdapter artifact, Set<String> originalClassFiles) throws IOException {
        Set<Path> matchingDirectories = new HashSet<>();

        for (Path potentialDirectory : pathToClassFilesDirectories) {
            AtomicBoolean successfulMatching = new AtomicBoolean(true);

            try (DirectoryStream<Path> stream = Files.newDirectoryStream(potentialDirectory)) {
                for (Path file : stream) {
                    if (Files.isRegularFile(file) && file.toString().endsWith(".class")) {
                        String fileName = file.getFileName().toString();
                        if (!originalClassFiles.contains(fileName)) {
                            successfulMatching.set(false);
                            break;
                        }
                    }
                }
            }

            if (successfulMatching.get()) {
                matchingDirectories.add(potentialDirectory);
            }
        }

        if (matchingDirectories.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(matchingDirectories);
    }

    private Set<Path> collectPotentialDirectories(Path rootPath) throws IOException {
        Set<Path> potentialDirectories = new HashSet<>();
        Files.walkFileTree(rootPath, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                if (file.toString().endsWith(".class")) {
                    Path classDirectory = file.getParent();
                    potentialDirectories.add(classDirectory);
                    return FileVisitResult.CONTINUE;
                }
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) {
                Path fileName = dir.getFileName();
                if (fileName != null && fileName.toString().equals("META-INF")) {
                    return FileVisitResult.SKIP_SUBTREE;
                }
                return FileVisitResult.CONTINUE;
            }
        });
        return potentialDirectories;
    }

    private Path pathFromGAVCoordinates(Path basePath, ArtifactAdapter artifact, boolean useArtifactId) throws IOException {
        FileSystem fileSystem = basePath.getFileSystem();
        Path expectedPath = basePath.resolve(fileSystem.getPath(
                artifact.groupId.replace('.', '/')
        ));
        if (useArtifactId) {
            expectedPath = expectedPath.resolve(artifact.artifactId.replace('.', '/'));
        }

        /* Handle case where there are multiple versions. */
        if (Files.isDirectory(expectedPath)) {
            try (DirectoryStream<Path> stream = Files.newDirectoryStream(expectedPath)) {
                boolean hasMultipleDirectories = StreamSupport.stream(stream.spliterator(), false)
                        .filter(Files::isDirectory)
                        .count() > 1;

                /* If multiple directories exist, append the version information. */
                if (hasMultipleDirectories) {
                    expectedPath = expectedPath.resolve(artifact.version);
                }
            }
        }

        return expectedPath;
    }


    private static Optional<String> getParameterFromPlugin(Plugin plugin, String parameter) {
        Xpp3Dom configuration = (Xpp3Dom) plugin.getConfiguration();
        if (configuration != null && parameter != null && !parameter.isEmpty()) {
            Xpp3Dom parameterNode = configuration.getChild(parameter);
            if (parameterNode != null) {
                return Optional.of(parameterNode.getValue());
            }
        }
        return Optional.empty();
    }

    private static boolean areRelocationsUsed(Plugin shadePlugin) {
        List<PluginExecution> executions = shadePlugin.getExecutions();
        if (executions == null || executions.isEmpty()) {
            return false;
        }

        for (PluginExecution execution : executions) {
            org.codehaus.plexus.util.xml.Xpp3Dom configuration = (org.codehaus.plexus.util.xml.Xpp3Dom) execution.getConfiguration();
            if (configuration != null) {
                org.codehaus.plexus.util.xml.Xpp3Dom relocationsNode = configuration.getChild("relocations");
                if (relocationsNode != null && relocationsNode.getChildCount() > 0) {
                    return true;
                }
            }
        }

        return false;
    }

    private static Optional<Plugin> getShadePluginIfUsed(MavenProject mavenProject) {
        return mavenProject.getBuildPlugins().stream()
                .filter(v -> mavenShadePluginName.equals(v.getArtifactId()))
                .findFirst();
    }

    private static FileSystem getOrCreateFileSystem(Path jarPath) throws IOException {
        try {
            return FileSystems.newFileSystem(jarPath, null);
        } catch (FileSystemAlreadyExistsException e) {
            /* If the file system already exists, return the existing file system. */
            return FileSystems.getFileSystem(jarPath.toUri());
        }
    }
}
