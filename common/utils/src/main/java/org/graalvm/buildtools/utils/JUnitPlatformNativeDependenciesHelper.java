/*
 * Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
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

package org.graalvm.buildtools.utils;

import java.util.List;

public abstract class JUnitPlatformNativeDependenciesHelper {

    private static final DependencyNotation JUNIT_PLATFORM_LAUNCHER = new DependencyNotation(
        "org.junit.platform", "junit-platform-launcher", ""
    );
    private static final DependencyNotation JUNIT_PLATFORM_ENGINE = new DependencyNotation(
        "org.junit.platform", "junit-platform-engine", ""
    );
    private static final DependencyNotation JUNIT_PLATFORM_CONSOLE = new DependencyNotation(
        "org.junit.platform", "junit-platform-console", ""
    );
    private static final DependencyNotation JUNIT_PLATFORM_REPORTING = new DependencyNotation(
        "org.junit.platform", "junit-platform-reporting", ""
    );

    private static final List<DependencyNotation> JUNIT_PLATFORM_DEPENDENCIES = List.of(
        JUNIT_PLATFORM_LAUNCHER,
        JUNIT_PLATFORM_CONSOLE,
        JUNIT_PLATFORM_REPORTING
    );

    private JUnitPlatformNativeDependenciesHelper() {

    }

    /**
     * Returns the list of dependencies which should be added to the
     * native test classpath in order for tests to execute.
     * @param input the current list of dependencies
     * @return a list of dependencies which need to be added
     */
    public static List<DependencyNotation> inferMissingDependenciesForTestRuntime(
        List<DependencyNotation> input
    ) {
        var junitPlatformVersion = input.stream()
            .filter(d -> d.equalsIgnoreVersion(JUNIT_PLATFORM_ENGINE))
            .findFirst()
            .map(DependencyNotation::version)
            .orElse("");
        var list = JUNIT_PLATFORM_DEPENDENCIES.stream()
            .filter(d -> input.stream().noneMatch(o -> o.equalsIgnoreVersion(d)))
            .map(d -> d.withVersion(junitPlatformVersion))
            .toList();
        return list;
    }


    public record DependencyNotation(
        String groupId,
        String artifactId,
        String version
    ) {
        public boolean equalsIgnoreVersion(DependencyNotation other) {
            return other.groupId.equals(groupId) &&
                   other.artifactId.equals(artifactId);
        }

        public DependencyNotation withVersion(String version) {
            return new DependencyNotation(groupId, artifactId, version);
        }
    }
}
