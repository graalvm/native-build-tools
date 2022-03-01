package org.graalvm.example;

import org.graalvm.internal.reflect.Greeter;

public class Application {
    public static void main(String[] args) {
        Greeter greeter = new Greeter();
        greeter.greet();
    }
}
