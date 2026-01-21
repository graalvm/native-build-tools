/*
 * Copyright (c) 2026, Oracle and/or its affiliates. All rights reserved.
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

class CompatibilityModeNativeTestsFunctionalTest extends AbstractGraalVMMavenFunctionalTest {

    def "OFF: native tests execute when Compatibility Mode is not enabled"() {
        given:
        // Use the multi-module sample which is known to execute native tests successfully
        withSample("multi-project-with-tests")

        when:
        // Run a full lifecycle so the test goal executes as part of 'test' phase
        mvn '-DquickBuild', 'package'

        then:
        buildSucceeded
        outputDoesNotContain "Compatibility Mode detected (-H:+CompatibilityMode); skipping native-image test goal, JVM tests will run instead."
        // Native test runner executed
        outputContains "[junit-platform-native] Running in 'test listener' mode"
        outputContains "[         0 containers skipped    ]"
        outputContains "[         0 tests skipped         ]"
    }

    def "ON via buildArgs: native test goal is skipped and JVM tests run"() {
        given:
        withSample("java-application-with-tests")

        when:
        // Provide Compatibility Mode via plugin build args (mapped from system property to @Parameter(property="buildArgs"))
        mvn '-Pnative', '-DquickBuild', '-DbuildArgs=-H:+CompatibilityMode', 'test'

        then:
        buildSucceeded
        outputContains "Compatibility Mode detected (-H:+CompatibilityMode); skipping native-image test goal, JVM tests will run instead."
        // Ensure native-image build/run was not invoked
        outputDoesNotContain "GraalVM Native Image: Generating 'native-tests"
        outputDoesNotContain "containers found"
        outputDoesNotContain "tests found"
    }

    def "ON via NATIVE_IMAGE_OPTIONS env in surefire config: native test goal is skipped and JVM tests run"() {
        given:
        withSample("java-application-with-tests")
        // Mutate the copied POM to configure Surefire environmentVariables with NATIVE_IMAGE_OPTIONS
        def pom = file("pom.xml")
        def xml = pom.text
        xml = xml.replaceFirst(
            /(<artifactId>maven-surefire-plugin<\/artifactId>\s*\R\s*<version>[^<]+<\/version>)/,
            '''$1
                            <configuration>
                                <environmentVariables>
                                    <NATIVE_IMAGE_OPTIONS>-H:+CompatibilityMode</NATIVE_IMAGE_OPTIONS>
                                </environmentVariables>
                            </configuration>'''
        )
        pom.text = xml

        when:
        mvn '-Pnative', '-DquickBuild', 'test'

        then:
        buildSucceeded
        outputContains "Compatibility Mode detected (-H:+CompatibilityMode); skipping native-image test goal, JVM tests will run instead."
        // Ensure native-image build/run was not invoked
        outputDoesNotContain "GraalVM Native Image: Generating 'native-tests"
        outputDoesNotContain "containers found"
        outputDoesNotContain "tests found"
    }
}
