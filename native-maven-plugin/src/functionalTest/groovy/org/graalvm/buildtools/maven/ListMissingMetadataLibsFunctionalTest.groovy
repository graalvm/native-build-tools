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
package org.graalvm.buildtools.maven

import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer

import java.net.InetSocketAddress
import java.nio.charset.StandardCharsets
import java.util.concurrent.atomic.AtomicInteger

class ListMissingMetadataLibsFunctionalTest extends AbstractGraalVMMavenFunctionalTest {
    private HttpServer githubServer
    private AtomicInteger searchRequests = new AtomicInteger()
    private AtomicInteger createRequests = new AtomicInteger()

    def cleanup() {
        githubServer?.stop(0)
    }

    def "createIssues creates tickets only for missing direct dependencies"() {
        given:
        withSample('native-config-integration')
        startGithubServer()

        when:
        mvn '-PmetadataLocal',
            "-DgithubApiUrl=http://localhost:${githubServer.address.port}/api/v3",
            '-DtargetRepository=test/repo',
            '-DgithubToken=test-token',
            '-DcreateIssues=true',
            'native:list-missing-metadata-libs'

        then:
        isBuildSucceeded()
        outputContains 'org.apache.commons:commons-lang3:3.18.0'
        outputContains "Created ticket: http://localhost:${githubServer.address.port}/test/repo/issues/99"
        outputDoesNotContain '- org.graalvm.internal:library-with-reflection:1.5'

        and:
        def report = file('target/native/list-missing-metadata-libs.json').text
        report.contains('"scanned": 2')
        report.contains('"supported": 1')
        report.contains('"missing": 1')
        report.contains('"createdIssues": 1')
        report.contains('"existingOpenIssue": 0')
        report.contains('"coordinates": "org.apache.commons:commons-lang3:3.18.0"')
        report.contains('"issueStatus": "created_issue"')
        searchRequests.get() == 1
        createRequests.get() == 1
    }

    private void startGithubServer() {
        githubServer = HttpServer.create(new InetSocketAddress(0), 0)
        githubServer.createContext('/api/v3/search/issues') { HttpExchange exchange ->
            searchRequests.incrementAndGet()
            writeJson(exchange, '{"items":[]}')
        }
        githubServer.createContext('/api/v3/repos/test/repo/issues') { HttpExchange exchange ->
            createRequests.incrementAndGet()
            writeJson(exchange, """
                {
                  "number": 99,
                  "html_url": "http://localhost:${githubServer.address.port}/test/repo/issues/99"
                }
            """.stripIndent().trim(), 201)
        }
        githubServer.start()
    }

    private static void writeJson(HttpExchange exchange, String json, int status = 200) {
        byte[] body = json.getBytes(StandardCharsets.UTF_8)
        exchange.responseHeaders.add('Content-Type', 'application/json')
        exchange.sendResponseHeaders(status, body.length)
        exchange.responseBody.withCloseable { it.write(body) }
    }
}
