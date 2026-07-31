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
package org.graalvm.buildtools.gradle.tasks

import org.graalvm.buildtools.gradle.NativeImagePlugin
import org.graalvm.buildtools.gradle.dsl.GraalVMExtension
import org.gradle.api.GradleException
import org.gradle.testfixtures.ProjectBuilder
import spock.lang.Specification

// Protects layer preflight validation before Native Image invocation. §FS-native-invocation.3.
class BuildNativeImageTaskTest extends Specification {
    def "rejects an empty named layer"() {
        given:
        def project = ProjectBuilder.builder().build()
        project.plugins.apply("java")
        project.plugins.apply(NativeImagePlugin)
        def extension = project.extensions.getByType(GraalVMExtension)
        def layer = extension.layers.create("empty")
        def task = project.tasks.getByName("nativeEmptyLayer") as BuildNativeImageTask

        when:
        BuildNativeImageTask.validateLayerConfiguration(task.options.get())

        then:
        def e = thrown(GradleException)
        e.message.contains("Layer 'empty' has no contents")
    }

    def "rejects duplicate provider-backed layer selections"() {
        given:
        def project = ProjectBuilder.builder().build()
        project.plugins.apply("java")
        project.plugins.apply(NativeImagePlugin)
        def extension = project.extensions.getByType(GraalVMExtension)
        def layer = extension.layers.create("dependencies")
        layer.contents.modules("java.base")
        def binary = extension.binaries.main
        binary.useLayer(project.provider { layer })
        binary.useLayer(project.provider { layer })

        when:
        BuildNativeImageTask.validateLayerConfiguration(binary)

        then:
        def e = thrown(GradleException)
        e.message.contains("A Native Image layer is selected more than once")
    }
}
