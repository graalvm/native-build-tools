/*
 * Copyright (c) 2020, 2022 Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.buildtools.utils;

import org.apache.maven.execution.MavenSession;
import org.codehaus.plexus.util.xml.Xpp3Dom;
import org.graalvm.buildtools.agent.*;

import java.util.ArrayList;
import java.util.stream.Collectors;

import static org.graalvm.buildtools.utils.Utils.parseBoolean;

public final class AgentUtils {

    private AgentUtils() {}

    public static AgentMode getAgentMode(Xpp3Dom agent) throws Exception {
        Xpp3Dom defaultModeNode = Xpp3DomParser.getTagByName(agent, "defaultMode");
        if (defaultModeNode == null) {
            throw new RuntimeException("Default agent mode not provided in configuration.");
        }

        AgentMode agentMode;
        String mode = defaultModeNode.getValue();
        switch (mode) {
            case "standard":
                agentMode = new StandardAgentMode();
                break;
            case "disabled":
                agentMode = new DisabledAgentMode();
                break;
            case "conditional":
                // conditional mode needs few more options declared in xml
                Xpp3Dom userCodeFilterPathNode = Xpp3DomParser.getTagByName(agent, "userCodeFilterPath");
                Xpp3Dom extraFilterPathNode = Xpp3DomParser.getTagByName(agent, "extraFilterPath");

                if (userCodeFilterPathNode == null) {
                    throw new Exception("UserCodeFilterPath must be provided in agent configuration");
                }

                agentMode = new ConditionalAgentMode(userCodeFilterPathNode.getValue(),
                                                    extraFilterPathNode != null ? extraFilterPathNode.getValue() : "",
                                                     parseBooleanNode(agent, "parallel"));
                break;
            case "direct":
                ArrayList<String> options = new ArrayList<>();

                Xpp3Dom directModeNode = Xpp3DomParser.getTagByName(agent, "direct");
                if (directModeNode != null) {
                    // if user provided options for direct mode
                    Xpp3DomParser.getAllTagsByName(directModeNode, "option")
                            .stream()
                            .map(Xpp3Dom::getValue)
                            .forEach(options::add);
                }

                agentMode = new DirectAgentMode(options);
                break;
            default:
                throw new Exception("Unknown agent mode selected: " + mode);
        }

        return agentMode;
    }

    public static AgentConfiguration collectAgentProperties(MavenSession session, Xpp3Dom rootNode) {
        Xpp3Dom agent = Xpp3DomParser.getTagByName(rootNode, "agent");
        if (agent == null) {
            return new AgentConfiguration();
        }

        ArrayList<String> callerFilterFiles = getFilterFiles(agent, "callerFilterFiles");
        ArrayList<String> accessFilterFiles = getFilterFiles(agent, "accessFilterFiles");
        boolean builtinCallerFilter = parseBooleanNode(agent, "builtinCallerFilter");
        boolean builtinHeuristicFilter = parseBooleanNode(agent, "builtinHeuristicFilter");
        boolean enableExperimentalPredefinedClasses = parseBooleanNode(agent, "enableExperimentalPredefinedClasses");
        boolean enableExperimentalUnsafeAllocationTracing = parseBooleanNode(agent, "enableExperimentalUnsafeAllocationTracing");
        boolean trackReflectionMetadata = parseBooleanNode(agent, "trackReflectionMetadata");

        AgentMode mode;
        try {
             mode = getAgentMode(agent);
        } catch (Exception e) {
            throw new RuntimeException("Agent mode cannot be determined. Reason:" + e.getMessage());
        }

        return isAgentEnabled(session, agent) ? new AgentConfiguration(callerFilterFiles, accessFilterFiles, builtinCallerFilter,
                builtinHeuristicFilter, enableExperimentalPredefinedClasses, enableExperimentalUnsafeAllocationTracing,
                trackReflectionMetadata, mode) : new AgentConfiguration();
    }

    public static ArrayList<String> getDisabledPhases(Xpp3Dom rootNode) {
        ArrayList<String> disabledPhases = new ArrayList<>();

        Xpp3Dom agent = Xpp3DomParser.getTagByName(rootNode, "agent");
        if (agent != null) {
            Xpp3Dom disabledPhasesNode = Xpp3DomParser.getTagByName(agent, "disabledPhases");
            if (disabledPhasesNode != null){
                Xpp3DomParser.getAllTagsByName(disabledPhasesNode, "phase").forEach(phaseNode -> disabledPhases.add(phaseNode.getValue()));
            }
        }

        return disabledPhases;
    }

    private static boolean isAgentEnabled(MavenSession session, Xpp3Dom agent) {
        String systemProperty = session.getSystemProperties().getProperty("agent");
        if (systemProperty != null) {
            // -Dagent=[true|false] overrides configuration in the POM.
            return parseBoolean("agent system property", systemProperty);
        }
        return parseBooleanNode(agent, "enabled");
    }

    private static ArrayList<String> getFilterFiles(Xpp3Dom agent, String type) {
        return Xpp3DomParser.getAllTagsByName(agent, type)
                .stream()
                .map(Xpp3Dom::getValue)
                .collect(Collectors.toCollection(ArrayList::new));
    }

    private static boolean parseBooleanNode(Xpp3Dom root, String name) {
        Xpp3Dom node = Xpp3DomParser.getTagByName(root, name);
        if (node == null) {
            // if node is not provided, default value is false
            return false;
        }

        return Utils.parseBoolean("<" + name + ">", node.getValue());
    }
}
