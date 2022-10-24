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
import org.apache.maven.execution.MavenSession;
import org.apache.maven.model.Build;
import org.apache.maven.model.Plugin;
import org.apache.maven.model.PluginExecution;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.project.MavenProject;
import org.codehaus.plexus.component.annotations.Component;
import org.codehaus.plexus.logging.LogEnabled;
import org.codehaus.plexus.logging.Logger;
import org.codehaus.plexus.util.xml.Xpp3Dom;
import org.graalvm.buildtools.Utils;
import org.graalvm.buildtools.utils.SharedConstants;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

/**
 * This extension is responsible for configuring the Surefire plugin to enable
 * the JUnit Platform test listener and registering the native dependency transparently.
 */
@Component(role = AbstractMavenLifecycleParticipant.class, hint = "native-build-tools")
public class NativeExtension extends AbstractMavenLifecycleParticipant implements LogEnabled {

    private static final String JUNIT_PLATFORM_LISTENERS_UID_TRACKING_ENABLED = "junit.platform.listeners.uid.tracking.enabled";
    private static final String JUNIT_PLATFORM_LISTENERS_UID_TRACKING_OUTPUT_DIR = "junit.platform.listeners.uid.tracking.output.dir";
    private static final String NATIVEIMAGE_IMAGECODE = "org.graalvm.nativeimage.imagecode";

    private static Logger logger;

    @Override
    public void enableLogging(Logger logger) {
        this.logger = logger;
    }

    /**
     * Enumeration of execution contexts.
     * <p>Enum constants are intentionally lowercase for use as directory names
     * and within the Maven POM as values of the {@code name} attribute in
     * {@code <options name="...">}.
     */
    enum Context {main, test}

    static String testIdsDirectory(String baseDir) {
        return baseDir + File.separator + "test-ids";
    }

    private static String graalvmJava;

    static String buildAgentArgument(String baseDir, Context context, List<String> agentOptions) {
        List<String> options = new ArrayList<>(agentOptions);
        String effectiveOutputDir = agentOutputDirectoryFor(baseDir, context);
        if (context == Context.test) {
            // We need to patch the config dir IF, and only IF, we are running tests, because
            // test execution can be forked into a separate process and there's a race condition.
            // We have to special case testing here instead of using a generic strategy THEN
            // invoke the merging tool, because there's no way in Maven to do something as easy
            // as finalizing a goal (that is, let me do the merge AFTER you're done executing tests,
            // or invoking exec, or whatever, because what I need to do actually participates into
            // the same unit of work !).
            effectiveOutputDir = effectiveOutputDir + File.separator + SharedConstants.AGENT_SESSION_SUBDIR;
        }
        options.add("config-output-dir=" + effectiveOutputDir);
        return "-agentlib:native-image-agent=" + String.join(",", options);
    }

    static String agentOutputDirectoryFor(String baseDir, Context context) {
        return (baseDir + "/native/agent-output/" + context).replace('/', File.separatorChar);
    }

