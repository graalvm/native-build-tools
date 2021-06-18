/*
 * Copyright (c) 2020, 2021 Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * The Universal Permissive License (UPL), Version 1.0
 *
 * Subject to the condition set forth below, permission is hereby granted to any
 * person obtaining a copy of this software, associated documentation and/or
 * data (collectively the "Software"), free of charge and under any and all
 * copyright rights in the Software, and any and all patent rights owned or
 * freely licensable by each licensor hereunder covering either (i) the
 * unmodified Software as contributed to or provided by such licensor, or (ii)
 * the Larger Works (as defined below), to deal in both
 *
 * (a) the Software, and
 *
 * (b) any piece of software and/or hardware listed in the lrgrwrks.txt file if
 * one is included with the Software each a "Larger Work" to which the Software
 * is contributed by such licensors),
 *
 * without restriction, including without limitation the rights to copy, create
 * derivative works of, display, perform, and distribute the Software and make,
 * use, sell, offer for sale, import, export, have made, and have sold the
 * Software and the Larger Work(s), and to sublicense the foregoing rights on
 * either these or other terms.
 *
 * This license is subject to the following condition:
 *
 * The above copyright notice and either this complete permission notice or at a
 * minimum a reference to the UPL must be included in all copies or substantial
 * portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */
package org.graalvm.junit.platform;

import org.junit.platform.engine.TestExecutionResult;
import org.junit.platform.launcher.TestExecutionListener;
import org.junit.platform.launcher.TestIdentifier;
import org.junit.platform.launcher.TestPlan;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.List;

/**
 * JUnit Platform {@link TestExecutionListener} that tracks the test
 * that were executed and generates a file (currently hard coded to
 * {@code ./build/test_ids.txt} with Gradle or {@code ./target/test_ids.txt} with Maven)
 * that contains test IDs which can be passed to custom launcher to select exactly those test classes.
 * <p>
 * This file should be replaced with org.junit.platform.launcher.listeners.UniqueIdTrackingListener,
 * once junit-platform-launcher version 1.8 gets released.
 *
 * @author Sam Brannen
 */
@SuppressWarnings("unused")
public class UniqueIdTrackingTestExecutionListener implements TestExecutionListener {

    static final String FILE_NAME = "test_ids.txt";

    private final List<String> uniqueIds = new ArrayList<>();

    @Override
    public void executionSkipped(TestIdentifier testIdentifier, String reason) {
        addTest(testIdentifier);
    }

    @Override
    public void executionFinished(TestIdentifier testIdentifier, TestExecutionResult testExecutionResult) {
        addTest(testIdentifier);
    }

    private void addTest(TestIdentifier testIdentifier) {
        if (testIdentifier.isTest()) {
            this.uniqueIds.add(testIdentifier.getUniqueId());
        }
    }

    @Override
    public void testPlanExecutionFinished(TestPlan testPlan) {
        File file = getFile();
        if (file == null) {
            return;
        }

        try (PrintWriter writer = new PrintWriter(new BufferedWriter(new FileWriter(file)), true)) {
            this.uniqueIds.stream().sorted().forEach(writer::println);
        } catch (IOException ex) {
            System.err.println("Failed to write test class names to file " + file);
            ex.printStackTrace(System.err);
        }
    }

    @SuppressWarnings("ResultOfMethodCallIgnored")
    private static File getFile() {

        File buildDir;

        if (new File("pom.xml").exists()) {
            buildDir = new File("target");
        } else {
            buildDir = new File("build");
        }

        if (!buildDir.exists() && !buildDir.mkdirs()) {
            System.err.println("Failed to create directory " + buildDir);
            return null;
        }

        File file = new File(buildDir, FILE_NAME);
        if (file.exists() && !file.delete()) {
            System.err.println("Failed to delete file " + file);
            return null;
        }

        try {
            file.createNewFile();
        } catch (IOException ex) {
            System.err.println("Failed to create file " + file);
            ex.printStackTrace(System.err);
            return null;
        }

        return file;
    }

}
