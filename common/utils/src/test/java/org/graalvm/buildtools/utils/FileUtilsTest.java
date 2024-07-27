/*
 * Copyright (c) 2022, Oracle and/or its affiliates. All rights reserved.
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

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FileUtilsTest {

    @Test
    @DisplayName("It can download a file from a URL that exists")
    void testDownloadOk(@TempDir Path tempDir) throws IOException {
        URL url = new URL("https://github.com/graalvm/native-build-tools/archive/refs/heads/master.zip");
        List<String> errorLogs = new ArrayList<>();

        Optional<Path> download = FileUtils.download(url, tempDir, errorLogs::add);
        System.out.println("errorLogs = " + errorLogs);

        assertTrue(download.isPresent());
        assertEquals("native-build-tools-master.zip", download.get().getFileName().toString());
        assertEquals(0, errorLogs.size());
    }

    @Test
    @DisplayName("It doesn't blow up with a URL that isn't a file download")
    void testDownloadNoFile(@TempDir Path tempDir) throws IOException {
        URL url = new URL("https://httpbin.org/html");
        List<String> errorLogs = new ArrayList<>();

        Optional<Path> download = FileUtils.download(url, tempDir, errorLogs::add);
        System.out.println("errorLogs = " + errorLogs);

        assertTrue(download.isPresent());
        assertEquals("html", download.get().getFileName().toString());
        assertEquals(0, errorLogs.size());
    }

    @Test
    @DisplayName("It doesn't blow up with a URL that does not exist")
    void testDownloadNotFound(@TempDir Path tempDir) throws IOException {
        URL url = new URL("https://google.com/notfound");
        List<String> errorLogs = new ArrayList<>();

        Optional<Path> download = FileUtils.download(url, tempDir, errorLogs::add);
        System.out.println("errorLogs = " + errorLogs);

        assertFalse(download.isPresent());
        assertEquals(1, errorLogs.size());
    }

    @Test
    @DisplayName("It doesn't blow up with connection timeouts")
    void testDownloadTimeout(@TempDir Path tempDir) throws IOException {
        URL url = new URL("https://httpbin.org/delay/10");
        List<String> errorLogs = new ArrayList<>();

        Optional<Path> download = FileUtils.download(url, tempDir, errorLogs::add);
        System.out.println("errorLogs = " + errorLogs);

        assertFalse(download.isPresent());
        assertEquals(1, errorLogs.size());
    }


    @Test
    @DisplayName("It can unzip a file")
    void testExtract(@TempDir Path tempDir) throws IOException {
        Path zipFile = new File("src/test/resources/graalvm-reachability-metadata.zip").toPath();
        List<String> errorLogs = new ArrayList<>();

        FileUtils.extract(zipFile, tempDir, errorLogs::add);

        assertEquals(0, errorLogs.size());

        assertTrue(Files.exists(tempDir.resolve("index.json")));
        assertEquals("[]", String.join("\n", Files.readAllLines(tempDir.resolve("index.json"))));

        assertTrue(Files.isDirectory(tempDir.resolve("org.graalvm.internal")));
        assertTrue(Files.isDirectory(tempDir.resolve("org.graalvm.internal/library-with-reflection")));
        assertTrue(Files.exists(tempDir.resolve("org.graalvm.internal/library-with-reflection/index.json")));
        assertTrue(Files.isDirectory(tempDir.resolve("org.graalvm.internal/library-with-reflection/1")));

        assertTrue(Files.exists(tempDir.resolve("org.graalvm.internal/library-with-reflection/1/reflect-config.json")));
        assertEquals("[  {    \"name\": \"org.graalvm.internal.reflect.Message\",    \"allDeclaredFields\": true,    \"allDeclaredMethods\": true  }]",
                String.join("", Files.readAllLines(tempDir.resolve("org.graalvm.internal/library-with-reflection/1/reflect-config.json"))));
    }

    @Test
    @DisplayName("It is protected against ZIP slip attacks")
    void testZipSlip(@TempDir Path tempDir) throws IOException {
        Path zipFile = new File("src/test/resources/zip-slip.zip").toPath();
        List<String> errorLogs = new ArrayList<>();

        FileUtils.extract(zipFile, tempDir, errorLogs::add);

        assertEquals(1, errorLogs.size());
        assertEquals("Wrong entry ../../../../../../../../../../../../../../../../../../../../../../../../../../../../../../../../../../../../../../../../tmp/evil.txt in src/test/resources/zip-slip.zip", errorLogs.get(0));

        assertTrue(Files.exists(tempDir.resolve("good.txt")));
        assertTrue(Files.notExists(tempDir.resolve("evil.txt")));
        assertTrue(Files.notExists(Paths.get("/tmp/evil.txt")));

        Stream<Path> stream = Files.list(tempDir);
        assertEquals(1, stream.count());
        stream.close();
    }

    @ParameterizedTest(name = "Archives with format {0} are not supported")
    @ValueSource(strings = {"tar.gz", "tar.bz2"})
    void testExtractNonZip(String format, @TempDir Path tempDir) {
        Path archive = new File("src/test/resources/graalvm-reachability-metadata." + format).toPath();
        List<String> errorLogs = new ArrayList<>();

        FileUtils.extract(archive, tempDir, errorLogs::add);

        assertEquals(1, errorLogs.size());
        assertEquals("Unsupported archive format: src/test/resources/graalvm-reachability-metadata." + format + ". Only ZIP files are supported", errorLogs.get(0));
    }

}
