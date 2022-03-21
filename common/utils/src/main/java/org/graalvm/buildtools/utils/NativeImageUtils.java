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
package org.graalvm.buildtools.utils;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

import static org.graalvm.buildtools.utils.SharedConstants.GRAALVM_EXE_EXTENSION;

public class NativeImageUtils {
    public static void maybeCreateConfigureUtilSymlink(File configureUtilFile, Path nativeImageExecutablePath) {
        if (!configureUtilFile.exists()) {
            // possibly the symlink is missing
            Path target = configureUtilFile.toPath();
            Path source = nativeImageExecutablePath.getParent().getParent().resolve("lib/svm/bin/" + nativeImageConfigureFileName());
            if (Files.exists(source)) {
                try {
                    Files.createLink(target, source);
                } catch (IOException e) {
                    // ignore as this is handled by consumers
                }
            }
        }
    }

    public static String nativeImageConfigureFileName() {
        return "native-image-configure" + GRAALVM_EXE_EXTENSION;
    }

    public static List<String> convertToArgsFile(List<String> cliArgs) {
        try {
            File tmpFile = File.createTempFile("native-image", "args");
            tmpFile.deleteOnExit();
            cliArgs = cliArgs.stream().map(NativeImageUtils::escapeArg).collect(Collectors.toList());
            Files.write(tmpFile.toPath(), cliArgs, StandardCharsets.UTF_8, StandardOpenOption.CREATE);
            return Collections.singletonList("@" + tmpFile.getAbsolutePath());
        } catch (IOException e) {

            return Collections.unmodifiableList(cliArgs);
        }
    }

    public static String escapeArg(String arg) {
        arg = arg.replace("\\", "\\\\");
        if (arg.contains(" ")) {
            arg = "\"" + arg + "\"";
        }
        return arg;
    }
}
