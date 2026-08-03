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

import org.graalvm.buildtools.utils.NativeImageLayerArguments
import org.graalvm.buildtools.utils.NativeImageUtils
import spock.lang.IgnoreIf
import spock.lang.Requires

// Exercises Maven reactor production and consumption of nil layer artifacts. §E2E-functional-tests.3.8.
class LayeredApplicationFunctionalTest extends AbstractGraalVMMavenFunctionalTest {
    def "loads the nil artifact handler and reactor model"() {
        given:
        withSample("layered-maven-application")

        when:
        mvn 'help:effective-pom', '-DskipTests'

        then:
        buildSucceeded
        outputContains "<type>nil</type>"
        outputContains "<goal>layer-create</goal>"
    }

    @Requires({ NativeImageUtils.getMajorJDKVersion(GraalVMSupport.getGraalVMHomeVersionString()) >= 25 })
    @IgnoreIf({ os.windows || os.macOs })
    def "builds and consumes a layer artifact in one reactor"() {
        given:
        withSample("layered-maven-application")
        def unsupportedLayerConsumption = NativeImageLayerArguments.isLayerConsumptionUnsupported(
                GraalVMSupport.getGraalVMHomeVersionString())

        when:
        mvn '-DquickBuild', '-DskipTests', 'package'

        then:
        file("base-layer/target/native/layers/base/base.nil").isFile()
        outputContains "-H:LayerCreate=base.nil"
        if (unsupportedLayerConsumption) {
            buildFailed
            outputContains "Native Image 25.0.3 and 25.0.4 do not support reliable layer consumption"
            outputContains "Upgrade Native Image, remove the useLayers configuration"
            outputDoesNotContain "LayeredDispatchTableFeature"
        } else {
            assert result.exitCode == 0
            def application = file("application/target/application")
            assert application.isFile()
            assert outputContains("-H:LayerUse=")
            def environment = System.getenv().collect { key, value -> "${key}=${value}" }
            def layerDirectory = file("base-layer/target/native/layers/base").absolutePath
            def inheritedLibraryPath = System.getenv("LD_LIBRARY_PATH")
            environment << "LD_LIBRARY_PATH=${inheritedLibraryPath ? inheritedLibraryPath + File.pathSeparator : ''}${layerDirectory}"
            def execution = [application.absolutePath].execute(environment, application.parentFile)
            assert execution.waitFor() == 0
            assert execution.in.text.contains("Hello, layered Maven!")
        }
    }

    @Requires({ NativeImageUtils.getMajorJDKVersion(GraalVMSupport.getGraalVMHomeVersionString()) >= 25 })
    @IgnoreIf({ os.windows || os.macOs })
    def "builds a modules-only layer without leaking the project classpath"() {
        given:
        withSample("layered-maven-application")
        def basePom = file("base-layer/pom.xml")
        basePom.text = basePom.text
            .replace('''    <dependencies>
        <dependency>
            <groupId>org.slf4j</groupId>
            <artifactId>slf4j-api</artifactId>
            <version>2.0.17</version>
            <optional>true</optional>
        </dependency>
    </dependencies>

''', '')
            .replace('''                                <packages>
                                    <package>org.slf4j</package>
                                </packages>
                                <dependencies>
                                    <dependency>
                                        <artifact>org.slf4j:slf4j-api</artifact>
                                    </dependency>
                                </dependencies>
''', '')

        when:
        mvn '-DquickBuild', '-DskipTests', '-pl', 'base-layer', 'package'

        then:
        buildSucceeded
        file("base-layer/target/native/layers/base/base.nil").isFile()
        def invocation = result.stdOut.readLines().find {
            it.contains("Executing:") && it.contains("-H:LayerCreate=base.nil,module=java.base")
        }
        invocation != null
        !invocation.contains(" -cp ")
    }
}
