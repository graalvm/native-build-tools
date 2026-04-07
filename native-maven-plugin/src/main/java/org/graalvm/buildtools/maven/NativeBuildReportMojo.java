/*
 * Copyright (c) 2026, 2026, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.buildtools.maven;

import org.apache.maven.doxia.sink.Sink;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.MavenProject;
import org.apache.maven.reporting.MavenReport;
import org.apache.maven.reporting.MavenReportException;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * Copies the GraalVM Native Image build report into the Maven site output.
 */
@Mojo(name = "build-report", defaultPhase = LifecyclePhase.SITE, threadSafe = true)
public class NativeBuildReportMojo extends AbstractMojo implements MavenReport {
    private static final Pattern ASSET_REFERENCE_PATTERN = Pattern.compile("(?i)(?:src|href)\\s*=\\s*[\"']([^\"']+)[\"']");

    @Parameter(defaultValue = "${project}", readonly = true, required = true)
    private MavenProject project;

    @Parameter(property = "native.build.report.skip", defaultValue = "false")
    private boolean skip;

    @Parameter(property = "native.build.report.name", defaultValue = "Native Image Build Report")
    private String name;

    @Parameter(property = "native.build.report.description", defaultValue = "Copies the GraalVM Native Image build report into the Maven site.")
    private String description;

    @Parameter(property = "native.build.report.outputDirectory", defaultValue = "native-build-report")
    private String siteReportDirectory;

    @Parameter(property = "outputDir", defaultValue = "${project.build.directory}", required = true)
    private File buildOutputDirectory;

    @Parameter(property = "imageName", defaultValue = "${project.artifactId}")
    private String imageName;

    @Parameter(property = "native.build.report.file")
    private File buildReportFile;

    @Parameter(property = "native.build.report.dynamicAccessMetadataFile", defaultValue = "${project.build.directory}/dynamic-access-metadata.json")
    private File dynamicAccessMetadataFile;

    private File reportOutputDirectory;

    @Override
    public void execute() throws MojoExecutionException {
        try {
            generate(null, Locale.getDefault());
        } catch (MavenReportException e) {
            throw new MojoExecutionException("Unable to generate native build report", e);
        }
    }

    @Override
    public void generate(Sink sink, Locale locale) throws MavenReportException {
        File resolvedReportOutputDirectory = reportOutputDirectory != null
                ? reportOutputDirectory
                : new File(project.getBuild().getDirectory(), "site");
        Path siteDirectory = new File(resolvedReportOutputDirectory, siteReportDirectory).toPath();
        try {
            Files.createDirectories(siteDirectory);
            Optional<Path> sourceReport = resolveBuildReport();
            if (sourceReport.isPresent()) {
                copyReportBundle(sourceReport.get(), siteDirectory);
            } else {
                writeMissingReportPage(siteDirectory.resolve("index.html"));
            }
        } catch (IOException e) {
            throw new MavenReportException("Unable to generate native build report page", e);
        }
    }

    @Override
    public String getOutputName() {
        return siteReportDirectory + File.separator + "index";
    }

    @Override
    public String getCategoryName() {
        return CATEGORY_PROJECT_REPORTS;
    }

    @Override
    public String getName(Locale locale) {
        return name;
    }

    @Override
    public String getDescription(Locale locale) {
        return description;
    }

    @Override
    public void setReportOutputDirectory(File directory) {
        this.reportOutputDirectory = directory;
    }

    @Override
    public File getReportOutputDirectory() {
        return reportOutputDirectory;
    }

    @Override
    public boolean isExternalReport() {
        return true;
    }

    @Override
    public boolean canGenerateReport() {
        return !skip;
    }

    Optional<Path> resolveBuildReport() {
        if (buildReportFile != null) {
            Path configuredReport = buildReportFile.toPath();
            if (Files.isRegularFile(configuredReport)) {
                return Optional.of(configuredReport);
            }
            getLog().warn("Configured build report file does not exist: " + configuredReport);
        }
        return findBuildReport(buildOutputDirectory.toPath(), imageName);
    }

    static Optional<Path> findBuildReport(Path buildDirectory, String imageName) {
        if (!Files.isDirectory(buildDirectory)) {
            return Optional.empty();
        }
        try (Stream<Path> stream = Files.list(buildDirectory)) {
            return stream
                    .filter(Files::isRegularFile)
                    .filter(NativeBuildReportMojo::isHtmlFile)
                    .max(Comparator.<Path>comparingInt(path -> buildReportScore(path, imageName))
                            .thenComparing(path -> path.getFileName().toString()));
        } catch (IOException e) {
            return Optional.empty();
        }
    }

    static int buildReportScore(Path candidate, String imageName) {
        String fileName = candidate.getFileName().toString().toLowerCase(Locale.ROOT);
        String normalizedImageName = imageName == null ? "" : imageName.toLowerCase(Locale.ROOT);
        int score = 0;
        if (!normalizedImageName.isEmpty() && (fileName.equals(normalizedImageName + ".html") || fileName.equals(normalizedImageName + ".htm"))) {
            score += 400;
        }
        if (fileName.contains("build-report")) {
            score += 300;
        }
        if (!normalizedImageName.isEmpty() && fileName.contains(normalizedImageName)) {
            score += 200;
        }
        if (fileName.contains("native")) {
            score += 100;
        }
        return score;
    }

