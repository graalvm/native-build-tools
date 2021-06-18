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

import org.graalvm.junit.platform.config.core.PluginConfigProvider;
import org.graalvm.nativeimage.ImageSingletons;
import org.graalvm.nativeimage.hosted.Feature;
import org.graalvm.nativeimage.hosted.RuntimeClassInitialization;
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
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.ServiceLoader;
import java.util.function.Consumer;
import java.util.stream.Collectors;

@SuppressWarnings("unused")
public final class JUnitPlatformFeature implements Feature {

    public final boolean debug = System.getProperty("debug") != null;

    private static final NativeImageConfigurationImpl nativeImageConfigImpl = new NativeImageConfigurationImpl();
    private final ServiceLoader<PluginConfigProvider> extensionConfigProviders = ServiceLoader.load(PluginConfigProvider.class);

    @Override
    public void duringSetup(DuringSetupAccess access) {
        forEachProvider(p -> p.onLoad(nativeImageConfigImpl));
    }

    @Override
    public void beforeAnalysis(BeforeAnalysisAccess access) {
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
            System.out.println("[junit-platform-native] Running in 'test listener' mode.");
            return classes.stream().map(DiscoverySelectors::selectUniqueId).collect(Collectors.toList());
        } catch (IOException | NullPointerException e) {
            System.out.println("[junit-platform-native] Running in 'test discovery' mode. Note that this is a fallback mode.");
        }

        // Run a a junit launcher to discover tests and register classes for reflection
        if (debug) {
            classpath.forEach(path -> debug("Found classpath: " + path));
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
                .map(ClassSource::getJavaClass)
                .forEach(this::registerTestClassForReflection);

        return testPlan;
    }

    private void registerTestClassForReflection(Class<?> clazz) {
        debug("Registering test class for reflection: %s", clazz.getName());
        nativeImageConfigImpl.registerAllClassMembersForReflection(clazz);
        forEachProvider(p -> p.onTestClassRegistered(clazz, nativeImageConfigImpl));
        Class<?> superClass = clazz.getSuperclass();
        if (superClass != null && superClass != Object.class) {
            registerTestClassForReflection(superClass);
        }
    }

    private void forEachProvider(Consumer<PluginConfigProvider> consumer) {
        for (PluginConfigProvider provider : extensionConfigProviders) {
            consumer.accept(provider);
        }
    }

    public static void debug(String format, Object... args) {
        if (debug()) {
            System.out.printf("[Debug] " + format + "%n", args);
        }
    }

    public static boolean debug() {
        return ImageSingletons.lookup(JUnitPlatformFeature.class).debug;
    }
}
