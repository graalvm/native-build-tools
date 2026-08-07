package org.graalvm.demo;

public class Application {
    public static void main(String[] args) {
        System.out.println(args.length == 0 ? "Hello, layered application!" : String.join(", ", args));
    }

}
