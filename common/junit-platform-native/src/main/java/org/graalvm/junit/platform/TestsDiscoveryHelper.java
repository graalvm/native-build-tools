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

import org.graalvm.compiler.serviceprovider.JavaVersionUtil;
import org.junit.platform.engine.DiscoverySelector;
import org.junit.platform.engine.discovery.DiscoverySelectors;
import org.junit.platform.engine.discovery.UniqueIdSelector;
import org.junit.platform.engine.support.descriptor.ClassSource;
import org.junit.platform.launcher.Launcher;
import org.junit.platform.launcher.LauncherDiscoveryRequest;
import org.junit.platform.launcher.TestIdentifier;
import org.junit.platform.launcher.TestPlan;
import org.junit.platform.launcher.core.LauncherDiscoveryRequestBuilder;
import org.junit.platform.launcher.core.LauncherFactory;
import org.junit.platform.launcher.listeners.UniqueIdTrackingListener;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class TestsDiscoveryHelper {
    public static final String TESTDISCOVERY_OUTPUT = "testdiscovery.output";
    public static final String DEBUG = "debug";

    private List<? extends DiscoverySelector> selectors;
    private Launcher launcher = LauncherFactory.create();
    private TestPlan testPlan;

    public static void main(String[] args) throws IOException {
        if (args.length == 0) {
            throw new RuntimeException("Must set classpath roots");
        }
        List<Path> list = Arrays.stream(args[0].split(File.pathSeparator)).map(s -> Paths.get(s)).collect(Collectors.toList());
        TestsDiscoveryHelper testsDiscoveryHelper = new TestsDiscoveryHelper(Boolean.parseBoolean(System.getProperty(DEBUG, "false")), list);
        List<Class<?>> ret = testsDiscoveryHelper.discoverTests();
        String outputPath = System.getProperty(TESTDISCOVERY_OUTPUT);
        String output = ret.stream().map(c -> c.getName()).reduce((s1, s2) -> s1 + "\n" + s2).get();
        try (FileWriter fw = new FileWriter(new File(outputPath))) {
            fw.write(output);
            fw.flush();
        } catch (IOException e) {
            throw e;
        }
    }

    public TestsDiscoveryHelper(boolean debug, List<Path> classpathRoots) {
        selectors = getSelectors(debug, classpathRoots);
    }

    public Launcher getLauncher() {
        return launcher;
    }

    public TestPlan discoverTestPlan() {
        LauncherDiscoveryRequest request = LauncherDiscoveryRequestBuilder.request()
                .selectors(selectors)
                .build();
        testPlan = launcher.discover(request);
        return testPlan;
    }

    /**
     * Launch another Java process to discover tests to avoid unintended class initialization for native image
     * building.
     * @param debug is debug turned on
     * @param classpathRoots class paths
     * @return a list of discovered test names
     */
    public static List<String> launchTestDiscovery(boolean debug, List<Path> classpathRoots) {
        int discoverResult;
        Path resultFile;
        try {
            resultFile = Files.createTempFile("native-image-build-tool-ret-", "");
            ProcessBuilder pb = new ProcessBuilder();

            String javaHome = System.getProperty("java.home");
            List<String> command = new ArrayList<>();
            command.add(javaHome + File.separator + "bin" + File.separator + "java");
            StringBuilder args = new StringBuilder(" ");
            String debugPort = System.getProperty("isolateTestDiscoveryDebugPort");
            if (debugPort != null && !debugPort.equals("-1")) {
                args.append("-agentlib:jdwp=transport=dt_socket,server=y,suspend=y,address=");
                if (JavaVersionUtil.JAVA_SPEC >= 9) {
                    args.append("*:");
                }
                args.append(debugPort).append(" ");
            }

            // Use the same system properties as current Java process
            System.getProperties().forEach((k, v) -> {
                if (!k.equals("line.separator") && !k.equals("java.system.class.loader")
                        && !((String) k).startsWith("jdk.module")) {
                    args.append("-D" + k + "=\"" + v + "\"").append(" ");
                }
            });
            args.append("-D" + TESTDISCOVERY_OUTPUT + "=" + resultFile).append(" ");
            args.append("-D" + DEBUG + "=" + debug).append(" ");
            args.append("-cp").append(" ");
            String cp = classpathRoots.stream().map(p -> p.toString()).collect(Collectors.joining(File.pathSeparator));
            args.append(cp).append(" ");
            args.append(TestsDiscoveryHelper.class.getName()).append(" ");
            args.append(cp).append(" ");

            // Run the new process in the form of "java @argfile"
            Path argFile = Files.createTempFile("native-image-build-tool-args-", "");
            try (FileWriter fw = new FileWriter(argFile.toFile())) {
                fw.write(args.toString());
                fw.flush();
            } catch (IOException e) {
                throw e;
            }
            command.add("@" + argFile.toString());
            pb.command(command);
            Map<String, String> env = pb.environment();
            if (env == null) {
                env = new HashMap<>();
            }
            env.putAll(System.getenv());

            pb.inheritIO();
            System.out.println("[junit-platform-native] Launching tests discovery in a a separated JVM.");
            Process process = pb.start();
            discoverResult = process.waitFor();
        } catch (IOException | InterruptedException e) {
            throw new RuntimeException(e);
        }
        // The discovery results are written in the file, read it.
        List<String> ret;
        if (discoverResult == 0) {
            try {
                ret = new ArrayList<>();
                BufferedReader br = new BufferedReader(new FileReader(resultFile.toFile()));
                String line;
                while ((line = br.readLine()) != null) {
                    ret.add(line);
                }
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        } else {
            throw new RuntimeException("Discover test plan was failed.");
        }
        return ret;
    }

    /**
     * Use the JUnit Platform Launcher to discover tests and register classes
     * for reflection.
     *
     * @return a List of discovered junit test classes
     */
    public List<Class<?>> discoverTests() {
        discoverTestPlan();
        return testPlan.getRoots().stream()
                .flatMap(rootIdentifier -> testPlan.getDescendants(rootIdentifier).stream())
                .map(TestIdentifier::getSource)
                .filter(Optional::isPresent)
                .map(Optional::get)
                .filter(ClassSource.class::isInstance)
                .map(ClassSource.class::cast)
                .map(cs -> cs.getJavaClass()).collect(Collectors.toList());
    }

    private List<? extends DiscoverySelector> getSelectors(boolean debug, List<Path> classpathRoots) {
        try {
            Path outputDir = Paths.get(System.getProperty(UniqueIdTrackingListener.OUTPUT_DIR_PROPERTY_NAME));
            String prefix = System.getProperty(UniqueIdTrackingListener.OUTPUT_FILE_PREFIX_PROPERTY_NAME,
                    UniqueIdTrackingListener.DEFAULT_OUTPUT_FILE_PREFIX);
            List<UniqueIdSelector> selectors = readAllFiles(outputDir, prefix)
                    .map(DiscoverySelectors::selectUniqueId)
                    .collect(Collectors.toList());
            if (!selectors.isEmpty()) {
                System.out.printf(
                        "[junit-platform-native] Running in 'test listener' mode using files matching pattern [%s*] "
                                + "found in folder [%s] and its subfolders.%n",
                        prefix, outputDir.toAbsolutePath());
                return selectors;
            }
        } catch (Exception ex) {
            JUnitPlatformFeature.debug("Failed to read UIDs from UniqueIdTrackingListener output files: " + ex.getMessage());
        }

        System.out.println("[junit-platform-native] Running in 'test discovery' mode. Note that this is a fallback mode.");
        if (debug) {
            classpathRoots.forEach(entry -> JUnitPlatformFeature.debug("Selecting classpath root: " + entry));
        }
        return DiscoverySelectors.selectClasspathRoots(new HashSet<>(classpathRoots));
    }

    private Stream<String> readAllFiles(Path dir, String prefix) throws IOException {
        return findFiles(dir, prefix).map(outputFile -> {
            try {
                return Files.readAllLines(outputFile);
            } catch (IOException ex) {
                throw new UncheckedIOException(ex);
            }
        }).flatMap(List::stream);
    }

    private static Stream<Path> findFiles(Path dir, String prefix) throws IOException {
        if (!Files.exists(dir)) {
            return Stream.empty();
        }
        return Files.find(dir, Integer.MAX_VALUE,
                (path, basicFileAttributes) -> (basicFileAttributes.isRegularFile()
                        && path.getFileName().toString().startsWith(prefix)));
    }
}
