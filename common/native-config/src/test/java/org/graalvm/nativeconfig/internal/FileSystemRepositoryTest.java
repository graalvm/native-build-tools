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

package org.graalvm.nativeconfig.internal;

import org.junit.jupiter.api.Test;

import java.io.File;
import java.net.URISyntaxException;
import java.nio.file.Path;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;

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
        result.hasSinglePath("org/foo/1");

        // when:
        lookup("org:foo:1.1");

        // then:
        result.hasSinglePath("org/foo/2");

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
        result.hasSinglePath("org/foo/2");
    }

    private void lookup(String gav) {
        result = new Result(repository.findConfigurationDirectoriesFor(gav), repoPath);
    }

    private void withRepo(String id) {
        try {
            repoPath = new File(FileSystemRepositoryTest.class.getResource("/repos/" + id).toURI()).toPath();
            repository = new FileSystemRepository(repoPath);
        } catch (URISyntaxException e) {
            throw new RuntimeException(e);
        }
    }

    private static class Result {
        private final Path repoPath;
        private final Set<Path> configDirs;

        private Result(Set<Path> configDirs, Path repoPath) {
            this.configDirs = configDirs;
            this.repoPath = repoPath;
        }

        public void isEmpty() {
            assertEquals(0, configDirs.size());
        }

        public void hasSinglePath(String path) {
            assertEquals(1, configDirs.size());
            assertEquals(repoPath.resolve(path), configDirs.iterator().next());
        }
    }
}
