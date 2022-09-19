/*
 * Copyright (c) 2020, 2022 Oracle and/or its affiliates. All rights reserved.
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

package org.graalvm.reachability.internal;

import org.graalvm.reachability.DirectoryConfiguration;
import org.graalvm.reachability.Query;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.net.URISyntaxException;
import java.nio.file.Path;
import java.util.Set;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FileSystemRepositoryTest {
    private FileSystemRepository repository;
    private Path repoPath;
    private Result result;

    @Test
    void testRepo1() {
        // when:
        withRepo("repo1");
        lookup("org:foo:1.0");

        // then:
        result.hasSinglePath("org/foo/1.0");
        result.hasNoOverride();

        // when:
        lookup("org:foo:1.1");
        result.hasOverride();

        // then:
        result.hasSinglePath("org/foo/1.1");

        // when:
        lookup("org:foo:1.2");

        // then:
        result.isEmpty();
    }

    @Test
    void testRepo2() {
        // when:
        withRepo("repo2");
        lookup("org:bar:2.1");

        // then:
        result.hasSinglePath("org/foo/1.1");
        result.hasNoOverride();
    }

    @Test
    void canDefaultToLatestConfigDir() {
        // when:
        withRepo("repo1");
        lookup(q -> {
            q.useLatestConfigWhenVersionIsUntested();
            q.forArtifacts("org:foo:1.2");
        });

        // then:
        result.hasSinglePath("org/foo/1.1");

        //when: "order of spec shouldn't matter"
        lookup(q -> {
            q.forArtifacts("org:foo:1.2");
            q.useLatestConfigWhenVersionIsUntested();
        });

        // then:
        result.hasSinglePath("org/foo/1.1");
    }

    @Test
    void canForceToParticularConfigVersion() {
        // when:
        withRepo("repo1");

        lookup(q -> q.forArtifact(artifact -> {
            artifact.gav("org:foo:1.2");
            artifact.forceConfigVersion("1.0");
        }));

        // then:
        result.hasSinglePath("org/foo/1.0");
    }

    @Test
    void forcingToNonExistentDirectoryReturnsEmpty() {
        // when:
        withRepo("repo1");

        lookup(q -> q.forArtifact(artifact -> {
            artifact.gav("org:foo:1.2");
            artifact.forceConfigVersion("123");
        }));

        // then:
        result.isEmpty();
    }

    @Test
    void canUseLatestConfigDir() {
        // when:
        withRepo("repo1");
        lookup(q -> q.forArtifact(artifact -> {
            artifact.gav("org:foo:1.2");
            artifact.useLatestConfigWhenVersionIsUntested();
        }));

        // then:
        result.hasSinglePath("org/foo/1.1");

        // when:
        lookup(q -> {
            q.useLatestConfigWhenVersionIsUntested();
            q.forArtifact(artifact -> {
                artifact.gav("org:foo:1.2");
                // Can override default global
                artifact.doNotUseLatestConfigWhenVersionIsUntested();
            });
        });

        // then:
        result.isEmpty();
    }

    private void lookup(Consumer<? super Query> builder) {
        result = new Result(repository.findConfigurationsFor(builder), repoPath);
    }

    private void lookup(String gav) {
        result = new Result(repository.findConfigurationsFor(gav), repoPath);
    }

    private void withRepo(String id) {
        try {
            repoPath = new File(FileSystemRepositoryTest.class.getResource("/repos/" + id).toURI()).toPath();
            repository = new FileSystemRepository(repoPath);
        } catch (URISyntaxException e) {
            throw new RuntimeException(e);
        }
    }

    private static final class Result {
        private final Path repoPath;
        private final Set<DirectoryConfiguration> configs;

        private Result(Set<DirectoryConfiguration> configs, Path repoPath) {
            this.configs = configs;
            this.repoPath = repoPath;
        }

        public void isEmpty() {
            assertEquals(0, configs.size());
        }

        public void hasSinglePath(String path) {
            assertEquals(1, configs.size());
            assertEquals(repoPath.resolve(path), configs.iterator().next().getDirectory());
        }

        public void hasOverride() {
            for (DirectoryConfiguration config : configs) {
                assertTrue(config.isOverride());
            }
        }

        public void hasNoOverride() {
            for (DirectoryConfiguration config : configs) {
                assertFalse(config.isOverride());
            }
        }
    }
}
