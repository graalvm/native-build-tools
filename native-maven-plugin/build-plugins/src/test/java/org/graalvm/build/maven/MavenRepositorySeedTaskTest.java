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

import org.gradle.testkit.runner.BuildResult;
import org.gradle.testkit.runner.GradleRunner;
import org.gradle.testkit.runner.TaskOutcome;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Protects staged seed publication, failed-refresh preservation, and offline preflight. §E2E-functional-tests.4
 */
class MavenRepositorySeedTaskTest {
    @TempDir
    Path projectDirectory;
    private Path repository;

    @BeforeEach
    void writeFixture() throws IOException {
        repository = projectDirectory.resolve("build/seed");
        Files.writeString(projectDirectory.resolve("settings.gradle"), "rootProject.name = 'seed-fixture'");
        Files.writeString(projectDirectory.resolve("build.gradle"), """
                import org.graalvm.build.maven.SeedMavenRepository
                import org.graalvm.build.maven.ValidateMavenRepositorySeed

                plugins {
                    id 'java'
                    id 'java-test-fixtures'
                    id 'org.graalvm.build.maven-embedder'
                }

                java.toolchain.languageVersion = JavaLanguageVersion.of(17)

                def seed = tasks.register('seed', SeedMavenRepository) {
                    dependsOn(tasks.classes)
                    projectDirectory.set(layout.projectDirectory.dir('seed-project'))
                    settingsFile.set(layout.projectDirectory.file('seed-project/settings.xml'))
                    pomFile.set(layout.projectDirectory.file('seed-project/pom.xml'))
                    mavenEmbedderClasspath.from(sourceSets.main.output)
                    outputDirectory.set(layout.buildDirectory.dir('seed'))
                    seedProperties.put('key', providers.gradleProperty('key').orElse('current'))
                    arguments.set(providers.gradleProperty('failure').map { ['-Dfailure=' + it] }.orElse([]))
                }

                def embeddedJavaVersion = providers.gradleProperty('embeddedJavaVersion')
                if (embeddedJavaVersion.present) {
                    seed.configure {
                        javaLauncher.set(javaToolchains.launcherFor {
                            languageVersion.set(JavaLanguageVersion.of(embeddedJavaVersion.get().toInteger()))
                        })
                    }
                }

                tasks.register('validateSeed', ValidateMavenRepositorySeed, seed.get())
                """);
        Path seedProject = projectDirectory.resolve("seed-project");
        Files.createDirectories(seedProject);
        Files.writeString(seedProject.resolve("settings.xml"), "<settings/>");
        Files.writeString(seedProject.resolve("pom.xml"), "<project/>");
        Path source = projectDirectory.resolve("src/main/java/org/apache/maven/cli/MavenCli.java");
        Files.createDirectories(source.getParent());
        Files.writeString(source, """
                package org.apache.maven.cli;

                import java.nio.file.Files;
                import java.nio.file.Path;
                import java.util.Arrays;

                public class MavenCli {
                    public static void main(String[] args) throws Exception {
                        Path repository = null;
                        for (String argument : args) {
                            if (argument.startsWith("-Dmaven.repo.local=")) {
                                repository = Path.of(argument.substring("-Dmaven.repo.local=".length()));
                                Files.createDirectories(repository);
                            }
                        }
                        System.err.println("ARGUMENTS=" + Arrays.toString(args));
                        System.err.println("FAKE_JAVA_VERSION=" + System.getProperty("java.version"));
                        if (Arrays.asList(args).contains("-Dfailure=resolution")) {
                            for (int index = 0; index < 2; index++) {
                                System.err.println("[ERROR] Could not transfer artifact com.example:missing:pom:1.0 from/to private-repo (https://user:secret@example.test:8443/maven/releases?token=hidden#fragment): Connect timed out");
                                System.err.println("resolver-stack-line");
                                System.err.println("WARNING: sun.misc.Unsafe support is unavailable");
                            }
                            System.exit(1);
                        }
                        if (Arrays.asList(args).contains("-Dfailure=generic")) {
                            System.err.println("[ERROR] Earlier failure detail");
                            System.err.println("[ERROR] Build configuration is invalid.");
                            System.err.println("[ERROR] -> [Help 1]");
                            System.exit(1);
                        }
                        Files.writeString(repository.resolve("artifact.jar"), "complete");
                        Files.writeString(repository.resolve("arguments.txt"), String.join("\\n", args));
                        Files.writeString(repository.resolve("java-version.txt"), System.getProperty("java.version"));
                    }
                }
                """);
    }

    @Test
    void failedRefreshPreservesThePreviousSeed() throws IOException {
        runner("seed").build();
        Path artifact = repository.resolve("artifact.jar");
        Path manifest = repository.resolve(MavenRepositorySeedState.MANIFEST_NAME);
        byte[] artifactBefore = Files.readAllBytes(artifact);
        byte[] manifestBefore = Files.readAllBytes(manifest);

        runner("seed", "-Pfailure=generic", "--rerun-tasks").buildAndFail();

        assertArrayEquals(artifactBefore, Files.readAllBytes(artifact));
        assertArrayEquals(manifestBefore, Files.readAllBytes(manifest));
    }

