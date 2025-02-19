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

import org.graalvm.junit.platform.config.core.PluginConfigProvider;
import org.graalvm.nativeimage.ImageSingletons;
import org.graalvm.nativeimage.hosted.Feature;
import org.graalvm.nativeimage.hosted.RuntimeClassInitialization;
import org.graalvm.nativeimage.hosted.RuntimeReflection;

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

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.ServiceLoader;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@SuppressWarnings("unused")
public final class JUnitPlatformFeature implements Feature {

    public final boolean debug = System.getProperty("debug") != null;
    private static final NativeImageConfigurationImpl nativeImageConfigImpl = new NativeImageConfigurationImpl();
    private final ServiceLoader<PluginConfigProvider> extensionConfigProviders = ServiceLoader.load(PluginConfigProvider.class);

    public static void debug(String format, Object... args) {
        if (debug()) {
            System.out.printf("[Debug] " + format + "%n", args);
        }
    }

    public static boolean debug() {
        return ImageSingletons.lookup(JUnitPlatformFeature.class).debug;
    }

    @Override
    public void duringSetup(DuringSetupAccess access) {
        forEachProvider(p -> p.onLoad(nativeImageConfigImpl));
    }

    @Override
    public void beforeAnalysis(BeforeAnalysisAccess access) {
        /* Before GraalVM version 22 we couldn't have classes initialized at run-time
           that are also used at build-time but not added to the image heap */
        if (Runtime.version().feature() <= 21) {
            RuntimeClassInitialization.initializeAtBuildTime("org.junit");
        }

        List<? extends DiscoverySelector> selectors = getSelectors();
        registerTestClassesForReflection(selectors);

        /* support for JUnit Vintage */
        registerClassesForHamcrestSupport(access);
    }

    private List<? extends DiscoverySelector> getSelectors() {
        try {
            Path uniqueIdDirectory = Paths.get(System.getProperty(UniqueIdTrackingListener.OUTPUT_DIR_PROPERTY_NAME));
            String uniqueIdFilePrefix = System.getProperty(UniqueIdTrackingListener.OUTPUT_FILE_PREFIX_PROPERTY_NAME,
                    UniqueIdTrackingListener.DEFAULT_OUTPUT_FILE_PREFIX);

            List<UniqueIdSelector> selectors = readAllFiles(uniqueIdDirectory, uniqueIdFilePrefix)
                    .map(DiscoverySelectors::selectUniqueId)
                    .collect(Collectors.toList());
            if (!selectors.isEmpty()) {
                System.out.printf(
                        "[junit-platform-native] Running in 'test listener' mode using files matching pattern [%s*] "
                                + "found in folder [%s] and its subfolders.%n",
                        uniqueIdFilePrefix, uniqueIdDirectory.toAbsolutePath());
                return selectors;
            }
        } catch (Exception ex) {
            debug("Failed to read UIDs from UniqueIdTrackingListener output files: " + ex.getMessage());
        }

        throw new RuntimeException("Cannot compute test selectors from test ids.");
    }

    /**
     * Use the JUnit Platform Launcher to register classes for reflection.
     */
    private void registerTestClassesForReflection(List<? extends DiscoverySelector> selectors) {
        LauncherDiscoveryRequest request = LauncherDiscoveryRequestBuilder.request()
                .selectors(selectors)
                .build();

        Launcher launcher = LauncherFactory.create();
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
    }

    private void registerTestClassForReflection(Class<?> clazz) {
        debug("Registering test class for reflection: %s", clazz.getName());
        nativeImageConfigImpl.registerAllClassMembersForReflection(clazz);
        forEachProvider(p -> p.onTestClassRegistered(clazz, nativeImageConfigImpl));

        Class<?>[] interfaces = clazz.getInterfaces();
        for (Class<?> inter : interfaces) {
            registerTestClassForReflection(inter);
        }

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

    private static void registerClassesForHamcrestSupport(BeforeAnalysisAccess access) {
        ClassLoader applicationLoader = access.getApplicationClassLoader();
        Class<?> typeSafeMatcher = findClassOrNull(applicationLoader, "org.hamcrest.TypeSafeMatcher");
        Class<?> typeSafeDiagnosingMatcher = findClassOrNull(applicationLoader, "org.hamcrest.TypeSafeDiagnosingMatcher");
        if (typeSafeMatcher != null || typeSafeDiagnosingMatcher != null) {
            BiConsumer<DuringAnalysisAccess, Class<?>> registerMatcherForReflection = (a, c) -> RuntimeReflection.register(c.getDeclaredMethods());
            if (typeSafeMatcher != null) {
                access.registerSubtypeReachabilityHandler(registerMatcherForReflection, typeSafeMatcher);
            }
            if (typeSafeDiagnosingMatcher != null) {
                access.registerSubtypeReachabilityHandler(registerMatcherForReflection, typeSafeDiagnosingMatcher);
            }
        }
    }

    private static Class<?> findClassOrNull(ClassLoader loader, String className) {
        try {
            return loader.loadClass(className);
        } catch (ClassNotFoundException e) {
            return null;
        }
    }
}
