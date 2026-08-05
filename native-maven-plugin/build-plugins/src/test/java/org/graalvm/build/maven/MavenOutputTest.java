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

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Protects concise, sanitized embedded Maven failure classification. §E2E-functional-tests.4
 */
class MavenOutputTest {
    @Test
    void classifiesTransferAndNotFoundDiagnostics() {
        assertEquals(
                "Embedded Maven could not resolve artifact example:missing:pom:1.0 from repository private "
                        + "(https://repo.example.test:8443/releases). Rerun with --info or --debug for Maven diagnostics.",
                MavenOutput.failureMessage("[ERROR] Could not transfer artifact example:missing:pom:1.0 from/to private "
                        + "(https://user:secret@repo.example.test:8443/releases?token=hidden#fragment): timeout"));
        assertEquals(
                "Embedded Maven could not resolve artifact example:absent:jar:2.0 from repository central "
                        + "(https://repo.example.test/maven2). Rerun with --info or --debug for Maven diagnostics.",
                MavenOutput.failureMessage("[ERROR] Could not find artifact example:absent:jar:2.0 in central "
                        + "(https://repo.example.test/maven2)"));
        assertEquals(
                "Embedded Maven could not resolve artifact example:cached:jar:3.0 from repository "
                        + "https://repo.example.test/releases. Rerun with --info or --debug for Maven diagnostics.",
                MavenOutput.failureMessage("[main] ERROR org.apache.maven.cli.MavenCli - "
                        + "example:cached:jar:3.0 was not found in "
                        + "https://user:secret@repo.example.test/releases?token=hidden#fragment during a previous attempt"));
    }

    @Test
    void sanitizesUrlsInFallbackErrors() {
        String message = MavenOutput.failureMessage("""
                [ERROR] Earlier error
                [ERROR] Request to https://user:secret@repo.example.test/path?token=hidden#fragment failed.
                [ERROR] -> [Help 1]
                """);

        assertTrue(message.contains("https://repo.example.test/path"));
        assertFalse(message.contains("user:secret"));
        assertFalse(message.contains("token=hidden"));
    }

    @Test
    void recognizesSlf4jPrefixedFallbackErrors() {
        String message = MavenOutput.failureMessage("""
                [main] ERROR org.apache.maven.cli.MavenCli - Earlier error
                [main] ERROR org.apache.maven.cli.MavenCli - Build configuration is invalid.
                [main] ERROR org.apache.maven.cli.MavenCli - -> [Help 1]
                [main] ERROR org.apache.maven.cli.MavenCli - [Help 1] https://example.test/help
                """);

        assertTrue(message.contains("Embedded Maven failed: Build configuration is invalid."));
        assertFalse(message.contains("Earlier error"));
    }
}