    @Test
    void validatorAcceptsCurrentStateAndRejectsStaleAndCorruptState() throws IOException {
        runner("seed").build();
        runner("validateSeed").build();

        BuildResult stale = runner("validateSeed", "-Pkey=changed").buildAndFail();
        assertTrue(stale.getOutput().contains(MavenRepositorySeedState.INVALID_SEED_MESSAGE));

        Files.writeString(repository.resolve("artifact.jar"), "corrupt");
        BuildResult corrupt = runner("validateSeed").buildAndFail();
        assertTrue(corrupt.getOutput().contains(MavenRepositorySeedState.INVALID_SEED_MESSAGE));
    }

    @Test
    void normalOnlineSeedTaskRepairsAnInvalidSeed() throws IOException {
        runner("seed").build();
        Files.writeString(repository.resolve("artifact.jar"), "corrupt");

        BuildResult repaired = runner("seed").build();

        assertNotNull(repaired.task(":seed"));
        assertEquals(TaskOutcome.SUCCESS, repaired.task(":seed").getOutcome());
        assertEquals("complete", Files.readString(repository.resolve("artifact.jar")));
        runner("validateSeed").build();
    }

    @Test
    void validatorRejectsMissingStateWithTheStableDiagnostic() {
        BuildResult missing = runner("validateSeed").buildAndFail();
        assertTrue(missing.getOutput().contains(MavenRepositorySeedState.INVALID_SEED_MESSAGE));
        assertFalse(Files.exists(repository));
    }

    @Test
    void normalExecutionUsesJava17WithoutVerboseMavenFlags() throws IOException {
        BuildResult result = runner("seed").build();

        String arguments = Files.readString(repository.resolve("arguments.txt"));
        assertFalse(arguments.contains("--errors"));
        assertFalse(arguments.contains("--debug"));
        assertFalse(arguments.contains("-U"));
        assertTrue(Files.readString(repository.resolve("java-version.txt")).startsWith("17"));
        assertFalse(result.getOutput().contains("FAKE_JAVA_VERSION"));
    }

    @Test
    void normalResolutionFailureIsSummarizedOnceAndSanitized() {
        BuildResult result = runner("seed", "-Pfailure=resolution").buildAndFail();

        String output = result.getOutput();
        String summary = "Embedded Maven could not resolve artifact com.example:missing:pom:1.0 from repository "
                + "private-repo (https://example.test:8443/maven/releases).";
        assertEquals(1, occurrences(output, summary));
        assertFalse(output.contains("user:secret"));
        assertFalse(output.contains("token=hidden"));
        assertFalse(output.contains("resolver-stack-line"));
        assertFalse(output.contains("sun.misc.Unsafe"));
    }

    @Test
    void infoAndDebugReplayMavenOutputAndEnableTheirDetailFlags() {
        BuildResult info = runner("seed", "-Pfailure=generic", "--info").buildAndFail();
        assertTrue(info.getOutput().contains("ARGUMENTS=[--errors,"));
        assertFalse(info.getOutput().contains("ARGUMENTS=[--errors, --debug,"));
        assertTrue(info.getOutput().contains("Earlier failure detail"));

        BuildResult debug = runner("seed", "-Pfailure=generic", "--debug").buildAndFail();
        assertTrue(debug.getOutput().contains("ARGUMENTS=[--errors, --debug,"));
        assertTrue(debug.getOutput().contains("Earlier failure detail"));
    }

    @Test
    void unrelatedFailureUsesTheFinalMeaningfulMavenError() {
        BuildResult result = runner("seed", "-Pfailure=generic").buildAndFail();

        assertTrue(result.getOutput().contains("Embedded Maven failed: Build configuration is invalid."));
        assertFalse(result.getOutput().contains("Earlier failure detail"));
        assertFalse(result.getOutput().contains("[Help 1]"));
    }

    @Test
    void launcherMetadataParticipatesInSeedValidity() throws IOException {
        runner("seed").build();
        byte[] manifestForJava17 = Files.readAllBytes(repository.resolve(MavenRepositorySeedState.MANIFEST_NAME));

        BuildResult refreshed = runner("seed", "-PembeddedJavaVersion=25").build();

        assertEquals(TaskOutcome.SUCCESS, refreshed.task(":seed").getOutcome());
        assertTrue(Files.readString(repository.resolve("java-version.txt")).startsWith("25"));
        assertFalse(java.util.Arrays.equals(
                manifestForJava17,
                Files.readAllBytes(repository.resolve(MavenRepositorySeedState.MANIFEST_NAME))));
    }

    private static int occurrences(String text, String expected) {
        int count = 0;
        int offset = 0;
        while ((offset = text.indexOf(expected, offset)) >= 0) {
            count++;
            offset += expected.length();
        }
        return count;
    }

    private GradleRunner runner(String... arguments) {
        return GradleRunner.create()
                .withProjectDir(projectDirectory.toFile())
                .withPluginClasspath()
                .withArguments(arguments);
    }
}
