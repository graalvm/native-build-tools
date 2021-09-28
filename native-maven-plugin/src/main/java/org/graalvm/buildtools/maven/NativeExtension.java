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
package org.graalvm.buildtools.maven;

import org.apache.maven.AbstractMavenLifecycleParticipant;
import org.apache.maven.MavenExecutionException;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.model.Build;
import org.apache.maven.model.Plugin;
import org.codehaus.plexus.component.annotations.Component;
import org.codehaus.plexus.util.xml.Xpp3Dom;

import java.io.File;

/**
 * This extension is responsible for configuring the Surefire plugin to enable
 * the JUnit Platform test listener and registering the native dependency transparently.
 */
@Component(role = AbstractMavenLifecycleParticipant.class, hint = "native-build-tools")
public class NativeExtension extends AbstractMavenLifecycleParticipant {

    private static final String JUNIT_PLATFORM_LISTENERS_UID_TRACKING_ENABLED = "junit.platform.listeners.uid.tracking.enabled";
    private static final String JUNIT_PLATFORM_LISTENERS_UID_TRACKING_OUTPUT_DIR = "junit.platform.listeners.uid.tracking.output.dir";

    static String testIdsDirectory(String baseDir) {
        return baseDir + File.separator + "test-ids";
    }

    @Override
    public void afterProjectsRead(MavenSession session) throws MavenExecutionException {
        Build build = session.getCurrentProject()
                .getBuild();
        String target = build.getDirectory();
        String testIdsDir = testIdsDirectory(target);
        build.getPlugins()
                .stream()
                .filter(p -> "maven-surefire-plugin".equals(p.getArtifactId()))
                .findFirst()
                .ifPresent(surefirePlugin -> configureJunitListener(surefirePlugin, testIdsDir));
    }

    private static void configureJunitListener(Plugin surefirePlugin, String testIdsDir) {
        surefirePlugin.getExecutions().forEach(exec -> {
            Xpp3Dom configuration = (Xpp3Dom) exec.getConfiguration();
            if (configuration == null) {
                configuration = new Xpp3Dom("configuration");
                exec.setConfiguration(configuration);
            }
            Xpp3Dom systemProperties = findOrAppend(configuration, "systemProperties");
            Xpp3Dom junitTracking = findOrAppend(systemProperties, JUNIT_PLATFORM_LISTENERS_UID_TRACKING_ENABLED);
            Xpp3Dom testIdsProperty = findOrAppend(systemProperties, JUNIT_PLATFORM_LISTENERS_UID_TRACKING_OUTPUT_DIR);
            junitTracking.setValue("true");
            testIdsProperty.setValue(testIdsDir);
        });
    }

    private static Xpp3Dom findOrAppend(Xpp3Dom parent, String childName) {
        Xpp3Dom child = parent.getChild(childName);
        if (child == null) {
            child = new Xpp3Dom(childName);
            parent.addChild(child);
        }
        return child;
    }

}