    private void copyReportBundle(Path sourceReport, Path siteDirectory) throws IOException {
        Path sourceParent = sourceReport.getParent();
        copyFile(sourceReport, siteDirectory.resolve(sourceReport.getFileName()));
        copyFile(sourceReport, siteDirectory.resolve("index.html"));
        if (sourceParent != null) {
            for (Path asset : findReferencedAssets(sourceReport)) {
                try {
                    Path target = relativizeAgainst(sourceParent, asset, siteDirectory);
                    copyPath(asset, target);
                } catch (IOException ex) {
                    getLog().warn("Unable to copy build report asset " + asset + ": " + ex.getMessage());
                }
            }
        }
        Path dynamicAccessMetadata = dynamicAccessMetadataFile.toPath();
        if (Files.isRegularFile(dynamicAccessMetadata)) {
            copyFile(dynamicAccessMetadata, siteDirectory.resolve(dynamicAccessMetadata.getFileName()));
        }
    }

    static Set<Path> findReferencedAssets(Path sourceReport) throws IOException {
        Set<Path> assets = new LinkedHashSet<>();
        String html = Files.readString(sourceReport, StandardCharsets.UTF_8);
        Matcher matcher = ASSET_REFERENCE_PATTERN.matcher(html);
        Path baseDirectory = sourceReport.getParent();
        while (matcher.find()) {
            String reference = sanitizeReference(matcher.group(1));
            if (reference == null || baseDirectory == null) {
                continue;
            }
            Path asset = baseDirectory.resolve(reference).normalize();
            if (Files.exists(asset) && !asset.equals(sourceReport)) {
                assets.add(asset);
            }
        }
        return assets;
    }

    private static String sanitizeReference(String reference) {
        if (reference == null || reference.isBlank()) {
            return null;
        }
        int fragmentSeparator = reference.indexOf('#');
        if (fragmentSeparator >= 0) {
            reference = reference.substring(0, fragmentSeparator);
        }
        int querySeparator = reference.indexOf('?');
        if (querySeparator >= 0) {
            reference = reference.substring(0, querySeparator);
        }
        if (reference.isBlank() || reference.startsWith("#") || reference.startsWith("/") || reference.startsWith("data:") || reference.startsWith("http:") || reference.startsWith("https:") || reference.startsWith("mailto:") || reference.startsWith("javascript:")) {
            return null;
        }
        return reference;
    }

    private static boolean isHtmlFile(Path path) {
        String fileName = path.getFileName().toString().toLowerCase(Locale.ROOT);
        return fileName.endsWith(".html") || fileName.endsWith(".htm");
    }

    private static Path relativizeAgainst(Path sourceParent, Path source, Path destinationRoot) {
        if (source.startsWith(sourceParent)) {
            return destinationRoot.resolve(sourceParent.relativize(source).toString());
        }
        return destinationRoot.resolve(source.getFileName().toString());
    }

    private static void copyPath(Path source, Path destination) throws IOException {
        if (Files.isDirectory(source)) {
            try (Stream<Path> stream = Files.walk(source)) {
                for (Path entry : (Iterable<Path>) stream::iterator) {
                    Path target = destination.resolve(source.relativize(entry).toString());
                    if (Files.isDirectory(entry)) {
                        Files.createDirectories(target);
                    } else {
                        copyFile(entry, target);
                    }
                }
            }
        } else {
            copyFile(source, destination);
        }
    }

    private static void copyFile(Path source, Path destination) throws IOException {
        Path parent = destination.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        Files.copy(source, destination, StandardCopyOption.REPLACE_EXISTING);
    }

    private void writeMissingReportPage(Path indexFile) throws IOException {
        String configuredReportFile = buildReportFile == null ? "auto-detect" : buildReportFile.getAbsolutePath();
        String html = "<!DOCTYPE html>\n"
                + "<html lang=\"en\">\n"
                + "<head>\n"
                + "  <meta charset=\"UTF-8\">\n"
                + "  <title>Native Image Build Report</title>\n"
                + "  <style>body{font-family:sans-serif;max-width:60rem;margin:2rem auto;line-height:1.5;padding:0 1rem;}code{background:#f4f4f4;padding:.1rem .3rem;border-radius:3px;}pre{background:#f4f4f4;padding:1rem;overflow:auto;border-radius:6px;}</style>\n"
                + "</head>\n"
                + "<body>\n"
                + "  <h1>Native Image Build Report</h1>\n"
                + "  <p>No GraalVM Native Image build report was found for this module.</p>\n"
                + "  <p>The site report looks for an existing HTML build report in <code>" + escapeHtml(buildOutputDirectory.getAbsolutePath()) + "</code>"
                + " or at the explicitly configured file <code>" + escapeHtml(configuredReportFile) + "</code>.</p>\n"
                + "  <p>To generate one before running Maven Site, enable either <code>--emit build-report</code> or <code>-H:+BuildReport</code> in the native image build arguments, then run the native build phase before <code>site</code>.</p>\n"
                + "  <pre>mvn -Pnative package site</pre>\n"
                + "</body>\n"
                + "</html>\n";
        Files.writeString(indexFile, html, StandardCharsets.UTF_8);
    }

    private static String escapeHtml(String value) {
        return value
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;");
    }
}

