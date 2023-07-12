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
import org.graalvm.nativeimage.ImageInfo;
import org.graalvm.nativeimage.ImageSingletons;
import org.graalvm.nativeimage.hosted.Feature;
import org.graalvm.nativeimage.hosted.RuntimeClassInitialization;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.ServiceLoader;
import java.util.function.Consumer;

@SuppressWarnings("unused")
public final class JUnitPlatformFeature implements Feature {

    public static final boolean debug = System.getProperty(TestsDiscoveryHelper.DEBUG) != null;

    private static final NativeImageConfigurationImpl nativeImageConfigImpl = new NativeImageConfigurationImpl();
    private final ServiceLoader<PluginConfigProvider> extensionConfigProviders = ServiceLoader.load(PluginConfigProvider.class);

    @Override
    public void duringSetup(DuringSetupAccess access) {
        forEachProvider(p -> p.onLoad(nativeImageConfigImpl));
    }

    @Override
    public void beforeAnalysis(BeforeAnalysisAccess access) {
        RuntimeClassInitialization.initializeAtBuildTime(NativeImageJUnitLauncher.class);
        List<Path> classpathRoots = access.getApplicationClassPath();
        List<Class<?>> discoveredTests;
        if (Boolean.parseBoolean(System.getProperty("isolateTestDiscovery"))) {
            List<String> discoveredTestNames = TestsDiscoveryHelper.launchTestDiscovery(debug, classpathRoots);
            discoveredTests = new ArrayList<>();
            for (String discoveredTestName : discoveredTestNames) {
                try {
                    discoveredTests.add(Class.forName(discoveredTestName, false, access.getApplicationClassLoader()));
                } catch (ClassNotFoundException e) {
                    throw new RuntimeException(e);
                }
            }
        } else {
            TestsDiscoveryHelper helper = new TestsDiscoveryHelper(debug, classpathRoots);
            discoveredTests = helper.discoverTests();
        }
        for (Class<?> discoveredTest : discoveredTests) {
            registerTestClassForReflection(discoveredTest);
        }

        ImageSingletons.add(NativeImageJUnitLauncher.class,
                new NativeImageJUnitLauncher(new TestsDiscoveryHelper(debug, classpathRoots)));
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
        if (!ImageInfo.inImageCode()) {
            return debug;
        } else {
            return ImageSingletons.lookup(JUnitPlatformFeature.class).debug;
        }
    }

}
