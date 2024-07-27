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
import spock.lang.Unroll

class JavaApplicationWithAgentFunctionalTest extends AbstractFunctionalTest {

    @Unroll("agent is not passed and the application fails with JUnit Platform #junitVersion")
    def "agent is not passed"() {
        given:
        withSample("java-application-with-reflection")

        when:
        fails 'nativeTest'

        then:
        outputContains "expected: <Hello, native!> but was: <null>"

        where:
        junitVersion = System.getProperty('versions.junit')
    }

    @Unroll("agent is passed, generates metadata files and copies metadata with JUnit Platform #junitVersion")
    def "agent is passed"() {
        debug = true
        given:
        withSample("java-application-with-reflection")

        when:
        run 'nativeTest', '-Pagent'

        then:
        tasks {
            succeeded ':jar',
                    ':nativeTest'
            doesNotContain ':build'
        }

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
            assert file("build/native/agent-output/test/${name}-config.json").exists()
        }

        when:
        run 'metadataCopy'

        then:
        ['jni', 'proxy', 'reflect', 'resource', 'serialization'].each { name ->
            assert file("build/native/metadataCopyTest/${name}-config.json").exists()
        }


        where:
        junitVersion = System.getProperty('versions.junit')
    }

    @Unroll("agent property takes precedence with JUnit Platform #junitVersion")
    def "agent property takes precedence"() {
        given:
        withSample("java-application-with-reflection")

        when:
        run 'nativeTest', '-Pagent=conditional'

        then:
        tasks {
            succeeded ':nativeTest'
        }

        and:
        assert file("build/native/agent-output/test/reflect-config.json").text.contains("\"condition\"")

        where:
        junitVersion = System.getProperty('versions.junit')
    }

    @Unroll("agent instruments run task, metadata is copied and merged, and the app runs JUnit Platform #junitVersion")
    def "agent instruments run task"() {
        debug = true
        var metadata_dir = 'src/main/resources/META-INF/native-image'
        given:
        withSample("java-application-with-reflection")

        when:
        run 'run', '-Pagent=standard'

        then:
        tasks {
            succeeded ':run'
            doesNotContain ':jar'
        }

        and:
        ['jni', 'proxy', 'reflect', 'resource', 'serialization'].each { name ->
            assert file("build/native/agent-output/run/${name}-config.json").exists()
        }

        when:
        run'metadataCopy', '--task', 'run', '--dir', metadata_dir

        then:
        ['jni', 'proxy', 'reflect', 'resource', 'serialization'].each { name ->
            assert file("${metadata_dir}/${name}-config.json").exists()
        }

        and:
        var reflect_config = file("${metadata_dir}/reflect-config.json")
        var reflect_config_contents = reflect_config.text
        assert reflect_config_contents.contains("DummyClass") && reflect_config_contents.contains("org.graalvm.demo.Message")

        when:
        run 'nativeRun'

        then:
        outputContains "Application message: Hello, native!"

        where:
        junitVersion = System.getProperty('versions.junit')
    }

    @Unroll("plugin supports configuration cache (JUnit Platform #junitVersion)")
    def "supports configuration cache"() {
        given:
        withSample("java-application-with-reflection")

        when:
        run 'run', '-Pagent', '--configuration-cache'

        then:
        tasks {
            succeeded ':run'
            doesNotContain ':jar'
        }

        and:
        ['jni', 'proxy', 'reflect', 'resource', 'serialization'].each { name ->
            assert file("build/native/agent-output/run/${name}-config.json").exists()
        }

        when:
        run'run', '-Pagent', '--configuration-cache', '--rerun-tasks'

        then:
        tasks {
            succeeded ':run'
            doesNotContain ':jar'
        }
        outputContains "Reusing configuration cache"

        where:
        junitVersion = System.getProperty('versions.junit')
    }
}
