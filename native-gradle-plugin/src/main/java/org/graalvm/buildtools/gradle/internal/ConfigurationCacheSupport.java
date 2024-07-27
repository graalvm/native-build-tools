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

package org.graalvm.buildtools.gradle.internal;

import org.gradle.api.Transformer;

import java.io.Serializable;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.stream.Collector;

/**
 * Helper class to deal with Gradle configuration cache.
 */
public class ConfigurationCacheSupport {
    /**
     * Generates a serializable supplier lambda.
     * @param supplier the supplier
     * @return a serializable supplier
     * @param <T> the type of the supplier
     */
    public static <T> Supplier<T> serializableSupplierOf(SerializableSupplier<T> supplier) {
        return supplier;
    }

    /**
     * Generates a serializable predicate lambda.
     * @param predicate the predicate
     * @return a serializable predicate
     * @param <T> the type of the predicate
     */
    public static <T> Predicate<T> serializablePredicateOf(SerializablePredicate<T> predicate) {
        return predicate;
    }

    /**
     * Generates a serializable transformer lambda.
     * @param transformer the transformer
     * @return a serializable transformer
     * @param <OUT> the output type of the transformer
     * @param <IN> the input type of the transformer
     */
    public static <OUT, IN> Transformer<OUT, IN> serializableTransformerOf(SerializableTransformer<OUT, IN> transformer) {
        return transformer;
    }

    /**
     * Generates a serializable function lambda.
     * @param function the bifunction
     * @param <F> the type of the parameter
     * @param <T> the type of the result
     * @return a serializable function
     */
    public static <F, T> Function<F, T> serializableFunctionOf(SerializableFunction<F, T> function) {
        return function;
    }

    /**
     * Generates a serializable bifunction lambda.
     * @param bifunction the bifunction
     * @param <T> the type of the first parameter
     * @param <U> the type of the second parameter
     * @param <R> the type of the result
     * @return a serializable bifunction
     */
    public static <T, U, R> BiFunction<T, U, R> serializableBiFunctionOf(SerializableBiFunction<T, U, R> bifunction) {
        return bifunction;
    }

    public static <T, A, R> Collector<T, A, R> serializableCollectorOf(SerializableCollector<T, A, R> collector) {
        return collector;
    }

    public interface SerializableSupplier<T> extends Supplier<T>, Serializable {

    }

    public interface SerializablePredicate<T> extends Predicate<T>, Serializable {

    }

    public interface SerializableTransformer<OUT, IN> extends Transformer<OUT, IN>, Serializable {

    }

    public interface SerializableFunction<F, T> extends Function<F, T>, Serializable {

    }

    public interface SerializableBiFunction<T, U, R> extends BiFunction<T, U, R>, Serializable {

    }

    public interface SerializableCollector<T, A, R> extends Collector<T, A, R>, Serializable {

    }

}
