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

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Shared support used by Gradle and Maven to report direct runtime dependencies
 * that have no reachability metadata in the configured repository.
 */
public final class MissingMetadataCommandSupport {
    public static final String COMMAND_NAME = "listMissingMetadataLibs";
    public static final String DEFAULT_SCOPE = "direct-runtime";
    public static final String DEFAULT_GITHUB_API_URL = "https://api.github.com";
    public static final String DEFAULT_TARGET_REPOSITORY = "jormundur00/graalvm-reachability-metadata";

    private static final String AUTOMATION_NOTE = "_This issue was created by automation._";
    private static final String ISSUE_TEMPLATE = "01_support_new_library.yml";
    private static final Pattern COORDINATES_PATTERN = Pattern.compile("([A-Za-z0-9_.-]+):([A-Za-z0-9_.-]+)(?::([A-Za-z0-9_.-]+))?");
    private static final long GITHUB_CLI_TIMEOUT_SECONDS = 5;

    private MissingMetadataCommandSupport() {
    }

    public static Report run(Collection<DependencyCoordinate> dependencies,
                             GraalVMReachabilityMetadataRepository repository,
                             Set<String> excludedModules,
                             Map<String, String> forcedVersions,
                             Options options) {
        Objects.requireNonNull(dependencies, "dependencies");
        Objects.requireNonNull(repository, "repository");
        Objects.requireNonNull(options, "options");
        Set<String> effectiveExcludedModules = excludedModules == null ? Collections.emptySet() : excludedModules;
        Map<String, String> effectiveForcedVersions = forcedVersions == null ? Collections.emptyMap() : forcedVersions;
        List<DependencyCoordinate> candidates = dependencies.stream()
            .filter(Objects::nonNull)
            .filter(dependency -> !effectiveExcludedModules.contains(dependency.groupAndArtifact()))
            .sorted()
            .distinct()
            .toList();
        GitHubIssueClient issueClient = new GitHubIssueClient(options);
        Map<String, IssueReference> issueCache = new LinkedHashMap<>();
        List<Result> results = new ArrayList<>(candidates.size());
        for (DependencyCoordinate dependency : candidates) {
            try {
                Set<DirectoryConfiguration> configurations = repository.findConfigurationsFor(query -> {
                    query.forArtifact(artifact -> {
                        artifact.gav(dependency.coordinates());
                        String forcedVersion = effectiveForcedVersions.get(dependency.groupAndArtifact());
                        if (forcedVersion != null) {
                            artifact.forceConfigVersion(forcedVersion);
                        }
                        artifact.useLatestConfigWhenVersionIsUntested();
                    });
                });
                if (!configurations.isEmpty()) {
                    results.add(Result.supported(dependency));
                    continue;
                }
                IssueReference issue = issueCache.computeIfAbsent(dependency.groupAndArtifact(), ga ->
                    resolveIssue(issueClient, dependency)
                );
                results.add(Result.missing(dependency, issue));
            } catch (Exception ex) {
                results.add(Result.error(dependency, ex));
            }
        }
        return new Report(options, candidates.size(), results);
    }

    private static IssueReference resolveIssue(GitHubIssueClient issueClient, DependencyCoordinate dependency) {
        Optional<IssueReference> existing = issueClient.findOpenIssue(dependency);
        if (existing.isPresent()) {
            return existing.get();
        }
        if (issueClient.options.createIssues()) {
            return issueClient.createIssue(dependency);
        }
        return issueClient.newIssueLink(dependency);
    }

    public static final class Options {
        private final String buildTool;
        private final String projectName;
        private final String metadataRepositoryUri;
        private final boolean createIssues;
        private final String githubToken;
        private final String targetRepository;
        private final String githubApiUrl;
        private final Clock clock;
        private final GitHubCliTokenSupplier gitHubCliTokenSupplier;

        public Options(String buildTool,
                       String projectName,
                       String metadataRepositoryUri,
                       boolean createIssues,
                       String githubToken,
                       String targetRepository,
                       String githubApiUrl,
                       Clock clock) {
            this(buildTool, projectName, metadataRepositoryUri, createIssues, githubToken, targetRepository, githubApiUrl, clock,
                GitHubCliTokenSupplier.DEFAULT);
        }

