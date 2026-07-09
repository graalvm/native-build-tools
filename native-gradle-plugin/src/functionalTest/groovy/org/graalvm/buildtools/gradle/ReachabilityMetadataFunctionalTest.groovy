/*
 * Copyright (c) 2022, Oracle and/or its affiliates. All rights reserved.
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

class ReachabilityMetadataFunctionalTest extends AbstractFunctionalTest {

    def "replaces fallback metadata with canonical metadata for a CI-friendly Maven relocation"() {
        given:
        settingsFile.text = ""
        writeFile("repo/example/legacy/legacy-library/1.0/legacy-library-1.0.pom", """
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
""")
        writeFile("repo/example/current/current-library/2.0/current-library-2.0.pom", """
<project xmlns="http://maven.apache.org/POM/4.0.0">
  <modelVersion>4.0.0</modelVersion>
  <groupId>example.current</groupId>
  <artifactId>current-library</artifactId>
  <version>2.0</version>
  <packaging>pom</packaging>
</project>
""")
        writeFile("metadata/schemas/reachability-metadata-schema-v1.2.0.json", '{"version":"1.2.0"}')
        writeFile("metadata/schemas/metadata-library-index-schema-v2.0.0.json", '{}')
        writeFile("metadata/schemas/library-and-framework-list-schema-v1.0.0.json", '{}')
        writeFile("metadata/example.legacy/legacy-library/index.json", '''
[
  {
    "tested-versions": ["1.0"],
    "metadata-version": "1",
    "latest": true
  }
]
''')
        writeFile("metadata/example.legacy/legacy-library/1/resource-config.json", '''
{
  "resources": {
    "includes": [{"pattern": "legacy-relocation-marker"}]
  }
}
''')
        buildFile.text = """
plugins {
    id 'java'
    id 'org.graalvm.buildtools.native'
}

repositories {
    maven { url = uri(file('repo')) }
}

dependencies {
    implementation 'example.legacy:legacy-library:1.0'
}

graalvmNative {
    metadataRepository {
        uri(file('metadata'))
    }
}
"""

        when:
        run 'collectReachabilityMetadata'

        then:
        tasks {
            succeeded ':collectReachabilityMetadata'
        }

        and: "only fallback metadata is initially available"
        File legacyMetadata = file("build/native-reachability-metadata/META-INF/native-image/example.legacy/legacy-library/1.0/resource-config.json")
        legacyMetadata.text.contains("legacy-relocation-marker")

        when: "canonical metadata becomes available on a later execution"
        writeFile("metadata/example.current/current-library/index.json", '''
[
  {
    "tested-versions": ["2.0"],
    "metadata-version": "2",
    "latest": true
  }
]
''')
        writeFile("metadata/example.current/current-library/2/resource-config.json", '''
{
  "resources": {
    "includes": [{"pattern": "canonical-relocation-marker"}]
  }
}
''')
        run 'collectReachabilityMetadata', '--rerun-tasks'

        then:
        tasks {
            succeeded ':collectReachabilityMetadata'
        }

        and: "the current selection replaces the previous output without merging coordinates"
        File canonicalMetadata = file("build/native-reachability-metadata/META-INF/native-image/example.current/current-library/2.0/resource-config.json")
        canonicalMetadata.text.contains("canonical-relocation-marker")
        !legacyMetadata.exists()
    }

    def "the application runs when using the official metadata repository"() {
        given:
        withSample("metadata-repo-integration")

        when:
        run 'collectReachabilityMetadata', "-D${NativeImagePlugin.CONFIG_REPO_LOGLEVEL}=${LogLevel.LIFECYCLE}"

        then:
        tasks {
            succeeded ':collectReachabilityMetadata'
        }

        and: "has copied metadata file"
        contains(file("build/native-reachability-metadata/META-INF/native-image/com.h2database/h2/2.2.220/reachability-metadata.json").text.trim(), '''
  "resources": [
    {
      "condition": {
        "typeReached": "org.h2.util.Utils"
      },
      "glob": "org/h2/util/data.zip"
    }
  ]
''')
        and: "has copied reachability-metadata.properties file"
        file("build/native-reachability-metadata/META-INF/native-image/io.netty/netty-codec-http/4.1.80.Final/reachability-metadata.properties").text.trim() == 'override=true'
    }

    private void writeFile(String relativePath, String contents) {
        File target = file(relativePath)
        target.parentFile.mkdirs()
        target.text = contents
    }

}
