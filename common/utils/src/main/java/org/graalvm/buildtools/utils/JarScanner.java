/*
 * Copyright 2003-2021 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
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
            try (FileSystem jarFileSystem = FileSystems.newFileSystem(inputJar, null)) {
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
