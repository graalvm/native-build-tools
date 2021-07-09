/*
 * Copyright (c) 2020, 2021 Oracle and/or its affiliates. All rights reserved.
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

package org.graalvm.junit.platform;

import org.junit.platform.commons.logging.Logger;
import org.junit.platform.commons.logging.LoggerFactory;
import org.junit.platform.commons.util.StringUtils;
import org.junit.platform.engine.TestExecutionResult;
import org.junit.platform.launcher.TestExecutionListener;
import org.junit.platform.launcher.TestIdentifier;
import org.junit.platform.launcher.TestPlan;

import java.io.IOException;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.List;

/**
 * This file is a modified copy of the
 * {@code org.junit.platform.launcher.listeners.UniqueIdTrackingListener} from
 * the {@code junit-platform-launcher} module. Once JUnit Platform 1.8 has been
 * released, this file should be removed, and the {@code native-build-tools}
 * project should support the official {@code UniqueIdTrackingListener} instead.
 *
 * <p>{@code UniqueIdTrackingListener} is a {@link TestExecutionListener} that
 * tracks the {@linkplain TestIdentifier#getUniqueId() unique IDs} of all
 * {@linkplain TestIdentifier#isTest() tests} that were
 * {@linkplain #executionFinished executed} during the execution of the
 * {@link TestPlan} and generates a file containing the unique IDs once
 * execution of the {@code TestPlan} has
 * {@linkplain #testPlanExecutionFinished(TestPlan) finished}.
 *
 * <p>
 * Tests are tracked regardless of their {@link TestExecutionResult} or whether
 * they were skipped, and the unique IDs are written to an output file, one ID
 * per line, encoding using UTF-8.
 *
 * <p>
 * The output file can be used to execute the same set of tests again without
 * having to query the user configuration for the test plan and without having
 * to perform test discovery again. This can be useful for test environments
 * such as within a native image &mdash; for example, a GraalVM native image
 * &mdash; in order to rerun the exact same tests from a standard JVM test run
 * within a native image.
 *
 * <h3>Configuration and Defaults</h3>
 *
 * <p>
 * The {@code OUTPUT_DIR} is the directory in which this listener generates the
 * output file. The exact path of the generated file is
 * {@code OUTPUT_DIR/OUTPUT_FILE_PREFIX-<random number>.txt}, where
 * {@code <random number>} is a pseudo-random number. The inclusion of a random
 * number in the file name ensures that multiple concurrently executing test
 * plans do not overwrite each other's results.
 *
 * <p>
 * The value of the {@code OUTPUT_FILE_PREFIX} defaults to
 * {@link #DEFAULT_OUTPUT_FILE_PREFIX}, but a custom prefix can be set via the
 * {@link #OUTPUT_FILE_PREFIX_PROPERTY_NAME} configuration property.
 *
 * <p>
 * The {@code OUTPUT_DIR} can be set to a custom directory via the
 * {@link #OUTPUT_DIR_PROPERTY_NAME} configuration property. Otherwise the
 * following algorithm is used to select a default output directory.
 *
 * <ul>
 * <li>If the current working directory of the Java process contains a file
 * named {@code pom.xml}, the output directory will be {@code ./target},
 * following the conventions of Maven.</li>
 * <li>If the current working directory of the Java process contains a file with
 * the extension {@code .gradle} or {@code .gradle.kts}, the output directory
 * will be {@code ./build}, following the conventions of Gradle.</li>
 * <li>Otherwise, the current working directory of the Java process will be used
 * as the output directory.</li>
 * </ul>
 *
 * <p>
 * For example, in a project using Gradle as the build tool, the file generated
 * by this listener would be
 * {@code ./build/junit-platform-unique-ids-<random number>.txt} by default.
 *
 * <p>
 * Configuration properties can be set via JVM system properties.
 */
public class UniqueIdTrackingListener implements TestExecutionListener {

    /**
     * Property name used to set the path to the output directory for the file
     * generated by the {@code UniqueIdTrackingListener}: {@value}.
     *
     * <p>
     * For details on the default output directory, see the
     * {@linkplain UniqueIdTrackingListener class-level Javadoc}.
     */
    public static final String OUTPUT_DIR_PROPERTY_NAME = "junit.platform.listeners.uid.tracking.output.dir";

