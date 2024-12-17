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

import java.time.Duration;
import java.time.temporal.ChronoUnit;

/**
 * An utility class for exponential backoff of operations which
 * can fail and can be retried.
 */
public class ExponentialBackoff {
    private static final int DEFAULT_MAX_RETRIES = 3;
    private static final Duration DEFAULT_INITIAL_WAIT_PERIOD = Duration.of(250, ChronoUnit.MILLIS);

    private final int maxRetries;
    private final Duration initialWaitPeriod;

    public ExponentialBackoff() {
        this(DEFAULT_MAX_RETRIES, DEFAULT_INITIAL_WAIT_PERIOD);
    }

    private ExponentialBackoff(int maxRetries, Duration initialWaitPeriod) {
        if (maxRetries < 1) {
            throw new IllegalArgumentException("Max retries must be at least 1");
        }
        if (initialWaitPeriod.isNegative() || initialWaitPeriod.isZero()) {
            throw new IllegalArgumentException("Initial backoff wait delay must be strictly positive");
        }
        this.maxRetries = maxRetries;
        this.initialWaitPeriod = initialWaitPeriod;
    }

    public static ExponentialBackoff get() {
        return new ExponentialBackoff();
    }

    /**
     * The maximum number of retries.
     *
     * @param maxRetries the maximum number of retries
     * @return an exponential backoff with the specified number of retries
     */
    public ExponentialBackoff withMaxRetries(int maxRetries) {
        return new ExponentialBackoff(maxRetries, initialWaitPeriod);
    }

    /**
     * The initial backoff duration, that is to say the time we will wait
     * before the first retry (there's no wait for the initial attempt).
     *
     * @param duration the duration for the first retry
     * @return an exponential backoff with the specified initial wait period
     */
    public ExponentialBackoff withInitialWaitPeriod(Duration duration) {
        return new ExponentialBackoff(maxRetries, duration);
    }

    /**
     * Executes an operation which returns a result. Retries a maximum number of
     * times by multiplying the delay between each attempt by 2.
     * @param supplier the operation to execute
     * @return the result of the operation
     * @param <T> the type of the result
     */
    public <T> T supply(FailableSupplier<T> supplier) {
        int attempts = maxRetries + 1;
        Duration waitPeriod = initialWaitPeriod;
        Exception last = null;
        while (attempts > 0) {
            try {
                return supplier.get();
            } catch (Exception ex) {
                last = ex;
                attempts--;
                try {
                    Thread.sleep(waitPeriod.toMillis());
                    waitPeriod = waitPeriod.multipliedBy(2);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new RetriableOperationFailedException("Thread was interrupted", e);
                }
            }
        }
        throw new RetriableOperationFailedException("Operation failed after " + maxRetries + " retries", last);
    }

    /**
     * Executes an operation which doesn't return any result, until it passes,
     * with this exponential backoff parameters.
     * See {@link #supply(FailableSupplier)} for an operation which returns a result.
     * @param operation the operation to execute.
     */
    public void execute(FailableOperation operation) {
        supply(() -> {
            operation.run();
            return null;
        });
    }

    @FunctionalInterface
    public interface FailableOperation {
        void run() throws Exception;
    }

    @FunctionalInterface
    public interface FailableSupplier<T> {
        T get() throws Exception;
    }

    public static final class RetriableOperationFailedException extends RuntimeException {
                public RetriableOperationFailedException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
