/*
 * Copyright (c) 2021, 2021 Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.substratevm;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Optional;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * Utility class containing various native-image and JVM related methods.
 */
public class Utils {
    public static final String EXECUTABLE_EXTENSION = (System.getProperty("os.name", "unknown").startsWith("Windows") ? ".exe" : "");
    public static final String NATIVE_IMAGE_EXE = "native-image" + EXECUTABLE_EXTENSION;
    public static final String AGENT_OUTPUT_FOLDER = "agent-output";
    public static final String NATIVE_TESTS_EXE = "native-image-tests" + EXECUTABLE_EXTENSION;
    public static final String AGENT_FILTER = "agent-filter.json";

    @SuppressWarnings("SameParameterValue")
    public static Path getJavaHomeNativeImage(String javaHomeVariable) {
        String graalHome = System.getenv(javaHomeVariable);
        if (graalHome == null) {
            return null;
        }

        Path graalExe = Paths.get(graalHome).resolve("bin").resolve(NATIVE_IMAGE_EXE);
        if (!Files.exists(graalExe)) {
            throw new RuntimeException("native-image is not installed in your " + javaHomeVariable + "." +
                    "You should install it using `gu install native-image`");
        }
        return graalExe;
    }

    public static Path getNativeImageFromPath() {
        Optional<Path> exePath = Stream.of(System.getenv("PATH").split(Pattern.quote(File.pathSeparator)))
                .map(Paths::get)
                .filter(path -> Files.exists(path.resolve(NATIVE_IMAGE_EXE)))
                .findFirst();
        return exePath.map(path -> path.resolve(NATIVE_IMAGE_EXE)).orElse(null);
    }

    public static Path getNativeImage() {
        Path nativeImage = getJavaHomeNativeImage("GRAALVM_HOME");

        if (nativeImage == null) {
            nativeImage = getNativeImageFromPath();
        }

        // Search for native-image executable in JAVA_HOME environment variable was removed
        // since if it isn't present in either GRAALVM_HOME or PATH, user should
        // change that in order to ensure interoperability with other standard tools.

        if (nativeImage == null) {
            throw new RuntimeException("GraalVM native-image is missing from your system.\n " +
                    "Make sure that GRAALVM_HOME environment variable is present.");
        }

        return nativeImage;
    }

    public static int startProcess(Path pwd, String ... cmd) {
        File pwdDir = pwd.toFile();

        if (!pwdDir.exists()) {
            if (!pwdDir.mkdirs()) {
                System.err.println("Failed creating working dir: " + pwd);
                return 1;
            }
        }

        ProcessBuilder pb = new ProcessBuilder(cmd);
        pb.directory(pwdDir);
        pb.redirectErrorStream(true);
        int result = 1;
        try {
            Process p = pb.start();
            Thread outputThread = mergeProcessOutput(p.getInputStream());
            result = p.waitFor();
            outputThread.join();
            System.out.println();
        } catch (IOException | InterruptedException e) {
            e.printStackTrace();
        }
        return result;
    }

    public static Thread mergeProcessOutput(final InputStream is) {
        Runnable r = () -> {
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(is))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    System.out.println(line);
                }
            } catch (IOException ex) {
                ex.printStackTrace();
            }
        };
        Thread thread = new Thread(r);
        thread.start();
        return thread;
    }
}
