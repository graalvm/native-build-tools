/*
 * Copyright (c) 2021, Oracle and/or its affiliates. All rights reserved.
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

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * Utility class containing various native-image and JVM related methods.
 * Keep this file in sync across all build tool plugins.
 */
public interface SharedConstants {
    boolean IS_WINDOWS = System.getProperty("os.name", "unknown").contains("Windows");
    boolean IS_CI = System.getenv("CI") != null;
    boolean IS_DUMB_TERM = Arrays.asList(null, "", "dumb", "unknown").contains(System.getenv("TERM"));
    boolean NO_COLOR = System.getenv("NO_COLOR") != null; // https://no-color.org/
    String GRAALVM_EXE_EXTENSION = (IS_WINDOWS ? ".cmd" : "");
    String EXECUTABLE_EXTENSION = (IS_WINDOWS ? ".exe" : "");
    String NATIVE_IMAGE_EXE = "native-image" + GRAALVM_EXE_EXTENSION;
    String GU_EXE = "gu" + GRAALVM_EXE_EXTENSION;
    String NATIVE_IMAGE_OUTPUT_FOLDER = "native";
    String AGENT_PROPERTY = "agent";
    String AGENT_OUTPUT_FOLDER = NATIVE_IMAGE_OUTPUT_FOLDER + "/agent-output";
    String NATIVE_TESTS_SUFFIX = "-tests";
    List<String> DEFAULT_EXCLUDES_FOR_RESOURCE_DETECTION = Collections.unmodifiableList(Arrays.asList(
            "META-INF/services/.*",
            "META-INF/native-image/.*",
            "META-INF/maven/.*",
            "META-INF/LICENSE.*",
            "META-INF/NOTICE.*",
            "META-INF/.*[.](md|adoc)",
            "META-INF/INDEX.LIST",
            ".*/package.html"
    ));
    String AGENT_SESSION_SUBDIR = "session-{pid}-{datetime}";
    String AGENT_OUTPUT_DIRECTORY_MARKER = "{output_dir}";
    String AGENT_OUTPUT_DIRECTORY_OPTION = "config-output-dir=";
    String METADATA_REPO_URL_TEMPLATE = "https://github.com/oracle/graalvm-reachability-metadata/releases/download/%1$s/graalvm-reachability-metadata-%1$s.zip";
    String METADATA_REPO_DEFAULT_VERSION = "0.2.2";
}
