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

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

class SchemaValidationUtilsTest {

    // ---------- validateSchemas tests ----------

    @Test
    @DisplayName("validateSchemas succeeds when required schemas with exact majors are present")
    void validateSchemas_successExactMajors(@TempDir Path tempDir) throws IOException {
        Path repoRoot = tempDir.resolve("repo-success");
        Path schemas = repoRoot.resolve("schemas");
        Files.createDirectories(schemas);
        // Required schemas: exact majors: 1.x.x and 2.x.x
        writeJson(schemas.resolve("library-and-framework-list-schema-v1.0.0.json"), "{}");
        writeJson(schemas.resolve("metadata-library-index-schema-v2.0.0.json"), "{}");
        // Optional reachability schema should be ignored for "count" purposes
        writeJson(schemas.resolve("reachability-metadata-schema-v1.2.0.json"), schemaJson("1.2.0"));

        assertDoesNotThrow(() -> SchemaValidationUtils.validateSchemas(repoRoot));
    }

    @Test
    @DisplayName("validateSchemas fails when 'schemas' directory is missing")
    void validateSchemas_missingSchemasDir(@TempDir Path tempDir) {
        Path repoRoot = tempDir.resolve("repo-missing");
        // Do not create 'schemas' directory
        assertThrows(IllegalStateException.class, () -> SchemaValidationUtils.validateSchemas(repoRoot));
    }

    @Test
    @DisplayName("validateSchemas fails when repository provides an older required major")
    void validateSchemas_metadataTooOld(@TempDir Path tempDir) throws IOException {
        Path repoRoot = tempDir.resolve("repo-too-old");
        Path schemas = repoRoot.resolve("schemas");
        Files.createDirectories(schemas);
        // library-and-framework-list-schema requires major 1 (OK)
        writeJson(schemas.resolve("library-and-framework-list-schema-v1.9.9.json"), "{}");
        // metadata-library-index-schema requires major 2, but repo provides 1 => too old
        writeJson(schemas.resolve("metadata-library-index-schema-v1.0.0.json"), "{}");

        assertThrows(IllegalStateException.class, () -> SchemaValidationUtils.validateSchemas(repoRoot));
    }

    @Test
    @DisplayName("validateSchemas fails when repository provides a newer required major")
    void validateSchemas_toolsTooOld(@TempDir Path tempDir) throws IOException {
        Path repoRoot = tempDir.resolve("repo-tools-too-old");
        Path schemas = repoRoot.resolve("schemas");
        Files.createDirectories(schemas);
        // library-and-framework-list-schema requires major 1, but repo provides 2 => tools too old
        writeJson(schemas.resolve("library-and-framework-list-schema-v2.0.0.json"), "{}");
        // metadata-library-index-schema requires major 2 (OK)
        writeJson(schemas.resolve("metadata-library-index-schema-v2.3.4.json"), "{}");

        assertThrows(IllegalStateException.class, () -> SchemaValidationUtils.validateSchemas(repoRoot));
    }

    @Test
    @DisplayName("validateSchemas fails when more schema files than supported are present (excluding reachability)")
    void validateSchemas_tooManySchemasExcludingReachability(@TempDir Path tempDir) throws IOException {
        Path repoRoot = tempDir.resolve("repo-too-many");
        Path schemas = repoRoot.resolve("schemas");
        Files.createDirectories(schemas);
        // The two supported required schemas:
        writeJson(schemas.resolve("library-and-framework-list-schema-v1.0.0.json"), "{}");
        writeJson(schemas.resolve("metadata-library-index-schema-v2.0.0.json"), "{}");
        // Extra, non-reachability schema should trigger the "too many schemas" error
        writeJson(schemas.resolve("some-other-schema-v1.0.0.json"), "{}");

        assertThrows(IllegalStateException.class, () -> SchemaValidationUtils.validateSchemas(repoRoot));
    }

    // ---------- validateReachabilityMetadataSchema tests ----------

    @Test
    @DisplayName("validateReachabilityMetadataSchema does nothing when neither side provides the schema")
    void validateReachabilitySchema_neitherSideProvided(@TempDir Path tempDir) throws IOException {
        Path repoRoot = tempDir.resolve("repo-none");
        Files.createDirectories(repoRoot.resolve("schemas"));

        // Create fake graal home without schema
        Path nativeImage = createFakeGraalHome(tempDir.resolve("graal-none"), null);

        assertDoesNotThrow(() -> SchemaValidationUtils.validateReachabilityMetadataSchema(repoRoot, 21, nativeImage));
    }

