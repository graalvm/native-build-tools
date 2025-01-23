/*
 * Copyright (c) 2020, 2021, Oracle and/or its affiliates. All rights reserved.
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

package org.graalvm.buildtools.maven

import org.eclipse.jetty.server.Server
import org.eclipse.jetty.server.ServerConnector
import org.eclipse.jetty.server.handler.ContextHandler
import org.eclipse.jetty.server.handler.ResourceHandler
import spock.lang.Specification
import spock.lang.TempDir

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption

abstract class AbstractGraalVMMavenFunctionalTest extends Specification {
    @TempDir
    Path testDirectory

    Path testOrigin

    private IsolatedMavenExecutor executor

    MavenExecutionResult result

    Server server
    ServerConnector connector

    boolean IS_WINDOWS = System.getProperty("os.name", "unknown").contains("Windows")
    boolean IS_LINUX = System.getProperty("os.name", "unknown").contains("Linux")
    boolean IS_MAC = System.getProperty("os.name", "unknown").contains("Mac")

    def setup() {
        executor = new IsolatedMavenExecutor(
                new File(System.getProperty("java.executable")),
                testDirectory.resolve("m2-home").toFile(),
                System.getProperty("maven.classpath")
        )
    }

    def cleanup() {
        if (server != null) {
            server.stop()
            server.destroy()
        }
    }

    protected void withDebug() {
        executor.debug = true
    }

    protected void withSpacesInProjectDir() {
        testDirectory = testDirectory.resolve("with spaces")
        Files.createDirectory(testDirectory)
    }

    protected void withReproducer(String name) {
        testOrigin = new File("reproducers/$name").toPath()
        copySample(testOrigin, testDirectory)
    }

    protected void withSample(String name) {
        testOrigin = new File("../samples/$name").toPath()
        copySample(testOrigin, testDirectory)
    }

    protected void withLocalServer() {
        if (server == null) {
            server = new Server()
            connector = new ServerConnector(server)
            server.addConnector(connector)
            def handler = new ResourceHandler()
            handler.resourceBase = testDirectory.toRealPath().toString()
            handler.directoriesListed = true
            def ctx = new ContextHandler("/")
            ctx.handler = handler
            server.handler = ctx
            server.start()
        }
    }

    protected int getLocalServerPort() {
        return connector.getLocalPort()
    }

    private static void copySample(Path from, Path into) {
        Files.walk(from).forEach(sourcePath -> {
            Path target = into.resolve(from.relativize(sourcePath))
            Files.copy(sourcePath, target, StandardCopyOption.REPLACE_EXISTING)
        })
    }

    private static List<String> getInjectedSystemProperties() {
        ["junit.jupiter.version",
         "native.maven.plugin.version",
         "junit.platform.native.version"
        ].collect {
            "-D${it}=${System.getProperty(it)}".toString()
        }
    }

    void mvn(List<String> args) {
        Map<String, String> systemProperties = [
            "org.apache.http" : "off",
            "org.apache.http.wire" : "off",
        ].collectEntries { key, value ->
            ["org.slf4j.simpleLogger.log.${key}".toString(), value]
        }
        mvn(args, systemProperties)
    }

    void mvn(String... args) {
        mvn(args.toList())
    }

    void mvn(List<String> args, Map<String, String> systemProperties) {
        println("Running copy of maven project ${testOrigin} in ${testDirectory}  with $args")
        var resultingSystemProperties = [
                "common.repo.uri": System.getProperty("common.repo.uri"),
                "seed.repo.uri": System.getProperty("seed.repo.uri"),
                "maven.repo.local": testDirectory.resolve("local-repo").toFile().absolutePath
        ]
        println "Using local repo: ${resultingSystemProperties['maven.repo.local']}"
        resultingSystemProperties.putAll(systemProperties)

        result = executor.execute(
                testDirectory.toFile(),
                resultingSystemProperties,
                [*injectedSystemProperties,
                 *args],
                new File(System.getProperty("maven.settings"))
        )
        println "Exit code is ${result.exitCode}"

    }

    void mvnDebug(String... args) {
        Map<String, String> systemProperties = [
            "org.apache.http"                                                    : "off",
            "org.apache.http.wire"                                               : "off",
            "org.apache.maven.cli.transfer.Slf4jMavenTransferListener"           : "warn",
            "org.eclipse.aether.internal.impl.DefaultTransporterProvider"        : "error",
            "org.eclipse.aether.internal.impl.DefaultRepositoryConnectorProvider": "error",
            "org.eclipse.aether.internal.impl.TrackingFileManager"               : "error",
            "org.eclipse.aether.internal.impl.DefaultArtifactResolver"           : "error",
            "org.codehaus.plexus.PlexusContainer"                                : "error",
            "org.apache.maven.plugin.internal.DefaultPluginDependenciesResolver" : "error",
            "org.apache.maven.shared.filtering.DefaultMavenFileFilter"           : "error",
            "org.codehaus.mojo.exec.ExecMojo"                                    : "debug",
            // This is THE logger we need for checking java execution
            "org.apache.maven.lifecycle.internal.LifecycleDebugLogger"           : "error"
        ].collectEntries { key, value ->
            ["org.slf4j.simpleLogger.log.${key}".toString(), value]
        }

        ArrayList<String> resultingArgs = args.toList()
        resultingArgs.add(0, "-X")
        mvn(resultingArgs, systemProperties)
    }

    boolean isDidNotCrash() {
        return result.exitCode <= 1
    }

    boolean isBuildSucceeded() {
        result.stdOut.contains("BUILD SUCCESS")
    }

    boolean isBuildFailed() {
        !isBuildSucceeded()
    }

    boolean outputContains(String text) {
        normalizeString(result.stdOut).contains(normalizeString(text))
    }

    boolean outputContainsPattern(String pattern) {
        def normalizedOutput = normalizeString(result.stdOut)
        def lines = normalizedOutput.split('\n')
        return lines.any { line -> line.trim().matches(pattern) }
    }

    String after(String text) {
        def out = normalizeString(result.stdOut)
        out.substring(out.indexOf(normalizeString(text)))
    }

    boolean outputDoesNotContain(String text) {
        !normalizeString(result.stdOut).contains(normalizeString(text))
    }

    List<String> getOutputLines() {
        return normalizeString(result.stdOut).split("\n")
    }

    static boolean matches(String actual, String expected) {
        normalizeString(actual) == normalizeString(expected)
    }

    File file(String path) {
        testDirectory.resolve(path).toFile()
    }

    private static String normalizeString(String input) {
        input.replace("\r\n", "\n").replace("\\\\", "/")
    }

    String getArgFileContents() {
        result.stdOut.lines().filter {
            it.contains('Executing:') && it.contains('bin/native-image')
        }.map {
            new File(it.substring(it.lastIndexOf('@') + 1))
        }.findFirst()
                .map { it.text }
                .orElse("")
    }
}