        Options(String buildTool,
                String projectName,
                String metadataRepositoryUri,
                boolean createIssues,
                String githubToken,
                String targetRepository,
                String githubApiUrl,
                Clock clock,
                GitHubCliTokenSupplier gitHubCliTokenSupplier) {
            this.buildTool = Objects.requireNonNull(buildTool, "buildTool");
            this.projectName = Objects.requireNonNull(projectName, "projectName");
            this.metadataRepositoryUri = metadataRepositoryUri;
            this.createIssues = createIssues;
            this.githubToken = blankToNull(githubToken);
            this.targetRepository = blankToNull(targetRepository) == null ? DEFAULT_TARGET_REPOSITORY : targetRepository;
            this.githubApiUrl = blankToNull(githubApiUrl) == null ? DEFAULT_GITHUB_API_URL : stripTrailingSlash(githubApiUrl);
            this.clock = clock == null ? Clock.systemUTC() : clock;
            this.gitHubCliTokenSupplier = Objects.requireNonNull(gitHubCliTokenSupplier, "gitHubCliTokenSupplier");
        }

        public String buildTool() {
            return buildTool;
        }

        public String projectName() {
            return projectName;
        }

        public String metadataRepositoryUri() {
            return metadataRepositoryUri;
        }

        public boolean createIssues() {
            return createIssues;
        }

        public String githubToken() {
            return githubToken;
        }

        public String targetRepository() {
            return targetRepository;
        }

        public String githubApiUrl() {
            return githubApiUrl;
        }

        public Clock clock() {
            return clock;
        }

        GitHubCliTokenSupplier gitHubCliTokenSupplier() {
            return gitHubCliTokenSupplier;
        }
    }

    public record DependencyCoordinate(String groupId, String artifactId, String version)
        implements Comparable<DependencyCoordinate> {
        public DependencyCoordinate {
            Objects.requireNonNull(groupId, "groupId");
            Objects.requireNonNull(artifactId, "artifactId");
            Objects.requireNonNull(version, "version");
        }

        public String coordinates() {
            return groupId + ":" + artifactId + ":" + version;
        }

        public String groupAndArtifact() {
            return groupId + ":" + artifactId;
        }

        @Override
        public String toString() {
            return coordinates();
        }

        @Override
        public int compareTo(DependencyCoordinate other) {
            return Comparator.comparing(DependencyCoordinate::coordinates).compare(this, other);
        }
    }

    public enum Status {
        SUPPORTED("supported"),
        MISSING("missing"),
        ERROR("error");

        private final String jsonValue;

        Status(String jsonValue) {
            this.jsonValue = jsonValue;
        }

        public String jsonValue() {
            return jsonValue;
        }
    }

    public enum IssueStatus {
        EXISTING_OPEN_ISSUE("existing_open_issue"),
        NEW_ISSUE_LINK_GENERATED("new_issue_link_generated"),
        CREATED_ISSUE("created_issue");

        private final String jsonValue;

        IssueStatus(String jsonValue) {
            this.jsonValue = jsonValue;
        }

        public String jsonValue() {
            return jsonValue;
        }
    }

    public static final class Result {
        private final DependencyCoordinate dependency;
        private final Status status;
        private final IssueStatus issueStatus;
        private final String issueUrl;
        private final Integer issueNumber;
        private final String errorMessage;

        private Result(DependencyCoordinate dependency,
                       Status status,
                       IssueStatus issueStatus,
                       String issueUrl,
                       Integer issueNumber,
                       String errorMessage) {
            this.dependency = dependency;
            this.status = status;
            this.issueStatus = issueStatus;
            this.issueUrl = issueUrl;
            this.issueNumber = issueNumber;
            this.errorMessage = errorMessage;
        }

        public static Result supported(DependencyCoordinate dependency) {
            return new Result(dependency, Status.SUPPORTED, null, null, null, null);
        }

        public static Result missing(DependencyCoordinate dependency, IssueReference issueReference) {
            return new Result(
                dependency,
                Status.MISSING,
                issueReference.issueStatus(),
                issueReference.issueUrl(),
                issueReference.issueNumber(),
                null
            );
        }

        public static Result error(DependencyCoordinate dependency, Exception ex) {
            return new Result(
                dependency,
                Status.ERROR,
                null,
                null,
                null,
                ex.getMessage() == null ? ex.getClass().getName() : ex.getMessage()
            );
        }

        public DependencyCoordinate dependency() {
            return dependency;
        }

        public Status status() {
            return status;
        }

        public Optional<IssueStatus> issueStatus() {
            return Optional.ofNullable(issueStatus);
        }

        public Optional<String> issueUrl() {
            return Optional.ofNullable(issueUrl);
        }

        public Optional<Integer> issueNumber() {
            return Optional.ofNullable(issueNumber);
        }

