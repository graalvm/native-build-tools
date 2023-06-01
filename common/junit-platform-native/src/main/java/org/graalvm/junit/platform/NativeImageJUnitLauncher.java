/*
 * Copyright (c) 2020, 2021, Oracle and/or its affiliates. All rights reserved.
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

import org.graalvm.nativeimage.ImageInfo;
import org.graalvm.nativeimage.ImageSingletons;
import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;
import org.junit.platform.launcher.Launcher;
import org.junit.platform.launcher.TestExecutionListener;
import org.junit.platform.launcher.TestPlan;
import org.junit.platform.launcher.listeners.SummaryGeneratingListener;
import org.junit.platform.launcher.listeners.TestExecutionSummary;
import org.junit.platform.reporting.legacy.xml.LegacyXmlReportGeneratingListener;

import java.io.PrintWriter;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.LinkedList;

public class NativeImageJUnitLauncher {
    static final String DEFAULT_OUTPUT_FOLDER = Paths.get("test-results-native").resolve("test").toString();

    final Launcher launcher;
    TestPlan testPlan;
    final TestsDiscoveryHelper testsDiscoveryHelper;

    @Platforms(Platform.HOSTED_ONLY.class)
    public NativeImageJUnitLauncher(TestsDiscoveryHelper testsDiscoveryHelper) {
        this.testsDiscoveryHelper = testsDiscoveryHelper;
        launcher = testsDiscoveryHelper.getLauncher();
    }

    private void discoverTests() {
        testPlan = testsDiscoveryHelper.discoverTestPlan();
    }

    public void registerTestExecutionListeners(TestExecutionListener testExecutionListener) {
        launcher.registerTestExecutionListeners(testExecutionListener);
    }

    public void execute() {
        launcher.execute(testPlan);
    }

    static String stringPad(String input) {
        return String.format("%1$-20s", input);
    }

    public static void main(String... args) {
        if (!ImageInfo.inImageCode()) {
            System.err.println("NativeImageJUnitLauncher can only be used for native-image compiled tests.");
            System.exit(1);
        }

        String xmlOutput = DEFAULT_OUTPUT_FOLDER;
        boolean silent = false;

        LinkedList<String> arguments = new LinkedList<>(Arrays.asList(args));
        while (!arguments.isEmpty()) {
            String arg = arguments.poll();
            switch (arg) {
                case "--help":
                    System.out.println("JUnit Platform launcher for GraalVM Native Image");
                    System.out.println("----------------------------------------\n");
                    System.out.println("Flags:");
                    System.out.println(stringPad("--xml-output-dir") + "Selects report xml output directory (default: `" + DEFAULT_OUTPUT_FOLDER + "`)");
                    System.out.println(stringPad("--silent") + "Only output xml without stdout summary");
                    System.out.println(stringPad("--help") + "Displays this help screen");
                    System.exit(0);
                    break;
                case "--xml-output-dir":
                    xmlOutput = arguments.poll();
                    break;
                case "--silent":
                    silent = true;
                    break;
                default:
                    System.err.println("Found unknown command line option: " + arg);
                    System.exit(1);
                    break;
            }
        }

        PrintWriter out = new PrintWriter(System.out);
        NativeImageJUnitLauncher launcher = ImageSingletons.lookup(NativeImageJUnitLauncher.class);
        //Discover the test plan at runtime.
        launcher.discoverTests();
        if (!silent) {
            out.println("JUnit Platform on Native Image - report");
            out.println("----------------------------------------\n");
            out.flush();
            launcher.registerTestExecutionListeners(new PrintTestExecutionListener(out));
        }

        SummaryGeneratingListener summaryListener = new SummaryGeneratingListener();
        launcher.registerTestExecutionListeners(summaryListener);
        launcher.registerTestExecutionListeners(new LegacyXmlReportGeneratingListener(Paths.get(xmlOutput), out));
        launcher.execute();

        TestExecutionSummary summary = summaryListener.getSummary();
        if (!silent) {
            summary.printFailuresTo(out);
            summary.printTo(out);
        }

        long failedCount = summary.getTotalFailureCount();
        System.exit(failedCount > 0 ? 1 : 0);
    }
}
