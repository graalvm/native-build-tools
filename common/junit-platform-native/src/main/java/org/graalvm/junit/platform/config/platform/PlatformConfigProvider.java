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

package org.graalvm.junit.platform.config.platform;

import org.graalvm.junit.platform.config.core.NativeImageConfiguration;
import org.graalvm.junit.platform.config.core.PluginConfigProvider;

public class PlatformConfigProvider implements PluginConfigProvider {

    @Override
    public void onLoad(NativeImageConfiguration config) {
        config.initializeAtBuildTime(
                "org.junit.platform.launcher.TestIdentifier",
                "org.junit.platform.launcher.core.InternalTestPlan",
                "org.junit.platform.commons.util.StringUtils",
                "org.junit.platform.launcher.core.TestExecutionListenerRegistry",
                "org.junit.platform.commons.logging.LoggerFactory$DelegatingLogger",
                "org.junit.platform.launcher.core.EngineDiscoveryOrchestrator",
                "org.junit.platform.launcher.core.LauncherConfigurationParameters",
                "org.junit.platform.commons.logging.LoggerFactory",
                "org.junit.platform.engine.UniqueIdFormat",
                "org.junit.platform.commons.util.ReflectionUtils",
                // https://github.com/graalvm/native-build-tools/issues/300
                "org.junit.platform.reporting.open.xml.OpenTestReportGeneratingListener"
        );

        if (getMajorJDKVersion() >= 21) {
            /* new with --strict-image-heap */
            config.initializeAtBuildTime(
                    "org.junit.platform.engine.support.descriptor.ClassSource",
                    "org.junit.platform.engine.support.descriptor.MethodSource",
                    "org.junit.platform.engine.support.hierarchical.Node$ExecutionMode",
                    "org.junit.platform.engine.TestDescriptor$Type",
                    "org.junit.platform.engine.UniqueId",
                    "org.junit.platform.engine.UniqueId$Segment",
                    "org.junit.platform.launcher.core.DefaultLauncher",
                    "org.junit.platform.launcher.core.DefaultLauncherConfig",
                    "org.junit.platform.launcher.core.EngineExecutionOrchestrator",
                    "org.junit.platform.launcher.core.LauncherConfigurationParameters$ParameterProvider$1",
                    "org.junit.platform.launcher.core.LauncherConfigurationParameters$ParameterProvider$2",
                    "org.junit.platform.launcher.core.LauncherConfigurationParameters$ParameterProvider$3",
                    "org.junit.platform.launcher.core.LauncherConfigurationParameters$ParameterProvider$4",
                    "org.junit.platform.launcher.core.LauncherDiscoveryResult",
                    "org.junit.platform.launcher.core.LauncherListenerRegistry",
                    "org.junit.platform.launcher.core.ListenerRegistry",
                    "org.junit.platform.launcher.core.SessionPerRequestLauncher",
                    "org.junit.platform.launcher.LauncherSessionListener$1",
                    "org.junit.platform.launcher.listeners.UniqueIdTrackingListener",
                    "org.junit.platform.reporting.shadow.org.opentest4j.reporting.events.api.DocumentWriter$1",
                    "org.junit.platform.suite.engine.SuiteEngineDescriptor",
                    "org.junit.platform.suite.engine.SuiteLauncher",
                    "org.junit.platform.suite.engine.SuiteTestDescriptor",
                    "org.junit.platform.suite.engine.SuiteTestEngine"
            );
        }

        try {
            /* Verify if the core JUnit Platform test class is available on the classpath */
            Class.forName("org.junit.platform.commons.annotation.Testable");
        } catch (ClassNotFoundException e) {
            throw new RuntimeException("Missing some JUnit Platform classes for runtime reflection configuration. \n" +
                    "Check if JUnit Platform is on your classpath or if that version is supported. \n" +
                    "Original error: " + e);
        }
    }

    @Override
    public void onTestClassRegistered(Class<?> testClass, NativeImageConfiguration registry) {
    }
}
