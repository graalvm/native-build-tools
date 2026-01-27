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
package org.graalvm.buildtools.gradle

import org.graalvm.buildtools.gradle.dsl.GraalVMExtension
import org.graalvm.buildtools.gradle.dsl.NativeImageOptions
import org.gradle.api.Project
import org.gradle.api.plugins.JavaPlugin
import org.gradle.testfixtures.ProjectBuilder
import org.gradle.api.JavaVersion
import spock.lang.Requires
import spock.lang.Specification

@Requires({ JavaVersion.current().isCompatibleWith(JavaVersion.toVersion(25)) })
class CompatibilityModeGatingTest extends Specification {

    private static final String NATIVE_LAUNCHER = "org.graalvm.junit.platform.NativeImageJUnitLauncher"
    private static final String JVM_LAUNCHER = "org.junit.platform.console.ConsoleLauncher"

    private Project project
    private GraalVMExtension graal

    def setup() {
        project = ProjectBuilder.builder().build()
        // Apply Java first so the plugin configures testing wiring
        project.plugins.apply(JavaPlugin)
        project.plugins.apply(NativeImagePlugin)
        graal = project.extensions.findByType(GraalVMExtension)
        assert graal != null
    }

    def "default (Compatibility Mode OFF) wires JUnit native launcher main class"() {
        when:
        NativeImageOptions testOptions = graal.binaries.getByName("test")

        then:
        testOptions.getMainClass().getOrNull() == NATIVE_LAUNCHER
    }

    def "Compatibility Mode via buildArgs wires JVM ConsoleLauncher main class"() {
        given:
        NativeImageOptions testOptions = graal.binaries.getByName("test")
        // Toggle compatibility mode via explicit build arg
        testOptions.getBuildArgs().add("-H:+CompatibilityMode")

        when:
        String main = testOptions.getMainClass().getOrNull()

        then:
        main == JVM_LAUNCHER
    }

    def "Compatibility Mode via NATIVE_IMAGE_OPTIONS in task options env wires JVM ConsoleLauncher main class"() {
        given:
        NativeImageOptions testOptions = graal.binaries.getByName("test")
        // Toggle compatibility mode via environment map on the options
        testOptions.getEnvironmentVariables().put("NATIVE_IMAGE_OPTIONS", "-H:+CompatibilityMode")

        when:
        String main = testOptions.getMainClass().getOrNull()

        then:
        main == JVM_LAUNCHER
    }
}