        public Optional<String> errorMessage() {
            return Optional.ofNullable(errorMessage);
        }

        private JSONObject toJson() {
            JSONObject json = new JSONObject();
            json.put("coordinates", dependency.coordinates());
            json.put("scope", DEFAULT_SCOPE);
            json.put("status", status.jsonValue());
            if (issueStatus != null) {
                json.put("issueStatus", issueStatus.jsonValue());
            }
            if (issueUrl != null) {
                json.put("issueUrl", issueUrl);
            }
            if (issueNumber != null) {
                json.put("issueNumber", issueNumber);
            }
            if (errorMessage != null) {
                json.put("error", errorMessage);
            }
            return json;
        }
    }

    public static final class Report {
        private final Options options;
        private final int scanned;
        private final Instant scannedAt;
        private final List<Result> results;

        private Report(Options options, int scanned, List<Result> results) {
            this.options = options;
            this.scanned = scanned;
            this.scannedAt = Instant.now(options.clock());
            this.results = List.copyOf(results);
        }

        public List<Result> results() {
            return results;
        }

        public String toJsonString() {
            return toJson().toString(2);
        }

        public String renderConsoleOutput() {
            List<Result> missingResults = results.stream()
                .filter(result -> result.status == Status.MISSING || result.status == Status.ERROR)
                .toList();
            if (missingResults.isEmpty()) {
                return "No missing metadata libraries found.\nScanned: " + scanned + ", supported: " + supportedCount()
                    + ", missing: 0, existing issues: 0, new issue links: 0, created issues: 0, errors: 0";
            }
            StringBuilder out = new StringBuilder();
            out.append("Missing metadata libraries:\n");
            for (int i = 0; i < missingResults.size(); i++) {
                Result result = missingResults.get(i);
                out.append("- ").append(result.dependency.coordinates()).append('\n');
                if (result.status == Status.ERROR) {
                    out.append("  Error: ").append(result.errorMessage().orElse("Unknown error")).append('\n');
                } else if (result.issueStatus == IssueStatus.EXISTING_OPEN_ISSUE) {
                    out.append("  Existing ticket: ").append(result.issueUrl).append('\n');
                } else if (result.issueStatus == IssueStatus.CREATED_ISSUE) {
                    out.append("  Created ticket: ").append(result.issueUrl).append('\n');
                } else if (result.issueStatus == IssueStatus.NEW_ISSUE_LINK_GENERATED) {
                    out.append("  Open ticket: ").append(result.issueUrl).append('\n');
                }
                if (i < missingResults.size() - 1) {
                    out.append('\n');
                }
            }
            out.append('\n');
            out.append("Scanned: ").append(scanned)
                .append(", supported: ").append(supportedCount())
                .append(", missing: ").append(missingCount())
                .append(", existing issues: ").append(existingIssueCount())
                .append(", new issue links: ").append(newIssueLinkCount())
                .append(", created issues: ").append(createdIssueCount())
                .append(", errors: ").append(errorCount());
            return out.toString();
        }

        private JSONObject toJson() {
            JSONObject json = new JSONObject();
            json.put("command", COMMAND_NAME);
            json.put("mode", options.createIssues() ? "create" : "list");
            json.put("targetRepository", options.targetRepository());
            if (options.metadataRepositoryUri() != null) {
                json.put("metadataRepositoryUri", options.metadataRepositoryUri());
            }
            json.put("scannedAt", DateTimeFormatter.ISO_INSTANT.format(scannedAt));
            JSONObject project = new JSONObject();
            project.put("buildTool", options.buildTool().toLowerCase(Locale.US));
            project.put("name", options.projectName());
            json.put("project", project);
            JSONObject summary = new JSONObject();
            summary.put("scanned", scanned);
            summary.put("supported", supportedCount());
            summary.put("missing", missingCount());
            summary.put("existingOpenIssue", existingIssueCount());
            summary.put("newIssueLinks", newIssueLinkCount());
            summary.put("createdIssues", createdIssueCount());
            summary.put("errors", errorCount());
            json.put("summary", summary);
            JSONArray resultsJson = new JSONArray();
            for (Result result : results) {
                resultsJson.put(result.toJson());
            }
            json.put("results", resultsJson);
            return json;
        }

        private long supportedCount() {
            return results.stream().filter(result -> result.status == Status.SUPPORTED).count();
        }

        private long missingCount() {
            return results.stream().filter(result -> result.status == Status.MISSING).count();
        }

        private long existingIssueCount() {
            return results.stream().filter(result -> result.issueStatus == IssueStatus.EXISTING_OPEN_ISSUE).count();
        }

