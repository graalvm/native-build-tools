package org.graalvm.demo;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.apache.commons.math3.complex.Complex;

import java.io.IOException;
import java.io.FileInputStream;

class CalculatorTest {

    @Test
    @DisplayName("1 + 1 = 2")
    void addsTwoNumbers() {
        Calculator calculator = new Calculator();
        assertEquals(2, calculator.add(1, 1), "1 + 1 should equal 2");
    }

    @Test
    @DisplayName("1 + 2 = 3")
    void addsTwoNumbers2() {
        Calculator calculator = new Calculator();
        assertEquals(3, calculator.add(1, 2), "1 + 2 should equal 3");
    }

    @Test
    @DisplayName("I == complex(0, 1)")
    void createComplexNumber() {
        Calculator calculator = new Calculator();
        assertEquals(Complex.I, calculator.complex(0, 1), "complex(0,1) should equal I");
    }

    @ParameterizedTest(name = "{0} + {1} = {2}")
    @CsvSource({
            "0,    1,   1",
            "1,    2,   3",
            "49,  51, 100",
            "1,  100, 101"
    })
    void add(int first, int second, int expectedResult) {
        Calculator calculator = new Calculator();
        assertEquals(expectedResult, calculator.add(first, second),
                () -> first + " + " + second + " should equal " + expectedResult);
    }

    @Test
    void messageIsFoundUsingRelativePaths() throws IOException {
        try (FileInputStream fileInputStream = new FileInputStream("src/test/resources/hello.txt")) {
            assertNotNull(fileInputStream);
        }
    }
}
