/*
 * Copyright (c) 2020, 2022, Oracle and/or its affiliates. All rights reserved.
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

import java.io.File;
import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.FileSystem;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;

/**
 * Utility class for walking file trees and collecting package names from Java source and class files.
 */
final class FileWalkerUtility {
    static Optional<Set<String>> collectPackageNamesFromDirectory(Path path) throws IOException {
        return walkFileTreeAndCollectPackageNames(path, path);
    }

    static Optional<Set<String>> collectPackageNamesFromFileSystem(FileSystem fileSystem, Path startPath) throws IOException {
        return walkFileTreeAndCollectPackageNames(fileSystem.getPath(startPath.toString()), fileSystem.getPath("/"));
    }

    private static Optional<Set<String>> walkFileTreeAndCollectPackageNames(Path pathToSearchIn, Path basePathForPackageNameResolution) throws IOException {
        Set<String> packageNames = new HashSet<>();
        FileWalkerUtility.walkFileTreeWithExtensions(pathToSearchIn, Set.of(".java", ".class"), file -> {
            Optional<String> optionalPackageName = extractPackageName(file, basePathForPackageNameResolution);
            optionalPackageName.ifPresent(packageNames::add);
        });
        return Optional.of(packageNames);
    }

    static void walkFileTreeWithExtensions(Path startPath, Set<String> fileExtensions, Consumer<Path> fileHandler) throws IOException {
        Files.walkFileTree(startPath, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                for (String extension : fileExtensions) {
                    if (file.toString().endsWith(extension)) {
                        fileHandler.accept(file);
                        break;
                    }
                }
                return FileVisitResult.CONTINUE;
            }
        });
    }

    private static Optional<String> extractPackageName(Path filePath, Path basePath) {
        String relativePath = basePath.relativize(filePath).toString();
        int lastSeparatorIndex = relativePath.lastIndexOf(File.separator);
        if (lastSeparatorIndex == -1) {
            return Optional.empty();
        }
        String packageName = relativePath.substring(0, lastSeparatorIndex);
        packageName = packageName.replace(File.separatorChar, '.');
        return Optional.of(packageName);
    }

    static boolean containsClassFiles(Path directory) {
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(directory, "*.class")) {
            return stream.iterator().hasNext();
        } catch (Exception e) {
            return false;
        }
    }
}
