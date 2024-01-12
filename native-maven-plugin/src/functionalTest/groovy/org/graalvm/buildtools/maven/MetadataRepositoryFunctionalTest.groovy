/*
 * Copyright (c) 2020, 2021, Oracle and/or its affiliates. All rights reserved.
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

import static org.graalvm.buildtools.utils.SharedConstants.NATIVE_IMAGE_EXE;

class MetadataRepositoryFunctionalTest extends AbstractGraalVMMavenFunctionalTest {

    void "if metadata is disabled, reflection fails"() {
        given:
        withSample("native-config-integration")

        when:
        mvn '-Pnative', '-DquickBuild', '-DskipTests', 'package', 'exec:exec@native'

        then:
        buildSucceeded
        outputContains "Reflection failed"
    }

    void "it can use a metadata repository"() {
        given:
        withSample("native-config-integration")

        when:
        mvn '-Pnative,metadataLocal', '-DquickBuild', '-DskipTests', 'package', 'exec:exec@native'

        then:
        buildSucceeded
        outputContains "Hello, from reflection!"

        and: "it doesn't find a configuration directory for the current version"
        outputContains "[graalvm reachability metadata repository for org.graalvm.internal:library-with-reflection:1.5]: Configuration directory not found. Trying latest version."

        and: "but it finds one thanks to the latest configuration field"
        outputContains "[graalvm reachability metadata repository for org.graalvm.internal:library-with-reflection:1.5]: Configuration directory is org.graalvm.internal" + File.separator + "library-with-reflection" + File.separator + "1"
    }

    void "if excludeConfig is set it is added to the command line invocation"() {
        given:
        withSample("native-config-integration")

        when:
        mvn '-Pnative,metadataDefault,excludeConfigTest', '-DnativeDryRun', 'package'

        then:
        buildSucceeded
        outputContains NATIVE_IMAGE_EXE + " --exclude-config dummy/path/to/file.jar \"*\""
   }

    void "if the path doesn't exist, the repository cannot be pulled"() {
        given:
        withSample("native-config-integration")

        when:
        mvn '-Pnative,metadataMissing', '-DquickBuild', '-DskipTests', 'package', 'exec:exec@native'

        then:
        buildFailed
        outputContains " Cannot pull GraalVM reachability metadata repository either from the one specified in the configuration or the default one"
    }

    void "it can exclude dependencies"() {
        given:
        withSample("native-config-integration")

        when:
        mvn '-Pnative,metadataExclude', '-DquickBuild', '-DskipTests', 'package', 'exec:exec@native'

        then:
        buildSucceeded
        outputContains "Reflection failed"
    }

    void "it can force metadata versions"() {
        given:
        withSample("native-config-integration")

        when:
        mvn '-Pnative,metadataForceVersion',  '-DquickBuild','-DskipTests', 'package', 'exec:exec@native'

        then:
        buildSucceeded
        outputContains "[graalvm reachability metadata repository for org.graalvm.internal:library-with-reflection:1.5]: Configuration is forced to version 2"
        outputContains "Reflection failed"
    }

    void "it can use a ZIP metadata repository"() {
        given:
        withSample("native-config-integration")

        when:
        mvn '-Pnative,metadataArchive', '-DquickBuild', '-DrepoFormat=zip', '-DskipTests', 'package', 'exec:exec@native'

        then:
        buildSucceeded
        outputContains "Hello, from reflection!"

        and: "it doesn't find a configuration directory for the current version"
        outputContains "[graalvm reachability metadata repository for org.graalvm.internal:library-with-reflection:1.5]: Configuration directory not found. Trying latest version."

        and: "but it finds one thanks to the latest configuration field"
        outputContains "[graalvm reachability metadata repository for org.graalvm.internal:library-with-reflection:1.5]: Configuration directory is org.graalvm.internal" + File.separator + "library-with-reflection" + File.separator + "1"
    }

    void "it can download a remote repository"() {
        given:
        withSample("native-config-integration")
        withLocalServer()

        when:
        mvn '-Pnative,metadataUrl', '-DquickBuild', "-Dmetadata.url=http://localhost:${localServerPort}/target/repo.zip", '-DskipTests', 'package', 'exec:exec@native'

        then:
        buildSucceeded
        outputContains "Hello, from reflection!"
        outputContains "Downloaded GraalVM reachability metadata repository from http://localhost:${localServerPort}/target/repo.zip"

        and: "it doesn't find a configuration directory for the current version"
        outputContains "[graalvm reachability metadata repository for org.graalvm.internal:library-with-reflection:1.5]: Configuration directory not found. Trying latest version."

        and: "but it finds one thanks to the latest configuration field"
        outputContains "[graalvm reachability metadata repository for org.graalvm.internal:library-with-reflection:1.5]: Configuration directory is org.graalvm.internal" + File.separator + "library-with-reflection" + File.separator + "1"
    }

    void "when pointing to a missing URL, the repository cannot be pulled"() {
        given:
        withSample("native-config-integration")
        withLocalServer()

        when:
        mvn '-Pnative,metadataUrl', '-DquickBuild', "-Dmetadata.url=https://google.com/notfound", '-DskipTests', 'package', 'exec:exec@native'

        then:
        buildFailed
        outputContains "Cannot pull GraalVM reachability metadata repository either from the one specified in the configuration or the default one"
    }

    void "it can include hints in jar"() {
        given:
        withSample("native-config-integration")

        when:
        mvn '-X', '-PaddMetadataHints', '-DquickBuild', '-DskipTests', 'package'

        then:
        buildSucceeded

        and:
        matches(file("target/classes/META-INF/native-image/org.graalvm.internal/library-with-reflection/1.5/reflect-config.json").text.trim(), '''[
  {
    "name": "org.graalvm.internal.reflect.Message",
    "allDeclaredFields": true,
    "allDeclaredMethods": true
  }
]''')
    }
}
