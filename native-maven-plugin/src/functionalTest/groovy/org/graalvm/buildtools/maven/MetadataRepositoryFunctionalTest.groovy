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

class MetadataRepositoryFunctionalTest extends AbstractGraalVMMavenFunctionalTest {

    void "if metadata is disabled, reflection fails"() {
        withSample("native-config-integration")

        when:
        mvn '-Pnative', '-DskipTests', 'package', 'exec:exec@native'

        then:
        buildSucceeded
        outputContains "Reflection failed"
    }

    void "it produces a warning if enabled but no repository is configured"() {
        withSample("native-config-integration")

        when:
        mvn '-Pnative,metadataEnabled', '-DskipTests', 'package', 'exec:exec@native'

        then:
        buildSucceeded
        outputContains "JVM reachability metadata repository is enabled, but no repository has been configured"
        outputContains "Reflection failed"
    }

    void "it produces a warning if repository version is defined"() {
        withSample("native-config-integration")

        when:
        mvn '-Pnative,metadataVersion', '-DskipTests', 'package', 'exec:exec@native'

        then:
        buildSucceeded
        outputContains "The official JVM reachability metadata repository is not released yet. Only local repositories are supported"
        outputContains "JVM reachability metadata repository is enabled, but no repository has been configured"
        outputContains "Reflection failed"
    }

    void "it can use a metadata repository"() {
        withSample("native-config-integration")

        when:
        mvn '-Pnative,metadataLocal', '-DskipTests', 'package', 'exec:exec@native'

        then:
        buildSucceeded
        outputContains "Hello, from reflection!"

        and: "it doesn't find a configuration directory for the current version"
        outputContains "[jvm reachability metadata repository for org.graalvm.internal:library-with-reflection:1.5]: Configuration directory not found. Trying latest version."

        and: "but it finds one thanks to the latest configuration field"
        outputContains "[jvm reachability metadata repository for org.graalvm.internal:library-with-reflection:1.5]: Configuration directory is org/graalvm/internal/library-with-reflection/1"
    }

    void "if the path doesn't exist it throws an error"() {
        withSample("native-config-integration")

        when:
        mvn '-Pnative,metadataMissing', '-DskipTests', 'package', 'exec:exec@native'

        then:
        buildSucceeded
        outputContains "JVM reachability metadata repository path does not exist"
        outputContains "Reflection failed"
    }

    void "it can exclude dependencies"() {
        withSample("native-config-integration")

        when:
        mvn '-Pnative,metadataExclude', '-DskipTests', 'package', 'exec:exec@native'

        then:
        buildSucceeded
        outputContains "Reflection failed"
    }

    void "it can force metadata versions"() {
        withSample("native-config-integration")

        when:
        mvn '-Pnative,metadataForceVersion', '-DskipTests', 'package', 'exec:exec@native'

        then:
        buildSucceeded
        outputContains "[jvm reachability metadata repository for org.graalvm.internal:library-with-reflection:1.5]: Configuration is forced to version 2"
        outputContains "Reflection failed"
    }

    void "it can use a #format metadata repository"(String format) {
        withSample("native-config-integration")
        withDebug()

        when:
        mvn '-e', '-Pnative,metadataArchive', '-DrepoFormat=' + format, '-DskipTests', 'package', 'exec:exec@native'

        then:
        buildSucceeded
        outputContains "Hello, from reflection!"

        and: "it doesn't find a configuration directory for the current version"
        outputContains "[jvm reachability metadata repository for org.graalvm.internal:library-with-reflection:1.5]: Configuration directory not found. Trying latest version."

        and: "but it finds one thanks to the latest configuration field"
        outputContains "[jvm reachability metadata repository for org.graalvm.internal:library-with-reflection:1.5]: Configuration directory is org/graalvm/internal/library-with-reflection/1"

        where:
        format << ['zip', 'tar.gz', 'tar.bz2']
    }

}