        private long newIssueLinkCount() {
            return results.stream().filter(result -> result.issueStatus == IssueStatus.NEW_ISSUE_LINK_GENERATED).count();
        }

        private long createdIssueCount() {
            return results.stream().filter(result -> result.issueStatus == IssueStatus.CREATED_ISSUE).count();
        }

        private long errorCount() {
            return results.stream().filter(result -> result.status == Status.ERROR).count();
        }
    }

    private record IssueReference(IssueStatus issueStatus, String issueUrl, Integer issueNumber) {
        private IssueReference {
            Objects.requireNonNull(issueStatus, "issueStatus");
            Objects.requireNonNull(issueUrl, "issueUrl");
        }
    }

    private static final class GitHubIssueClient {
        private final Options options;
        private final HttpClient client;
        private final URI apiBaseUri;
        private final URI htmlBaseUri;
        private final String githubToken;

        private GitHubIssueClient(Options options) {
            this.options = options;
            this.client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
            this.apiBaseUri = URI.create(options.githubApiUrl());
            this.htmlBaseUri = htmlBaseUri(options.githubApiUrl());
            this.githubToken = resolveGithubToken(options.githubToken(), options.gitHubCliTokenSupplier());
            if (options.createIssues() && githubToken == null) {
                throw new IllegalArgumentException(missingGithubTokenMessage(options.buildTool()));
            }
        }

        private static String missingGithubTokenMessage(String buildTool) {
            if ("gradle".equals(buildTool)) {
                return "createIssues=true requires a GitHub token. Provide it with -PgithubToken=..., the GITHUB_TOKEN/GH_TOKEN environment variable, or authenticate with GitHub CLI via `gh auth login`.";
            }
            if ("maven".equals(buildTool)) {
                return "createIssues=true requires a GitHub token. Provide it with -DgithubToken=..., the GITHUB_TOKEN/GH_TOKEN environment variable, or authenticate with GitHub CLI via `gh auth login`.";
            }
            return "createIssues=true requires a GitHub token. Provide it with githubToken, the GITHUB_TOKEN/GH_TOKEN environment variable, or authenticate with GitHub CLI via `gh auth login`.";
        }

        private Optional<IssueReference> findOpenIssue(DependencyCoordinate dependency) {
            try {
                String query = "repo:" + options.targetRepository()
                    + " is:issue is:open label:library-new-request \"" + dependency.groupAndArtifact() + "\"";
                URI uri = URI.create(apiBaseUri + "/search/issues?q=" + encode(query) + "&per_page=20");
                JSONObject json = send(request(uri).GET().build(), false);
                JSONArray items = json.optJSONArray("items");
                if (items == null) {
                    return Optional.empty();
                }
                for (int i = 0; i < items.length(); i++) {
                    JSONObject item = items.getJSONObject(i);
                    if (referencesGroupAndArtifact(item.optString("title"), item.optString("body"), dependency.groupAndArtifact())) {
                        return Optional.of(new IssueReference(
                            IssueStatus.EXISTING_OPEN_ISSUE,
                            item.getString("html_url"),
                            item.optInt("number")
                        ));
                    }
                }
                return Optional.empty();
            } catch (RuntimeException ex) {
                if (options.createIssues()) {
                    throw ex;
                }
                return Optional.empty();
            }
        }

        private IssueReference newIssueLink(DependencyCoordinate dependency) {
            String issueUrl = htmlBaseUri + "/" + options.targetRepository() + "/issues/new?template=" + encode(ISSUE_TEMPLATE)
                + "&title=" + encode(issueTitle(dependency))
                + "&maven_coordinates=" + encode(dependency.coordinates());
            return new IssueReference(IssueStatus.NEW_ISSUE_LINK_GENERATED, issueUrl, null);
        }

        private IssueReference createIssue(DependencyCoordinate dependency) {
            URI uri = URI.create(apiBaseUri + "/repos/" + options.targetRepository() + "/issues");
            JSONObject body = new JSONObject();
            body.put("title", issueTitle(dependency));
            body.put("body", issueBody(dependency));
            JSONArray labels = new JSONArray();
            labels.put("library-new-request");
            body.put("labels", labels);
            JSONObject response = send(
                request(uri)
                    .POST(HttpRequest.BodyPublishers.ofString(body.toString()))
                    .build(),
                true
            );
            return new IssueReference(
                IssueStatus.CREATED_ISSUE,
                response.getString("html_url"),
                response.optInt("number")
            );
        }

