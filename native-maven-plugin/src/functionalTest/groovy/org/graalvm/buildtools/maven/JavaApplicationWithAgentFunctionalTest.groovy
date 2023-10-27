/*
 * Copyright (c) 2020, 2021 Oracle and/or its affiliates. All rights reserved.
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

import spock.lang.Issue
import spock.lang.Unroll

class JavaApplicationWithAgentFunctionalTest extends AbstractGraalVMMavenFunctionalTest {

    @Issue("https://github.com/graalvm/native-build-tools/issues/179")
    def "agent is used for JVM tests when native image tests are skipped via -DskipNativeTests"() {
        given:
        withSample("java-application-with-reflection")

        when:
        // Run Maven in debug mode (-X) in order to capture the command line arguments
        // used to launch Surefire with the agent.
        mvnDebug '-Pnative', '-DquickBuild', 'test', '-Dagent=true', '-DskipNativeTests'

        then:
        // Agent is used with Surefire
        outputContains '-agentlib:native-image-agent='

        and:
        // Agent generates files
        ['jni', 'proxy', 'reflect', 'resource', 'serialization'].each { name ->
            assert file("target/native/agent-output/test/${name}-config.json").exists()
        }

        and:
        // Surefire / JVM tests run
        buildSucceeded
        outputContains "SurefirePlugin - Running org.graalvm.demo.ApplicationTest"
        outputContains "SurefirePlugin - Running org.graalvm.demo.CalculatorTest"

        and:
        // Native tests do not run
        outputContains "Skipping native-image tests (parameter 'skipTests' or 'skipNativeTests' is true)."
        outputDoesNotContain "containers found"
    }

    def "test agent with metadata copy task"() {
        given:
        withSample("java-application-with-reflection")
        mvn'-Pnative', '-DquickBuild', '-DskipNativeBuild=true', 'package', '-Dagent=true', 'exec:exec@java-agent'

        when:
        mvn'-Pnative', '-DquickBuild', '-DskipNativeTests', '-Dagent=true', 'native:metadata-copy'

        then:
        buildSucceeded
        outputDoesNotContain "Cannot merge agent files because native-image-configure is not installed. Please upgrade to a newer version of GraalVM."
        outputDoesNotContain "returned non-zero result"
        outputDoesNotContain "Agent files cannot be copied."
        outputDoesNotContain "Cannot collect agent configuration."
        outputContains "Metadata copy process finished."
    }

    def "test agent with metadata copy task and disabled stages"() {
        given:
        withSample("java-application-with-reflection")
        mvn'-PagentConfigurationWithDisabledStages', '-DquickBuild', '-DskipNativeBuild=true', 'package', 'exec:exec@java-agent'

        when:
        mvn '-PagentConfigurationWithDisabledStages', '-DquickBuild', '-DskipNativeTests', 'native:metadata-copy'

        then:
        buildSucceeded
        outputDoesNotContain "Cannot collect agent configuration."
        outputDoesNotContain "Cannot merge agent files because native-image-configure is not installed. Please upgrade to a newer version of GraalVM."
        outputDoesNotContain "returned non-zero result"
        outputDoesNotContain "Agent files cannot be copied."
        outputContains "Copying files from: test"
        outputContains "Metadata copy process finished."
    }

    def "test agent in direct mode with metadata copy task"() {
        given:
        withSample("java-application-with-reflection")
        mvn'-PagentConfigurationDirectMode', '-DquickBuild', '-DskipNativeBuild=true', 'package', 'exec:exec@java-agent'

        when:
        mvn '-PagentConfigurationDirectMode', '-DquickBuild', '-DskipNativeTests', 'native:metadata-copy'

        then:
        buildSucceeded
        outputDoesNotContain "Cannot collect agent configuration."
        outputDoesNotContain "Cannot merge agent files because native-image-configure is not installed. Please upgrade to a newer version of GraalVM."
        outputDoesNotContain "returned non-zero result"
        outputContains "You are running agent in direct mode. Skipping both merge and metadata copy tasks."
    }

    def "test agent in conditional mode with metadata copy task"() {
        given:
        withSample("java-application-with-reflection")
        mvn '-PagentConfigurationConditionalMode', '-DquickBuild', '-DskipNativeBuild=true', 'package', 'exec:exec@java-agent'

        when:
        mvn '-PagentConfigurationConditionalMode', '-DquickBuild', '-DskipNativeTests', 'native:metadata-copy'

        then:
        buildSucceeded
        outputDoesNotContain "Cannot collect agent configuration."
        outputDoesNotContain "Cannot merge agent files because native-image-configure is not installed. Please upgrade to a newer version of GraalVM."
        outputDoesNotContain "returned non-zero result"
    }

    def "test without agent configuration"() {
        given:
        withSample("java-application-with-reflection")

        when:
        mvn'-PnoAgentConfiguration', '-DquickBuild', 'package'

        then:
        buildSucceeded
    }

    def "agent is not used for tests when enabled in POM but disabled via the command line"() {
        given:
        withSample("java-application-with-reflection")

        when:
        mvn '-Pnative', '-DquickBuild', '-Dagent=false', 'test'

        then:
        outputContains """
[         4 containers found      ]
[         0 containers skipped    ]
[         4 containers started    ]
[         0 containers aborted    ]
[         4 containers successful ]
[         0 containers failed     ]
[         7 tests found           ]
[         0 tests skipped         ]
[         7 tests started         ]
[         0 tests aborted         ]
[         6 tests successful      ]
[         1 tests failed          ]
""".trim()

        and:
        outputContains 'expected: <Hello, native!> but was: <null>'
    }
}
