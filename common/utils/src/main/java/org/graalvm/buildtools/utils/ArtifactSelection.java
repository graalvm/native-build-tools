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

import java.nio.file.Path;
import java.util.List;
import java.util.Objects;

/**
 * Immutable build-tool-neutral selection of artifacts for a Native Image layer.
 * §FS-common-libraries.1.
 */
public final class ArtifactSelection {
    private final boolean all;
    private final List<String> modules;
    private final List<String> packages;
    private final List<Path> paths;

    public ArtifactSelection(boolean all, List<String> modules, List<String> packages, List<Path> paths) {
        this.all = all;
        this.modules = validatedStrings("module", modules);
        this.packages = validatedStrings("package", packages);
        this.paths = List.copyOf(Objects.requireNonNull(paths, "paths"));
        if (this.paths.stream().anyMatch(Objects::isNull)) {
            throw new IllegalArgumentException("Layer paths must not contain null values");
        }
        if (all && (!this.modules.isEmpty() || !this.packages.isEmpty() || !this.paths.isEmpty())) {
            throw new IllegalArgumentException("The 'all' layer selector cannot be combined with modules, packages, or paths");
        }
    }

    public static ArtifactSelection empty() {
        return new ArtifactSelection(false, List.of(), List.of(), List.of());
    }

    private static List<String> validatedStrings(String kind, List<String> values) {
        List<String> copy = List.copyOf(Objects.requireNonNull(values, kind + "s"));
        if (copy.stream().anyMatch(value -> value == null || value.isBlank())) {
            throw new IllegalArgumentException("Layer " + kind + " selectors must not be blank");
        }
        return copy;
    }

    public boolean isAll() {
        return all;
    }

    public List<String> getModules() {
        return modules;
    }

    public List<String> getPackages() {
        return packages;
    }

    public List<Path> getPaths() {
        return paths;
    }

    public boolean isEmpty() {
        return !all && modules.isEmpty() && packages.isEmpty() && paths.isEmpty();
    }
}
