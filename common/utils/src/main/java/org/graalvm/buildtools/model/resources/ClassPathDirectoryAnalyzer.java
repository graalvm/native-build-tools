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
package org.graalvm.buildtools.model.resources;

import org.graalvm.buildtools.utils.FileUtils;

import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Function;

class ClassPathDirectoryAnalyzer extends ClassPathEntryAnalyzer {
    private final Path root;
    private final boolean ignoreExistingResourcesConfig;

    ClassPathDirectoryAnalyzer(Path root, Function<String, Boolean> resourceFilter, boolean ignoreExistingResourcesConfig) {
        super(resourceFilter);
        this.root = root;
        this.ignoreExistingResourcesConfig = ignoreExistingResourcesConfig;
    }

    protected List<String> initialize() throws IOException {
        if (Files.exists(root)) {
            DirectoryVisitor visitor = new DirectoryVisitor();
            Files.walkFileTree(root, visitor);
            return visitor.hasNativeImageResourceFile && !ignoreExistingResourcesConfig ? Collections.emptyList() : visitor.resources;
        } else {
            return Collections.emptyList();
        }
    }

    private class DirectoryVisitor extends SimpleFileVisitor<Path> {
        List<String> resources = new ArrayList<>();
        boolean hasNativeImageResourceFile;
        boolean inNativeImageDir;

        @Override
        public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) {
            String relativePath = relativePathOf(dir);
            if (Helper.META_INF_NATIVE_IMAGE.equals(relativePath)) {
                inNativeImageDir = true;
            }
            return FileVisitResult.CONTINUE;
        }

        private String relativePathOf(Path path) {
            return FileUtils.normalizePathSeparators(root.relativize(path).toString());
        }

        @Override
        public FileVisitResult postVisitDirectory(Path dir, IOException exc) throws IOException {
            String relativePath = relativePathOf(dir);
            if (Helper.META_INF_NATIVE_IMAGE.equals(relativePath)) {
                inNativeImageDir = false;
            }
            return FileVisitResult.CONTINUE;
        }

        @Override
        public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
            if (!ignoreExistingResourcesConfig && inNativeImageDir && relativePathOf(file).endsWith("resource-config.json")) {
                hasNativeImageResourceFile = true;
                return FileVisitResult.TERMINATE;
            }
            maybeAddResource(root.relativize(file).toString(), resources);
            return FileVisitResult.CONTINUE;
        }
    }
}
