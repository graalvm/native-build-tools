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
import org.gradle.api.artifacts.result.ResolvedComponentResult
import org.gradle.api.artifacts.result.ResolvedDependencyResult
import spock.lang.Specification
import spock.lang.TempDir

// Protects verified Maven relocation metadata fallback. §FS-resources-and-metadata.3.
class CollectReachabilityMetadataTest extends Specification {
    @TempDir
    File testDirectory

    def "uses the associated resolved coordinate when the POM has a CI-friendly version"() {
        given:
        File pom = new File(testDirectory, "legacy-library-1.0.pom")
        pom.text = """
<project xmlns="http://maven.apache.org/POM/4.0.0">
  <modelVersion>4.0.0</modelVersion>
  <groupId>example.legacy</groupId>
  <artifactId>legacy-library</artifactId>
  <version>\${revision}</version>
  <properties>
    <revision>1.0</revision>
  </properties>
  <distributionManagement>
    <relocation>
      <groupId>example.current</groupId>
      <artifactId>current-library</artifactId>
      <version>2.0</version>
    </relocation>
  </distributionManagement>
</project>
"""
        def relocationPom = new CollectReachabilityMetadata.RelocationPom(
                "example.legacy", "legacy-library", "1.0", pom)
        def relocations = CollectReachabilityMetadata.readRelocations([relocationPom])
        def canonical = component("example.current", "current-library", "2.0")
        def legacy = component("example.legacy", "legacy-library", "1.0", canonical)
        def components = [
                "example.legacy:legacy-library:1.0": legacy,
                "example.current:current-library:2.0": canonical,
        ]

        expect:
        CollectReachabilityMetadata.verifiedRelocations(components, relocations) == [
                "example.legacy:legacy-library:1.0": "example.current:current-library:2.0",
        ]

        when:
        components["example.legacy:legacy-library:1.0"] = component("example.legacy", "legacy-library", "1.0")

        then:
        CollectReachabilityMetadata.verifiedRelocations(components, relocations).isEmpty()
    }

    def "ignores relocation elements outside Maven distribution management"() {
        given:
        File pom = new File(testDirectory, "shade-plugin.pom")
        pom.text = """
<project xmlns="http://maven.apache.org/POM/4.0.0">
  <modelVersion>4.0.0</modelVersion>
  <groupId>example</groupId>
  <artifactId>library</artifactId>
  <version>1.0</version>
  <build>
    <plugins>
      <plugin>
        <configuration>
          <relocations>
            <relocation>
              <groupId>not.maven</groupId>
              <artifactId>not-a-relocation</artifactId>
              <version>2.0</version>
            </relocation>
          </relocations>
        </configuration>
      </plugin>
    </plugins>
  </build>
</project>
"""

        expect:
        def relocationPom = new CollectReachabilityMetadata.RelocationPom(
                "example", "library", "1.0", pom)
        CollectReachabilityMetadata.readRelocations([relocationPom]).isEmpty()
    }

    def "uses the resolved module to decide whether fallback is excluded"() {
        expect:
        CollectReachabilityMetadata.isExcluded(selected("example.current", "current-library", "2.0"), ["example.current:current-library"] as Set)
        !CollectReachabilityMetadata.isExcluded(selected("example.legacy", "legacy-library", "1.0"), ["example.current:current-library"] as Set)
    }
    private ResolvedComponentResult component(String group, String name, String version,
                                              ResolvedComponentResult dependency = null) {
        Stub(ResolvedComponentResult) {
            getModuleVersion() >> selected(group, name, version)
            getDependencies() >> (dependency == null ? [] : [Stub(ResolvedDependencyResult) {
                getSelected() >> dependency
            }])
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