    @Test
    @DisplayName("validateReachabilityMetadataSchema fails when repository provides schema but graal does not")
    void validateReachabilitySchema_repoOnly(@TempDir Path tempDir) throws IOException {
        Path repoRoot = tempDir.resolve("repo-only");
        Path schemas = repoRoot.resolve("schemas");
        Files.createDirectories(schemas);
        // Repo provides reachability schema (version content is used)
        writeJson(schemas.resolve("reachability-metadata-schema-v1.2.0.json"), schemaJson("1.2.0"));

        // Graal without schema
        Path nativeImage = createFakeGraalHome(tempDir.resolve("graal-no-schema"), null);

        assertThrows(IllegalStateException.class, () -> SchemaValidationUtils.validateReachabilityMetadataSchema(repoRoot, 21, nativeImage));
    }

    @Test
    @DisplayName("validateReachabilityMetadataSchema fails when graal provides schema but repository does not")
    void validateReachabilitySchema_graalOnly(@TempDir Path tempDir) throws IOException {
        Path repoRoot = tempDir.resolve("repo-no-schema");
        Files.createDirectories(repoRoot.resolve("schemas"));
        // No reachability schema file in repo

        // Graal provides schema
        Path nativeImage = createFakeGraalHome(tempDir.resolve("graal-only"), "1.2.0");

        assertThrows(IllegalStateException.class, () -> SchemaValidationUtils.validateReachabilityMetadataSchema(repoRoot, 21, nativeImage));
    }

    @Test
    @DisplayName("validateReachabilityMetadataSchema succeeds when versions match")
    void validateReachabilitySchema_match_ok(@TempDir Path tempDir) throws IOException {
        Path repoRoot = tempDir.resolve("repo-match");
        Path schemas = repoRoot.resolve("schemas");
        Files.createDirectories(schemas);
        writeJson(schemas.resolve("reachability-metadata-schema-v1.2.0.json"), schemaJson("1.2.0"));

        Path nativeImage = createFakeGraalHome(tempDir.resolve("graal-match"), "1.2.0");

        assertDoesNotThrow(() -> SchemaValidationUtils.validateReachabilityMetadataSchema(repoRoot, 21, nativeImage));
    }

    @Test
    @DisplayName("validateReachabilityMetadataSchema fails when versions mismatch")
    void validateReachabilitySchema_mismatch(@TempDir Path tempDir) throws IOException {
        Path repoRoot = tempDir.resolve("repo-mismatch");
        Path schemas = repoRoot.resolve("schemas");
        Files.createDirectories(schemas);
        writeJson(schemas.resolve("reachability-metadata-schema-v1.2.0.json"), schemaJson("1.2.0"));

        Path nativeImage = createFakeGraalHome(tempDir.resolve("graal-mismatch"), "1.3.0");

        assertThrows(IllegalStateException.class, () -> SchemaValidationUtils.validateReachabilityMetadataSchema(repoRoot, 21, nativeImage));
    }

    // ---------- Helpers ----------

    private static Path writeJson(Path file, String content) throws IOException {
        Files.createDirectories(file.getParent());
        return Files.writeString(file, content);
    }

    private static String schemaJson(String version) {
        // Minimal JSON containing the "version" field used by the validator
        return "{\n  \"version\": \"" + version + "\"\n}\n";
    }

    /**
     * Creates a fake GraalVM home under graalHomeRoot with:
     * - bin/native-image (empty file)
     * - lib/svm/schemas/reachability-metadata-schema.json (if version != null)
     *
     * Returns the path to bin/native-image, which the validator expects.
     */
    private static Path createFakeGraalHome(Path graalHomeRoot, String version) throws IOException {
        Path bin = graalHomeRoot.resolve("bin");
        Path schemas = graalHomeRoot.resolve("lib/svm/schemas");
        Files.createDirectories(bin);
        Files.createDirectories(schemas);
        Path nativeImage = bin.resolve("native-image");
        Files.writeString(nativeImage, ""); // content is irrelevant for these tests

        if (version != null) {
            Path graalSchema = schemas.resolve("reachability-metadata-schema.json");
            writeJson(graalSchema, schemaJson(version));
        }
        return nativeImage;
    }
}
