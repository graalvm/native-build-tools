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

import spock.lang.Specification
import spock.lang.TempDir

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption

abstract class AbstractGraalVMMavenFunctionalTest extends Specification {
    private final IsolatedMavenExecutor executor = new IsolatedMavenExecutor(
            new File(System.getProperty("java.executable")),
            System.getProperty("maven.classpath")
    )

    @TempDir
    Path testDirectory

    MavenExecutionResult result

    protected void withSample(String name) {
        File sampleDir = new File("../samples/$name")
        copySample(sampleDir.toPath(), testDirectory)
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

    void mvn(String... args) {
        result = executor.execute(
                testDirectory.toFile(),
                [
                        "org.slf4j.simpleLogger.log.org.apache.maven.cli.transfer.Slf4jMavenTransferListener": "warn",
                        "common.repo.uri": System.getProperty("common.repo.uri"),
                        "seed.repo.uri": System.getProperty("seed.repo.uri"),
                        "maven.repo.local": testDirectory.resolve("local-repo").toFile().absolutePath
                ],
                [*injectedSystemProperties,
                 *args],
                new File(System.getProperty("maven.settings"))
        )
        System.out.println("Exit code is ${result.exitCode}")
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
        result.stdOut.contains(text)
    }

    boolean outputDoesNotContain(String text) {
        !result.stdOut.contains(text)
    }

}
