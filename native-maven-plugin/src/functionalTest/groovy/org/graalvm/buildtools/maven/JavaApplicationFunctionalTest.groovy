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

import spock.lang.Issue

import static org.graalvm.buildtools.utils.SharedConstants.NATIVE_IMAGE_EXE;

class JavaApplicationFunctionalTest extends AbstractGraalVMMavenFunctionalTest {
    def "proper options are added to the native-image invocation"() {
        withSample("java-application")

        when:
        mvn '-Pnative', '-DskipTests', '-DnativeDryRun', '-DuseArgFile=false',
                '-Dclasspath=/', '-Ddebug', '-Dfallback=false', '-Dverbose', '-DsharedLibrary',
                '-DquickBuild',
                'package'

        then:
        buildSucceeded
        outputContains NATIVE_IMAGE_EXE
        outputContains "-cp " // actual path is OS-specific (/ vs C:\)
        outputContains "-g --no-fallback --verbose --shared -Ob"
    }

    def "can build and execute a native image with the Maven plugin"() {
        withSample("java-application")

        when:
        mvn '-Pnative', '-DskipTests', 'package', 'exec:exec@native'

        then:
        buildSucceeded
        outputContains "Hello, native!"
    }

    def "can build and execute a native image with the Maven plugin and the shade plugin"() {
        withSample("java-application")

        when:
        mvn '-Pshaded', '-DskipTests', 'package', 'exec:exec@native'

        then:
        buildSucceeded
        outputContains "Hello, native!"
    }

    @Issue("")
    def "supports spaces in file names (useArgFile = #argFile)"() {
        withSpacesInProjectDir()
        withSample("java-application")

        when:
        mvn '-Pnative', '-DskipTests', 'package', 'exec:exec@native', "-DuseArgFile=${argFile}"

        then:
        buildSucceeded
        outputContains "Hello, native!"

        where:
        argFile << [true, false]
    }

    def "can build and execute a native image with the Maven plugin when the application has a custom packaging type"() {
        withSample("java-application-with-custom-packaging")

        when:
        mvnDebug 'package', '-Dpackaging=native-image', 'exec:exec@native'

        then:
        buildSucceeded
        outputContains "ImageClasspath Entry: org.graalvm.buildtools.examples:java-application-with-custom-packaging:native-image:0.1"
        outputContains "Hello, native!"
    }

    def "can write the args file"() {
        withSample("java-application")

        when:
        mvn '-Pnative', 'native:write-args-file'

        then:
        buildSucceeded
        outputContains "Args file written to: target" + File.separator + "native-image"
    }

}
