/*
 * Copyright 2003-2021 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
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
