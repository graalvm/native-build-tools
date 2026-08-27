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

import org.graalvm.buildtools.maven.config.PreserveConfiguration
import org.graalvm.buildtools.maven.config.PreserveDependencyConfiguration
import spock.lang.Specification

// Verifies Maven XML bean semantics and application-goal ownership for Preserve. §FS-config-model.8.
class PreserveConfigurationTest extends Specification {
    def "dependency selection is transitive by default and configurable"() {
        given:
        def dependency = new PreserveDependencyConfiguration(artifact: "com.acme:extension")

        expect:
        dependency.transitive

        when:
        dependency.transitive = false

        then:
        !dependency.transitive
    }

    def "configuration exposes dependencies only"() {
        expect:
        PreserveConfiguration.declaredFields.findAll { !it.synthetic }*.name == ["dependencies"]
    }

    def "parameter belongs only to application compile goals"() {
        expect:
        NativeCompileNoForkGoalMojo.getDeclaredField("preserve")
        NativeCompileNoForkGoalMojo.superclass == NativeCompileNoForkMojo
        NativeCompileMojo.getDeclaredField("preserve")
        DeprecatedNativeBuildMojo.getDeclaredField("preserve")
        NativeCompileMojo.superclass == NativeCompileNoForkMojo
        DeprecatedNativeBuildMojo.superclass == NativeCompileNoForkMojo
        AbstractNativeImageMojo.declaredMethods*.name.contains("preserveConfiguration")
        !AbstractNativeImageMojo.declaredMethods*.name.contains("getPreserveConfiguration")
        !WriteArgsFileMojo.declaredFields*.name.contains("preserve")
        !NativeTestMojo.declaredFields*.name.contains("preserve")
        !NativeIntegrationTestMojo.declaredFields*.name.contains("preserve")
        !LayerCreateMojo.declaredFields*.name.contains("preserve")
    }
}
