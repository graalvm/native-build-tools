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

import static org.junit.jupiter.api.Assertions.*;

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
        URL url = new URL("https://httpstat.us/200");
        List<String> errorLogs = new ArrayList<>();

        Optional<Path> download = FileUtils.download(url, tempDir, errorLogs::add);
        System.out.println("errorLogs = " + errorLogs);

        assertTrue(download.isPresent());
        assertEquals("200", download.get().getFileName().toString());
        assertEquals(0, errorLogs.size());
    }

    @Test
    @DisplayName("It doesn't blow up with a URL that does not exist")
    void testDownloadNotFound(@TempDir Path tempDir) throws IOException {
        URL url = new URL("https://httpstat.us/404");
        List<String> errorLogs = new ArrayList<>();

        Optional<Path> download = FileUtils.download(url, tempDir, errorLogs::add);
        System.out.println("errorLogs = " + errorLogs);

        assertFalse(download.isPresent());
        assertEquals(1, errorLogs.size());
    }

    @Test
    @DisplayName("It doesn't blow up with connection timeouts")
    void testDownloadTimeout(@TempDir Path tempDir) throws IOException {
        URL url = new URL("https://httpstat.us/200?sleep=" + (FileUtils.READ_TIMEOUT + 1000));
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

        assertTrue(Files.isDirectory(tempDir.resolve("org")));
        assertTrue(Files.isDirectory(tempDir.resolve("org.graalvm")));
        assertTrue(Files.isDirectory(tempDir.resolve("org.graalvm.internal")));
        assertTrue(Files.isDirectory(tempDir.resolve("org.graalvm.internal/library-with-reflection")));
        assertTrue(Files.exists(tempDir.resolve("org.graalvm.internal/library-with-reflection/index.json")));
        assertTrue(Files.isDirectory(tempDir.resolve("org.graalvm.internal/library-with-reflection/1")));

        assertTrue(Files.exists(tempDir.resolve("org.graalvm.internal/library-with-reflection/1/reflect-config.json")));
        assertEquals("[  {    \"name\": \"org.graalvm.internal.reflect.Message\",    \"allDeclaredFields\": true,    \"allDeclaredMethods\": true  }]", String.join("", Files.readAllLines(tempDir.resolve("org.graalvm.internal/library-with-reflection/1/reflect-config.json"))));
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
