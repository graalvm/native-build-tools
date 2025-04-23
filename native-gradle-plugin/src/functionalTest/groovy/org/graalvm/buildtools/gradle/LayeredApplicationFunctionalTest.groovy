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
import org.graalvm.buildtools.gradle.fixtures.GraalVMSupport
import org.graalvm.buildtools.utils.NativeImageUtils
import spock.lang.Requires

@Requires(
        { NativeImageUtils.getMajorJDKVersion(GraalVMSupport.getGraalVMHomeVersionString()) >= 25 }
)
class LayeredApplicationFunctionalTest extends AbstractFunctionalTest {
    def "can build a native image using layers"() {
        def nativeApp = getExecutableFile("build/native/nativeCompile/layered-java-application")

        given:
        withSample("layered-java-application")

        when:
        run 'nativeLibdependenciesCompile'

        then:
        tasks {
            succeeded ':nativeLibdependenciesCompile'
        }
        outputContains "'-H:LayerCreate' (origin(s): command line)"

        when:
        run 'nativeRun', '-Pmessage="Hello, layered images!"'

        then:
        tasks {
            upToDate ':nativeLibdependenciesCompile'
            succeeded ':nativeCompile'
        }
        nativeApp.exists()

        and:
        outputContains "- '-H:LayerUse' (origin(s): command line)"
        outputContains "Hello, layered images!"

        when: "Updating the application without changing the dependencies"
        file("src/main/java/org/graalvm/demo/Application.java").text = """
package org.graalvm.demo;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Application {
    private static final Logger LOGGER = LoggerFactory.getLogger(Application.class);

    public static void main(String[] args) {
        LOGGER.info("App started with args {}", String.join(", ", args));
    }

}

"""
        run 'nativeRun', '-Pmessage="Hello, layered images!"'

        then:
        tasks {
            // Base layer is not rebuilt
            upToDate ':nativeLibdependenciesCompile'
            // Application layer is recompiled
            succeeded ':nativeCompile'
        }

        outputContains "- '-H:LayerUse' (origin(s): command line)"
        outputContains "Hello, layered images!"
    }
}
