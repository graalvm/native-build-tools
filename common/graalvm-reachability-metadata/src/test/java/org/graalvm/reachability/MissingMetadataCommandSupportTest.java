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
package org.graalvm.reachability;

import com.github.openjson.JSONArray;
import com.github.openjson.JSONObject;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MissingMetadataCommandSupportTest {
    @Test
    void detectsCoordinatesInIssueTitleAndBody() {
        assertTrue(MissingMetadataCommandSupport.referencesGroupAndArtifact(
            "Support for org.example:demo-lib:1.2.3",
            "",
            "org.example:demo-lib"
        ));
        assertTrue(MissingMetadataCommandSupport.referencesGroupAndArtifact(
            "Different title",
            "### Full Maven coordinates\n\norg.example:demo-lib:1.2.3\n",
            "org.example:demo-lib"
        ));
    }

    @Test
    void reportsSupportedAndMissingDependenciesAndGeneratesPrefilledLinks() {
        MissingMetadataCommandSupport.Report report = MissingMetadataCommandSupport.run(
            List.of(
                new MissingMetadataCommandSupport.DependencyCoordinate("org.example", "supported-lib", "1.0.0"),
                new MissingMetadataCommandSupport.DependencyCoordinate("org.example", "missing-lib", "2.0.0")
            ),
            new TestRepository(Set.of("org.example:supported-lib:1.0.0")),
            Set.of(),
            java.util.Map.of(),
            new MissingMetadataCommandSupport.Options(
                "gradle",
                "demo-app",
                "file:///tmp/repo",
                false,
                null,
                MissingMetadataCommandSupport.DEFAULT_TARGET_REPOSITORY,
                "http://127.0.0.1:9/api/v3",
                Clock.fixed(Instant.parse("2026-04-09T10:00:00Z"), ZoneOffset.UTC)
            )
        );

        JSONObject json = new JSONObject(report.toJsonString());
        JSONObject summary = json.getJSONObject("summary");
        JSONArray results = json.getJSONArray("results");

        assertEquals(2, summary.getInt("scanned"));
        assertEquals(1, summary.getInt("supported"));
        assertEquals(1, summary.getInt("missing"));
        assertEquals(1, summary.getInt("newIssueLinks"));
        assertEquals(0, summary.getInt("errors"));
        assertEquals(MissingMetadataCommandSupport.REPORT_SCHEMA_URI, json.getString("$schema"));
        assertEquals("2026-04-09T10:00:00Z", json.getString("scannedAt"));
        JSONObject missing = findByCoordinates(results, "org.example:missing-lib:2.0.0");
        JSONObject supported = findByCoordinates(results, "org.example:supported-lib:1.0.0");
        assertEquals("supported", supported.getString("status"));
        assertEquals("missing", missing.getString("status"));
        assertEquals("new_issue_link_generated", missing.getString("issueStatus"));
        String missingUrl = missing.getString("issueUrl");
        assertTrue(missingUrl.contains("/" + MissingMetadataCommandSupport.DEFAULT_TARGET_REPOSITORY + "/issues/new?template="));
        assertTrue(missingUrl.contains("title=Support+for+org.example:missing-lib:2.0.0"),
            "title should keep colons readable, was: " + missingUrl);
        assertTrue(missingUrl.contains("maven_coordinates=org.example%3Amissing-lib%3A2.0.0"),
            "maven_coordinates= should prefill the issue form field, was: " + missingUrl);
        String console = report.renderConsoleOutput();
        assertTrue(console.contains("Missing metadata libraries: 1 of 2 scanned"),
            "console should headline missing/scanned counts, was:\n" + console);
        assertTrue(console.contains("To request support for this library automatically, re-run with createIssues=true:"),
            "console should include the createIssues=true call to action, was:\n" + console);
        assertTrue(console.contains("./gradlew listLibrariesMissingMetadata -PcreateIssues=true -PgithubToken=<token>"),
            "console should print the Gradle CTA command, was:\n" + console);
        assertTrue(console.contains("-PgithubToken=...  ->  $GITHUB_TOKEN  ->  $GH_TOKEN  ->  `gh auth token`"),
            "console should print the Gradle token-resolution order, was:\n" + console);
        assertTrue(console.contains("-> request support [1]"),
            "console should label manual entries with footnote indices, was:\n" + console);
        assertTrue(console.contains("[1] " + missingUrl),
            "console should list the prefilled issue URL as a numbered footnote, was:\n" + console);
    }

    @Test
    void reusesExistingOpenIssueAndSuppressesDuplicateSearchesByGroupAndArtifact() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        AtomicInteger searches = new AtomicInteger();
        server.createContext("/api/v3/search/issues", exchange -> {
            searches.incrementAndGet();
            writeJson(exchange, """
                {
                  "items": [
                    {
                      "number": 42,
                      "html_url": "http://localhost:%d/test/repo/issues/42",
                      "title": "Support for org.example:duplicate-lib:1.0.0",
                      "body": "### Full Maven coordinates\\n\\norg.example:duplicate-lib:1.0.0\\n"
                    }
                  ]
                }
                """.formatted(server.getAddress().getPort()));
        });
        server.start();
        try {
            MissingMetadataCommandSupport.Report report = MissingMetadataCommandSupport.run(
                List.of(
                    new MissingMetadataCommandSupport.DependencyCoordinate("org.example", "duplicate-lib", "1.0.0"),
                    new MissingMetadataCommandSupport.DependencyCoordinate("org.example", "duplicate-lib", "2.0.0")
                ),
                new TestRepository(Set.of()),
                Set.of(),
                java.util.Map.of(),
                new MissingMetadataCommandSupport.Options(
                    "maven",
                    "demo-app",
                    "file:///tmp/repo",
                    false,
                    null,
                    "test/repo",
                    "http://localhost:" + server.getAddress().getPort() + "/api/v3",
                    Clock.fixed(Instant.parse("2026-04-09T10:00:00Z"), ZoneOffset.UTC)
                )
            );

            JSONArray results = new JSONObject(report.toJsonString()).getJSONArray("results");
            assertEquals(1, searches.get());
            assertEquals("existing_open_issue", results.getJSONObject(0).getString("issueStatus"));
            assertEquals("existing_open_issue", results.getJSONObject(1).getString("issueStatus"));
            assertEquals("http://localhost:" + server.getAddress().getPort() + "/test/repo/issues/42",
                results.getJSONObject(0).getString("issueUrl"));
            String console = report.renderConsoleOutput();
            String issueUrl = "http://localhost:" + server.getAddress().getPort() + "/test/repo/issues/42";
            assertTrue(console.contains("Missing metadata libraries: 2 of 2 scanned (2 already requested)"),
                "headline should mention the existing-request count, was:\n" + console);
            assertTrue(console.contains("Already requested (no action needed):"), console);
            assertTrue(console.contains("- org.example:duplicate-lib:1.0.0  -> existing request [E1]"), console);
            assertTrue(console.contains("- org.example:duplicate-lib:2.0.0  -> existing request [E2]"), console);
            assertTrue(console.contains("[E1] " + issueUrl), console);
            assertTrue(console.contains("[E2] " + issueUrl), console);
        } finally {
            server.stop(0);
        }
    }

    @Test
    void explainsHowToProvideGithubTokenForGradle() {
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () ->
            MissingMetadataCommandSupport.run(
                List.of(new MissingMetadataCommandSupport.DependencyCoordinate("org.example", "missing-lib", "1.0.0")),
                new TestRepository(Set.of()),
                Set.of(),
                java.util.Map.of(),
                new MissingMetadataCommandSupport.Options(
                    "gradle",
                    "demo-app",
                    "file:///tmp/repo",
                    true,
                    null,
                    "test/repo",
                    "http://127.0.0.1:9/api/v3",
                    Clock.fixed(Instant.parse("2026-04-09T10:00:00Z"), ZoneOffset.UTC),
                    hostname -> null
                )
            )
        );

        assertEquals(
            "createIssues=true requires a GitHub token. Provide it with -PgithubToken=..., the GITHUB_TOKEN/GH_TOKEN environment variable, or authenticate with GitHub CLI via `gh auth login`.",
            exception.getMessage()
        );
    }

    @Test
    void explainsHowToProvideGithubTokenForMaven() {
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () ->
            MissingMetadataCommandSupport.run(
                List.of(new MissingMetadataCommandSupport.DependencyCoordinate("org.example", "missing-lib", "1.0.0")),
                new TestRepository(Set.of()),
                Set.of(),
                java.util.Map.of(),
                new MissingMetadataCommandSupport.Options(
                    "maven",
                    "demo-app",
                    "file:///tmp/repo",
                    true,
                    null,
                    "test/repo",
                    "http://127.0.0.1:9/api/v3",
                    Clock.fixed(Instant.parse("2026-04-09T10:00:00Z"), ZoneOffset.UTC),
                    hostname -> null
                )
            )
        );

        assertEquals(
            "createIssues=true requires a GitHub token. Provide it with -DgithubToken=..., the GITHUB_TOKEN/GH_TOKEN environment variable, or authenticate with GitHub CLI via `gh auth login`.",
            exception.getMessage()
        );
    }

    @Test
    void createIssuesModeFailsFastWhenIssueCreationFails() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        AtomicInteger searches = new AtomicInteger();
        AtomicInteger creations = new AtomicInteger();
        server.createContext("/api/v3/search/issues", exchange -> {
            searches.incrementAndGet();
            writeJson(exchange, "{\"items\":[]}");
        });
        server.createContext("/api/v3/repos/test/repo/issues", exchange -> {
            creations.incrementAndGet();
            byte[] content = "{\"message\":\"boom\"}".getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(500, content.length);
            try (OutputStream outputStream = exchange.getResponseBody()) {
                outputStream.write(content);
            }
        });
        server.start();
        try {
            RuntimeException exception = assertThrows(RuntimeException.class, () ->
                MissingMetadataCommandSupport.run(
                    List.of(new MissingMetadataCommandSupport.DependencyCoordinate("org.example", "missing-lib", "1.0.0")),
                    new TestRepository(Set.of()),
                    Set.of(),
                    java.util.Map.of(),
                    new MissingMetadataCommandSupport.Options(
                        "gradle",
                        "demo-app",
                        "file:///tmp/repo",
                        true,
                        "gho_test_token",
                        "test/repo",
                        "http://localhost:" + server.getAddress().getPort() + "/api/v3",
                        Clock.fixed(Instant.parse("2026-04-09T10:00:00Z"), ZoneOffset.UTC)
                    )
                )
            );

            assertEquals(1, searches.get());
            assertEquals(1, creations.get());
            assertTrue(exception.getMessage().contains("createIssues=true failed for org.example:missing-lib:1.0.0"));
            assertTrue(exception.getMessage().contains("GitHub API request failed with status 500"));
        } finally {
            server.stop(0);
        }
    }

    @Test
    void forcedConfigVersionIsNotClobberedByLatestFallback() {
        RecordingRepository repository = new RecordingRepository();
        MissingMetadataCommandSupport.run(
            List.of(
                new MissingMetadataCommandSupport.DependencyCoordinate("org.example", "pinned-lib", "9.9.9"),
                new MissingMetadataCommandSupport.DependencyCoordinate("org.example", "other-lib", "1.0.0")
            ),
            repository,
            Set.of(),
            java.util.Map.of("org.example:pinned-lib", "3.2.1"),
            new MissingMetadataCommandSupport.Options(
                "gradle",
                "demo-app",
                "file:///tmp/repo",
                false,
                null,
                "test/repo",
                "http://127.0.0.1:9/api/v3",
                Clock.fixed(Instant.parse("2026-04-09T10:00:00Z"), ZoneOffset.UTC)
            )
        );

        RecordingArtifactQuery pinned = repository.queriesByGroupAndArtifact.get("org.example:pinned-lib");
        assertEquals("3.2.1", pinned.forcedConfigVersion,
            "forced version must survive - useLatestConfigWhenVersionIsUntested must not clobber it");
        assertTrue(!pinned.useLatest,
            "when a forced version is supplied, the latest-fallback flag must not be enabled");

        RecordingArtifactQuery unpinned = repository.queriesByGroupAndArtifact.get("org.example:other-lib");
        assertEquals(null, unpinned.forcedConfigVersion);
        assertTrue(unpinned.useLatest,
            "without a forced version, latest-fallback should still be enabled");
    }

    @Test
    void resolvesGithubTokenFromGithubCliWhenNoExplicitTokenIsProvided() {
        assertEquals(
            "gho_test_token",
            MissingMetadataCommandSupport.resolveGithubToken(null, "https://api.github.com", hostname -> "gho_test_token")
        );
    }

    @Test
    void explicitGithubTokenWinsOverGithubCliAuthentication() {
        assertEquals(
            "explicit-token",
            MissingMetadataCommandSupport.resolveGithubToken("explicit-token", "https://api.github.com", hostname -> "gho_test_token")
        );
    }

    @Test
    void passesEnterpriseHostToGithubCli() {
        java.util.concurrent.atomic.AtomicReference<String> seenHostname = new java.util.concurrent.atomic.AtomicReference<>();
        MissingMetadataCommandSupport.resolveGithubToken(
            null,
            "https://ghe.example.com/api/v3",
            hostname -> {
                seenHostname.set(hostname);
                return "gho_ghe_token";
            }
        );
        assertEquals("ghe.example.com", seenHostname.get());
    }

    @Test
    void mapsPublicGithubApiUrlToGithubDotComHostForGithubCli() {
        java.util.concurrent.atomic.AtomicReference<String> seenHostname = new java.util.concurrent.atomic.AtomicReference<>();
        MissingMetadataCommandSupport.resolveGithubToken(
            null,
            "https://api.github.com",
            hostname -> {
                seenHostname.set(hostname);
                return "gho_public_token";
            }
        );
        assertEquals("github.com", seenHostname.get());
    }

    private static void writeJson(HttpExchange exchange, String json) throws IOException {
        byte[] content = json.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "application/json");
        exchange.sendResponseHeaders(200, content.length);
        try (OutputStream outputStream = exchange.getResponseBody()) {
            outputStream.write(content);
        }
    }

    private static JSONObject findByCoordinates(JSONArray results, String coordinates) {
        for (int i = 0; i < results.length(); i++) {
            JSONObject result = results.getJSONObject(i);
            if (coordinates.equals(result.getString("coordinates"))) {
                return result;
            }
        }
        throw new AssertionError("Missing result for " + coordinates);
    }

    private static final class TestRepository implements GraalVMReachabilityMetadataRepository {
        private final Set<String> supportedCoordinates;

        private TestRepository(Set<String> supportedCoordinates) {
            this.supportedCoordinates = new HashSet<>(supportedCoordinates);
        }

        @Override
        public Set<DirectoryConfiguration> findConfigurationsFor(Consumer<? super Query> queryBuilder) {
            CapturingQuery query = new CapturingQuery();
            queryBuilder.accept(query);
            Set<DirectoryConfiguration> result = new HashSet<>();
            for (String coordinates : query.coordinates()) {
                if (supportedCoordinates.contains(coordinates)) {
                    String[] parts = coordinates.split(":");
                    result.add(new DirectoryConfiguration(parts[0], parts[1], parts[2], Path.of("."), false));
                }
            }
            return result;
        }
    }

    private static final class CapturingQuery implements Query {
        private final List<String> coordinates = new ArrayList<>();

        @Override
        public void forArtifacts(String... gavCoordinates) {
            java.util.Collections.addAll(coordinates, gavCoordinates);
        }

        @Override
        public void forArtifact(Consumer<? super ArtifactQuery> config) {
            CapturingArtifactQuery query = new CapturingArtifactQuery();
            config.accept(query);
            coordinates.add(query.coordinates);
        }

        @Override
        public void useLatestConfigWhenVersionIsUntested() {
        }

        private List<String> coordinates() {
            return coordinates;
        }
    }

    private static final class RecordingRepository implements GraalVMReachabilityMetadataRepository {
        private final java.util.Map<String, RecordingArtifactQuery> queriesByGroupAndArtifact = new java.util.LinkedHashMap<>();

        @Override
        public Set<DirectoryConfiguration> findConfigurationsFor(Consumer<? super Query> queryBuilder) {
            RecordingQuery query = new RecordingQuery();
            queryBuilder.accept(query);
            for (RecordingArtifactQuery artifactQuery : query.artifactQueries) {
                String[] parts = artifactQuery.coordinates.split(":");
                queriesByGroupAndArtifact.put(parts[0] + ":" + parts[1], artifactQuery);
            }
            return Set.of();
        }
    }

    private static final class RecordingQuery implements Query {
        private final List<RecordingArtifactQuery> artifactQueries = new ArrayList<>();

        @Override
        public void forArtifacts(String... gavCoordinates) {
            for (String gav : gavCoordinates) {
                RecordingArtifactQuery q = new RecordingArtifactQuery();
                q.coordinates = gav;
                artifactQueries.add(q);
            }
        }

        @Override
        public void forArtifact(Consumer<? super ArtifactQuery> config) {
            RecordingArtifactQuery q = new RecordingArtifactQuery();
            config.accept(q);
            artifactQueries.add(q);
        }

        @Override
        public void useLatestConfigWhenVersionIsUntested() {
        }
    }

    private static final class RecordingArtifactQuery implements Query.ArtifactQuery {
        private String coordinates;
        private String forcedConfigVersion;
        private boolean useLatest;

        @Override
        public void gav(String gavCoordinates) {
            this.coordinates = gavCoordinates;
        }

        @Override
        public void useLatestConfigWhenVersionIsUntested() {
            useLatest = true;
        }

        @Override
        public void doNotUseLatestConfigWhenVersionIsUntested() {
            useLatest = false;
        }

        @Override
        public void forceConfigVersion(String version) {
            forcedConfigVersion = version;
        }
    }

    private static final class CapturingArtifactQuery implements Query.ArtifactQuery {
        private String coordinates;

        @Override
        public void gav(String gavCoordinates) {
            this.coordinates = gavCoordinates;
        }

        @Override
        public void useLatestConfigWhenVersionIsUntested() {
        }

        @Override
        public void doNotUseLatestConfigWhenVersionIsUntested() {
        }

        @Override
        public void forceConfigVersion(String version) {
        }
    }
}
