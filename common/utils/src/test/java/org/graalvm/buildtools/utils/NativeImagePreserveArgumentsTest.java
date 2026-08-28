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
package org.graalvm.buildtools.utils;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifies the shared Preserve selector grammar and validation. §FS-common-libraries.1. §FS-common-libraries.8.
 */
class NativeImagePreserveArgumentsTest {
    @TempDir
    Path testDirectory;

    @Test
    void rendersAllAndMixedSelectorsDeterministically() {
        assertEquals("-H:Preserve=all", NativeImagePreserveArguments.renderPreserve(
            new ArtifactSelection(true, List.of(), List.of(), List.of())));

        ArtifactSelection selection = new ArtifactSelection(true,
            List.of("java.base", "java.logging"),
            List.of("com.example", "org.example.*"),
            List.of(Path.of("libs", "one.jar"), Path.of("libs", "two with spaces.jar")));

        assertEquals("-H:Preserve=all,module=java.base,module=java.logging,"
                + "package=com.example,package=org.example.*,path=libs/one.jar,path=libs/two with spaces.jar",
            NativeImagePreserveArguments.renderPreserve(selection).replace('\\', '/'));
    }

    @Test
    void rendersPathOnlyPlatformSelections() {
        String argument = NativeImagePreserveArguments.renderPreserve(
            new ArtifactSelection(false, List.of(), List.of(), List.of(Path.of("build", "dependency.jar"))));

        assertTrue(argument.endsWith("path=" + Path.of("build", "dependency.jar")));
    }

    @Test
    void rejectsEmptySelections() {
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
            () -> NativeImagePreserveArguments.renderPreserve(ArtifactSelection.empty()));

        assertEquals("Preserve selection must not be empty", error.getMessage());
    }

    @Test
    void preservesOneSelectorExpressionThroughAnArgumentFile() throws Exception {
        String argument = NativeImagePreserveArguments.renderPreserve(new ArtifactSelection(false,
            List.of(), List.of(), List.of(testDirectory.resolve("dependency with spaces.jar"))));

        List<String> converted = NativeImageUtils.convertToArgsFile(List.of(argument), testDirectory, null);
        Path argsFile = Path.of(converted.get(0).substring(1));

        assertEquals(List.of(NativeImageUtils.escapeArg(argument)), Files.readAllLines(argsFile));
    }
}
