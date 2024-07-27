/*
 * Copyright (c) 2022, Oracle and/or its affiliates. All rights reserved.
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

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.regex.Pattern;

class NativeImageUtilsTest {

    @Test
    void invalidVersionToCheck() {
        Assertions.assertThrows(IllegalArgumentException.class, () ->
                NativeImageUtils.checkVersion("22.3", "invalid"));
        Assertions.assertThrows(IllegalArgumentException.class, () ->
                NativeImageUtils.checkVersion("22.3", "GraalVM"));
    }

    @Test
    void invalidRequiredVersion() {
        Assertions.assertThrows(IllegalArgumentException.class, () ->
                NativeImageUtils.checkVersion("invalid", "GraalVM 22.3.0"));
        Assertions.assertThrows(IllegalArgumentException.class, () ->
                NativeImageUtils.checkVersion("22.3.0-dev", "GraalVM 22.3.0"));
    }

    @Test
    void checkGraalVMCEVersion() {
        String graalVMCE_22_3 = "GraalVM 22.3.0 Java 17 CE (Java Version 17.0.5+8-jvmci-22.3-b08)";
        NativeImageUtils.checkVersion("22", graalVMCE_22_3);
        NativeImageUtils.checkVersion("22.3", graalVMCE_22_3);
        NativeImageUtils.checkVersion("22.3.0", graalVMCE_22_3);
        Assertions.assertEquals(17, NativeImageUtils.getMajorJDKVersion(graalVMCE_22_3));

        String graalVMCEForJDK17 = "native-image 17.0.7 2023-04-18\nGraalVM Runtime Environment GraalVM CE 17.0.7+4.1 (build 17.0.7+4-jvmci-23.0-b10)\nSubstrate VM GraalVM CE 17.0.7+4.1 (build 17.0.7+4, serial gc)";
        NativeImageUtils.checkVersion("22.3.0", graalVMCEForJDK17);
        NativeImageUtils.checkVersion("23", graalVMCEForJDK17);
        NativeImageUtils.checkVersion("23.0", graalVMCEForJDK17);
        NativeImageUtils.checkVersion("23.0.0", graalVMCEForJDK17);
        Assertions.assertEquals(17, NativeImageUtils.getMajorJDKVersion(graalVMCEForJDK17));

        String graalVMCEForJDK20 = "native-image 20 2023-04-18\nGraalVM Runtime Environment GraalVM CE 20+34.1 (build 20+34-jvmci-23.0-b10)\nSubstrate VM GraalVM CE 20+34.1 (build 20+34, serial gc)";
        NativeImageUtils.checkVersion("22.3.0", graalVMCEForJDK20);
        NativeImageUtils.checkVersion("23", graalVMCEForJDK20);
        NativeImageUtils.checkVersion("23.0", graalVMCEForJDK20);
        NativeImageUtils.checkVersion("23.0.0", graalVMCEForJDK20);
        Assertions.assertEquals(20, NativeImageUtils.getMajorJDKVersion(graalVMCEForJDK20));

        String graalVMCEForJDK21 = "native-image 21 2023-09-19\nGraalVM Runtime Environment GraalVM CE 21+35.1 (build 21+35-jvmci-23.1-b15)\nSubstrate VM GraalVM CE 21+35.1 (build 21+35, serial gc)";
        NativeImageUtils.checkVersion("22.3.0", graalVMCEForJDK20);
        NativeImageUtils.checkVersion("23", graalVMCEForJDK20);
        NativeImageUtils.checkVersion("23.0", graalVMCEForJDK20);
        NativeImageUtils.checkVersion("23.1", graalVMCEForJDK20);
        NativeImageUtils.checkVersion("23.1.0", graalVMCEForJDK20);
        Assertions.assertEquals(21, NativeImageUtils.getMajorJDKVersion(graalVMCEForJDK21));
    }

    @Test
    void checkGraalVMCEDevVersion() {
        NativeImageUtils.checkVersion("22", "GraalVM 22.3.0-dev Java 17 CE (Java Version 17.0.5+8-LTS)");
        NativeImageUtils.checkVersion("22.3", "GraalVM 22.3.0-dev Java 17 CE (Java Version 17.0.5+8-LTS)");
        NativeImageUtils.checkVersion("22.3.0", "GraalVM 22.3.0-dev Java 17 CE (Java Version 17.0.5+8-LTS)");
        NativeImageUtils.checkVersion("22.3.0", "GraalVM 23.0.0-dev Java 17.0.6+2-jvmci-23.0-b04 CE (Java Version 17.0.6+2-jvmci-23.0-b04)");
        NativeImageUtils.checkVersion("22.3.0", "GraalVM dev CE (Java Version 19+36-jvmci-23.0-b01)");
        NativeImageUtils.checkVersion("22.3.0", "native-image dev CE (Java Version 19+36-jvmci-23.0-b01)");
        String latestGraalVMDevFormat = "native-image 21 2023-09-19\nGraalVM Runtime Environment GraalVM CE 21-dev+35.1 (build 21+35-jvmci-23.1-b15)\nSubstrate VM GraalVM CE 21-dev+35.1 (build 21+35, serial gc)";
        NativeImageUtils.checkVersion("22.3.0", latestGraalVMDevFormat);
    }

    @Test
    void checkGraalVMEEVersion() {
        NativeImageUtils.checkVersion("22", "GraalVM 22.3.0 Java 17 EE (Java Version 17.0.5+9-LTS-jvmci-22.3-b07)");
        NativeImageUtils.checkVersion("22.3", "GraalVM 22.3.0 Java 17 EE (Java Version 17.0.5+9-LTS-jvmci-22.3-b07)");
        NativeImageUtils.checkVersion("22.3.0", "GraalVM 22.3.0 Java 17 EE (Java Version 17.0.5+9-LTS-jvmci-22.3-b07)");
    }

    @Test
    void checkOracleGraalVMVersion() {
        String oracleGraalVMForJDK17 = "native-image 17.0.7 2023-04-18\nGraalVM Runtime Environment Oracle GraalVM (build 17.0.7+8-jvmci-23.0-b10)\nSubstrate VM Oracle GraalVM (build 17.0.7+8, serial gc)";
        NativeImageUtils.checkVersion("22.3.0", oracleGraalVMForJDK17);
        NativeImageUtils.checkVersion("23", oracleGraalVMForJDK17);
        NativeImageUtils.checkVersion("23.0", oracleGraalVMForJDK17);
        NativeImageUtils.checkVersion("23.0.0", oracleGraalVMForJDK17);
        Assertions.assertEquals(17, NativeImageUtils.getMajorJDKVersion(oracleGraalVMForJDK17));

        String oracleGraalVMForJDK20 = "native-image 20.0.1 2023-04-18\nGraalVM Runtime Environment Oracle GraalVM 20.0.1+9.1 (build 20.0.1+9-jvmci-23.0-b10)\nSubstrate VM Oracle GraalVM 20.0.1+9.1 (build 20.0.1+9, serial gc)";
        NativeImageUtils.checkVersion("22.3.0", oracleGraalVMForJDK20);
        NativeImageUtils.checkVersion("23", oracleGraalVMForJDK20);
        NativeImageUtils.checkVersion("23.0", oracleGraalVMForJDK20);
        NativeImageUtils.checkVersion("23.0.0", oracleGraalVMForJDK20);
        Assertions.assertEquals(20, NativeImageUtils.getMajorJDKVersion(oracleGraalVMForJDK20));

        String oracleGraalVMForJDK21 = "native-image 21 2023-09-19\nGraalVM Runtime Environment Oracle GraalVM 21+35.1 (build 21+35-jvmci-23.1-b15)\nSubstrate VM Oracle GraalVM 21+35.1 (build 21+35, serial gc, compressed references)";
        NativeImageUtils.checkVersion("22.3.0", oracleGraalVMForJDK21);
        NativeImageUtils.checkVersion("23", oracleGraalVMForJDK21);
        NativeImageUtils.checkVersion("23.1", oracleGraalVMForJDK21);
        NativeImageUtils.checkVersion("23.1.0", oracleGraalVMForJDK21);
        Assertions.assertEquals(21, NativeImageUtils.getMajorJDKVersion(oracleGraalVMForJDK21));
    }

    @Test
    void checkGreaterVersion() {
        NativeImageUtils.checkVersion("22", "GraalVM 23.2.1");
        NativeImageUtils.checkVersion("23.1", "GraalVM 23.2.1");
        NativeImageUtils.checkVersion("23.2.0", "GraalVM 23.2.1");
        NativeImageUtils.checkVersion("22.3.0", "GraalVM 23.0.0");
        NativeImageUtils.checkVersion("22.2.1", "GraalVM 22.3.0");
        NativeImageUtils.checkVersion("22.2.1", "native-image 22.3.0");
    }

    @Test
    void checkVersionWithTrailingNewLine() {
        NativeImageUtils.checkVersion("22.3.0", "GraalVM 22.3.0\n");
    }

    @Test
    void checkLowerVersion() {
        Assertions.assertThrows(IllegalStateException.class, () ->
            NativeImageUtils.checkVersion("23", "GraalVM 22.2.1")
        );
        Assertions.assertThrows(IllegalStateException.class, () ->
            NativeImageUtils.checkVersion("22.3", "GraalVM 22.2.1")
        );
        Assertions.assertThrows(IllegalStateException.class, () ->
            NativeImageUtils.checkVersion("22.2.2", "GraalVM 22.2.1")
        );
    }

    @Test
    void escapeArg() {
        Assertions.assertEquals("/foo/bar", NativeImageUtils.escapeArg("/foo/bar"));
        Assertions.assertEquals("c:\\\\foo\\\\bar", NativeImageUtils.escapeArg("c:\\foo\\bar"));
        Assertions.assertEquals("\"c:\\\\foo\\\\bar baz\"", NativeImageUtils.escapeArg("c:\\foo\\bar baz"));
    }

    @Test
    void doNotEscapeQuotedRegexp() {
        Assertions.assertEquals(Pattern.quote("/foo/bar"), NativeImageUtils.escapeArg(Pattern.quote("/foo/bar")));
        Assertions.assertEquals(Pattern.quote("c:\\foo\\bar"), NativeImageUtils.escapeArg(Pattern.quote("c:\\foo\\bar")));
        Assertions.assertEquals(Pattern.quote("c:\\foo\\bar baz"), NativeImageUtils.escapeArg(Pattern.quote("c:\\foo\\bar baz")));
    }

}
