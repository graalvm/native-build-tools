package org.graalvm.demo;

import java.util.Arrays;
import java.util.stream.Collectors;

public class Application {
    static String getMessage() {
        try {
            String className = Arrays.asList("org", "graalvm", "demo", "Message").stream().collect(Collectors.joining("."));
            return (String) Class.forName(className).getDeclaredField("MESSAGE").get(null);
        } catch (Exception e) {
            return null;
        }
    }

    public static void main(String[] args) {
        System.out.println("Application message: " + getMessage());
    }
}
