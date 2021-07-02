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
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

public class MethodSourceTests {

    public abstract static class ArgumentTestBase {
        private static final List<int[]> actualArgs = new ArrayList<>();
        protected static final List<int[]> expectedArgs = new ArrayList<>();

        @BeforeAll
        public static void setup() {
            actualArgs.clear();
        }

        @AfterAll
        public static void afterAll() {
            Assertions.assertEquals(expectedArgs.size(), actualArgs.size());
            for (int i = 0; i < expectedArgs.size(); ++i) {
                Assertions.assertArrayEquals(expectedArgs.get(i), actualArgs.get(i));
            }
        }

        protected static void addExpectedArgs(int a, int b) {
            expectedArgs.add(new int[]{a, b});
        }

        protected static void traceArgs(int a, int b) {
            actualArgs.add(new int[]{a, b});
        }
    }

    public static class EmptyMethodSourceTests extends ArgumentTestBase {

        @BeforeAll
        public static void setup() {
            addExpectedArgs(1, 5);
            addExpectedArgs(7, 12);
        }

        @ParameterizedTest
        @MethodSource
        public void testEmptyMethodSource(int a, int b) {
            traceArgs(a, b);
        }

        public static Stream<Arguments> testEmptyMethodSource() {
            return Stream.of(
                    Arguments.of(1, 5),
                    Arguments.of(7, 12)
            );
        }
    }

    public static class SameClassMethodSourceTests extends ArgumentTestBase {

        @BeforeAll
        public static void setup() {
            addExpectedArgs(31, 32);
            addExpectedArgs(1, 3);
        }

        @ParameterizedTest
        @MethodSource("getTestInputs")
        public void testSameClassMethodSource(int a, int b) {
            traceArgs(a, b);
        }

        private static Stream<Arguments> getTestInputs() {
            return Stream.of(
                    Arguments.of(31, 32),
                    Arguments.of(1, 3)
            );
        }
    }

    public static class OtherClassMethodSourceTests extends ArgumentTestBase {

        @BeforeAll
        public static void setup() {
            addExpectedArgs(33, 35);
            addExpectedArgs(99, 1);
        }

        @ParameterizedTest
        @MethodSource("org.graalvm.junit.jupiter.MethodSourceProvider#getInputs")
        public void testOtherClassMethodSource(int a, int b) {
            traceArgs(a, b);
        }
    }

    public static class CombinedMethodSourceTests extends ArgumentTestBase {

        @BeforeAll
        public static void setup() {
            addExpectedArgs(33, 35);
            addExpectedArgs(99, 1);
            addExpectedArgs(31, 32);
            addExpectedArgs(1, 3);
        }

        @ParameterizedTest
        @MethodSource({"org.graalvm.junit.jupiter.MethodSourceProvider#getInputs", "getTestInputs"})
        public void combinedTests(int a, int b) {
            traceArgs(a, b);
        }

        private static Stream<Arguments> getTestInputs() {
            return Stream.of(
                    Arguments.of(31, 32),
                    Arguments.of(1, 3)
            );
        }
    }
}

class MethodSourceProvider {

    public static Stream<Arguments> getInputs() {
        return Stream.of(
                Arguments.of(33, 35),
                Arguments.of(99, 1)
        );
    }

}
