package org.graalvm.demo;

public class Application {
    static String getMessage() {
        return "Hello, native!";
    }

    public static void main(String[] args) {
        System.out.println(getMessage());
    }
}
