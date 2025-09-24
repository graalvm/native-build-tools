/*
 * Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
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

package org.graalvm.buildtools.utils;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.Writer;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Stream;

/**
 * Performs scanning of a JAR file and extracts some metadata in the
 * form of a properties file. For now this type only extracts the list
 * of packages from a jar.
 */
public class JarScanner {
    /**
     * Scans a jar and creates a properties file with metadata about the jar contents.
     * @param inputJar the input jar
     * @param outputFile the output file
     * @throws IOException
     */
    public static void scanJar(Path inputJar, Path outputFile) throws IOException {
        try (Writer fileWriter = Files.newBufferedWriter(outputFile); PrintWriter writer = new PrintWriter(fileWriter)) {
            Set<String> packageList = new TreeSet<>();
            try (FileSystem jarFileSystem = FileSystems.newFileSystem(inputJar, (ClassLoader) null)) {
                Path root = jarFileSystem.getPath("/");
                try (Stream<Path> files = Files.walk(root)) {
                    files.forEach(path -> {
                        if (path.toString().endsWith(".class") && !path.toString().contains("META-INF")) {
                            Path relativePath = root.relativize(path);
                            String className = relativePath.toString()
                                .replace('/', '.')
                                .replace('\\', '.')
                                .replaceAll("[.]class$", "");
                            var lastDot = className.lastIndexOf(".");
                            if (lastDot > 0) {
                                var packageName = className.substring(0, lastDot);
                                packageList.add(packageName);
                            }
                        }
                    });
                }
            }
            writer.println("packages=" + String.join(",", packageList));
        } catch (IOException ex) {
            throw new RuntimeException("Unable to write JAR analysis", ex);
        }
    }
}
