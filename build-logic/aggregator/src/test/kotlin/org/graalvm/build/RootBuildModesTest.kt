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

package org.graalvm.build

import org.gradle.testkit.runner.GradleRunner
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.exists
import kotlin.io.path.writeText

// Root mode selection and lifecycle aggregation are specified by §FS-build-infrastructure.1 and §FS-build-infrastructure.1.1.
class RootBuildModesTest {
    @TempDir
    lateinit var testDirectory: Path

    @Test
    fun `default full build evaluates and renders documentation`() {
        val fixture = createFixture()

        runner(fixture, "build").build()

        assertCoreLifecycleMarkers(fixture, includeTestAndInspections = false)
        assertTrue(marker(fixture, "docs-evaluated").exists())
        assertTrue(marker(fixture, "docs-asciidoctor").exists())
    }

    @Test
    fun `explicit false selects full mode and aggregates every included build`() {
        val fixture = createFixture()

        runner(fixture, "assemble", "test", "check", "inspections", "-Porg.graalvm.build.core=false").build()

        assertLifecycleMarkers(fixture, "product")
        assertLifecycleMarkers(fixture, "common")
        assertLifecycleMarkers(fixture, "build-logic")
        assertLifecycleMarkers(fixture, "docs")
        assertTrue(marker(fixture, "docs-evaluated").exists())
    }

    @Test
    fun `core build omits documentation evaluation and rendering`() {
        val fixture = createFixture()

        runner(fixture, "build", "test", "inspections", "-Porg.graalvm.build.core=true").build()

        assertCoreLifecycleMarkers(fixture, includeTestAndInspections = true)
        assertFalse(marker(fixture, "docs-evaluated").exists())
        assertFalse(marker(fixture, "docs-asciidoctor").exists())
    }

    @Test
    fun `malformed core property fails during settings evaluation`() {
        val fixture = createFixture()

        val result = runner(fixture, "help", "-Porg.graalvm.build.core=yes").buildAndFail()

        assertTrue(result.output.contains("must be 'true' or 'false', but was 'yes'"))
        assertFalse(marker(fixture, "docs-evaluated").exists())
    }

    // Missing-toolchain prerequisite diagnostics are specified by §FS-build-infrastructure.2.1.
    @Test
    fun `missing Java 17 toolchain fails with actionable repository prerequisite`() {
        val fixture = createFixture()
        val emptyInstallations = fixture.resolve("empty-java-installations").createDirectories()

        val result = runner(
            fixture,
            "help",
            "-Porg.graalvm.build.core=true",
            "-Dorg.gradle.java.installations.auto-detect=false",
            "-Dorg.gradle.java.installations.auto-download=false",
            "-Dorg.gradle.java.installations.paths=${emptyInstallations.toAbsolutePath()}"
        ).buildAndFail()

        assertTrue(result.output.contains("A discoverable JDK 17 installation is required to build Native Build Tools."))
        assertTrue(result.output.contains("org.gradle.java.installations.paths"))
        assertTrue(result.output.contains("Cannot find a Java installation"))
        assertFalse(marker(fixture, "product-assemble").exists())
        assertFalse(marker(fixture, "common-assemble").exists())
        assertFalse(marker(fixture, "build-logic-assemble").exists())
        assertFalse(marker(fixture, "docs-evaluated").exists())
    }

    private fun assertCoreLifecycleMarkers(fixture: Path, includeTestAndInspections: Boolean) {
        assertLifecycleMarkers(fixture, "product", includeTestAndInspections)
        assertLifecycleMarkers(fixture, "common", includeTestAndInspections)
        assertLifecycleMarkers(fixture, "build-logic", includeTestAndInspections)
    }

