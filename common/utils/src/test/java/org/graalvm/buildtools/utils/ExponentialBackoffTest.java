/*
 * Copyright (c) 2024, Oracle and/or its affiliates. All rights reserved.
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

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.Duration;
import java.time.temporal.ChronoUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ExponentialBackoffTest {
    @Test
    @DisplayName("executed a passing operation")
    void simpleExecution() {
        AtomicBoolean success = new AtomicBoolean();
        ExponentialBackoff.get().execute(() -> success.set(true));
        assertTrue(success.get());
    }

    @ParameterizedTest
    @ValueSource(ints = {1, 3})
    @DisplayName("retries expected amount of times")
    void countRetries(int retries) {
        AtomicInteger count = new AtomicInteger();
        assertThrows(ExponentialBackoff.RetriableOperationFailedException.class, () -> ExponentialBackoff.get().withMaxRetries(retries)
            .execute(() -> {
                count.incrementAndGet();
                throw new RuntimeException();
            }));
        assertEquals(retries + 1, count.get());
    }

    @ParameterizedTest
    @ValueSource(ints = {1, 3})
    @DisplayName("passes after one retry")
    void passAfterRetry(int retries) {
        AtomicInteger count = new AtomicInteger();
        int result = ExponentialBackoff.get().withMaxRetries(retries)
            .supply(() -> {
                if (count.getAndIncrement() == 0) {
                    throw new RuntimeException();
                }
                return 200;
            });
        assertEquals(2, count.get());
        assertEquals(200, result);
    }

    @Test
    @DisplayName("can configure initial backoff time")
    void canConfigureInitialBackoffTime() {
        double sd = System.currentTimeMillis();
        assertThrows(ExponentialBackoff.RetriableOperationFailedException.class, () -> ExponentialBackoff.get()
            .withMaxRetries(4)
            .withInitialWaitPeriod(Duration.of(1, ChronoUnit.MILLIS))
            .execute(() -> {
                throw new RuntimeException();
            }));
        double duration = System.currentTimeMillis() - sd;
        assertTrue(duration < 100);
    }

}
