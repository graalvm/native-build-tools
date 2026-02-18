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

package org.graalvm.reachability.internal.index.artifacts;

import org.graalvm.reachability.DirectoryConfiguration;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.net.URISyntaxException;
import java.nio.file.Path;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SingleModuleJsonVersionToConfigDirectoryIndexTest {
   private Path repoPath;

    private SingleModuleJsonVersionToConfigDirectoryIndex index;

    @Test
    void checkIndex() throws URISyntaxException {
        withIndex("artifact-1");

        Optional<DirectoryConfiguration> config = index.findConfiguration("com.foo", "bar", "1.0");
        assertTrue(config.isPresent());
        assertEquals(repoPath.resolve("1.0"), config.get().getDirectory());
        assertFalse(config.get().isOverride());

        config = index.findConfiguration("com.foo", "bar", "1.3");
        assertTrue(config.isPresent());
        assertEquals(repoPath.resolve("1.0"), config.get().getDirectory());
        assertFalse(config.get().isOverride());

        config = index.findConfiguration("com.foo", "bar", "2.0");
        assertTrue(config.isPresent());
        assertEquals(repoPath.resolve("2.0"), config.get().getDirectory());
        assertTrue(config.get().isOverride());

        config = index.findConfiguration("com.foo", "bar", "2.5");
        assertFalse(config.isPresent());

        config = index.findConfiguration("com.foo", "bar-all", "2.0");
        assertTrue(config.isPresent());
        assertEquals(repoPath.resolve("2.0"), config.get().getDirectory());
        assertFalse(config.get().isOverride());

        config = index.findConfiguration("com.foo", "nope", "1.0");
        assertFalse(config.isPresent());

        Optional<DirectoryConfiguration> latest = index.findLatestConfigurationFor("com.foo", "bar", "123");
        assertTrue(latest.isPresent());
        assertEquals(repoPath.resolve("2.0"), latest.get().getDirectory());
        assertTrue(latest.get().isOverride());

    }

    @Test
    void checkIndexWithDefaultFor() throws URISyntaxException {
        withIndex("artifact-2");

        Optional<DirectoryConfiguration> config = index.findConfiguration("com.foo", "bar", "1.0");
        assertTrue(config.isPresent());
        assertEquals(repoPath.resolve("1.0"), config.get().getDirectory());

        config = index.findConfiguration("com.foo", "bar", "1.1");
        assertTrue(config.isPresent());
        assertEquals(repoPath.resolve("1.0"), config.get().getDirectory());

        config = index.findConfiguration("com.foo", "bar", "2.0");
        assertTrue(config.isPresent());
        assertEquals(repoPath.resolve("2.0"), config.get().getDirectory());

        config = index.findConfiguration("com.foo", "bar", "2.1");
        assertTrue(config.isPresent());
        assertEquals(repoPath.resolve("2.0"), config.get().getDirectory());

        config = index.findConfiguration("com.foo", "baz", "1.1");
        assertTrue(config.isPresent());
        assertEquals(repoPath.resolve("1.0"), config.get().getDirectory());

        Optional<DirectoryConfiguration> latest = index.findLatestConfigurationFor("com.foo", "bar", "123");
        assertTrue(latest.isPresent());
        assertEquals(repoPath.resolve("2.0"), latest.get().getDirectory());

        latest = index.findLatestConfigurationFor("com.foo", "bar", "123");
        assertTrue(latest.isPresent());
        assertEquals(repoPath.resolve("2.0"), latest.get().getDirectory());

        latest = index.findLatestConfigurationFor("com.foo", "bar", "1.0");
        assertTrue(latest.isPresent());
        assertEquals(repoPath.resolve("1.0"), latest.get().getDirectory());

        latest = index.findLatestConfigurationFor("com.foo", "bar", "2.0");
        assertTrue(latest.isPresent());
        assertEquals(repoPath.resolve("2.0"), latest.get().getDirectory());
    }

    private void withIndex(String json) throws URISyntaxException {
        repoPath = new File(SingleModuleJsonVersionToConfigDirectoryIndexTest.class.getResource("/json/" + json).toURI()).toPath();
        index = new SingleModuleJsonVersionToConfigDirectoryIndex(repoPath);
    }

}
