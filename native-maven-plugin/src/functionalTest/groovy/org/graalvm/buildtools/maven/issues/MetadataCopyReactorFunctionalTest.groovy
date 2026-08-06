/*
 * Copyright (c) 2026, 2026, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * The Universal Permissive License (UPL), Version 1.0
 *
 * Subject to the condition set forth below, permission is hereby granted to any
 * person obtaining a copy of this software, associated documentation and/or
 * data (collectively the "Software"), free of charge under any and all
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

package org.graalvm.buildtools.maven.issues

import org.graalvm.buildtools.maven.AbstractGraalVMMavenFunctionalTest
import spock.lang.Issue

class MetadataCopyReactorFunctionalTest extends AbstractGraalVMMavenFunctionalTest {
    private static final String FIRST_MODULE_MARKER = "issue650.FirstModuleMarker"
    private static final String SECOND_MODULE_MARKER = "issue650.SecondModuleMarker"
    private static final String FOREIGN_CONFIG = "foreign-config.json"

    // Sequential reactor modules sharing one destination preserve explicit replace and merge semantics. §E2E-functional-tests.3.6.1
    @Issue("https://github.com/graalvm/native-build-tools/issues/650")
    def "metadata copy supports a shared reactor destination"() {
        given:
        withReproducer("issue-650")
        file("shared-metadata").mkdir()
        file("shared-metadata/$FOREIGN_CONFIG").text = '{"downcalls":[]}'

        when:
        mvn 'native:metadata-copy'

        then:
        buildSucceeded
        outputContains "Building first-module"
        outputContains "Building second-module"
        metadataContains SECOND_MODULE_MARKER
        !metadataContains(FIRST_MODULE_MARKER)
        !file("shared-metadata/$FOREIGN_CONFIG").exists()

        when:
        mvn 'native:metadata-copy'

        then:
        buildSucceeded
        metadataContains SECOND_MODULE_MARKER
        !metadataContains(FIRST_MODULE_MARKER)

        when:
        file("shared-metadata").deleteDir()
        mvn '-Dissue650.merge=true', 'native:metadata-copy'

        then:
        buildSucceeded
        metadataContains FIRST_MODULE_MARKER
        metadataContains SECOND_MODULE_MARKER
    }

    // A failed staged invocation leaves the configured destination unchanged and removes its staging directory. §E2E-functional-tests.3.6.1
    def "failed metadata copy preserves the shared destination"() {
        given:
        withReproducer("issue-650")
        file("shared-metadata").mkdir()
        file("shared-metadata/sentinel.txt").text = "preserved"
        file("first-module/fixture-target/native/agent-output/test/reflect-config.json").text = "invalid"

        when:
        mvn 'native:metadata-copy'

        then:
        buildFailed
        // Failure preserves the destination and removes staging output. §E2E-functional-tests.3.6.1
        file("shared-metadata/sentinel.txt").text == "preserved"
        !file("shared-metadata").listFiles().any { it.name.startsWith(".native-metadata-copy-") }
    }

    private boolean metadataContains(String marker) {
        file("shared-metadata").listFiles().findAll { it.name.endsWith(".json") }.any { it.text.contains(marker) }
    }
}
