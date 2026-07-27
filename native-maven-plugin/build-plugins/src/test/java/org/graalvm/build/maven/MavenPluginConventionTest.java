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
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Protects the unit-test class-directory and assembly descriptor boundaries. §AR-maven-plugin.6
 */
class MavenPluginConventionTest {
    @TempDir
    Path projectDirectory;

    @Test
    void unitTestsDoNotBuildThePluginJarButAssemblyDoes() throws IOException {
        writeFixture(projectDirectory);

        BuildResult test = runner("test", "--dry-run").build();
        assertTrue(test.getOutput().contains(":test SKIPPED"));
        assertFalse(test.getOutput().contains(":jar SKIPPED"));
        assertFalse(test.getOutput().contains(":generatePluginDescriptor SKIPPED"));

        BuildResult assemble = runner("assemble", "--dry-run").build();
        assertTrue(assemble.getOutput().contains(":jar SKIPPED"));
        assertTrue(assemble.getOutput().contains(":generatePluginDescriptor SKIPPED"));
    }

    @Test
    void includedBuildUnitTestsAlsoUseClassDirectories() throws IOException {
        Path includedBuild = projectDirectory.resolve("plugin");
        writeFixture(includedBuild);
        Files.writeString(projectDirectory.resolve("settings.gradle.kts"), """
                rootProject.name = "workspace"
                includeBuild("plugin")
                """);

        BuildResult test = runner(":plugin:test", "--dry-run").build();
        assertTrue(test.getOutput().contains(":plugin:test SKIPPED"));
        assertFalse(test.getOutput().contains(":plugin:jar SKIPPED"));
        assertFalse(test.getOutput().contains(":plugin:generatePluginDescriptor SKIPPED"));
    }

    private GradleRunner runner(String... arguments) {
        return GradleRunner.create()
                .withProjectDir(projectDirectory.toFile())
                .withPluginClasspath()
                .withArguments(arguments);
    }

    private void writeFixture(Path fixtureDirectory) throws IOException {
        Files.createDirectories(fixtureDirectory);
        Files.writeString(fixtureDirectory.resolve("settings.gradle.kts"), """
                rootProject.name = "fixture"
                includeBuild("utils")
                """);
        Files.writeString(fixtureDirectory.resolve("build.gradle.kts"), """
                plugins {
                    `maven-publish`
                    id("org.graalvm.build.maven-plugin")
                }

                group = "example"
                version = "1.0"

                publishing {
                    publications {
                        create<MavenPublication>("mavenPlugin") {
                            from(components["java"])
                        }
                    }
                }
                """);
        Files.createDirectories(fixtureDirectory.resolve("config"));
        Files.writeString(fixtureDirectory.resolve("config/settings.xml"), "<settings/>");
        Path utils = fixtureDirectory.resolve("utils");
        Files.createDirectories(utils);
        Files.writeString(utils.resolve("settings.gradle.kts"), "rootProject.name = \"utils\"");
        Files.writeString(utils.resolve("build.gradle.kts"), """
                tasks.register("publishAllPublicationsToCommonRepository")
                """);
        writeJava(fixtureDirectory, "src/main/java/example/Main.java", "Main");
        writeJava(fixtureDirectory, "src/testFixtures/java/example/Fixture.java", "Fixture");
        writeJava(fixtureDirectory, "src/test/java/example/MainTest.java", "MainTest");
    }

    private void writeJava(Path fixtureDirectory, String path, String className) throws IOException {
        Path file = fixtureDirectory.resolve(path);
        Files.createDirectories(file.getParent());
        Files.writeString(file, "package example; public class " + className + " {}");
    }
}
