/*
 * Copyright (c) 2020, 2021, Oracle and/or its affiliates. All rights reserved.
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

package org.graalvm.junit.jupiter;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledIf;
import org.junit.jupiter.api.condition.EnabledIf;

public class EnabledIfDisabledIfTests {

    public static final String SHOULD_NOT_RUN_ERROR = "If this ran, something went wrong.";

    public static class EnabledIfTests {

        @EnabledIf("shouldRun")
        public static class ShouldNeverRun {

            @Test
            public void testShouldNeverRun() {
                Assertions.fail(SHOULD_NOT_RUN_ERROR);
            }

            @SuppressWarnings("unused")
            public static boolean shouldRun() {
                return false;
            }
        }

        public static class Mixed {

            private static boolean methodExecuted = false;

            @Test
            @EnabledIf("run")
            public void testShouldRun() {
                methodExecuted = true;
            }

            @Test
            @EnabledIf("doNotRun")
            public void testShouldNotRun() {
                Assertions.fail(SHOULD_NOT_RUN_ERROR);
            }

            @AfterAll
            public static void afterAll() {
                Assertions.assertTrue(methodExecuted);
            }

            @SuppressWarnings("unused")
            public boolean doNotRun() {
                return false;
            }

            @SuppressWarnings("unused")
            public boolean run() {
                return true;
            }
        }


        public static class ExternalCondition {
            private static boolean methodExecuted;

            @Test
            @EnabledIf("org.graalvm.junit.jupiter.EnabledIfExternalConditions#run")
            public void testShouldRun() {
                methodExecuted = true;
            }

            @AfterAll
            public static void afterAll() {
                Assertions.assertTrue(methodExecuted);
            }

            @Test
            @EnabledIf("org.graalvm.junit.jupiter.EnabledIfExternalConditions#doNotRun")
            public void testShouldNotRun() {
                Assertions.fail(SHOULD_NOT_RUN_ERROR);
            }
        }
    }

    public static class DisabledIfTests {

        @DisabledIf("shouldRun")
        public static class ShouldNeverRun {

            @Test
            public void testShouldNeverRun() {
                Assertions.fail(SHOULD_NOT_RUN_ERROR);
            }

            @SuppressWarnings("unused")
            public static boolean shouldRun() {
                return true;
            }
        }

        public static class Mixed {
            private static boolean methodExecuted;

            @Test
            @DisabledIf("run")
            public void testShouldRun() {
                methodExecuted = true;
            }

            @AfterAll
            public static void afterAll() {
                Assertions.assertTrue(methodExecuted);
            }

            @Test
            @DisabledIf("doNotRun")
            public void testShouldNotRun() {
                Assertions.fail(SHOULD_NOT_RUN_ERROR);
            }

            @SuppressWarnings("unused")
            public boolean doNotRun() {
                return true;
            }

            @SuppressWarnings("unused")
            public boolean run() {
                return false;
            }
        }


        public static class ExternalCondition {

            @Test
            @DisabledIf("org.graalvm.junit.jupiter.DisabledIfExternalConditions#run")
            public void testShouldRun() {
            }

            @Test
            @DisabledIf("org.graalvm.junit.jupiter.DisabledIfExternalConditions#doNotRun")
            public void testShouldNotRun() {
                Assertions.fail(SHOULD_NOT_RUN_ERROR);
            }
        }
    }
}

@SuppressWarnings("unused")
class EnabledIfExternalConditions {

    public static boolean doNotRun() {
        return false;
    }

    public static boolean run() {
        return true;
    }
}

@SuppressWarnings("unused")
class DisabledIfExternalConditions {

    public static boolean doNotRun() {
        return true;
    }

    public static boolean run() {
        return false;
    }
}
