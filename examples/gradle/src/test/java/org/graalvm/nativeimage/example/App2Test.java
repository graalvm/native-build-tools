package org.graalvm.nativeimage.example;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class App2Test {
    @Test void appHasAGreeting() {
        App classUnderTest = new App();
        assertNotNull(classUnderTest.getGreeting(), "app should have a greeting");
    }
}