    @Override
    public void afterProjectsRead(MavenSession session) {
        try {
            graalvmJava = Utils.getNativeImage(logger).getParent().resolve("java").toString();
        } catch (MojoExecutionException e) {
            throw new RuntimeException(e);
        }

        for (MavenProject project : session.getProjects()) {
            Build build = project.getBuild();
            withPlugin(build, "native-maven-plugin", nativePlugin -> {
                String target = build.getDirectory();
                String testIdsDir = testIdsDirectory(target);
                boolean isAgentEnabled = isAgentEnabled(session, nativePlugin);
                String selectedOptionsName = getSelectedOptionsName(session);

                // Test configuration
                withPlugin(build, "maven-surefire-plugin", surefirePlugin -> {
                    configureJunitListener(surefirePlugin, testIdsDir);
                    if (isAgentEnabled) {
                        List<String> agentOptions = getAgentOptions(nativePlugin, Context.test, selectedOptionsName);
                        configureAgentForSurefire(surefirePlugin, buildAgentArgument(target, Context.test, agentOptions));
                    }
                });

                // Main configuration
                if (isAgentEnabled) {
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

                                    // Agent argument
                                    Xpp3Dom arg = new Xpp3Dom("argument");
                                    List<String> agentOptions = getAgentOptions(nativePlugin, Context.main, selectedOptionsName);
                                    arg.setValue(buildAgentArgument(target, Context.main, agentOptions));
                                    children.add(0, arg);

                                    // System property for org.graalvm.nativeimage.imagecode
                                    arg = new Xpp3Dom("argument");
                                    arg.setValue("-D" + NATIVEIMAGE_IMAGECODE + "=agent");
                                    children.add(1, arg);

                                    for (Xpp3Dom child : children) {
                                        commandlineArgs.addChild(child);
                                    }
                                    findOrAppend(config, "executable").setValue(graalvmJava);
                                }
                            })
                    );
                    updatePluginConfiguration(nativePlugin, (exec, configuration) -> {
                        Context context = exec.getGoals().stream().anyMatch("test"::equals) ? Context.test : Context.main;
                        Xpp3Dom agentResourceDirectory = findOrAppend(configuration, "agentResourceDirectory");
                        agentResourceDirectory.setValue(agentOutputDirectoryFor(target, context));
                        if (context == Context.test) {
                            setupMergeAgentFiles(exec, configuration, context);
                        }
                    });
                }
            });
        }
    }

    private static void setupMergeAgentFiles(PluginExecution exec, Xpp3Dom configuration, Context context) {
        List<String> goals = new ArrayList<>();
        goals.add("merge-agent-files");
        goals.addAll(exec.getGoals());
        exec.setGoals(goals);
        Xpp3Dom agentContext = findOrAppend(configuration, "context");
        agentContext.setValue(context.name());
    }

    private static void withPlugin(Build build, String artifactId, Consumer<? super Plugin> consumer) {
        build.getPlugins()
                .stream()
                .filter(p -> artifactId.equals(p.getArtifactId()))
                .findFirst()
                .ifPresent(consumer);
    }

    private static boolean isAgentEnabled(MavenSession session, Plugin nativePlugin) {
        String systemProperty = session.getSystemProperties().getProperty("agent");
        if (systemProperty != null) {
            // -Dagent=[true|false] overrides configuration in the POM.
            return parseBoolean("agent system property", systemProperty);
        }

        Xpp3Dom agent = getAgentNode(nativePlugin);
        if (agent != null) {
            Xpp3Dom enabled = agent.getChild("enabled");
            if (enabled != null) {
                return parseBoolean("<enabled>", enabled.getValue());
            }
        }

        return false;
    }

    private static String getSelectedOptionsName(MavenSession session) {
        String selectedOptionsName = session.getSystemProperties().getProperty("agentOptions");
        if (selectedOptionsName == null) {
            return null;
        }
        return assertNotEmptyAndTrim(selectedOptionsName, "agentOptions system property must have a value");
    }

    /**
     * Parses the configuration block for the {@code native-maven-plugin}, searching for
     * {@code <options>} elements whose names match the name of the supplied {@code context}
     * or {@code selectedOptionsName} and for unnamed, shared {@code <options>} elements,
     * and returns a list of the collected agent options.
     *
     * @param nativePlugin the {@code native-maven-plugin}; never null
     * @param context the current execution context; never null
     * @param selectedOptionsName the name of the set of custom agent options activated
     * by the user via the {@code agentOptions} system property; may be null if not
     * supplied via a system property
     */
    private static List<String> getAgentOptions(Plugin nativePlugin, Context context, String selectedOptionsName) {
        // <configuration>
        //     <agent>
        //         <enabled>true</enabled>
        //         <options>
        //             <option>experimental-class-loader-support</option>
        //         </options>
        //         <options name="main">
        //             <option>access-filter-file=${basedir}/src/main/resources/access-filter.json</option>
        //         </options>
        //         <options name="test">
        //             <option>access-filter-file=${basedir}/src/test/resources/access-filter.json</option>
        //         </options>
        //         <options name="periodic-config">
        //             <option>config-write-period-secs=30</option>
        //             <option>config-write-initial-delay-secs=5</option>
        //         </options>
        //     </agent>
        // </configuration>

        List<String> optionsList = new ArrayList<>();
        Xpp3Dom agent = getAgentNode(nativePlugin);
        if (agent != null) {
            for (Xpp3Dom options : agent.getChildren("options")) {
                String name = options.getAttribute("name");
                if (name != null) {
                    name = assertNotEmptyAndTrim(name, "<options> must declare a non-empty name attribute or omit the name attribute");
                }
                // If unnamed/shared options, or options for the current context (main/test), or user-selected options:
                if (name == null || name.equals(context.name()) || name.equals(selectedOptionsName)) {
                    processOptionNodes(options, optionsList);
                }
            }
        }
        return optionsList;
    }

    private static void processOptionNodes(Xpp3Dom options, List<String> optionsList) {
        for (Xpp3Dom option : options.getChildren("option")) {
            String value = assertNotEmptyAndTrim(option.getValue(), "<option> must declare a value");
            if (value.contains("config-output-dir")) {
                throw new IllegalStateException("config-output-dir cannot be supplied as an agent option");
            }
            optionsList.add(value);
        }
    }

    private static boolean parseBoolean(String description, String value) {
        value = assertNotEmptyAndTrim(value, description + " must have a value").toLowerCase();
        switch (value) {
            case "true":
                return true;
            case "false":
                return false;
            default:
                throw new IllegalStateException(description + " must have a value of 'true' or 'false'");
        }
    }

    private static String assertNotEmptyAndTrim(String input, String message) {
        if (input == null || input.isEmpty()) {
            throw new IllegalStateException(message);
        }
        return input.trim();
    }

    private static Xpp3Dom getAgentNode(Plugin nativePlugin) {
        Xpp3Dom configuration = (Xpp3Dom) nativePlugin.getConfiguration();
        if (configuration != null) {
            return configuration.getChild("agent");
        }
        return null;
    }

    private static void configureAgentForSurefire(Plugin surefirePlugin, String agentArgument) {
        updatePluginConfiguration(surefirePlugin, (exec, configuration) -> {
            Xpp3Dom systemProperties = findOrAppend(configuration, "systemProperties");
            Xpp3Dom agent = findOrAppend(systemProperties, NATIVEIMAGE_IMAGECODE);
            agent.setValue("agent");
            Xpp3Dom argLine = new Xpp3Dom("argLine");
            argLine.setValue(agentArgument);
            configuration.addChild(argLine);
            findOrAppend(configuration, "jvm").setValue(graalvmJava);
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
            Xpp3Dom configuration = configurationBlockOf(exec);
            consumer.accept(exec, configuration);
        });
    }

    private static Xpp3Dom configurationBlockOf(PluginExecution exec) {
        Xpp3Dom configuration = (Xpp3Dom) exec.getConfiguration();
        if (configuration == null) {
            configuration = new Xpp3Dom("configuration");
            exec.setConfiguration(configuration);
        }
        return configuration;
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
