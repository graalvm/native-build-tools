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

package org.graalvm.buildtools.gradle

import org.graalvm.buildtools.gradle.fixtures.AbstractFunctionalTest
import org.graalvm.buildtools.gradle.fixtures.TestResults
import spock.lang.Unroll

class KotlinApplicationWithTestsFunctionalTest extends AbstractFunctionalTest {

    @Unroll("can execute Kotlin tests in a native image directly with JUnit Platform #junitVersion")
    def "can execute Kotlin tests in a native image directly"() {
        given:
        withSample("kotlin-application-with-tests")

        when:
        run 'nativeTest'

        then:
        tasks {
            succeeded ':compileTestKotlin',
                    ':nativeTestCompile',
                    ':test',
                    ':nativeTest'
            doesNotContain ':build'
        }

        then:
        outputDoesNotContain "Running in 'test discovery' mode. Note that this is a fallback mode."
        outputContains "Running in 'test listener' mode using files matching pattern [junit-platform-unique-ids*] found in folder ["

        outputContains 'ktest.AppTest > testAppHasAGreeting() SUCCESSFUL'
        outputContains """
[         2 containers found      ]
[         0 containers skipped    ]
[         2 containers started    ]
[         0 containers aborted    ]
[         2 containers successful ]
[         0 containers failed     ]
[         1 tests found           ]
[         0 tests skipped         ]
[         1 tests started         ]
[         0 tests aborted         ]
[         1 tests successful      ]
[         0 tests failed          ]
""".trim()

        and:
        def results = TestResults.from(file("build/test-results/test/TEST-ktest.AppTest.xml"))
        def nativeResults = TestResults.from(file("build/test-results/test-native/TEST-junit-jupiter.xml"))

        results == nativeResults
        results.with {
            tests == 1
            failures == 0
            skipped == 0
            errors == 0
        }

        where:
        junitVersion = System.getProperty('versions.junit')
    }
}
