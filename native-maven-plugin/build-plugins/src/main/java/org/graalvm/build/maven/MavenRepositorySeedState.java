/*
 * Copyright (c) 2026, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * The Universal Permissive License (UPL), Version 1.0
 *
 * Subject to the condition set forth below, permission is hereby granted to any
 * person obtaining a copy of this software, associated documentation and/or
 * data (collectively the "Software"), free of charge and under any and all
 * copyright rights in the Software, and in any patent rights owned or freely
 * licensable by each licensor hereunder covering either (i) the unmodified
 * Software as contributed to or provided by such licensor, or (ii) the Larger
 * Works (as defined below), to deal in both
 *
 * (a) the Software, and
 *
 * (b) any piece of software and/or hardware listed in the lrgrwrks.txt file if
 * one is included with the Software each a "Larger Work" to which the Software
 * is contributed by such licensors),
 *
 * without restriction, including without limitation the rights to copy, create
 * derivative works of, display, perform, and distribute the Software and the
 * Larger Work(s), and to make, use, sell, offer for sale, import, export, have
 * made, and have sold the Software and the Larger Work(s), and to sublicense
 * the foregoing rights on either these or other terms.
 *
 * This license is subject to the following condition:
 *
 * The above copyright notice and either this complete permission notice or at
 * a minimum a reference to the UPL must be included in all copies or
 * substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */
package org.graalvm.build.maven;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.TreeMap;
import java.util.function.Predicate;
import java.util.stream.Stream;

/**
 * Records and validates the keyed inventory for a reusable Maven repository seed. §E2E-functional-tests.4
 */
final class MavenRepositorySeedState {
    static final String INVALID_SEED_MESSAGE =
            "The seeded Maven repository is missing or stale; run prepareMavenLocalRepo online.";
    static final String MANIFEST_NAME = ".native-build-tools-seed.properties";
    static final String SCHEMA_VERSION = "1";
    private static final String INPUT_KEY = "inputKey";
    private static final String SCHEMA_KEY = "schema";
    private static final String FILE_PREFIX = "file.";

    private MavenRepositorySeedState() {
    }

    static String inputKey(Map<String, String> values, Map<String, Path> files) throws IOException {
        MessageDigest digest = digest();
        update(digest, "schema", SCHEMA_VERSION);
        values.entrySet().stream().sorted(Map.Entry.comparingByKey())
                .forEach(entry -> update(digest, entry.getKey(), entry.getValue()));
        for (Map.Entry<String, Path> entry : new TreeMap<>(files).entrySet()) {
            Path path = entry.getValue();
            update(digest, entry.getKey(), Files.exists(path) ? hash(path) : "<missing>");
        }
        return hex(digest.digest());
    }

    static void write(Path repository, String inputKey) throws IOException {
        List<String> lines = new ArrayList<>();
        lines.add(SCHEMA_KEY + "=" + SCHEMA_VERSION);
        lines.add(INPUT_KEY + "=" + inputKey);
        for (Map.Entry<String, String> entry : inventory(repository).entrySet()) {
            String encodedPath = Base64.getUrlEncoder().withoutPadding()
                    .encodeToString(entry.getKey().getBytes(StandardCharsets.UTF_8));
            lines.add(FILE_PREFIX + encodedPath + "=" + entry.getValue());
        }
        Files.write(repository.resolve(MANIFEST_NAME), lines, StandardCharsets.UTF_8);
    }

    static boolean isValid(Path repository, String expectedInputKey) {
        Path manifest = repository.resolve(MANIFEST_NAME);
        if (!Files.isRegularFile(manifest)) {
            return false;
        }
        Properties properties = new Properties();
        try (InputStream input = new BufferedInputStream(Files.newInputStream(manifest))) {
            properties.load(input);
            if (!SCHEMA_VERSION.equals(properties.getProperty(SCHEMA_KEY))
                    || !expectedInputKey.equals(properties.getProperty(INPUT_KEY))) {
                return false;
            }
            Map<String, String> recorded = new TreeMap<>();
            for (String name : properties.stringPropertyNames()) {
                if (name.startsWith(FILE_PREFIX)) {
                    String encodedPath = name.substring(FILE_PREFIX.length());
                    String path = new String(Base64.getUrlDecoder().decode(encodedPath), StandardCharsets.UTF_8);
                    recorded.put(path, properties.getProperty(name));
                }
            }
            return recorded.equals(inventory(repository));
        } catch (IllegalArgumentException | IOException ex) {
            return false;
        }
    }

    private static Map<String, String> inventory(Path repository) throws IOException {
        Map<String, String> files = new TreeMap<>();
        if (!Files.isDirectory(repository)) {
            return files;
        }
        try (Stream<Path> paths = Files.walk(repository)) {
            paths.filter(Files::isRegularFile)
                    .filter(stableRepositoryFile())
                    .forEach(path -> {
                        try {
                            files.put(repository.relativize(path).toString().replace('\\', '/'), hash(path));
                        } catch (IOException ex) {
                            throw new SeedStateIOException(ex);
                        }
                    });
        } catch (SeedStateIOException ex) {
            throw ex.getCause();
        }
        return files;
    }

    private static Predicate<Path> stableRepositoryFile() {
        return path -> {
            String name = path.getFileName().toString();
            boolean lockDirectory = false;
            for (Path element : path) {
                if (element.toString().equals(".locks")) {
                    lockDirectory = true;
                    break;
                }
            }
            return !name.equals(MANIFEST_NAME)
                    && !lockDirectory
                    && !name.equals("_remote.repositories")
                    && !name.equals("resolver-status.properties")
                    && !name.endsWith(".lastUpdated")
                    && !name.endsWith(".lock")
                    && !name.endsWith(".tmp")
                    && !name.endsWith(".part");
        };
    }

    private static String hash(Path path) throws IOException {
        MessageDigest digest = digest();
        if (Files.isDirectory(path)) {
            try (Stream<Path> paths = Files.walk(path)) {
                for (Path file : paths.filter(Files::isRegularFile).sorted().toList()) {
                    update(digest, path.relativize(file).toString().replace('\\', '/'), hash(file));
                }
            }
        } else {
            try (InputStream input = new BufferedInputStream(Files.newInputStream(path))) {
                byte[] buffer = new byte[8192];
                int read;
                while ((read = input.read(buffer)) >= 0) {
                    digest.update(buffer, 0, read);
                }
            }
        }
        return hex(digest.digest());
    }

    private static MessageDigest digest() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException(ex);
        }
    }

    private static void update(MessageDigest digest, String key, String value) {
        digest.update(key.getBytes(StandardCharsets.UTF_8));
        digest.update((byte) 0);
        digest.update(value.getBytes(StandardCharsets.UTF_8));
        digest.update((byte) 0);
    }

    private static String hex(byte[] bytes) {
        StringBuilder result = new StringBuilder(bytes.length * 2);
        for (byte value : bytes) {
            result.append(String.format("%02x", value));
        }
        return result.toString();
    }

    private static final class SeedStateIOException extends RuntimeException {
        private static final long serialVersionUID = 1L;

        SeedStateIOException(IOException cause) {
            super(cause);
        }

        @Override
        public synchronized IOException getCause() {
            return (IOException) super.getCause();
        }
    }
}
