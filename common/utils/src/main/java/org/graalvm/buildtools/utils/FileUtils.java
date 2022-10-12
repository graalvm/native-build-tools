/*
 * Copyright (c) 2021, Oracle and/or its affiliates. All rights reserved.
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
import java.math.BigInteger;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

public final class FileUtils {

    public static final int CONNECT_TIMEOUT = 5000;
    public static final int READ_TIMEOUT = 5000;

    public static String normalizePathSeparators(String path) {
        return path.replace('\\', '/');
    }

    public static Optional<Path> download(URL url, Path destination, Consumer<String> errorLogger) {
        if ("file".equals(url.getProtocol())) {
            try {
                return Optional.of(new java.io.File(url.toURI()).toPath());
            } catch (URISyntaxException e) {
                return Optional.empty();
            }
        }
        try {
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("GET");
            connection.setConnectTimeout(CONNECT_TIMEOUT);
            connection.setReadTimeout(READ_TIMEOUT);

            if (connection.getResponseCode() != HttpURLConnection.HTTP_OK) {
                errorLogger.accept("Failed to download from " + url + ": " + connection.getResponseCode() + " " + connection.getResponseMessage());
            } else {
                String fileName = "";
                String disposition = connection.getHeaderField("Content-Disposition");

                if (disposition != null) {
                    int index = disposition.indexOf("filename=");
                    if (index > 0) {
                        fileName = disposition.substring(index + 9);
                    }
                } else {
                    fileName = url.getFile().substring(url.getFile().lastIndexOf("/") + 1);
                }

                if (!Files.exists(destination)) {
                    Files.createDirectories(destination);
                }
                Path result = destination.resolve(fileName);
                Files.copy(connection.getInputStream(), result);

                connection.disconnect();
                return Optional.of(result);
            }
        } catch (IOException e) {
            errorLogger.accept("Failed to download from " + url + ": " + e.getMessage());
        }

        return Optional.empty();
    }

    public static void extract(Path archive, Path destination, Consumer<String> errorLogger) {
        if (isZip(archive)) {
            try (ZipInputStream zis = new ZipInputStream(Files.newInputStream(archive.toFile().toPath()))) {
                for (ZipEntry entry = zis.getNextEntry(); entry != null; entry = zis.getNextEntry()) {
                    Optional<Path> sanitizedPath = sanitizePath(entry, destination);
                    if (sanitizedPath.isPresent()) {
                        Path zipEntryPath = sanitizedPath.get();
                        if (entry.isDirectory()) {
                            Files.createDirectories(zipEntryPath);
                        } else {
                            if (zipEntryPath.getParent() != null && !Files.exists(zipEntryPath.getParent())) {
                                Files.createDirectories(zipEntryPath.getParent());
                            }

                            Files.copy(zis, zipEntryPath, StandardCopyOption.REPLACE_EXISTING);
                        }
                    } else {
                        errorLogger.accept("Wrong entry " + entry.getName() + " in " + archive);
                    }
                    zis.closeEntry();
                }
            } catch (IOException e) {
                errorLogger.accept("Failed to extract " + archive + ": " + e.getMessage());
            }
        } else {
            errorLogger.accept("Unsupported archive format: " + archive + ". Only ZIP files are supported");
        }
    }

    public static boolean isZip(Path archive) {
        return archive.toString().toLowerCase().endsWith(".zip");
    }

    private static Optional<Path> sanitizePath(ZipEntry entry, Path destination) {
        Path normalized = destination.resolve(entry.getName()).normalize();
        if (normalized.startsWith(destination)) {
            return Optional.of(normalized);
        } else {
            return Optional.empty();
        }
    }

    public static String hashFor(URI uri) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-1");
            byte[] messageDigest = md.digest(md.digest(uri.toString().getBytes(StandardCharsets.UTF_8)));
            BigInteger no = new BigInteger(1, messageDigest);
            StringBuilder digest = new StringBuilder(no.toString(16));
            while (digest.length() < 32) {
                digest.insert(0, "0");
            }
            return digest.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new UnsupportedOperationException(e);
        }
    }
}
