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

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

/**
 * Discovers, packages, and stages the platform runtime files emitted with a Native Image layer.
 * §root/FS-native-builds.6.
 */
public final class NativeImageLayerRuntime {
    public static final String ARCHIVE_TYPE = "zip";
    private static final String CLASSIFIER_PREFIX = "layer-runtime-";
    private static final String CONTENT_MARKER = ".archive-sha256";

    private NativeImageLayerRuntime() {
    }

    public static String classifier(List<String> buildArgs) {
        String os = normalizedOs(System.getProperty("os.name", ""));
        String arch = normalizedArch(System.getProperty("os.arch", ""));
        String libc = configuredLibc(buildArgs).filter(value -> !"glibc".equals(value)).orElse(null);
        return CLASSIFIER_PREFIX + os + "-" + arch + (libc == null ? "" : "-" + libc);
    }

    public static List<Path> discoverRuntimeFiles(Path outputDirectory) throws IOException {
        if (!Files.isDirectory(outputDirectory)) {
            return List.of();
        }
        try (var files = Files.walk(outputDirectory)) {
            return files.filter(Files::isRegularFile)
                .filter(path -> isRuntimeLibrary(path.getFileName().toString()))
                .sorted(Comparator.comparing(path -> FileUtils.normalizePathSeparators(
                    outputDirectory.relativize(path).toString())))
                .toList();
        }
    }

    public static boolean containsPrimaryRuntimeLibrary(List<Path> runtimeFiles, String imageName) {
        String os = normalizedOs(System.getProperty("os.name", ""));
        String expected = switch (os) {
            case "windows" -> imageName + ".dll";
            case "macos" -> imageName + ".dylib";
            default -> imageName + ".so";
        };
        return runtimeFiles.stream().anyMatch(path -> path.getFileName().toString().equals(expected));
    }

    public static void createArchive(Path outputDirectory, List<Path> runtimeFiles, Path archive) throws IOException {
        Files.createDirectories(archive.toAbsolutePath().getParent());
        try (OutputStream output = Files.newOutputStream(archive);
             ZipOutputStream zip = new ZipOutputStream(output)) {
            for (Path runtimeFile : runtimeFiles) {
                String entryName = FileUtils.normalizePathSeparators(outputDirectory.relativize(runtimeFile).toString());
                ZipEntry entry = new ZipEntry(entryName);
                entry.setTime(0L);
                zip.putNextEntry(entry);
                Files.copy(runtimeFile, zip);
                zip.closeEntry();
            }
        }
    }

    public static void extractArchive(Path archive, Path destination, Consumer<String> warning) throws IOException {
        String archiveHash = sha256(archive);
        Path marker = destination.resolve(CONTENT_MARKER);
        if (Files.isRegularFile(marker) && archiveHash.equals(Files.readString(marker))) {
            return;
        }
        clearDestination(destination);
        Files.createDirectories(destination);
        try (InputStream input = Files.newInputStream(archive);
             ZipInputStream zip = new ZipInputStream(input)) {
            for (ZipEntry entry = zip.getNextEntry(); entry != null; entry = zip.getNextEntry()) {
                Path extracted = destination.resolve(entry.getName()).normalize();
                if (!extracted.startsWith(destination)) {
                    warning.accept("Ignoring unsafe layer runtime archive entry " + entry.getName());
                    continue;
                }
                if (entry.isDirectory()) {
                    Files.createDirectories(extracted);
                } else {
                    Files.createDirectories(extracted.getParent());
                    Files.copy(zip, extracted, StandardCopyOption.REPLACE_EXISTING);
                }
                zip.closeEntry();
            }
        }
        Files.writeString(marker, archiveHash);
    }

    public static boolean isRuntimeLibrary(String fileName) {
        String lower = fileName.toLowerCase(Locale.ROOT);
        return lower.endsWith(".dll") || lower.endsWith(".dylib") || lower.endsWith(".so")
            || lower.contains(".so.");
    }

    private static Optional<String> configuredLibc(List<String> buildArgs) {
        if (buildArgs == null) {
            return Optional.empty();
        }
        for (int i = 0; i < buildArgs.size(); i++) {
            String argument = buildArgs.get(i);
            if (argument == null) {
                continue;
            }
            if (argument.startsWith("--libc=")) {
                return Optional.of(normalizeToken(argument.substring("--libc=".length())));
            }
            if (argument.startsWith("-H:LibC=")) {
                return Optional.of(normalizeToken(argument.substring("-H:LibC=".length())));
            }
            if ("--libc".equals(argument) && i + 1 < buildArgs.size()) {
                return Optional.of(normalizeToken(buildArgs.get(i + 1)));
            }
        }
        return Optional.empty();
    }

    private static String normalizedOs(String value) {
        String os = value.toLowerCase(Locale.ROOT);
        if (os.contains("win")) {
            return "windows";
        }
        if (os.contains("mac") || os.contains("darwin")) {
            return "macos";
        }
        if (os.contains("linux")) {
            return "linux";
        }
        return normalizeToken(os);
    }

    private static String normalizedArch(String value) {
        String arch = value.toLowerCase(Locale.ROOT);
        if ("x86_64".equals(arch) || "x64".equals(arch)) {
            return "amd64";
        }
        if ("aarch64".equals(arch)) {
            return "arm64";
        }
        return normalizeToken(arch);
    }

    private static String normalizeToken(String value) {
        return value.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "-").replaceAll("(^-|-$)", "");
    }

    private static String sha256(Path file) throws IOException {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            try (InputStream input = Files.newInputStream(file)) {
                byte[] buffer = new byte[8192];
                for (int read = input.read(buffer); read >= 0; read = input.read(buffer)) {
                    digest.update(buffer, 0, read);
                }
            }
            return HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 is unavailable", ex);
        }
    }

    private static void clearDestination(Path destination) throws IOException {
        if (!Files.exists(destination)) {
            return;
        }
        try (var paths = Files.walk(destination)) {
            for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) {
                Files.delete(path);
            }
        }
    }
}
