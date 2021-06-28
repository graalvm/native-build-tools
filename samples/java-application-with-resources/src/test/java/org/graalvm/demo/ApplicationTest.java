package org.graalvm.demo;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.List;
import java.util.stream.Collectors;

class ApplicationTest {

    public static List<String> getExpectedMessages() throws IOException {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(ApplicationTest.class.getResourceAsStream("expected.txt")))) {
            return reader.lines().filter(s -> !s.isEmpty()).collect(Collectors.toList());
        }
    }

    @Test
    void messageIsFoundOnClasspath() throws IOException {
        List<String> messages = Application.getMessages();
        assertEquals(1, messages.size(), "should have a single message");
        assertEquals("Hello, native!", messages.get(0));
        assertEquals(getExpectedMessages(), messages);
    }
}
