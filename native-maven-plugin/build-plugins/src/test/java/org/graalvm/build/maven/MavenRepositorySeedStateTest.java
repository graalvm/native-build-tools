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

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Protects keyed, complete, read-only Maven seed validation. §E2E-functional-tests.4
 */
class MavenRepositorySeedStateTest {
    @TempDir
    Path temporaryDirectory;
    private Path repository;
    private String inputKey;

    @BeforeEach
    void createSeed() throws IOException {
        repository = temporaryDirectory.resolve("repository");
        Files.createDirectories(repository.resolve("example/artifact/1.0"));
        Files.writeString(repository.resolve("example/artifact/1.0/artifact-1.0.jar"), "artifact");
        inputKey = MavenRepositorySeedState.inputKey(Map.of("argument", "value"), Map.of());
        MavenRepositorySeedState.write(repository, inputKey);
    }

    @Test
    void acceptsACompleteMatchingSeed() {
        assertTrue(MavenRepositorySeedState.isValid(repository, inputKey));
    }

    @Test
    void rejectsMissingStaleTruncatedAndCorruptedSeeds() throws IOException {
        assertFalse(MavenRepositorySeedState.isValid(temporaryDirectory.resolve("missing"), inputKey));
        assertFalse(MavenRepositorySeedState.isValid(repository, "different-input-key"));

        Path artifact = repository.resolve("example/artifact/1.0/artifact-1.0.jar");
        Files.writeString(artifact, "corrupt");
        assertFalse(MavenRepositorySeedState.isValid(repository, inputKey));

        Files.writeString(artifact, "artifact");
        Files.writeString(repository.resolve("unexpected.jar"), "unexpected");
        assertFalse(MavenRepositorySeedState.isValid(repository, inputKey));
    }

    @Test
    void ignoresTransientResolverFiles() throws IOException {
        Files.writeString(repository.resolve("resolver-status.properties"), "transient");
        Files.writeString(repository.resolve("artifact.lastUpdated"), "transient");
        assertTrue(MavenRepositorySeedState.isValid(repository, inputKey));
    }

    @Test
    void exposesTheStableOfflinePrerequisiteDiagnostic() {
        assertEquals(
                "The seeded Maven repository is missing or stale; run prepareMavenLocalRepo online.",
                MavenRepositorySeedState.INVALID_SEED_MESSAGE);
    }
}
