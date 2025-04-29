package org.graalvm.junit.jupiter;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInfo;
import org.junit.jupiter.params.ParameterizedClass;
import org.junit.jupiter.params.provider.ValueSource;

abstract class BaseTests {
    @Test
    void test(TestInfo testInfo) {
        Assertions.assertEquals(ParameterizedClassTests.InnerTests.class, testInfo.getTestClass().orElseThrow());
    }
}

@ParameterizedClass
@ValueSource(ints = { 1, 2 })
class ParameterizedClassTests {

    private final int value;

    ParameterizedClassTests(int value) {
        this.value = value;
    }

    @Test
    void test() {
        Assertions.assertTrue(value == 1 || value == 2);
    }

    @Nested
    class InnerTests extends BaseTests {
    }
}
