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
package org.graalvm.buildtools.gradle

import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import org.graalvm.buildtools.gradle.fixtures.AbstractFunctionalTest
import org.gradle.testkit.runner.TaskOutcome

import java.net.InetSocketAddress
import java.nio.charset.StandardCharsets
import java.util.concurrent.atomic.AtomicInteger

class ListMissingMetadataLibsFunctionalTest extends AbstractFunctionalTest {
    private HttpServer server
    private AtomicInteger searchRequests = new AtomicInteger()

    def cleanup() {
        server?.stop(0)
    }

    def "lists unsupported direct runtime dependencies and writes a report"() {
        given:
        withSample("native-config-integration", false)
        buildFile << """
dependencies {
    implementation("org.apache.commons:commons-text:1.11.0")
}
"""
        startGithubServer()

        when:
        run 'listMissingMetadataLibs',
            "-PgithubApiUrl=http://localhost:${server.address.port}/api/v3",
            '-PtargetRepository=test/repo'

        then:
        tasks {
            succeeded ':listMissingMetadataLibs'
        }

        and:
        outputContains 'org.apache.commons:commons-text:1.11.0'
        outputContains "Existing ticket: http://localhost:${server.address.port}/test/repo/issues/42"
        outputDoesNotContain 'commons-lang3'

        and:
        def report = file('build/reports/native/list-missing-metadata-libs.json').text
        contains(report, '"scanned": 2')
        contains(report, '"supported": 1')
        contains(report, '"missing": 1')
        contains(report, '"existingOpenIssue": 1')
        contains(report, '"newIssueLinks": 0')
        contains(report, '"createdIssues": 0')
        contains(report, '"coordinates": "org.graalvm.internal:library-with-reflection:1.5"')
        contains(report, '"coordinates": "org.apache.commons:commons-text:1.11.0"')
        !contains(report, 'commons-lang3')
        searchRequests.get() == 1
    }

    def "runs on every invocation instead of becoming up-to-date"() {
        given:
        withSample("native-config-integration", false)
        buildFile << """
dependencies {
    implementation("org.apache.commons:commons-text:1.11.0")
}
"""
        startGithubServer()

        when:
        run 'listMissingMetadataLibs',
            "-PgithubApiUrl=http://localhost:${server.address.port}/api/v3",
            '-PtargetRepository=test/repo'

        then:
        result.task(':listMissingMetadataLibs').outcome == TaskOutcome.SUCCESS
        searchRequests.get() == 1

        when:
        run 'listMissingMetadataLibs',
            "-PgithubApiUrl=http://localhost:${server.address.port}/api/v3",
            '-PtargetRepository=test/repo'

        then:
        result.task(':listMissingMetadataLibs').outcome == TaskOutcome.SUCCESS
        outputContains 'org.apache.commons:commons-text:1.11.0'
        searchRequests.get() == 2
    }

    private void startGithubServer() {
        server = HttpServer.create(new InetSocketAddress(0), 0)
        server.createContext('/api/v3/search/issues') { HttpExchange exchange ->
            searchRequests.incrementAndGet()
            writeJson(exchange, """
                {
                  "items": [
                    {
                      "number": 42,
                      "html_url": "http://localhost:${server.address.port}/test/repo/issues/42",
                      "title": "Support for org.apache.commons:commons-text:1.11.0",
                      "body": "### Full Maven coordinates\\n\\norg.apache.commons:commons-text:1.11.0\\n"
                    }
                  ]
                }
            """.stripIndent().trim())
        }
        server.start()
    }

    private static void writeJson(HttpExchange exchange, String json) {
        byte[] body = json.getBytes(StandardCharsets.UTF_8)
        exchange.responseHeaders.add('Content-Type', 'application/json')
        exchange.sendResponseHeaders(200, body.length)
        exchange.responseBody.withCloseable { it.write(body) }
    }
}
