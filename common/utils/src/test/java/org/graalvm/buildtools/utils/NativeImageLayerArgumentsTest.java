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

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifies the shared layer selection and rendering contract.
 * §FS-common-libraries.1. §FS-common-libraries.8.
 */
class NativeImageLayerArgumentsTest {
    @Test
    void rendersMixedSelectionsDeterministically() {
        ArtifactSelection selection = new ArtifactSelection(false,
            List.of("java.base", "java.logging"),
            List.of("com.example"),
            List.of(Path.of("libs", "one.jar"), Path.of("libs", "two.jar")));

        assertEquals("-H:LayerCreate=base.nil,module=java.base,module=java.logging,"
                + "package=com.example,path=libs/one.jar,path=libs/two.jar",
            NativeImageLayerArguments.renderLayerCreate("base", selection).replace('\\', '/'));
    }

    @Test
    void rendersAllAsAnUnqualifiedCreateAndPlatformPaths() {
        assertEquals("-H:LayerCreate=dependencies.nil",
            NativeImageLayerArguments.renderLayerCreate("dependencies",
                new ArtifactSelection(true, List.of(), List.of(), List.of())));
        assertTrue(NativeImageLayerArguments.renderLayerUse(Path.of("build", "base.nil"))
            .endsWith(Path.of("build", "base.nil").toString()));
    }

    @Test
    void validatesNamesSelectorsAndCombinations() {
        assertThrows(IllegalArgumentException.class,
            () -> NativeImageLayerArguments.renderLayerCreate(" ", ArtifactSelection.empty()));
        IllegalArgumentException empty = assertThrows(IllegalArgumentException.class,
            () -> NativeImageLayerArguments.renderLayerCreate("base", ArtifactSelection.empty()));
        assertTrue(empty.getMessage().contains("Layer 'base' has no contents"));
        assertThrows(IllegalArgumentException.class,
            () -> new ArtifactSelection(false, List.of(""), List.of(), List.of()));
        assertThrows(IllegalArgumentException.class,
            () -> new ArtifactSelection(true, List.of("java.base"), List.of(), List.of()));
    }

    @Test
    void copiesInputCollections() {
        List<String> modules = new ArrayList<>(List.of("java.base"));
        ArtifactSelection selection = new ArtifactSelection(false, modules, List.of(), List.of());
        modules.add("java.logging");
        assertEquals(List.of("java.base"), selection.getModules());
        assertThrows(UnsupportedOperationException.class, () -> selection.getModules().add("java.sql"));
    }

    @Test
    void identifiesTheUnsupportedLayerConsumptionRelease() {
        assertTrue(NativeImageLayerArguments.isLayerConsumptionUnsupported(
            "native-image 25.0.3 2026-04-21\nGraalVM Runtime Environment Oracle GraalVM 25.0.3+9.1"));
        assertEquals(false, NativeImageLayerArguments.isLayerConsumptionUnsupported(
            "native-image 25.0.4 2026-07-21"));
    }
}
