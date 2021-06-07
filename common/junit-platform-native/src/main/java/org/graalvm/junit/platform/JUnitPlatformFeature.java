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

import org.graalvm.nativeimage.ImageSingletons;
import org.graalvm.nativeimage.hosted.Feature;
import org.graalvm.nativeimage.hosted.RuntimeClassInitialization;
import org.graalvm.nativeimage.hosted.RuntimeReflection;
import org.junit.platform.engine.DiscoverySelector;
import org.junit.platform.engine.discovery.DiscoverySelectors;
import org.junit.platform.engine.support.descriptor.ClassSource;
import org.junit.platform.launcher.Launcher;
import org.junit.platform.launcher.LauncherDiscoveryRequest;
import org.junit.platform.launcher.TestIdentifier;
import org.junit.platform.launcher.TestPlan;
import org.junit.platform.launcher.core.LauncherDiscoveryRequestBuilder;
import org.junit.platform.launcher.core.LauncherFactory;

import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@SuppressWarnings("unused")
public final class JUnitPlatformFeature implements Feature {

    final boolean debug = System.getProperty("debug") != null;

    @Override
    public void duringSetup(DuringSetupAccess access) {
        try {
            RuntimeReflection.register(org.junit.platform.commons.annotation.Testable.class.getMethods());
            RuntimeReflection.register(Class.forName("org.junit.jupiter.params.ParameterizedTestExtension").getDeclaredMethods());
            RuntimeReflection.registerForReflectiveInstantiation(Class.forName("org.junit.jupiter.params.ParameterizedTestExtension"));
            RuntimeReflection.register(Class.forName("org.junit.jupiter.params.provider.CsvArgumentsProvider").getMethods());
            RuntimeReflection.register(Class.forName("org.junit.jupiter.params.provider.CsvArgumentsProvider").getDeclaredMethods());
            RuntimeReflection.registerForReflectiveInstantiation(Class.forName("org.junit.jupiter.params.provider.CsvArgumentsProvider"));
        } catch (ClassNotFoundException e) {
            throw new RuntimeException("Missing some JUnit Platform classes for runtime reflection configuration. \n" +
                    "Check if JUnit Platform is on your classpath or if that version is supported. \n" +
                    "Original error: " + e);
        }
    }

    @Override
    public void beforeAnalysis(BeforeAnalysisAccess access) {
        RuntimeClassInitialization.initializeAtBuildTime("org.junit.vintage.engine.support.UniqueIdReader");
        RuntimeClassInitialization.initializeAtBuildTime("org.junit.vintage.engine.support.UniqueIdStringifier");
        RuntimeClassInitialization.initializeAtBuildTime("org.junit.platform.launcher.core.InternalTestPlan");
        RuntimeClassInitialization.initializeAtBuildTime("org.junit.platform.commons.util.StringUtils");
        RuntimeClassInitialization.initializeAtBuildTime("org.junit.platform.launcher.core.TestExecutionListenerRegistry");
        RuntimeClassInitialization.initializeAtBuildTime("org.junit.platform.commons.logging.LoggerFactory$DelegatingLogger");
        RuntimeClassInitialization.initializeAtBuildTime("org.junit.platform.launcher.core.EngineDiscoveryOrchestrator");
        RuntimeClassInitialization.initializeAtBuildTime("org.junit.platform.launcher.core.LauncherConfigurationParameters");
        RuntimeClassInitialization.initializeAtBuildTime("org.junit.platform.commons.logging.LoggerFactory");
        RuntimeClassInitialization.initializeAtBuildTime("org.junit.jupiter.engine.config.EnumConfigurationParameterConverter");
        RuntimeClassInitialization.initializeAtBuildTime("org.junit.jupiter.engine.descriptor.ClassTestDescriptor");
        RuntimeClassInitialization.initializeAtBuildTime("org.junit.jupiter.engine.descriptor.ClassBasedTestDescriptor");
        RuntimeClassInitialization.initializeAtBuildTime("org.junit.jupiter.engine.descriptor.TestFactoryTestDescriptor");
        RuntimeClassInitialization.initializeAtBuildTime("org.junit.platform.engine.UniqueIdFormat");
        RuntimeClassInitialization.initializeAtBuildTime("org.junit.jupiter.engine.descriptor.JupiterTestDescriptor");
        RuntimeClassInitialization.initializeAtBuildTime("org.junit.jupiter.engine.descriptor.MethodBasedTestDescriptor");
        RuntimeClassInitialization.initializeAtBuildTime("org.junit.jupiter.engine.descriptor.TestMethodTestDescriptor");
        RuntimeClassInitialization.initializeAtBuildTime("org.junit.platform.commons.util.ReflectionUtils");
        RuntimeClassInitialization.initializeAtBuildTime("org.junit.jupiter.engine.descriptor.TestTemplateTestDescriptor");
        RuntimeClassInitialization.initializeAtBuildTime("org.junit.jupiter.engine.execution.ConditionEvaluator");
        RuntimeClassInitialization.initializeAtBuildTime("org.junit.jupiter.engine.execution.ExecutableInvoker");
        RuntimeClassInitialization.initializeAtBuildTime(NativeImageJUnitLauncher.class);

        Launcher launcher = LauncherFactory.create();
        TestPlan testplan = registerTestPlan(launcher, access.getApplicationClassPath());
        ImageSingletons.add(NativeImageJUnitLauncher.class, new NativeImageJUnitLauncher(launcher, testplan));
    }

    private List<? extends DiscoverySelector> getSelectors(List<Path> classpath) {
        InputStream listenerStream;
        try {
            listenerStream = new FileInputStream(UniqueIdTrackingTestExecutionListener.FILE_NAME);
        } catch (FileNotFoundException e) {
            listenerStream = getClass().getClassLoader().getResourceAsStream(UniqueIdTrackingTestExecutionListener.FILE_NAME);
        }
        try (BufferedReader br = new BufferedReader(new InputStreamReader(listenerStream))) {
            List<String> classes = br.lines().collect(Collectors.toList());
            System.out.println("Test listener mode");
            return classes.stream().map(DiscoverySelectors::selectUniqueId).collect(Collectors.toList());
        } catch (IOException | NullPointerException e) {
            System.out.println("Test discovery mode");
        }

        // Run a a junit launcher to discover tests and register classes for reflection
        if (debug) {
            classpath.forEach(path -> System.out.println("[Debug] Found classpath: " + path));
        }
        return DiscoverySelectors.selectClasspathRoots(new HashSet<>(classpath));

    }

    private TestPlan registerTestPlan(Launcher launcher, List<Path> classpath) {
        LauncherDiscoveryRequest request = LauncherDiscoveryRequestBuilder.request()
                .selectors(getSelectors(classpath))
                .build();

        TestPlan testPlan = launcher.discover(request);

        testPlan.getRoots().stream()
                .flatMap(rootIdentifier -> testPlan.getDescendants(rootIdentifier).stream())
                .map(TestIdentifier::getSource)
                .filter(Optional::isPresent)
                .map(Optional::get)
                .filter(ClassSource.class::isInstance)
                .map(ClassSource.class::cast)
                .forEach((ClassSource classSource) -> {
                    Class<?> clazz = classSource.getJavaClass();
                    if (debug) {
                        System.out.println("[Debug] Found test class: " + clazz);
                    }
                    RuntimeReflection.registerForReflectiveInstantiation(clazz);
                    for (Field field : clazz.getDeclaredFields()) {
                        RuntimeReflection.register(field);
                    }
                    for (Method method : clazz.getDeclaredMethods()) {
                        RuntimeReflection.register(method);
                    }
                });

        return testPlan;
    }
}
