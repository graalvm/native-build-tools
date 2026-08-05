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

import java.nio.file.Files
import java.util.jar.JarOutputStream

class ReachabilityMetadataFunctionalTest extends AbstractFunctionalTest {

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

    // Required metadata stays additive and graph-aware across repeated collection.
    // §common/FS-common-libraries.5.1 §FS-resources-and-metadata.3 §E2E-functional-tests.
    def "requires metadata keeps requester and required module outputs distinct"() {
        given:
        settingsFile << """
rootProject.name = 'requires-metadata'
include 'present', 'absent', 'excluded'
"""
        file('present').mkdirs()
        file('absent').mkdirs()
        file('excluded').mkdirs()
        createMavenModule("com.requester", "app", "1.0")
        createMavenModule("com.required", "lib", "2.0")
        createMetadataRepository()
        buildFile << """
plugins {
    id 'org.graalvm.buildtools.native' apply false
}

subprojects {
    apply plugin: 'java'
    apply plugin: 'org.graalvm.buildtools.native'

    repositories {
        maven { url = rootProject.file('maven-repo') }
    }

    dependencies {
        implementation 'com.requester:app:1.0'
    }

    graalvmNative.metadataRepository.uri(rootProject.file('metadata-repo'))
}

project(':present') {
    dependencies {
        implementation 'com.required:lib:2.0'
    }
}

project(':excluded') {
    graalvmNative.metadataRepository.excludedModules.add('com.required:lib')
}
"""

        when:
        run ':present:clean', ':present:collectReachabilityMetadata', ':absent:clean', ':absent:collectReachabilityMetadata',
                ':excluded:clean', ':excluded:collectReachabilityMetadata'

        then:
        tasks {
            succeeded ':present:collectReachabilityMetadata', ':absent:collectReachabilityMetadata',
                    ':excluded:collectReachabilityMetadata'
        }
        assertRequiresOutputs()

        when: "the clean collection is repeated"
        run ':present:clean', ':present:collectReachabilityMetadata', ':absent:clean', ':absent:collectReachabilityMetadata',
                ':excluded:clean', ':excluded:collectReachabilityMetadata', '--rerun-tasks'

        then:
        tasks {
            succeeded ':present:collectReachabilityMetadata', ':absent:collectReachabilityMetadata',
                    ':excluded:collectReachabilityMetadata'
        }
        assertRequiresOutputs()
    }

    private void assertRequiresOutputs() {
        assert marker('present', 'com.requester/app/1.0').text == 'requester'
        assert marker('present', 'com.required/lib/2.0').text == 'required-2'
        assert marker('absent', 'com.requester/app/1.0').text == 'requester'
        assert marker('absent', 'com.required/lib/1.0').text == 'required-1'
        assert marker('excluded', 'com.requester/app/1.0').text == 'requester'
        assert !marker('excluded', 'com.required/lib/1.0').exists()
        assert marker('present', 'com.requester/app/1.0').text != marker('present', 'com.required/lib/2.0').text
    }

    private File marker(String projectName, String coordinates) {
        file(projectName, "build/native-reachability-metadata/META-INF/native-image/$coordinates/marker.txt")
    }

    private void createMavenModule(String group, String artifact, String version) {
        File module = file('maven-repo', group.replace('.', '/'), artifact, version)
        module.mkdirs()
        new JarOutputStream(Files.newOutputStream(module.toPath().resolve("${artifact}-${version}.jar"))).close()
        module.toPath().resolve("${artifact}-${version}.pom").toFile().text = """<project>
  <modelVersion>4.0.0</modelVersion>
  <groupId>$group</groupId>
  <artifactId>$artifact</artifactId>
  <version>$version</version>
</project>
"""
    }

    private void createMetadataRepository() {
        file('metadata-repo/schemas').mkdirs()
        file('metadata-repo/schemas/reachability-metadata-schema-v1.2.0.json').text = '{"version":"1.2.0"}'
        file('metadata-repo/schemas/metadata-library-index-schema-v2.0.0.json').text = '{}'
        file('metadata-repo/schemas/library-and-framework-list-schema-v1.0.0.json').text = '{}'
        metadataModule('com.requester', 'app', '''[
  {"tested-versions":["1.0"],"metadata-version":"requester","requires":["com.required:lib"]}
]''', ['requester': 'requester'])
        metadataModule('com.required', 'lib', '''[
  {"tested-versions":["1.0"],"metadata-version":"required-1"},
  {"tested-versions":["2.0"],"metadata-version":"required-2"}
]''', ['required-1': 'required-1', 'required-2': 'required-2'])
    }

    private void metadataModule(String group, String artifact, String index, Map<String, String> markers) {
        File module = file('metadata-repo', group, artifact)
        module.mkdirs()
        new File(module, 'index.json').text = index
        markers.each { directory, marker ->
            File metadata = new File(module, directory)
            metadata.mkdirs()
            new File(metadata, 'marker.txt').text = marker
        }
    }

}
