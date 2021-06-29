/*
 * Copyright (c) 2021, 2021 Oracle and/or its affiliates. All rights reserved.
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

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.extension.ParameterContext;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.aggregator.AggregateWith;
import org.junit.jupiter.params.aggregator.ArgumentsAccessor;
import org.junit.jupiter.params.aggregator.ArgumentsAggregationException;
import org.junit.jupiter.params.aggregator.ArgumentsAggregator;
import org.junit.jupiter.params.provider.CsvSource;

import java.util.Objects;

public class AggregateWithTests {

    @ParameterizedTest(name = "Test basic aggregation")
    @CsvSource({
            "0,    1,   1",
    })
    public void testAggregateWith(@AggregateWith(AdditionTestArgumentsAggregator.class) AggregatedArgs test) {
        Assertions.assertEquals(new AggregatedArgs(0, 1, 1), test);
    }

    @ParameterizedTest(name = "Test aggregation with two parameters")
    @CsvSource({
            "0,    1,   1,   1,    2,   3",
    })
    public void testAggregateWith(@AggregateWith(AdditionTestArgumentsAggregator.class) AggregatedArgs testOne, @AggregateWith(AdditionTestArgumentsAggregator.class) AggregatedArgs testTwo) {
        Assertions.assertEquals(new AggregatedArgs(0, 1, 1), testOne);
        Assertions.assertEquals(new AggregatedArgs(1, 2, 3), testTwo);
    }

}

class AdditionTestArgumentsAggregator implements ArgumentsAggregator {

    @Override
    public Object aggregateArguments(ArgumentsAccessor accessor, ParameterContext context) throws ArgumentsAggregationException {
        int offset = context.getIndex() * 3;
        int a = accessor.getInteger(offset);
        int b = accessor.getInteger(offset + 1);
        int result = accessor.getInteger(offset + 2);
        return new AggregatedArgs(a, b, result);
    }
}

class AggregatedArgs {

    public final int a;
    public final int b;
    public final int result;

    AggregatedArgs(int a, int b, int result) {
        this.a = a;
        this.b = b;
        this.result = result;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        AggregatedArgs that = (AggregatedArgs) o;
        return a == that.a && b == that.b && result == that.result;
    }

    @Override
    public int hashCode() {
        return Objects.hash(a, b, result);
    }
}
