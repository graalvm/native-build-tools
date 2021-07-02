package org.graalvm.demo;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class ApplicationTest {

    @Test
    @DisplayName("message is hello native")
    void usesReflection() {
        assertEquals("Hello, native!", Application.getMessage());
    }
}
