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

import org.graalvm.buildtools.utils.NativeImageUtils
import spock.lang.Requires

import static org.graalvm.buildtools.utils.SharedConstants.EXECUTABLE_EXTENSION

// Proves application-goal Preserve behavior against an equivalent control image. §E2E-functional-tests.3.9.
class PreserveDependencyFunctionalTest extends AbstractGraalVMMavenFunctionalTest {
    private static final String TRANSITIVE_CLASS = 'org.apache.hc.core5.http.HttpEntity'

    @Requires({ NativeImageUtils.getMajorJDKVersion(GraalVMSupport.getGraalVMHomeVersionString()) >= 25 })
    def "preserves a class from a transitive dependency"() {
        given:
        withSample('java-application')
        def pom = file('pom.xml')
        pom.text = pom.text.replaceFirst(/<dependencies>\s*<dependency>/, '''<dependencies>
        <dependency>
            <groupId>org.apache.httpcomponents.client5</groupId>
            <artifactId>httpclient5</artifactId>
            <version>5.4.1</version>
        </dependency>
        <dependency>''')
        file('src/main/java/org/graalvm/demo/Application.java').text = '''
            package org.graalvm.demo;

            public class Application {
                public static void main(String[] args) throws Exception {
                    System.out.println(Class.forName(args[0]).getName());
                }
            }
        '''.stripIndent()

        when:
        mvn '-Pnative', '-DquickBuild', '-DskipTests', '-DimageName=control', 'package'

        then:
        buildSucceeded
        def control = executeWithArgument(nativeExecutable('control'), TRANSITIVE_CLASS)
        control.exitCode != 0

        when:
        pom.text = pom.text.replace('''                            <skip>false</skip>''', '''                            <skip>false</skip>
                            <preserve>
                                <dependencies>
                                    <dependency>
                                        <artifact>org.apache.httpcomponents.core5:httpcore5:5.3.1</artifact>
                                        <transitive>false</transitive>
                                    </dependency>
                                </dependencies>
                            </preserve>''')
        mvn '-Pnative', '-DquickBuild', '-DskipTests', '-DimageName=preserved', 'package'

        then:
        buildSucceeded
        outputContains '-H:Preserve=path='
        def preserved = executeWithArgument(nativeExecutable('preserved'), TRANSITIVE_CLASS)
        preserved.exitCode == 0
        preserved.output.contains(TRANSITIVE_CLASS)
    }

    private static Map<String, Object> executeWithArgument(File executable, String argument) {
        def process = [executable.absolutePath, argument].execute(null, executable.parentFile)
        def output = new StringWriter()
        def error = new StringWriter()
        process.waitForProcessOutput(output, error)
        [exitCode: process.exitValue(), output: output.toString(), error: error.toString()]
    }

    private File nativeExecutable(String name) {
        file("target/${name}${EXECUTABLE_EXTENSION}")
    }
}
