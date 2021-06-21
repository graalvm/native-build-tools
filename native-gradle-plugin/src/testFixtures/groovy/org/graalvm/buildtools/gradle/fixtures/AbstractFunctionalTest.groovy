/*
 * Copyright (c) 2021, 2021 Oracle and/or its affiliates. All rights reserved.
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
import org.gradle.util.GFileUtils
import org.gradle.util.GradleVersion
import spock.lang.Specification
import spock.lang.TempDir

import java.nio.file.Path

abstract class AbstractFunctionalTest extends Specification {
    private static final Set<String> MINIMAL_COVERAGE = [
            GradleVersion.current().version // Only current Gradle version
    ]
    private static final Set<String> FULL_COVERAGE = MINIMAL_COVERAGE + [
            '7.1',
            '6.8.3',
            '6.7.1'
    ]
    static final List<String> TESTED_GRADLE_VERSIONS =
            (Boolean.getBoolean('full.coverage') ?
                    FULL_COVERAGE : MINIMAL_COVERAGE) as List<String>

    @TempDir
    Path testDirectory

    String gradleVersion

    private StringWriter outputWriter
    private StringWriter errorOutputWriter
    private String output
    private String errorOutput
    private File initScript

    BuildResult result

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

    protected void withSample(String name) {
        File sampleDir = new File("src/samples/$name")
        GFileUtils.copyDirectory(sampleDir, testDirectory.toFile())
    }

    void run(String... args) {
        try {
            result = newRunner(args)
                    .build()
        } finally {
            recordOutputs()
        }
    }

    void outputContains(String text) {
        assert output.contains(text)
    }

    void errorOutputContains(String text) {
        assert errorOutput.contains(text)
    }

    void tasks(@DelegatesTo(value = TaskExecutionGraph, strategy = Closure.DELEGATE_FIRST) Closure spec) {
        def graph = new TaskExecutionGraph()
        spec.delegate = graph
        spec.resolveStrategy = Closure.DELEGATE_FIRST
        spec()
    }

    private void recordOutputs() {
        output = outputWriter.toString()
        errorOutput = errorOutput.toString()
    }

    private GradleRunner newRunner(String... args) {
        assertInitScript()
        outputWriter = new StringWriter()
        errorOutputWriter = new StringWriter()
        def runner = GradleRunner.create()
                .forwardStdOutput(tee(new OutputStreamWriter(System.out), outputWriter))
                .forwardStdError(tee(new OutputStreamWriter(System.err), errorOutputWriter))
                .withPluginClasspath()
                .withProjectDir(testDirectory.toFile())
                .withArguments(["-I", initScript.getAbsolutePath(), *args])
        if (gradleVersion) {
            runner.withGradleVersion(gradleVersion)
        }
        runner
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
        new ProcessController(executablePath).execute()
    }

    private void assertInitScript() {
        initScript = file("init.gradle")
        initScript << """
            allprojects {
                repositories {
                    maven {
                        url = "\${System.getProperty('common.repo.url')}"
                    }
                    mavenCentral()
                }
            }
        """
    }

    private class TaskExecutionGraph {
        void succeeded(String... tasks) {
            tasks.each { task ->
                contains(task)
                assert result.task(task).outcome == TaskOutcome.SUCCESS
            }
        }

        void contains(String... tasks) {
            tasks.each { task ->
                assert result.task(task) != null: "Expected to find task $task in the graph but it was missing"
            }
        }

        void doesNotContain(String... tasks) {
            tasks.each { task ->
                assert result.task(task) == null: "Task $task should be missing from the task graph but it was found with an outcome of ${result.task(task).outcome}"
            }
        }
    }
}
