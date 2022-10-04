package org.graalvm.demo

import groovy.transform.CompileStatic

@CompileStatic
class Application {
    private static final String MESSAGE = System.getenv("CUSTOM_MESSAGE")

    static void main(String[] args) {
        System.out.println(MESSAGE != null ? MESSAGE : "Hello, native!")
    }
}
