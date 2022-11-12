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
        NativeImageUtils.checkVersion("22", "GraalVM 22.3.0 Java 17 CE (Java Version 17.0.5+8-jvmci-22.3-b08)");
        NativeImageUtils.checkVersion("22.3", "GraalVM 22.3.0 Java 17 CE (Java Version 17.0.5+8-jvmci-22.3-b08)");
        NativeImageUtils.checkVersion("22.3.0", "GraalVM 22.3.0 Java 17 CE (Java Version 17.0.5+8-jvmci-22.3-b08)");
        NativeImageUtils.checkVersion("22.3.0", "GraalVM 22.3.0 Java 17 CE (Java Version 17.0.5+8-jvmci-22.3-b08)\n");
    }

    @Test
    void checkGraalVMCEDevVersion() {
        NativeImageUtils.checkVersion("22", "GraalVM 22.3.0-dev Java 17 CE (Java Version 17.0.5+8-LTS)");
        NativeImageUtils.checkVersion("22.3", "GraalVM 22.3.0-dev Java 17 CE (Java Version 17.0.5+8-LTS)");
        NativeImageUtils.checkVersion("22.3.0", "GraalVM 22.3.0-dev Java 17 CE (Java Version 17.0.5+8-LTS)");
    }

    @Test
    void checkGraalVMEEVersion() {
        NativeImageUtils.checkVersion("22", "GraalVM 22.3.0 Java 17 EE (Java Version 17.0.5+9-LTS-jvmci-22.3-b07)");
        NativeImageUtils.checkVersion("22.3", "GraalVM 22.3.0 Java 17 EE (Java Version 17.0.5+9-LTS-jvmci-22.3-b07)");
        NativeImageUtils.checkVersion("22.3.0", "GraalVM 22.3.0 Java 17 EE (Java Version 17.0.5+9-LTS-jvmci-22.3-b07)");
    }

    @Test
    void checkGreaterVersion() {
        NativeImageUtils.checkVersion("22", "GraalVM 23.2.1");
        NativeImageUtils.checkVersion("23.1", "GraalVM 23.2.1");
        NativeImageUtils.checkVersion("23.2.0", "GraalVM 23.2.1");
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

}
