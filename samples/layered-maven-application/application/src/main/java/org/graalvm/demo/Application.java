package org.graalvm.demo;

// Sample entry point for Maven layer consumption. §maven/FS-goal-surface.6.
public final class Application {
    private Application() {
    }

    public static void main(String[] args) {
        System.out.println("Hello, layered application!");
    }
}
