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
import org.gradle.api.logging.LogLevel
import spock.lang.Unroll

class NativeConfigRepoFunctionalTest extends AbstractFunctionalTest {

    @Unroll
    def "can build a native image using native configuration from a #label"() {
        given:
        withSample("native-config-integration")

        switch (format) {
            case 'dir':
                break
            case 'zip':
                run 'configZip'
                break
            default:
                run "config${format.split('[.]').collect {it.capitalize()}.join("")}"
        }

        when:
        def extension = format == 'dir' ? '' : format
        run 'nativeRun', "-D${NativeImagePlugin.CONFIG_REPO_LOGLEVEL}=${LogLevel.LIFECYCLE}", "-Dextension=$extension"

        then:
        tasks {
            succeeded ':jar', ':nativeCompile', ':nativeRun'
        }

        then:
        outputContains "Hello, from reflection!"

        and: "doesn't find a configuration directory for the current version"
        outputContains "[graalvm reachability metadata repository for org.graalvm.internal:library-with-reflection:1.5]: Configuration directory not found. Trying latest version."

        and: "but finds one thanks to the latest configuration field"
        outputContains "[graalvm reachability metadata repository for org.graalvm.internal:library-with-reflection:1.5]: Configuration directory is org.graalvm.internal" + File.separator + "library-with-reflection" + File.separator + "1"

        where:
        format   | label
        'dir'  | "flat directory"
        'zip'    | 'zip file'
        'tar.gz' | 'tar.gz file'
        'tar.bz2' | 'tar.bz2 file'
    }

    def "can exclude a dependency from native configuration"() {
        given:
        withSample("native-config-integration")

        buildFile << """
graalvmNative {
    metadataRepository {
        excludedModules.add("org.graalvm.internal:library-with-reflection")
    }
}
        """

        when:
        run 'nativeRun', "-D${NativeImagePlugin.CONFIG_REPO_LOGLEVEL}=${LogLevel.LIFECYCLE}"

        then:
        tasks {
            succeeded ':jar', ':nativeCompile', ':nativeRun'
        }

        then:
        outputContains "Reflection failed"

        and: "doesn't look for a configuration directory for the current version"
        outputDoesNotContain "[graalvm reachability metadata repository for org.graalvm.internal:library-with-reflection:1.5]: Configuration directory not found. Trying latest version."
    }

    def "can force a dependency to a specific config version"() {
        given:
        withSample("native-config-integration")

        buildFile << """
graalvmNative {
    metadataRepository {
        moduleToConfigVersion.put("org.graalvm.internal:library-with-reflection", "2")
    }
}
        """

        when:
        run 'nativeRun', "-D${NativeImagePlugin.CONFIG_REPO_LOGLEVEL}=${LogLevel.LIFECYCLE}"

        then:
        tasks {
            succeeded ':jar', ':nativeCompile', ':nativeRun'
        }

        then:
        outputContains "Reflection failed"

        and: "looks for specific configuration version"
        outputContains "[graalvm reachability metadata repository for org.graalvm.internal:library-with-reflection:1.5]: Configuration is forced to version 2"
    }

}