    private fun assertLifecycleMarkers(fixture: Path, buildName: String, includeTestAndInspections: Boolean = true) {
        assertTrue(marker(fixture, "$buildName-assemble").exists())
        assertTrue(marker(fixture, "$buildName-check").exists())
        if (includeTestAndInspections) {
            assertTrue(marker(fixture, "$buildName-test").exists())
            assertTrue(marker(fixture, "$buildName-inspections").exists())
        }
    }

    private fun runner(fixture: Path, vararg arguments: String): GradleRunner =
        GradleRunner.create()
            .withProjectDir(fixture.toFile())
            .withPluginClasspath()
            .withArguments(*arguments, "--stacktrace")

    private fun createFixture(): Path {
        val fixture = testDirectory.resolve("fixture").createDirectories()
        fixture.resolve("gradle").createDirectories()
        fixture.resolve("gradle/libs.versions.toml").writeText(
            """
            [versions]
            nativeBuildTools = "1.0.0"
            junitJupiter = "5.13.0"
            junitPlatform = "1.13.0"
            """.trimIndent()
        )
        fixture.resolve("settings.gradle.kts").writeText(settingsScript(fixture))
        fixture.resolve("build.gradle.kts").writeText(
            """
            plugins {
                id("org.graalvm.build.aggregator")
            }
            """.trimIndent()
        )

        listOf("product", "common", "build-logic", "docs").forEach { createIncludedBuild(fixture, it) }
        return fixture
    }

    private fun settingsScript(fixture: Path): String {
        val docsEvaluated = escapedPath(marker(fixture, "docs-evaluated"))
        return """
            rootProject.name = "root-build-modes-fixture"

            val coreBuild = providers.gradleProperty("org.graalvm.build.core").orNull?.let { value ->
                when (value) {
                    "true" -> true
                    "false" -> false
                    else -> throw GradleException(
                        "Gradle property 'org.graalvm.build.core' must be 'true' or 'false', but was '${'$'}value'."
                    )
                }
            } ?: false

            includeBuild("product")
            includeBuild("common")
            includeBuild("build-logic")
            if (!coreBuild) {
                file("$docsEvaluated").apply {
                    parentFile.mkdirs()
                    writeText("evaluated")
                }
                includeBuild("docs")
            }
        """.trimIndent()
    }

    private fun createIncludedBuild(fixture: Path, buildName: String) {
        val buildDirectory = fixture.resolve(buildName).createDirectories()
        buildDirectory.resolve("settings.gradle.kts").writeText("rootProject.name = \"$buildName\"")
        buildDirectory.resolve("build.gradle.kts").writeText(includedBuildScript(fixture, buildName))
    }

    private fun includedBuildScript(fixture: Path, buildName: String): String {
        fun markerPath(taskName: String) = escapedPath(marker(fixture, "$buildName-$taskName"))
        val documentationLifecycle = if (buildName == "docs") {
            """
                val asciidoctor = tasks.register("asciidoctor") {
                    doLast { file("${markerPath("asciidoctor")}").apply { parentFile.mkdirs(); writeText("rendered") } }
                }
                tasks.named("build") { dependsOn(asciidoctor) }
            """.trimIndent()
        } else {
            ""
        }
        return """
            plugins { base }

            tasks.named("assemble") {
                doLast { file("${markerPath("assemble")}").apply { parentFile.mkdirs(); writeText("done") } }
            }
            tasks.named("check") {
                doLast { file("${markerPath("check")}").apply { parentFile.mkdirs(); writeText("done") } }
            }
            tasks.register("test") {
                doLast { file("${markerPath("test")}").apply { parentFile.mkdirs(); writeText("done") } }
            }
            tasks.register("inspections") {
                doLast { file("${markerPath("inspections")}").apply { parentFile.mkdirs(); writeText("done") } }
            }
            $documentationLifecycle
        """.trimIndent()
    }

    private fun marker(fixture: Path, name: String): Path = fixture.resolve("markers").resolve(name)

    private fun escapedPath(path: Path): String = path.toAbsolutePath().toString().replace("\\", "\\\\")
}
