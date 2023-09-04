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
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.Collections;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static org.graalvm.buildtools.utils.SharedConstants.GRAALVM_EXE_EXTENSION;

public class NativeImageUtils {

    private static final Pattern requiredVersionPattern = Pattern.compile("^([0-9]+)(?:\\.([0-9]+)?)?(?:\\.([0-9]+)?)?$");

    private static final Pattern graalvmVersionPattern = Pattern.compile("^(GraalVM|native-image) ([0-9]+)\\.([0-9]+)\\.([0-9]+).*");

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

    public static List<String> convertToArgsFile(List<String> cliArgs, Path outputDir) {
        return convertToArgsFile(cliArgs, outputDir, Paths.get(""));
    }

    public static List<String> convertToArgsFile(List<String> cliArgs, Path outputDir, Path projectDir) {
        try {
            boolean ignored = outputDir.toFile().mkdirs();
            File tmpFile = Files.createTempFile(outputDir, "native-image-", ".args").toFile();
            // tmpFile.deleteOnExit();
            cliArgs = cliArgs.stream().map(NativeImageUtils::escapeArg).collect(Collectors.toList());
            Files.write(tmpFile.toPath(), cliArgs, StandardCharsets.UTF_8, StandardOpenOption.CREATE);

            Path resultingPath = tmpFile.toPath().toAbsolutePath();
            if (projectDir != null) { // We know where the project dir is, so want to use relative paths
                resultingPath = projectDir.toAbsolutePath().relativize(resultingPath);
            }
            return Collections.singletonList("@" + resultingPath);
        } catch (IOException e) {
            return Collections.unmodifiableList(cliArgs);
        }
    }

    public static String escapeArg(String arg) {
        if (!(arg.startsWith("\\Q") && arg.endsWith("\\E"))) {
            arg = arg.replace("\\", "\\\\");
            if (arg.contains(" ")) {
                arg = "\"" + arg + "\"";
            }
        }
        return arg;
    }

    /**
     *
     * @param requiredVersion Required version can be {@code MAJOR}, {@code MAJOR.MINOR} or {@code MAJOR.MINOR.PATCH}
     * @param versionToCheck The version to check, as returned by {@code native-image --version}
     * @throws IllegalStateException when the version is not correct
     */
    public static void checkVersion(String requiredVersion, String versionToCheck) {
        if (versionToCheck.contains("GraalVM Runtime Environment")) {
            return; // later than 22.3.1 (e.g., GraalVM for JDK 17 / GraalVM for JDK 20)
        }
        if ((versionToCheck.startsWith("native-image") && versionToCheck.contains("dev")) ||
                versionToCheck.startsWith("GraalVM dev")) { /* For GraalVM 22.3 and earlier */
            return;
        }
        Matcher requiredMatcher = requiredVersionPattern.matcher(requiredVersion);
        if (!requiredMatcher.matches()) {
            throw new IllegalArgumentException("Invalid version " + requiredVersion + ", should be for example \"22\", \"22.3\" or \"22.3.0\".");
        }
        Matcher checkedMatcher = graalvmVersionPattern.matcher(versionToCheck.trim());
        if (!checkedMatcher.matches()) {
            throw new IllegalArgumentException("Version to check '" + versionToCheck + "' can't be parsed.");
        }
        int requiredMajor = Integer.parseInt(requiredMatcher.group(1));
        int checkedMajor = Integer.parseInt(checkedMatcher.group(2));
        if (checkedMajor < requiredMajor) {
            throw new IllegalStateException("GraalVM version " + requiredMajor + " is required but " + checkedMajor +
                    " has been detected, please upgrade.");
        }
        if (checkedMajor > requiredMajor) {
            return;
        }
        if (requiredMatcher.group(2) != null) {
            int requiredMinor = Integer.parseInt(requiredMatcher.group(2));
            int checkedMinor = Integer.parseInt(checkedMatcher.group(3));
            if (checkedMinor < requiredMinor) {
                throw new IllegalStateException("GraalVM version " + requiredMajor + "." + requiredMinor +
                        " is required but " + checkedMajor + "." + checkedMinor + " has been detected, please upgrade.");
            }
            if (checkedMinor > requiredMinor) {
                return;
            }
            if (requiredMatcher.group(3) != null) {
                int requiredPatch = Integer.parseInt(requiredMatcher.group(3));
                int checkedPatch = Integer.parseInt(checkedMatcher.group(4));
                if (checkedPatch < requiredPatch) {
                    throw new IllegalStateException("GraalVM version " + requiredMajor + "." + requiredMinor + "." +
                            requiredPatch +  " is required but " + checkedMajor + "." + checkedMinor + "." + checkedPatch +
                            " has been detected, please upgrade.");
                }
            }
        }
    }
}
