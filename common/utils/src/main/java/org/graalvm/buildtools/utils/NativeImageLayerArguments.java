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

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 * Shared rendering of Native Image layer arguments. §FS-common-libraries.1.
 */
public final class NativeImageLayerArguments {
    private static final Pattern GRAALVM_RUNTIME_VERSION = Pattern.compile(
        "(?m)^GraalVM Runtime Environment .*\\s(\\d+\\.\\d+\\.\\d+(?:[.+-][^\\s]*)?)\\s+\\(build");
    private static final Pattern NATIVE_IMAGE_VERSION = Pattern.compile(
        "(?m)^native-image\\s+(\\d+\\.\\d+\\.\\d+(?:[.+-][^\\s]*)?)(?:\\s|$)");
    private static final Pattern UNSUPPORTED_LAYER_CONSUMPTION_VERSION =
        Pattern.compile("25\\.0\\.[34](?:[.+-].*)?");
    private static final Pattern VALID_LAYER_NAME = Pattern.compile("[A-Za-z0-9._-]+");

    private NativeImageLayerArguments() {
    }

    public static String renderLayerCreate(String layerName, ArtifactSelection selection) {
        validateLayerName(layerName);
        Objects.requireNonNull(selection, "selection");
        if (selection.isEmpty()) {
            throw new IllegalArgumentException("Layer '" + layerName + "' has no contents");
        }
        List<String> selectors = new ArrayList<>();
        selection.getModules().forEach(module -> selectors.add("module=" + module));
        selection.getPackages().forEach(packageName -> selectors.add("package=" + packageName));
        selection.getPaths().forEach(path -> selectors.add("path=" + path));
        String argument = NativeImageFlags.LAYER_CREATE + "=" + layerName + ".nil";
        return selectors.isEmpty() ? argument : argument + "," + String.join(",", selectors);
    }

    public static String renderLayerUse(Path layerFile) {
        Objects.requireNonNull(layerFile, "layerFile");
        return NativeImageFlags.LAYER_USE + "=" + layerFile.toAbsolutePath();
    }

    public static boolean isLayerConsumptionUnsupported(String versionInformation) {
        String information = Objects.requireNonNull(versionInformation, "versionInformation");
        var runtimeVersion = GRAALVM_RUNTIME_VERSION.matcher(information);
        if (runtimeVersion.find()) {
            return UNSUPPORTED_LAYER_CONSUMPTION_VERSION.matcher(runtimeVersion.group(1)).matches();
        }
        var nativeImageVersion = NATIVE_IMAGE_VERSION.matcher(information);
        return nativeImageVersion.find()
            && UNSUPPORTED_LAYER_CONSUMPTION_VERSION.matcher(nativeImageVersion.group(1)).matches();
    }

    public static void validateLayerName(String layerName) {
        if (layerName == null || layerName.isBlank()) {
            throw new IllegalArgumentException("Layer name must not be blank");
        }
        if (!VALID_LAYER_NAME.matcher(layerName).matches()) {
            throw new IllegalArgumentException(
                "Layer name '" + layerName + "' must contain only letters, digits, dots, underscores, or hyphens");
        }
    }
}
