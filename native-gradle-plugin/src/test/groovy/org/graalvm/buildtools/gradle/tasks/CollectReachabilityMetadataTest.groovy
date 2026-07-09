/*
 * Copyright (c) 2026, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * The Universal Permissive License (UPL), Version 1.0
 *
 * Subject to the condition set forth below, permission is hereby granted to any
 * person obtaining a copy of this software and associated documentation files
 * (the "Software"), to deal in the Software without restriction, including
 * without limitation the rights to use, copy, modify, merge, publish,
 * distribute, sublicense, and/or sell copies of the Software, and to permit
 * persons to whom the Software is furnished to do so, subject to the following
 * condition:
 *
 * The above copyright notice and either this complete permission notice or at
 * a minimum a reference to the UPL must be included in all copies or substantial
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
import org.gradle.api.artifacts.VersionConstraint
import org.gradle.api.artifacts.component.ModuleComponentSelector
import spock.lang.Specification
import spock.lang.TempDir

// Protects verified Maven relocation metadata fallback. §FS-resources-and-metadata.3.
class CollectReachabilityMetadataTest extends Specification {
    @TempDir
    File testDirectory

    def "recognizes a POM relocation only for its exact requested and selected coordinates"() {
        given:
        File pom = new File(testDirectory, "legacy-library-1.0.pom")
        pom.text = """
<project xmlns="http://maven.apache.org/POM/4.0.0">
  <modelVersion>4.0.0</modelVersion>
  <groupId>example.legacy</groupId>
  <artifactId>legacy-library</artifactId>
  <version>1.0</version>
  <distributionManagement>
    <relocation>
      <groupId>example.current</groupId>
      <artifactId>current-library</artifactId>
      <version>2.0</version>
    </relocation>
  </distributionManagement>
</project>
"""
        def relocations = CollectReachabilityMetadata.readRelocations([pom] as Set)
        def legacyRequested = requested("example.legacy", "legacy-library", "1.0")

        expect:
        CollectReachabilityMetadata.isMavenRelocationTo(legacyRequested, selected("example.current", "current-library", "2.0"), relocations)
        !CollectReachabilityMetadata.isMavenRelocationTo(legacyRequested, selected("example.current", "current-library", "3.0"), relocations)
        !CollectReachabilityMetadata.isMavenRelocationTo(requested("example.legacy", "legacy-library", "1.1"), selected("example.current", "current-library", "2.0"), relocations)
    }

    def "uses the resolved module to decide whether fallback is excluded"() {
        expect:
        CollectReachabilityMetadata.isExcluded(selected("example.current", "current-library", "2.0"), ["example.current:current-library"] as Set)
        !CollectReachabilityMetadata.isExcluded(selected("example.legacy", "legacy-library", "1.0"), ["example.current:current-library"] as Set)
    }


    private ModuleComponentSelector requested(String group, String module, String version) {
        Stub(ModuleComponentSelector) {
            getGroup() >> group
            getModule() >> module
            getVersionConstraint() >> Stub(VersionConstraint) {
                getRequiredVersion() >> version
            }
        }
    }

    private ModuleVersionIdentifier selected(String group, String name, String version) {
        Stub(ModuleVersionIdentifier) {
            getGroup() >> group
            getName() >> name
            getVersion() >> version
        }
    }
}
