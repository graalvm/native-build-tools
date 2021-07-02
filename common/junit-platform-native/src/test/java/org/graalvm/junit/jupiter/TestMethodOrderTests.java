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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInfo;
import org.junit.jupiter.api.TestMethodOrder;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;


public class TestMethodOrderTests {

    public static class MethodOrderTests {

        private static final List<String> executedMethods = new ArrayList<>();

        @BeforeAll
        public static void beforeTests() {
            executedMethods.clear();
        }

        @BeforeEach
        public void beforeEach(TestInfo info) {
            executedMethods.add(info.getTestMethod().get().getName());
        }

        @AfterAll
        public static void afterTests() {
            List<String> expectedMethods = Arrays.asList("testOne", "testTwo");
            Assertions.assertEquals(expectedMethods, executedMethods);
        }

    }

    @TestMethodOrder(MethodOrderer.OrderAnnotation.class)
    public static class ArgumentOrderer extends MethodOrderTests {

        @Test
        @Order(2)
        public void testTwo() {
        }

        @Test
        @Order(1)
        public void testOne() {
        }
    }

    @TestMethodOrder(MethodOrderer.DisplayName.class)
    public static class DisplayNameOrderer extends MethodOrderTests {

        @Test
        @DisplayName("B - I must be second")
        public void testTwo() {
        }

        @Test
        @DisplayName("A - I must be first")
        public void testOne() {
        }
    }

    @TestMethodOrder(MethodOrderer.MethodName.class)
    public static class MethodNameOrderer extends MethodOrderTests {

        @Test
        public void testOne() {
        }

        @Test
        public void testTwo() {
        }
    }

    @TestMethodOrder(MethodOrderer.Random.class)
    public static class RandomOrderer {

        @Test
        public void testOne() {
        }

        @Test
        public void testTwo() {
        }
    }
}
