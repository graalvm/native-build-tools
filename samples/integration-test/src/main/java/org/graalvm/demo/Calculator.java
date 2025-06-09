package org.graalvm.demo;

public class Calculator {

    public Calculator() {
        if (System.getenv("TEST_ENV") != null) {
            System.out.println("TEST_ENV = " + System.getenv("TEST_ENV"));
        }
        if (System.getProperty("test-property") != null) {
            System.out.println("test-property = " + System.getProperty("test-property"));
        }
    }

    public int add(int a, int b) {
        return a + b;
    }

}
