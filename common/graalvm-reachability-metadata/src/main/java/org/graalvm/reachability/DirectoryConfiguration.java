/*
 * Copyright (c) 2020, 2022 Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.reachability;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Collection;

public class DirectoryConfiguration {

    private static final String PROPERTIES = "reachability-metadata.properties";

    private final String groupId;

    private final String artifactId;

    private final String version;

    private final Path directory;

    private final boolean override;

    public DirectoryConfiguration(String groupId, String artifactId, String version, Path directory, boolean override) {
        this.groupId = groupId;
        this.artifactId = artifactId;
        this.version = version;
        this.directory = directory;
        this.override = override;
    }

    public Path getDirectory() {
        return directory;
    }

    public boolean isOverride() {
        return override;
    }

    public static void copy(Collection<DirectoryConfiguration> configurations, Path destinationDirectory) throws IOException {
        Path nativeImageDestination = destinationDirectory.resolve("META-INF").resolve("native-image");
        for (DirectoryConfiguration configuration : configurations) {
            Path target = nativeImageDestination
                    .resolve(configuration.groupId)
                    .resolve(configuration.artifactId)
                    .resolve((configuration.version != null) ? configuration.version :
                            configuration.getDirectory().getFileName().toString());
            copyFileTree(configuration.directory, target);
            writeConfigurationProperties(configuration, target);
        }
    }

    private static void copyFileTree(Path source, Path target) throws IOException {
        if (Files.notExists(source)) {
            return;
        }
        Files.walkFileTree(source, new SimpleFileVisitor<Path>() {

            @Override
            public FileVisitResult preVisitDirectory(Path directory, BasicFileAttributes attrs) throws IOException {
                Files.createDirectories(target.resolve(source.relativize(directory)));
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                if (!"index.json".equalsIgnoreCase(file.getFileName().toString())) {
                    Files.copy(file, target.resolve(source.relativize(file)), StandardCopyOption.REPLACE_EXISTING);
                }
                return FileVisitResult.CONTINUE;
            }
        });
    }

    private static void writeConfigurationProperties(DirectoryConfiguration configuration, Path target)
            throws IOException {
        if (Files.notExists(target)) {
            Files.createDirectories(target);
        }
        StringBuilder content = new StringBuilder();
        if (configuration.isOverride()) {
            content.append("override=true\n");
        }
        if (content.length() > 0) {
            Files.write(target.resolve(PROPERTIES), content.toString().getBytes(StandardCharsets.ISO_8859_1));
        }
    }
}


