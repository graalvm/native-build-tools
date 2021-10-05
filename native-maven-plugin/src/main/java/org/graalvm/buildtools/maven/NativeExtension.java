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
import org.apache.maven.model.PluginExecution;
import org.apache.maven.plugin.MojoExecutionException;
import org.codehaus.plexus.component.annotations.Component;
import org.codehaus.plexus.util.xml.Xpp3Dom;
import org.graalvm.buildtools.Utils;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Properties;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

/**
 * This extension is responsible for configuring the Surefire plugin to enable
 * the JUnit Platform test listener and registering the native dependency transparently.
 */
@Component(role = AbstractMavenLifecycleParticipant.class, hint = "native-build-tools")
public class NativeExtension extends AbstractMavenLifecycleParticipant {

    private static final String JUNIT_PLATFORM_LISTENERS_UID_TRACKING_ENABLED = "junit.platform.listeners.uid.tracking.enabled";
    private static final String JUNIT_PLATFORM_LISTENERS_UID_TRACKING_OUTPUT_DIR = "junit.platform.listeners.uid.tracking.output.dir";
    private static final String NATIVEIMAGE_IMAGECODE = "org.graalvm.nativeimage.imagecode";

    static String testIdsDirectory(String baseDir) {
        return baseDir + File.separator + "test-ids";
    }

    static String buildAgentOption(String context, String baseDir) {
        String subdir = agentOutputDirectoryFor(context, baseDir);
        return "-agentlib:native-image-agent=experimental-class-loader-support," +
                "config-output-dir=" + subdir;
    }

    private static String agentOutputDirectoryFor(String context, String baseDir) {
        return (baseDir + "/native/agent-output/" + context).replace('/', File.separatorChar);
    }

    @Override
    public void afterProjectsRead(MavenSession session) throws MavenExecutionException {
        Build build = session.getCurrentProject().getBuild();
        boolean hasAgent = hasAgent(session);
        String target = build.getDirectory();
        String testIdsDir = testIdsDirectory(target);
        withPlugin(build, "maven-surefire-plugin", surefirePlugin -> {
            configureJunitListener(surefirePlugin, testIdsDir);
            if (hasAgent) {
                configureAgentForSurefire(surefirePlugin, "test", target);
            }
        });
        if (hasAgent) {
            withPlugin(build, "exec-maven-plugin", execPlugin ->
                    updatePluginConfiguration(execPlugin, (exec, config) -> {
                        if ("java-agent".equals(exec.getId())) {
                            Xpp3Dom commandlineArgs = findOrAppend(config, "arguments");
                            Xpp3Dom[] arrayOfChildren = commandlineArgs.getChildren();
                            for (int i = 0; i < arrayOfChildren.length; i++) {
                                commandlineArgs.removeChild(0);
                            }
                            List<Xpp3Dom> children = new ArrayList<>();
                            Collections.addAll(children, arrayOfChildren);
                            Xpp3Dom arg = new Xpp3Dom("argument");
                            arg.setValue(buildAgentOption("exec", target));
                            children.add(0, arg);
                            arg = new Xpp3Dom("argument");
                            arg.setValue("-D" + NATIVEIMAGE_IMAGECODE + "=agent");
                            children.add(1, arg);
                            for (Xpp3Dom child : children) {
                                commandlineArgs.addChild(child);
                            }
                            Xpp3Dom executable = findOrAppend(config, "executable");
                            try {
                                executable.setValue(Utils.getNativeImage().getParent().resolve("java").toString());
                            } catch (MojoExecutionException e) {
                                throw new RuntimeException(e);
                            }
                        }
                    })
            );
            withPlugin(build, "native-maven-plugin", nativePlugin ->
                    updatePluginConfiguration(nativePlugin, (exec, configuration) -> {
                        String context = exec.getGoals().stream().anyMatch("test"::equals) ? "test" : "exec";
                        Xpp3Dom agentResourceDirectory = findOrAppend(configuration, "agentResourceDirectory");
                        agentResourceDirectory.setValue(agentOutputDirectoryFor(context, target));
                    })
            );
        }
    }

    private static boolean hasAgent(MavenSession session) {
        Properties systemProperties = session.getSystemProperties();
        return Boolean.parseBoolean(String.valueOf(systemProperties.getOrDefault("agent", "false")));
    }

    private static void withPlugin(Build build, String artifactId, Consumer<? super Plugin> consumer) {
        build.getPlugins()
                .stream()
                .filter(p -> artifactId.equals(p.getArtifactId()))
                .findFirst()
                .ifPresent(consumer);
    }

    private static void configureAgentForSurefire(Plugin surefirePlugin, String context, String targetDir) {
        updatePluginConfiguration(surefirePlugin, (exec, configuration) -> {
            Xpp3Dom systemProperties = findOrAppend(configuration, "systemProperties");
            Xpp3Dom agent = findOrAppend(systemProperties, NATIVEIMAGE_IMAGECODE);
            agent.setValue("agent");
            Xpp3Dom argLine = new Xpp3Dom("argLine");
            argLine.setValue(buildAgentOption(context, targetDir));
            configuration.addChild(argLine);
        });
    }

    private static void configureJunitListener(Plugin surefirePlugin, String testIdsDir) {
        updatePluginConfiguration(surefirePlugin, (exec, configuration) -> {
            Xpp3Dom systemProperties = findOrAppend(configuration, "systemProperties");
            Xpp3Dom junitTracking = findOrAppend(systemProperties, JUNIT_PLATFORM_LISTENERS_UID_TRACKING_ENABLED);
            Xpp3Dom testIdsProperty = findOrAppend(systemProperties, JUNIT_PLATFORM_LISTENERS_UID_TRACKING_OUTPUT_DIR);
            junitTracking.setValue("true");
            testIdsProperty.setValue(testIdsDir);
        });
    }

    private static void updatePluginConfiguration(Plugin plugin, BiConsumer<PluginExecution, ? super Xpp3Dom> consumer) {
        plugin.getExecutions().forEach(exec -> {
            Xpp3Dom configuration = (Xpp3Dom) exec.getConfiguration();
            if (configuration == null) {
                configuration = new Xpp3Dom("configuration");
                exec.setConfiguration(configuration);
            }
            consumer.accept(exec, configuration);
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
