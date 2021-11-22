package org.graalvm.demo;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ApplicationTest {

    @Test
    @DisplayName("message is hello native")
    void usesReflection() {
        assertEquals("Hello, native!", Application.getMessage());
    }
}
