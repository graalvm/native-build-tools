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

    def "agent is used without custom options"() {
        given:
        withSample("java-application-with-reflection")

        when:
        mvn '-Pnative', '-Dagent=true', 'test'

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

        // If the custom access-filter.json is NOT applied, we should see a warning similar to the following.
        // Warning: Could not resolve org.apache.maven.surefire.junitplatform.JUnitPlatformProvider for reflection configuration. Reason: java.lang.ClassNotFoundException: org.apache.maven.surefire.junitplatform.JUnitPlatformProvider.
        and:
        outputContains 'Warning: Could not resolve org.apache.maven.surefire'
    }

    def "agent is used with custom options"() {
        given:
        withSample("java-application-with-reflection")

        when:
        // Run Maven in debug mode (-X) in order to capture the command line arguments
        // used to launch Surefire with the agent.
        mvn '-X', '-Pnative', '-Dagent=true', '-DagentOptions=test', 'test'

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
        // -agentlib:native-image-agent=experimental-class-loader-support,access-filter-file=<BUILD_DIR>/src/test/resources/access-filter.json,config-output-dir=<BUILD_DIR>/target/native/agent-output/test
        outputContains '-agentlib:native-image-agent=experimental-class-loader-support,access-filter-file='
        outputContains '/src/test/resources/access-filter.json'.replace('/', java.io.File.separator)
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
        mvn '-X', '-Pnative', '-Dagent=true', '-DskipTests=true', '-DskipNativeBuild=true', 'package', 'exec:exec@java-agent'

        then:
        ['jni', 'proxy', 'reflect', 'resource', 'serialization'].each { name ->
            assert file("target/native/agent-output/exec/${name}-config.json").exists()
        }

        and:
        // If custom agent options are not used, the Maven debug output should include
        // the following segments of the agent command line argument.
        // -agentlib:native-image-agent=config-output-dir=<BUILD_DIR>/target/native/agent-output/exec
        outputContains '-agentlib:native-image-agent=config-output-dir='
        outputContains '/target/native/agent-output/exec'.replace("/", java.io.File.separator)
        outputDoesNotContain 'experimental-class-loader-support'

        when:
        mvn '-Pnative', '-Dagent=true', '-DskipTests=true', 'package', 'exec:exec@native'

        then:
        outputContains "Hello, native!"
    }

    def "custom options and generated agent files are used when building native image"() {
        given:
        withSample("java-application-with-reflection")

        when:
        mvn '-X', '-Pnative', '-Dagent=true', '-DagentOptions=exec', '-DskipTests=true', '-DskipNativeBuild=true', 'package', 'exec:exec@java-agent'

        then:
        ['jni', 'proxy', 'reflect', 'resource', 'serialization'].each { name ->
            assert file("target/native/agent-output/exec/${name}-config.json").exists()
        }

        and:
        // If custom agent options are used, the Maven debug output should include
        // the following segments of the agent command line argument.
        // -agentlib:native-image-agent=experimental-class-loader-support,config-output-dir=<BUILD_DIR>/target/native/agent-output/exec
        outputContains '-agentlib:native-image-agent=experimental-class-loader-support,config-output-dir='
        outputContains '/target/native/agent-output/exec'.replace("/", java.io.File.separator)
        outputDoesNotContain 'access-filter-file='

        when:
        mvn '-Pnative', '-Dagent=true', '-DskipTests=true', 'package', 'exec:exec@native'

        then:
        outputContains "Hello, native!"
    }

}
