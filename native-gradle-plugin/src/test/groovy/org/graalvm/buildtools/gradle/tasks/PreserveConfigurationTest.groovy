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
import org.graalvm.buildtools.gradle.dsl.PreserveDependency
import org.gradle.api.artifacts.MinimalExternalModuleDependency
import org.gradle.api.artifacts.ModuleIdentifier
import org.gradle.api.artifacts.VersionConstraint
import org.gradle.api.plugins.ApplicationPlugin
import org.gradle.api.InvalidUserDataException
import spock.lang.Issue

// Verifies binary-scoped Preserve DSL wiring and provider-safe dependency notation. §FS-plugin-model.2.
class PreserveConfigurationTest extends AbstractPluginTest {
    @Issue("https://github.com/graalvm/native-build-tools/issues/978")
    def "dependency selection is transitive by default and configurable without eager resolution"() {
        given:
        def project = newProject()
        project.plugins.apply(ApplicationPlugin)
        project.plugins.apply(NativeImagePlugin)
        def options = project.extensions.getByType(GraalVMExtension).binaries.getByName("main")

        when:
        options.preserve {
            it.dependencies("com.acme:extension:1.0")
            it.dependencies("com.acme:standalone:1.0") { dependency ->
                dependency.transitive = false
            }
        }

        then:
        options.preserve.present
        options.preserve.get().dependencies.get()*.notation == [
                "com.acme:extension:1.0", "com.acme:standalone:1.0"
        ]
        options.preserve.get().dependencies.get()*.transitive == [true, false]
    }

    def "accepts provider-backed version catalog dependencies"() {
        given:
        def project = newProject()
        project.plugins.apply(ApplicationPlugin)
        project.plugins.apply(NativeImagePlugin)
        def module = Stub(ModuleIdentifier) {
            toString() >> "com.acme:catalog-extension"
        }
        def version = Stub(VersionConstraint) {
            getRequiredVersion() >> "2.0"
        }
        def dependency = Stub(MinimalExternalModuleDependency) {
            getModule() >> module
            getVersionConstraint() >> version
        }
        def options = project.extensions.getByType(GraalVMExtension).binaries.getByName("main")

        when:
        options.preserve {
            it.dependencies(project.providers.provider { dependency }) { selection ->
                selection.transitive = false
            }
        }

        then:
        options.preserve.get().dependencies.get()[0].notation == "com.acme:catalog-extension:2.0"
        !options.preserve.get().dependencies.get()[0].transitive
    }

    def "rejects blank dependency notation with a Preserve-specific diagnostic"() {
        when:
        new PreserveDependency(" ", true)

        then:
        def error = thrown(IllegalArgumentException)
        error.message == "Preserve dependency notation must not be blank"
    }

    def "reports malformed and unresolved dependencies as Preserve configuration errors"() {
        given:
        def project = newProject()
        project.plugins.apply(ApplicationPlugin)
        project.plugins.apply(NativeImagePlugin)
        def options = project.extensions.getByType(GraalVMExtension).binaries.getByName("main")

        when:
        options.preserve { it.dependencies("malformed") }

        then:
        def malformed = thrown(InvalidUserDataException)
        malformed.message.contains("Invalid Preserve dependency notation 'malformed'")

        when:
        options.preserve { it.dependencies("com.acme:missing:1.0") }
        BuildNativeImageTask.validatePreserveConfiguration(options)

        then:
        def unresolved = thrown(org.gradle.api.GradleException)
        unresolved.message.contains("Could not resolve Preserve dependencies")
    }

    def "exposes Preserve on main test custom and shared-library binaries"() {
        given:
        def project = newProject()
        project.plugins.apply(ApplicationPlugin)
        project.plugins.apply(NativeImagePlugin)
        def binaries = project.extensions.getByType(GraalVMExtension).binaries
        def custom = binaries.create("worker")
        def shared = binaries.create("nativeLibrary")
        shared.sharedLibrary.set(true)

        when:
        [binaries.getByName("main"), binaries.getByName("test"), custom, shared].eachWithIndex { binary, index ->
            binary.preserve { it.dependencies("com.acme:dependency:${index + 1}") }
        }

        then:
        [binaries.getByName("main"), binaries.getByName("test"), custom, shared].every {
            it.preserve.present && it.preserve.get().dependencies.get().size() == 1
        }
    }
}
