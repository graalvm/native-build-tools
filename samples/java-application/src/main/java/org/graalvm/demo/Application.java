package org.graalvm.demo;

public class Application {
    private static final String MESSAGE = System.getenv("CUSTOM_MESSAGE");

    public static void main(String[] args) {
        System.out.println(MESSAGE != null ? MESSAGE : "Hello, native!");
    }
}
