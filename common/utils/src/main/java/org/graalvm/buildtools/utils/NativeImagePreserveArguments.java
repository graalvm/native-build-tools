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

import org.graalvm.buildtools.model.resources.NativeImageFlags;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Shared rendering of Native Image Preserve arguments. §FS-common-libraries.1.
 */
public final class NativeImagePreserveArguments {
    private NativeImagePreserveArguments() {
    }

    public static String renderPreserve(ArtifactSelection selection) {
        Objects.requireNonNull(selection, "selection");
        if (selection.isEmpty()) {
            throw new IllegalArgumentException("Preserve selection must not be empty");
        }
        if (selection.isAll() && selection.getModules().isEmpty()
                && selection.getPackages().isEmpty() && selection.getPaths().isEmpty()) {
            return NativeImageFlags.PRESERVE + "=all";
        }
        List<String> selectors = new ArrayList<>();
        if (selection.isAll()) {
            selectors.add("all");
        }
        selection.getModules().forEach(module -> selectors.add("module=" + module));
        selection.getPackages().forEach(packageName -> selectors.add("package=" + packageName));
        selection.getPaths().forEach(path -> selectors.add("path=" + path));
        return NativeImageFlags.PRESERVE + "=" + String.join(",", selectors);
    }
}
