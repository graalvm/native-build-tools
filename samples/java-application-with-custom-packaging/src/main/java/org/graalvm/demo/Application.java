package org.graalvm.demo;

import io.micronaut.configuration.picocli.PicocliRunner;
import picocli.CommandLine.Command;

@Command(name = "demo", description = "...", mixinStandardHelpOptions = true)
public class Application implements Runnable {

    public static void main(String[] args) {
        PicocliRunner.run(Application.class, args);
    }

    public void run() {
        System.out.println("Hello, native!");
    }
}
