/*
 * Copyright (c) 2026, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * The Universal Permissive License (UPL), Version 1.0
 *
 * Subject to the condition set forth below, permission is hereby granted to any
 * person obtaining a copy of this software, associated documentation and/or
 * data (collectively the "Software"), free of charge and under any and all
 * copyright rights in the Software, and any and all patent rights owned by
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

package org.graalvm.buildtools.gradle.internal

import org.gradle.api.Project
import org.gradle.jvm.toolchain.JavaLauncher
import org.gradle.testfixtures.ProjectBuilder
import spock.lang.Specification

/**
 * Focused unit tests for the {@link JavaLauncherProperty} runtime proxy. They pin the three cases the
 * proxy must handle without deviating from the underlying Gradle {@code Property} contract:
 * {@code value(null)} as a no-op, exception propagation, and {@link Object} identity methods.
 */
class JavaLauncherPropertyTest extends Specification {

    private Project project
    private JavaLauncherProperty holder

    def setup() {
        project = ProjectBuilder.builder().build()
        holder = JavaLauncherProperty.of(
                project.objects.property(JavaLauncher.class),
                project.providers)
    }

    private JavaLauncher launcher() {
        Mock(JavaLauncher)
    }

    // §FS-native-invocation.1.2 — value(null) is a no-op on a convention-sourced property and must
    // not mark the value explicit, matching the underlying Property's behavior.
    def "value(null) on a convention-sourced property keeps the launcher non-explicit"() {
        given:
        holder.getProperty().convention(launcher())
        assert holder.getProperty().isPresent() // force evaluation of the convention

        when:
        holder.getProperty().value(null)

        then: "the convention still supplies the value and it is not explicit"
        holder.getProperty().isPresent()
        !holder.explicit().get()
    }

    // §FS-native-invocation.1.2 — a non-null assignment marks the value explicit.
    def "value(nonNull) marks the launcher explicit"() {
        given:
        holder.getProperty().convention(launcher())

        when:
        holder.getProperty().value(launcher())

        then:
        holder.getProperty().isPresent()
        holder.explicit().get()
    }

    // §FS-native-invocation.1.1 — a user-assigned launcher stays explicit across Object identity calls.
    def "proxied Property satisfies Object identity methods"() {
        when: "the property is assigned explicitly"
        holder.getProperty().set(launcher())
        def prop = holder.getProperty()

        then: "equals is referential and hashCode/toString are consistent"
        prop.equals(prop)
        prop == prop
        prop.hashCode() == System.identityHashCode(prop)
        prop.toString().contains(JavaLauncherProperty.name)
    }

    // §FS-native-invocation.1.2 — Gradle's own exception must propagate unwrapped rather than as a
    // reflection wrapping (UndeclaredThrowableException) when mutating a finalized property.
    def "mutating a finalized property propagates Gradle's exception unwrapped"() {
        given: "a property that is finalized to a fixed value"
        def prop = holder.getProperty()
        prop.value(launcher()).finalizeValue()

        when: "a mutation is attempted on the finalized property"
        prop.unset()

        then: "Gradle's IllegalStateException surfaces directly, not wrapped in InvocationTargetException"
        def e = thrown(IllegalStateException)
        e.cause == null
    }
}