        private HttpRequest.Builder request(URI uri) {
            HttpRequest.Builder builder = HttpRequest.newBuilder(uri)
                .timeout(Duration.ofSeconds(20))
                .header("Accept", "application/vnd.github+json")
                .header("Content-Type", "application/json")
                .header("User-Agent", "native-build-tools-missing-metadata");
            if (githubToken != null) {
                builder.header("Authorization", "Bearer " + githubToken);
            }
            return builder;
        }

        private JSONObject send(HttpRequest request, boolean authenticatedOperation) {
            try {
                HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
                int statusCode = response.statusCode();
                if (statusCode >= 200 && statusCode < 300) {
                    return new JSONObject(response.body());
                }
                if (!authenticatedOperation && (statusCode == 401 || statusCode == 403)) {
                    throw new GitHubApiException("GitHub API lookup failed with status " + statusCode);
                }
                throw new GitHubApiException("GitHub API request failed with status " + statusCode + ": " + response.body());
            } catch (IOException ex) {
                throw new UncheckedIOException(ex);
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                throw new RuntimeException(ex);
            }
        }
    }

    private static String issueTitle(DependencyCoordinate dependency) {
        return "Support for " + dependency.coordinates();
    }

    private static String issueBody(DependencyCoordinate dependency) {
        return "### Full Maven coordinates\n\n"
            + dependency.coordinates()
            + "\n\n"
            + AUTOMATION_NOTE + '\n';
    }

    static boolean referencesGroupAndArtifact(String title, String body, String groupAndArtifact) {
        Set<String> referencedModules = new LinkedHashSet<>();
        collectGroupAndArtifact(title, referencedModules);
        collectGroupAndArtifact(body, referencedModules);
        return referencedModules.contains(groupAndArtifact);
    }

    private static void collectGroupAndArtifact(String text, Set<String> destinations) {
        if (text == null || text.isBlank()) {
            return;
        }
        Matcher matcher = COORDINATES_PATTERN.matcher(text);
        while (matcher.find()) {
            destinations.add(matcher.group(1) + ":" + matcher.group(2));
        }
    }

    private static URI htmlBaseUri(String githubApiUrl) {
        URI apiUri = URI.create(githubApiUrl);
        if ("api.github.com".equalsIgnoreCase(apiUri.getHost())) {
            return URI.create("https://github.com");
        }
        String path = Optional.ofNullable(apiUri.getPath()).orElse("");
        if (path.endsWith("/api/v3")) {
            path = path.substring(0, path.length() - "/api/v3".length());
        }
        if (path.endsWith("/")) {
            path = path.substring(0, path.length() - 1);
        }
        StringBuilder builder = new StringBuilder();
        builder.append(apiUri.getScheme()).append("://").append(apiUri.getAuthority());
        if (!path.isEmpty()) {
            builder.append(path);
        }
        return URI.create(builder.toString());
    }

    private static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    private static String stripTrailingSlash(String value) {
        if (value.endsWith("/")) {
            return value.substring(0, value.length() - 1);
        }
        return value;
    }

    private static String blankToNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value;
    }

    static String resolveGithubToken(String explicitToken) {
        return resolveGithubToken(explicitToken, GitHubCliTokenSupplier.DEFAULT);
    }

    static String resolveGithubToken(String explicitToken, GitHubCliTokenSupplier gitHubCliTokenSupplier) {
        String token = blankToNull(explicitToken);
        if (token != null) {
            return token;
        }
        String envToken = blankToNull(System.getenv("GITHUB_TOKEN"));
        if (envToken != null) {
            return envToken;
        }
        String ghToken = blankToNull(System.getenv("GH_TOKEN"));
        if (ghToken != null) {
            return ghToken;
        }
        return blankToNull(gitHubCliTokenSupplier.get());
    }

    @FunctionalInterface
    interface GitHubCliTokenSupplier {
        GitHubCliTokenSupplier DEFAULT = () -> {
            try {
                Process process = new ProcessBuilder("gh", "auth", "token")
                    .redirectErrorStream(true)
                    .start();
                try {
                    if (!process.waitFor(GITHUB_CLI_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                        process.destroyForcibly();
                        return null;
                    }
                    if (process.exitValue() != 0) {
                        return null;
                    }
                    return new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8).trim();
                } finally {
                    process.getInputStream().close();
                }
            } catch (IOException ex) {
                return null;
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                return null;
            }
        };

        String get();
    }

    private static final class GitHubApiException extends RuntimeException {
        private GitHubApiException(String message) {
            super(message);
        }
    }
}
