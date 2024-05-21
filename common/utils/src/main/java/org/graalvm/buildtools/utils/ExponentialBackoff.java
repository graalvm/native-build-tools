/*
 * Copyright 2003-2021 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
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
    private static final Duration INITIAL_WAIT_PERIOD = Duration.of(250, ChronoUnit.MILLIS);

    private final int maxRetries;
    private final Duration initialWaitPeriod;

    public ExponentialBackoff() {
        this(DEFAULT_MAX_RETRIES, INITIAL_WAIT_PERIOD);
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