    /**
     * Property name used to set the prefix for the name of the file generated by
     * the {@code UniqueIdTrackingListener}: {@value}.
     *
     * <p>
     * Defaults to {@link #DEFAULT_OUTPUT_FILE_PREFIX}.
     */
    public static final String OUTPUT_FILE_PREFIX_PROPERTY_NAME = "junit.platform.listeners.uid.tracking.output.file.prefix";

    /**
     * The default prefix for the name of the file generated by the
     * {@code UniqueIdTrackingListener}: {@value}.
     *
     * @see #OUTPUT_FILE_PREFIX_PROPERTY_NAME
     */
    public static final String DEFAULT_OUTPUT_FILE_PREFIX = "junit-platform-unique-ids";

    private final Logger logger = LoggerFactory.getLogger(UniqueIdTrackingListener.class);

    private final List<String> uniqueIds = new ArrayList<>();

    @Override
    public void executionSkipped(TestIdentifier testIdentifier, String reason) {
        trackTestUid(testIdentifier);
    }

    @Override
    public void executionFinished(TestIdentifier testIdentifier, TestExecutionResult testExecutionResult) {
        trackTestUid(testIdentifier);
    }

    private void trackTestUid(TestIdentifier testIdentifier) {
        if (testIdentifier.isTest()) {
            this.uniqueIds.add(testIdentifier.getUniqueId());
        }
    }

    @Override
    public void testPlanExecutionFinished(TestPlan testPlan) {
        Path outputFile;
        try {
            outputFile = getOutputFile();
        } catch (IOException ex) {
            logger.error(ex, () -> "Failed to create output file");
            // Abort since we cannot generate the file.
            return;
        }

        logger.debug(() -> "Writing unique IDs to output file " + outputFile.toAbsolutePath());
        try (PrintWriter writer = new PrintWriter(Files.newBufferedWriter(outputFile, StandardCharsets.UTF_8))) {
            this.uniqueIds.forEach(writer::println);
            writer.flush();
        } catch (IOException ex) {
            logger.error(ex, () -> "Failed to write unique IDs to output file " + outputFile.toAbsolutePath());
        }
    }

    private Path getOutputFile() throws IOException {
        String prefix = System.getProperty(OUTPUT_FILE_PREFIX_PROPERTY_NAME, DEFAULT_OUTPUT_FILE_PREFIX);
        String filename = String.format("%s-%d.txt", prefix, Math.abs(new SecureRandom().nextLong()));
        Path outputFile = getOutputDir().resolve(filename);

        if (Files.exists(outputFile)) {
            Files.delete(outputFile);
        }

        Files.createFile(outputFile);

        return outputFile;
    }

    Path getOutputDir() throws IOException {
        Path cwd = currentWorkingDir();
        Path outputDir;

        String customDir = System.getProperty(OUTPUT_DIR_PROPERTY_NAME);
        if (StringUtils.isNotBlank(customDir)) {
            outputDir = cwd.resolve(customDir);
        } else if (Files.exists(cwd.resolve("pom.xml"))) {
            outputDir = cwd.resolve("target");
        } else if (containsFilesWithExtensions(cwd, ".gradle", ".gradle.kts")) {
            outputDir = cwd.resolve("build");
        } else {
            outputDir = cwd;
        }

        if (!Files.exists(outputDir)) {
            Files.createDirectories(outputDir);
        }

        return outputDir;
    }

    /**
     * Get the current working directory.
     * <p>
     * Package private for testing purposes.
     */
    Path currentWorkingDir() {
        return Paths.get(".");
    }

    /**
     * Determine if the supplied directory contains files with any of the supplied
     * extensions.
     */
    private boolean containsFilesWithExtensions(Path dir, String... extensions) throws IOException {
        return Files.find(dir, 1, //
                (path, basicFileAttributes) -> {
                    if (basicFileAttributes.isRegularFile()) {
                        for (String extension : extensions) {
                            if (path.getFileName().toString().endsWith(extension)) {
                                return true;
                            }
                        }
                    }
                    return false;
                }).findFirst().isPresent();
    }

}
