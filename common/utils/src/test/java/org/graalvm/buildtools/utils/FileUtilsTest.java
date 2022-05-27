package org.graalvm.buildtools.utils;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.net.URL;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

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

}
