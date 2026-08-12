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
package org.graalvm.buildtools.utils;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.zip.ZipFile;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NativeImageLayerRuntimeTest {
    @TempDir
    Path temporaryDirectory;

    @Test
    void classifierIncludesOsArchitectureAndNonDefaultLibc() {
        String classifier = NativeImageLayerRuntime.classifier(List.of());
        assertTrue(classifier.startsWith("layer-runtime-"));
        assertFalse(classifier.endsWith("-glibc"));
        assertEquals(classifier + "-musl", NativeImageLayerRuntime.classifier(List.of("--libc=musl")));
    }

    @Test
    void discoversSharedLibraryAndArchivesThemDeterministically() throws Exception {
        String libExtension = platformLibraryExtension();
        String primaryFile = "libbase" + libExtension;
        String nestedFile = "nested/libjava" + libExtension;

        Path output = temporaryDirectory.resolve("output");
        Files.createDirectories(output.resolve("nested"));
        Files.writeString(output.resolve(primaryFile), "base");
        Files.writeString(output.resolve(nestedFile), "java");
        Files.writeString(output.resolve("base.nil"), "nil");
        Files.writeString(output.resolve("graal_isolate.h"), "header");

        List<Path> runtimeFiles = NativeImageLayerRuntime.discoverRuntimeFiles(output);
        assertEquals(List.of(output.resolve(primaryFile), output.resolve(nestedFile)), runtimeFiles);
        assertTrue(NativeImageLayerRuntime.containsPrimaryRuntimeLibrary(runtimeFiles, "libbase"));

        Path first = temporaryDirectory.resolve("first.zip");
        Path second = temporaryDirectory.resolve("second.zip");
        NativeImageLayerRuntime.createArchive(output, runtimeFiles, first);
        NativeImageLayerRuntime.createArchive(output, runtimeFiles, second);
        assertArrayEquals(Files.readAllBytes(first), Files.readAllBytes(second));
        try (ZipFile zip = new ZipFile(first.toFile())) {
            assertEquals(List.of(primaryFile, nestedFile),
                zip.stream().map(entry -> entry.getName()).toList());
        }

        Path extracted = temporaryDirectory.resolve("extracted");
        NativeImageLayerRuntime.extractArchive(first, extracted, message -> { });
        assertEquals("base", Files.readString(extracted.resolve(primaryFile)));
        assertEquals("java", Files.readString(extracted.resolve(nestedFile)));
    }

    private static String platformLibraryExtension() {
        String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
        if (os.contains("win")) {
            return ".dll";
        }
        if (os.contains("mac") || os.contains("darwin")) {
            return ".dylib";
        }
        return ".so";
    }
}
