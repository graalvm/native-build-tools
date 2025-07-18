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

import org.graalvm.buildtools.utils.NativeImageUtils
import spock.lang.IgnoreIf
import spock.lang.Issue
import spock.lang.Requires

class JavaApplicationWithTestsFunctionalTest extends AbstractGraalVMMavenFunctionalTest {

    def "can run tests in a native image with the Maven plugin"() {
        withSample("java-application-with-tests")

        when:
        mvn '-Pnative', '-DquickBuild', 'test'

        then:
        buildSucceeded
        outputContains "[junit-platform-native] Running in 'test listener' mode"
        outputContains """
[         3 containers found      ]
[         0 containers skipped    ]
[         3 containers started    ]
[         0 containers aborted    ]
[         3 containers successful ]
[         0 containers failed     ]
[         6 tests found           ]
[         0 tests skipped         ]
[         6 tests started         ]
[         0 tests aborted         ]
[         6 tests successful      ]
[         0 tests failed          ]
""".trim()
    }

    def "can run tests in a native image with the Maven plugin using shading"() {
        withSample("java-application-with-tests")

        when:
        mvn '-Pshaded', '-DquickBuild', 'integration-test'

        then:
        buildSucceeded
        outputContains "[junit-platform-native] Running in 'test listener' mode"
        outputContains """
[         3 containers found      ]
[         0 containers skipped    ]
[         3 containers started    ]
[         0 containers aborted    ]
[         3 containers successful ]
[         0 containers failed     ]
[         6 tests found           ]
[         0 tests skipped         ]
[         6 tests started         ]
[         0 tests aborted         ]
[         6 tests successful      ]
[         0 tests failed          ]
""".trim()
    }

    @Issue("https://github.com/graalvm/native-build-tools/issues/179")
    def "can skip JVM tests and native image tests with the Maven plugin with -DskipTests"() {
        withSample("java-application-with-tests")

        when:
        mvn '-Pnative', '-DquickBuild', 'test', '-DskipTests'

        then:
        buildSucceeded
        outputDoesNotContain "SurefirePlugin - Running org.graalvm.demo.CalculatorTest"
        outputContains "Skipping native-image tests (parameter 'skipTests' or 'skipNativeTests' is true)."
        outputDoesNotContain "containers found"
    }

    @Issue("https://github.com/graalvm/native-build-tools/issues/179")
    def "can skip native image tests with the Maven plugin with -DskipNativeTests"() {
        withSample("java-application-with-tests")

        when:
        mvn '-Pnative', '-DquickBuild', 'test', '-DskipNativeTests'

        then:
        buildSucceeded
        outputContains "SurefirePlugin - Running org.graalvm.demo.CalculatorTest"
        outputContains "Skipping native-image tests (parameter 'skipTests' or 'skipNativeTests' is true)."
        outputDoesNotContain "containers found"
    }

    @Issue("https://github.com/graalvm/native-build-tools/issues/215")
    def "can pass environment variables to native test execution"() {
        given:
        withSample("java-application-with-tests")

        when:
        mvn '-Pnative', '-DquickBuild', '-Ptest-variables', 'test'

        then:
        buildSucceeded
        outputContains "[junit-platform-native] Running in 'test listener' mode"
        def nativeImageExecResult = after("JUnit Platform on Native Image - report")
        nativeImageExecResult.contains "TEST_ENV = test-value"
        nativeImageExecResult.contains "test-property = test-value"

    }

    @Issue("https://github.com/graalvm/native-build-tools/issues/178")
    def "can run tests in a multimodule project"() {
        given:
        withSample("multi-project-with-tests")

        when:
        mvn '-DquickBuild', 'package'

        then:
        buildSucceeded
        outputContains "SurefirePlugin - Tests run: 8, Failures: 0, Errors: 0, Skipped: 0"
    }

    @IgnoreIf({ os.windows })
    def "dependencies with scope provided are on classpath for test binary"() {
        withSample("java-application-with-tests")

        when:
        mvn '-Pnative', '-DquickBuild', 'test'

        def expectedOutput = ["local-repo", "org", "apache", "commons", "commons-lang3", "3.12.0", "commons-lang3-3.12.0.jar"].join(File.separator)

        then:
        buildSucceeded
        outputContains expectedOutput
    }

    @IgnoreIf({ os.windows })
    def "dependencies with scope provided are not on classpath for main binary"() {
        withSample("java-application-with-tests")

        when:
        mvn '-Pnative', '-DquickBuild', '-DskipNativeTests', 'package'

        def expectedOutput = ["local-repo", "org", "apache", "commons", "commons-lang3", "3.12.0", "commons-lang3-3.12.0.jar"].join(File.separator)

        then:
        buildSucceeded
        outputDoesNotContain expectedOutput
    }

    private static int getCurrentJDKVersion() {
        return NativeImageUtils.getMajorJDKVersion(GraalVMSupport.getGraalVMHomeVersionString())
    }

    @Requires({ getCurrentJDKVersion() >= 23 })
    def "can use the Maven plugin with the runtimeArgs config to run tests in a native image"() {
        withSample("java-application-with-tests")

        when:
        mvn '-Ptest-runtime-args', '-DquickBuild', 'test'

        def expectedOutput = "Note: this run will print partial stack traces of the locations where a"

        then:
        buildSucceeded
        outputContains expectedOutput
    }
}
