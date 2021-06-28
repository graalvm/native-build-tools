package org.graalvm.demo;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.List;
import java.util.stream.Collectors;

public class Application {
    public static List<String> getMessages() throws IOException {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(Application.class.getResourceAsStream("/message.txt")))) {
            return reader.lines().filter(s -> !s.isEmpty()).collect(Collectors.toList());
        }
    }
    public static void main(String[] args) throws IOException {
        getMessages().forEach(System.out::println);
    }
}
