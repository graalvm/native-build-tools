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
 * made, and have sold the Software and the Larger Work(s), and to sublicense the
 * foregoing rights on either these or other terms.
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

import java.io.ByteArrayOutputStream;
import java.io.OutputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Bounds embedded Maven output and renders one safe actionable failure. §E2E-functional-tests.4
 */
final class MavenOutput extends OutputStream {
    private static final int MAXIMUM_BYTES = 4 * 1024 * 1024;
    private static final Pattern ANSI_ESCAPE = Pattern.compile("\\x1B(?:\\[[0-?]*[ -/]*[@-~]|\\][^\\x07]*(?:\\x07|\\x1B\\\\))");
    private static final Pattern RESOLUTION_FAILURE = Pattern.compile(
            "Could not (?:transfer|find) artifact ([^\\s]+) (?:from/to|in) ([^\\s(]+) \\(([^)]+)\\)",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern CACHED_NOT_FOUND = Pattern.compile(
            "([^\\s]+) was not found in ([^\\s]+) during a previous attempt",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern SLF4J_ERROR = Pattern.compile("^\\[[^]]+]\\s+ERROR\\s+\\S+\\s+-\\s*(.*)$");
    private static final Pattern URL = Pattern.compile("[a-zA-Z][a-zA-Z0-9+.-]*://[^\\s)]+(?:\\)[^\\s]*)?");
    private static final String DIAGNOSTIC_HINT = "Rerun with --info or --debug for Maven diagnostics.";

    private final ByteArrayOutputStream bytes = new ByteArrayOutputStream();
    private boolean truncated;

    @Override
    public synchronized void write(int value) {
        if (bytes.size() < MAXIMUM_BYTES) {
            bytes.write(value);
        } else {
            truncated = true;
        }
    }

    @Override
    public synchronized void write(byte[] data, int offset, int length) {
        int remaining = MAXIMUM_BYTES - bytes.size();
        if (remaining > 0) {
            bytes.write(data, offset, Math.min(length, remaining));
        }
        if (length > remaining) {
            truncated = true;
        }
    }

    synchronized String contents() {
        String output = bytes.toString(StandardCharsets.UTF_8);
        if (truncated) {
            return output + System.lineSeparator() + "[embedded Maven output truncated]";
        }
        return output;
    }

    static String failureMessage(String diagnostics) {
        String plainOutput = ANSI_ESCAPE.matcher(diagnostics).replaceAll("");
        Matcher resolution = RESOLUTION_FAILURE.matcher(plainOutput);
        if (resolution.find()) {
            String artifact = resolution.group(1);
            String repository = resolution.group(2);
            String url = sanitizeUrl(resolution.group(3));
            String location = url == null ? repository : repository + " (" + url + ")";
            return "Embedded Maven could not resolve artifact " + artifact + " from repository " + location + ". " + DIAGNOSTIC_HINT;
        }
        Matcher cachedNotFound = CACHED_NOT_FOUND.matcher(plainOutput);
        if (cachedNotFound.find()) {
            String artifact = cachedNotFound.group(1);
            String repository = sanitizeUrl(cachedNotFound.group(2));
            String location = repository == null ? "[repository URL omitted]" : repository;
            return "Embedded Maven could not resolve artifact " + artifact + " from repository " + location + ". " + DIAGNOSTIC_HINT;
        }
        String detail = finalMeaningfulError(plainOutput);
        return "Embedded Maven failed: " + detail + ". " + DIAGNOSTIC_HINT;
    }

    private static String finalMeaningfulError(String output) {
        String[] lines = output.split("\\R");
        for (int index = lines.length - 1; index >= 0; index--) {
            String detail = errorDetail(lines[index].trim());
            if (detail == null) {
                continue;
            }
            if (!detail.isEmpty() && !detail.startsWith("-> [Help ") && !detail.startsWith("[Help ")
                    && !detail.startsWith("For more information")) {
                return stripTrailingPeriod(sanitizeUrls(detail));
            }
        }
        return "Maven exited with a non-zero status";
    }

    private static String errorDetail(String line) {
        if (line.startsWith("[ERROR]")) {
            return line.substring("[ERROR]".length()).trim();
        }
        Matcher matcher = SLF4J_ERROR.matcher(line);
        return matcher.matches() ? matcher.group(1).trim() : null;
    }

    private static String sanitizeUrls(String text) {
        Matcher matcher = URL.matcher(text);
        StringBuffer sanitized = new StringBuffer();
        while (matcher.find()) {
            String replacement = sanitizeUrl(matcher.group());
            matcher.appendReplacement(sanitized, Matcher.quoteReplacement(replacement == null ? "[repository URL omitted]" : replacement));
        }
        matcher.appendTail(sanitized);
        return sanitized.toString();
    }

    private static String sanitizeUrl(String value) {
        try {
            URI uri = new URI(value);
            if (uri.getScheme() == null) {
                return null;
            }
            if (uri.isOpaque()) {
                return uri.getScheme() + ":";
            }
            return new URI(uri.getScheme(), null, uri.getHost(), uri.getPort(), uri.getPath(), null, null).toASCIIString();
        } catch (URISyntaxException ex) {
            return null;
        }
    }

    private static String stripTrailingPeriod(String value) {
        return value.endsWith(".") ? value.substring(0, value.length() - 1) : value;
    }
}
