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
        mvn '-X', '-Pnative', 'test', '-DskipNativeTests'

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

    def "agent is used for tests when enabled in POM without custom options"() {
        given:
        withSample("java-application-with-reflection")

        when:
        // Run Maven in debug mode (-X) in order to capture the command line arguments
        // used to launch Surefire with the agent.
        mvn '-X', '-Pnative', 'test'

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
[         7 tests successful      ]
[         0 tests failed          ]
""".trim()

        and:
        ['jni', 'proxy', 'reflect', 'resource', 'serialization'].each { name ->
            assert file("target/native/agent-output/test/${name}-config.json").exists()
        }

        and:
        outputContains '-agentlib:native-image-agent'
        // From shared, unnamed config:
        outputContains '=experimental-class-loader-support'
        // From test config:
        outputContains ',access-filter-file='
        outputContains '/src/test/resources/access-filter.json'.replace('/', java.io.File.separator)
        // Always configured:
        outputContains ',config-output-dir='
        outputContains '/target/native/agent-output/test'.replace("/", java.io.File.separator)

        and:
        // If the custom access-filter.json is applied, we should not see any warnings about Surefire types.
        // The actual warning would be something like:
        // Warning: Could not resolve org.apache.maven.surefire.junitplatform.JUnitPlatformProvider for reflection configuration. Reason: java.lang.ClassNotFoundException: org.apache.maven.surefire.junitplatform.JUnitPlatformProvider.
        outputDoesNotContain 'Warning: Could not resolve org.apache.maven.surefire'
        // From periodic-config:
        outputDoesNotContain ',config-write-period-secs=30,config-write-initial-delay-secs=5'
    }

    def "agent is not used for tests when enabled in POM but disabled via the command line"() {
        given:
        withSample("java-application-with-reflection")

        when:
        mvn '-Pnative', '-Dagent=false', 'test'

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

    def "agent is used for tests when enabled in POM with custom options"() {
        given:
        withSample("java-application-with-reflection")

        when:
        // Run Maven in debug mode (-X) in order to capture the command line arguments
        // used to launch Surefire with the agent.
        mvn '-X', '-Pnative', '-DagentOptions=periodic-config', 'test'

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
[         7 tests successful      ]
[         0 tests failed          ]
""".trim()

        and:
        ['jni', 'proxy', 'reflect', 'resource', 'serialization'].each { name ->
            assert file("target/native/agent-output/test/${name}-config.json").exists()
        }

        and:
        // If custom agent options are processed, the debug output for Surefire
        // should include the following segments of the agent command line argument.
        outputContains '-agentlib:native-image-agent'
        // From shared, unnamed config:
        outputContains '=experimental-class-loader-support'
        // From test config:
        outputContains ',access-filter-file='
        outputContains '/src/test/resources/access-filter.json'.replace('/', java.io.File.separator)
        // From periodic-config:
        outputContains ',config-write-period-secs=30,config-write-initial-delay-secs=5'
        // Always configured:
        outputContains ',config-output-dir='
        outputContains '/target/native/agent-output/test'.replace("/", java.io.File.separator)

        and:
        // If the custom access-filter.json is applied, we should not see any warnings about Surefire types.
        // The actual warning would be something like:
        // Warning: Could not resolve org.apache.maven.surefire.junitplatform.JUnitPlatformProvider for reflection configuration. Reason: java.lang.ClassNotFoundException: org.apache.maven.surefire.junitplatform.JUnitPlatformProvider.
        outputDoesNotContain 'Warning: Could not resolve org.apache.maven.surefire'
    }

    @Issue("https://github.com/graalvm/native-build-tools/issues/134")
    @Unroll("generated agent files are added when building native image on Maven #version with JUnit Platform #junitVersion")
    def "generated agent files are used when building native image"() {
        given:
        withSample("java-application-with-reflection")

        when:
        mvn '-X', '-Pnative', '-DskipTests=true', '-DskipNativeBuild=true', 'package', 'exec:exec@java-agent'

        then:
        ['jni', 'proxy', 'reflect', 'resource', 'serialization'].each { name ->
            assert file("target/native/agent-output/main/${name}-config.json").exists()
        }

        and:
        // If custom agent options are not used, the Maven debug output should include
        // the following segments of the agent command line argument.
        outputContains '-agentlib:native-image-agent'
        // From shared, unnamed config:
        outputContains '=experimental-class-loader-support'
        // From main config:
        outputContains ',access-filter-file='
        outputContains '/src/main/resources/access-filter.json'.replace('/', java.io.File.separator)
        // Always configured:
        outputContains ',config-output-dir='
        outputContains '/target/native/agent-output/main'.replace("/", java.io.File.separator)

        when:
        mvn '-Pnative', '-DskipTests=true', 'package', 'exec:exec@native'

        then:
        outputContains "Application message: Hello, native!"
    }

    def "generated agent files are not used when building native image when agent is enabled in POM but disabled via the command line"() {
        given:
        withSample("java-application-with-reflection")

        when:
        mvn '-X', '-Pnative', '-Dagent=false', '-DskipTests=true', '-DskipNativeBuild=true', 'package', 'exec:exec@java-agent'

        then:
        outputDoesNotContain '-agentlib:native-image-agent'

        when:
        mvn '-Pnative', '-DskipTests=true', 'package', 'exec:exec@native'

        then:
        outputContains "Application message: null"
    }

    def "custom options and generated agent files are used when building native image"() {
        given:
        withSample("java-application-with-reflection")

        when:
        mvn '-X', '-Pnative', '-DagentOptions=periodic-config', '-DskipTests=true', '-DskipNativeBuild=true', 'package', 'exec:exec@java-agent'

        then:
        ['jni', 'proxy', 'reflect', 'resource', 'serialization'].each { name ->
            assert file("target/native/agent-output/main/${name}-config.json").exists()
        }

        and:
        // If custom agent options are used, the Maven debug output should include
        // the following segments of the agent command line argument.
        outputContains '-agentlib:native-image-agent'
        // From shared, unnamed config:
        outputContains '=experimental-class-loader-support'
        // From main config:
        outputContains ',access-filter-file='
        outputContains '/src/main/resources/access-filter.json'.replace('/', java.io.File.separator)
        // From periodic-config:
        outputContains ',config-write-period-secs=30,config-write-initial-delay-secs=5'
        // Always configured:
        outputContains ',config-output-dir='
        outputContains '/target/native/agent-output/main'.replace("/", java.io.File.separator)

        when:
        mvn '-Pnative', '-DskipTests=true', 'package', 'exec:exec@native'

        then:
        outputContains "Application message: Hello, native!"
    }

}
