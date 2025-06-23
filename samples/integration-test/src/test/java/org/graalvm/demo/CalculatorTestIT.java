package org.graalvm.demo;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class CalculatorTestIT {

    private int add(int a, int b) {
        return a + b;
    }

    @Test
    @DisplayName("1 + 1 = 2")
    void addsTwoNumbers() {
        assertEquals(2, add(1, 1), "1 + 1 should equal 2");
    }

    @Test
    @DisplayName("1 + 2 = 3")
    void addsTwoNumbers2() {
        assertEquals(3, add(1, 2), "1 + 2 should equal 3");
    }

    @ParameterizedTest(name = "{0} + {1} = {2}")
    @CsvSource({
            "0,    1,   1",
            "1,    2,   3",
            "49,  51, 100",
            "1,  100, 101"
    })
    void add(int first, int second, int expectedResult) {
        assertEquals(expectedResult, add(first, second),
                () -> first + " + " + second + " should equal " + expectedResult);
    }
}
