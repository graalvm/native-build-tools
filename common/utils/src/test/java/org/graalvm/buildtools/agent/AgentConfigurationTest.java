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
package org.graalvm.buildtools.agent;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AgentConfigurationTest {

    private static final String ACCESS_FILTER_OPTION = "access-filter-file=";

    @TempDir
    Path agentConfigDir;

    @Test
    void disabledConfigurationReturnsEmptyCommandLine() {
        AgentConfiguration disabled = new AgentConfiguration();

        List<String> cmdLine = disabled.getAgentCommandLine();

        assertTrue(cmdLine.isEmpty(), "Disabled agent configuration should not emit any options, but was: " + cmdLine);
    }

    @Test
    void standardConfigurationEmitsDefaultAccessFilterBeforeUserAccessFilters() {
        List<String> userAccessFilters = List.of("user-1.json", "user-2.json");
        AgentConfiguration configuration = new AgentConfiguration(
                List.of(),
                new ArrayList<>(userAccessFilters),
                null,
                null,
                null,
                null,
                null,
                new StandardAgentMode(),
                agentConfigDir);

        List<String> cmdLine = configuration.getAgentCommandLine();

        List<String> accessFilterOptions = cmdLine.stream()
                .filter(option -> option.startsWith(ACCESS_FILTER_OPTION))
                .toList();

        assertEquals(userAccessFilters.size() + 1, accessFilterOptions.size(),
                "Expected one default access filter plus user access filters, but got: " + accessFilterOptions);

        for (int i = 0; i < userAccessFilters.size(); i++) {
            assertEquals(ACCESS_FILTER_OPTION + userAccessFilters.get(i), accessFilterOptions.get(i + 1),
                    "User access filters must follow the default in their original order");
        }
    }
}
