/*
 * Copyright (c) 2021, Oracle and/or its affiliates. All rights reserved.
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

package org.graalvm.buildtools.gradle.fixtures

import org.gradle.testkit.runner.BuildResult
import org.gradle.testkit.runner.GradleRunner
import org.gradle.testkit.runner.TaskOutcome
import org.gradle.util.GradleVersion
import spock.lang.Specification
import spock.lang.TempDir

import java.nio.file.Files
import java.nio.file.Path

abstract class AbstractFunctionalTest extends Specification {
    @TempDir
    Path testDirectory

    String gradleVersion = testGradleVersion()
    boolean debug
    boolean hasConfigurationCache = Boolean.getBoolean("config.cache")

    boolean IS_WINDOWS = System.getProperty("os.name", "unknown").contains("Windows");
    boolean IS_LINUX = System.getProperty("os.name", "unknown").contains("Linux");
    boolean IS_MAC = System.getProperty("os.name", "unknown").contains("Mac");

    private StringWriter outputWriter
    private StringWriter errorOutputWriter
    private String output
    private String errorOutput
    private File initScript

    BuildResult result

    private static String testGradleVersion() {
        String version = System.getProperty("gradle.test.version", GradleVersion.current().version)
        if ("current" == version) {
            version = GradleVersion.current().version
        }
        version
    }

    Path path(String... pathElements) {
        Path cur = testDirectory
        pathElements.each {
            cur = cur.resolve(it)
        }
        cur
    }

    File file(String... pathElements) {
        path(pathElements).toFile()
    }

    File getExecutableFile(String path) {
        file(IS_WINDOWS ? path + ".exe" : path)
    }

    File getSharedLibraryFile(String path) {
        def libExt = ""
        if (IS_LINUX) {
            libExt = ".so"
        } else if (IS_WINDOWS) {
            libExt = ".dll"
        } else if (IS_MAC) {
            libExt = ".dylib"
        }
        assert !libExt.isEmpty(): "Unable to determine shared library extension: unexpected operating system"
        file("build/native/nativeCompile/java-library" + libExt)
    }

    File getGroovyBuildFile() {
        file("build.gradle")
    }

    File getKotlinBuildFile() {
        file("build.gradle.kts")
    }

    File getGroovySettingsFile() {
        file("settings.gradle")
    }

    File getKotlinSettingsFile() {
        file("settings.gradle.kts")
    }

    File getBuildFile() {
        groovyBuildFile
    }

    File getSettingsFile() {
        groovySettingsFile
    }

    protected void withSpacesInProjectDir() {
        testDirectory = testDirectory.resolve("with spaces")
        Files.createDirectory(testDirectory)
    }

    protected void withSample(String name) {
        File sampleDir = new File("../samples/$name")
        FileUtils.copyDirectory(sampleDir.toPath(), testDirectory)
    }

    void run(String... args) {
        try {
            result = newRunner(args)
                    .run()
            if (hasConfigurationCache) {
                // run a 2d time to check that not only we can store in
                // the configuration cache, but that we can also load from it
                result = newRunner([*args, "--rerun-tasks"] as String[])
                        .run()
            }
        } finally {
            recordOutputs()
        }
    }

    void outputContains(String text) {
        assert output.contains(normalizeString(text))
    }

    void outputDoesNotContain(String text) {
        assert !output.contains(normalizeString(text))
    }

    void errorOutputContains(String text) {
        assert errorOutput.contains(normalizeString(text))
    }

    void tasks(@DelegatesTo(value = TaskExecutionGraph, strategy = Closure.DELEGATE_FIRST) Closure spec) {
        def graph = new TaskExecutionGraph()
        spec.delegate = graph
        spec.resolveStrategy = Closure.DELEGATE_FIRST
        spec()
    }

    private void recordOutputs() {
        output = normalizeString(outputWriter.toString())
        errorOutput = normalizeString(errorOutputWriter.toString())
    }

    private GradleRunner newRunner(String... args) {
        assertInitScript()
        outputWriter = new StringWriter()
        errorOutputWriter = new StringWriter()
        ArrayList<String> autoArgs = computeAutoArgs()
        def runner = GradleRunner.create()
                .forwardStdOutput(tee(new OutputStreamWriter(System.out), outputWriter))
                .forwardStdError(tee(new OutputStreamWriter(System.err), errorOutputWriter))
                .withPluginClasspath()
                .withProjectDir(testDirectory.toFile())
                .withArguments([*autoArgs, *args])
        if (gradleVersion) {
            runner.withGradleVersion(gradleVersion)
        }
        if (debug) {
            runner.withDebug(true)
        }
        runner
    }

    private ArrayList<String> computeAutoArgs() {
        List<String> autoArgs = [
                "-S",
        ]
        if (hasConfigurationCache) {
            autoArgs << '--configuration-cache'
        }
        autoArgs << "-I"
        autoArgs << initScript.getAbsolutePath()
        autoArgs
    }

    private static Writer tee(Writer one, Writer two) {
        return TeeWriter.of(one, two)
    }

    void fails(String... args) {
        try {
            result = newRunner(args)
                    .buildAndFail()
        } finally {
            recordOutputs()
        }
    }

    ProcessController execute(File executablePath) {
        new ProcessController(executablePath, file("build"))
                .execute()
    }

    private void assertInitScript() {
        initScript = file("init.gradle")
        if (!initScript.exists()) {
            initScript << """
            allprojects {
                repositories {
                    maven {
                        url = "\${providers.systemProperty('common.repo.url').get()}"
                    }
                    mavenCentral()
                }
            }
        """
        }
    }

    private class TaskExecutionGraph {
        void succeeded(String... tasks) {
            tasks.each { task ->
                contains(task)
                assert result.task(task).outcome == TaskOutcome.SUCCESS
            }
        }

        void skipped(String... tasks) {
            tasks.each { task ->
                contains(task)
                assert result.task(task).outcome == TaskOutcome.SKIPPED
            }
        }

        void upToDate(String... tasks) {
            tasks.each { task ->
                contains(task)
                assert result.task(task).outcome == TaskOutcome.UP_TO_DATE
            }
        }

        void contains(String... tasks) {
            tasks.each { task ->
                assert result.task(task) != null: "Expected to find task $task in the graph but it was missing. Found tasks: ${result.tasks.collect { it.path }}"
            }
        }

        void doesNotContain(String... tasks) {
            tasks.each { task ->
                assert result.task(task) == null: "Task $task should be missing from the task graph but it was found with an outcome of ${result.task(task).outcome}"
            }
        }
    }

    private static String normalizeString(String input) {
        input.replace("\r\n", "\n")
    }
}
