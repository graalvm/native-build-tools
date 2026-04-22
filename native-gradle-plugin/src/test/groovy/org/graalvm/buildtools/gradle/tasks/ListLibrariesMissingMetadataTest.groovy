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

import org.gradle.api.artifacts.ModuleVersionIdentifier
import org.gradle.api.artifacts.component.ModuleComponentIdentifier
import org.gradle.api.artifacts.component.ProjectComponentIdentifier
import org.gradle.api.artifacts.result.ResolvedComponentResult
import org.gradle.api.artifacts.result.ResolvedDependencyResult
import org.gradle.api.artifacts.result.UnresolvedDependencyResult
import org.graalvm.reachability.MissingMetadataCommandSupport
import spock.lang.Specification

class ListLibrariesMissingMetadataTest extends Specification {

    def "selects only direct external module dependencies"() {
        given:
        def directExternal = resolvedExternalDependency("org.example", "lib-a", "1.0.0")
        def alsoDirect = resolvedExternalDependency("com.acme", "client", "2.3.4")
        def projectDependency = resolvedProjectDependency()
        def unresolved = Mock(UnresolvedDependencyResult)
        def root = Stub(ResolvedComponentResult) {
            getDependencies() >> ([directExternal, alsoDirect, projectDependency, unresolved] as Set)
        }

        when:
        def coords = ListLibrariesMissingMetadata.directExternalRuntimeDependencies(root)

        then:
        coords.size() == 2
        coords*.coordinates().toSet() == ["org.example:lib-a:1.0.0", "com.acme:client:2.3.4"] as Set
        coords.every { it instanceof MissingMetadataCommandSupport.DependencyCoordinate }
    }

    def "ignores transitive dependencies (only walks the root component's first-level children)"() {
        given:
        def transitiveOfDirect = resolvedExternalDependency("org.example", "transitive", "9.9.9")
        def directSelected = Stub(ResolvedComponentResult) {
            getId() >> Stub(ModuleComponentIdentifier)
            getModuleVersion() >> moduleVersion("org.example", "direct", "1.0.0")
            getDependencies() >> ([transitiveOfDirect] as Set)
        }
        def direct = Stub(ResolvedDependencyResult) {
            getSelected() >> directSelected
        }
        def root = Stub(ResolvedComponentResult) {
            getDependencies() >> ([direct] as Set)
        }

        when:
        def coords = ListLibrariesMissingMetadata.directExternalRuntimeDependencies(root)

        then:
        coords*.coordinates() == ["org.example:direct:1.0.0"]
    }

    def "returns empty list when there are no module dependencies"() {
        given:
        def root = Stub(ResolvedComponentResult) {
            getDependencies() >> ([resolvedProjectDependency()] as Set)
        }

        expect:
        ListLibrariesMissingMetadata.directExternalRuntimeDependencies(root).isEmpty()
    }

    private ResolvedDependencyResult resolvedExternalDependency(String group, String artifact, String version) {
        def selected = Stub(ResolvedComponentResult) {
            getId() >> Stub(ModuleComponentIdentifier)
            getModuleVersion() >> moduleVersion(group, artifact, version)
        }
        return Stub(ResolvedDependencyResult) {
            getSelected() >> selected
        }
    }

    private ResolvedDependencyResult resolvedProjectDependency() {
        def selected = Stub(ResolvedComponentResult) {
            getId() >> Stub(ProjectComponentIdentifier)
        }
        return Stub(ResolvedDependencyResult) {
            getSelected() >> selected
        }
    }

    private ModuleVersionIdentifier moduleVersion(String group, String name, String version) {
        return Stub(ModuleVersionIdentifier) {
            getGroup() >> group
            getName() >> name
            getVersion() >> version
        }
    }
}
