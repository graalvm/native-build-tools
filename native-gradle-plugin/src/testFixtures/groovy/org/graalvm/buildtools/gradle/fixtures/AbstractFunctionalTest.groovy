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

    boolean IS_WINDOWS = System.getProperty("os.name", "unknown").contains("Windows")
    boolean IS_LINUX = System.getProperty("os.name", "unknown").contains("Linux")
    boolean IS_MAC = System.getProperty("os.name", "unknown").contains("Mac")

    private final URI commonRepositoryUri = commonRepositoryUri()
    private StringWriter outputWriter
    private StringWriter errorOutputWriter
    private String output
    private String errorOutput
    private String configurationCacheStoreOutput
    private File initScript
    private Map<String, String> environment

    BuildResult result
    BuildResult configurationCacheStoreResult

    private static String testGradleVersion() {
        String version = System.getProperty("gradle.test.version", GradleVersion.current().version)
        if ("current" == version) {
            version = GradleVersion.current().version
        }
        version
    }

    private static URI commonRepositoryUri() {
        String repositoryPath = System.getProperty("common.repo.url")
        repositoryPath == null ? null : new File(repositoryPath).toURI()
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

    protected void withEnvironmentOverrides(Map<String, String> overrides) {
        def updatedEnvironment = new LinkedHashMap<>(System.getenv())
        overrides.each { key, value ->
            if (value == null) {
                updatedEnvironment.remove(key)
            } else {
                updatedEnvironment.put(key, value)
            }
        }
        environment = updatedEnvironment
    }

    protected void withSample(String name, boolean quickBuildMode = true) {
        File sampleDir = new File("../samples/$name")
        FileUtils.copyDirectory(sampleDir.toPath(), testDirectory)

        if (quickBuildMode) {
            buildFile << """
            graalvmNative {
                binaries.all {
                    buildArgs.add("-Ob")
                }
            }
            """.stripIndent()
        }
    }

    void run(String... args) {
        try {
            configurationCacheStoreResult = null
            configurationCacheStoreOutput = null
            result = newRunner(args)
                    .run()
            if (hasConfigurationCache) {
                // run a 2d time to check that not only we can store in
                // the configuration cache, but that we can also load from it
                configurationCacheStoreResult = result
                configurationCacheStoreOutput = normalizeString(outputWriter.toString())
                result = newRunner([*args, "--rerun-tasks"] as String[])
                        .run()
            }
        } finally {
            recordOutputs()
        }
    }

    void runAndReloadConfigurationCache(String... args) {
        try {
            configurationCacheStoreResult = null
            configurationCacheStoreOutput = null
            result = newRunner(args)
                    .run()
            if (hasConfigurationCache) {
                configurationCacheStoreResult = result
                configurationCacheStoreOutput = normalizeString(outputWriter.toString())
                result = newRunner(args)
                        .run()
            }
        } finally {
            recordOutputs()
        }
    }

    // Helper method to run Gradle with custom environment variables
    void runWithEnv(Map<String, String> env, String... args) {
        // Debug mode is not allowed when environment variables are set
        // because Gradle TestKit needs to fork a separate process
        def wasDebug = debug
        debug = false
        try {
            def runner = newRunner(*args)
            // Preserve current environment and override only specified variables
            // This prevents issues like NPE when PATH is missing
            def currentEnv = System.getenv() as Map<String, String> ?: [:]
            def mergedEnv = new HashMap<>(currentEnv)
            mergedEnv.putAll(env)
            runner.withEnvironment(mergedEnv)
            result = runner.run()
            if (hasConfigurationCache) {
                // run a 2d time to check that not only we can store in
                // the configuration cache, but that we can also load from it
                result = newRunner(*[*args, "--rerun-tasks"] as String[])
                        .withEnvironment(mergedEnv)
                        .run()
            }
        } finally {
            recordOutputs()

            debug = wasDebug
        }
    }

    void outputContains(String text) {
        assert output.contains(normalizeString(text))
    }

    void configurationCacheStoreOutputContains(String text) {
        assert configurationCacheStoreOutput != null: "No configuration-cache store build output is available"
        assert configurationCacheStoreOutput.contains(normalizeString(text))
    }

    void outputDoesNotContain(String text) {
        assert !output.contains(normalizeString(text))
    }

    void errorOutputContains(String text) {
        assert errorOutput.contains(normalizeString(text))
    }

    static boolean matches(String actual, String expected) {
        normalizeString(actual) == normalizeString(expected)
    }

    /**
     * Returns true if the normalized 'actual' contains the normalized 'expectedPart'.
     * Uses the same normalization as 'matches' (CRLF to LF and backslash to forward slash).
     */
    static boolean contains(String actual, String expectedPart) {
        normalizeString(actual).contains(normalizeString(expectedPart))
    }

    void tasks(@DelegatesTo(value = TaskExecutionGraph, strategy = Closure.DELEGATE_FIRST) Closure spec) {
        inspectTasks(result, spec)
    }

    void configurationCacheStoreTasks(@DelegatesTo(value = TaskExecutionGraph, strategy = Closure.DELEGATE_FIRST) Closure spec) {
        assert configurationCacheStoreResult != null: "No configuration-cache store build result is available"
        inspectTasks(configurationCacheStoreResult, spec)
    }

    private void inspectTasks(BuildResult buildResult, @DelegatesTo(value = TaskExecutionGraph, strategy = Closure.DELEGATE_FIRST) Closure spec) {
        def graph = new TaskExecutionGraph()
        graph.result = buildResult
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
        if (environment != null) {
            runner.withEnvironment(environment)
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
            assert commonRepositoryUri != null: "Expected common.repo.url system property for functional test repository"
            // The generated init script must be self-contained before the first cached TestKit build starts. §AR-gradle-plugin.6, §E2E-functional-tests.4.
            initScript << """
            allprojects {
                repositories {
                    maven {
                        url = "${commonRepositoryUri}"
                    }
                    mavenCentral()
                }
            }
        """
        }
    }

    private class TaskExecutionGraph {
        BuildResult result

        void succeeded(String... tasks) {
            tasks.each { task ->
                contains(task)
                assert result.task(task).outcome == TaskOutcome.SUCCESS
            }
        }

        void failed(String... tasks) {
            tasks.each { task ->
                contains(task)
                assert result.task(task).outcome == TaskOutcome.FAILED
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
        input.replace("\r\n", "\n").replace("\\\\", "/")
    }
}
