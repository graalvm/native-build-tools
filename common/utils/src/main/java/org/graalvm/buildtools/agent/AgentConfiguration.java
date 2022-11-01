/*
 * Copyright (c) 2022, 2022 Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.buildtools.agent;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

public class AgentConfiguration implements Serializable {

    private final Collection<String> callerFilterFiles;
    private final Collection<String> accessFilterFiles;
    private final boolean builtinCallerFilter;
    private final boolean builtinHeuristicFilter;
    private final boolean experimentalPredefinedClasses;
    private final boolean experimentalUnsafeAllocationTracing;
    private final boolean trackReflectionMetadata;

    private final AgentMode agentMode;

    // This constructor should be used only to specify that we have instance of agent that is disabled (to avoid using null for agent enable check)
    public AgentConfiguration() {
        this.callerFilterFiles = null;
        this.accessFilterFiles = null;
        this.builtinCallerFilter = false;
        this.builtinHeuristicFilter = false;
        this.experimentalPredefinedClasses = false;
        this.experimentalUnsafeAllocationTracing = false;
        this.trackReflectionMetadata = false;
        this.agentMode = new DisabledAgentMode();
    }

    public AgentConfiguration(Collection<String> callerFilterFiles,
                              Collection<String> accessFilterFiles,
                              boolean builtinCallerFilter,
                              boolean builtinHeuristicFilter,
                              boolean experimentalPredefinedClasses,
                              boolean experimentalUnsafeAllocationTracing,
                              boolean trackReflectionMetadata,
                              AgentMode agentMode) {
        this.callerFilterFiles = callerFilterFiles;
        this.accessFilterFiles = accessFilterFiles;
        this.builtinCallerFilter = builtinCallerFilter;
        this.builtinHeuristicFilter = builtinHeuristicFilter;
        this.experimentalPredefinedClasses = experimentalPredefinedClasses;
        this.experimentalUnsafeAllocationTracing = experimentalUnsafeAllocationTracing;
        this.trackReflectionMetadata = trackReflectionMetadata;
        this.agentMode = agentMode;
    }

    public List<String> getAgentCommandLine() {
        List<String> cmdLine = new ArrayList<>(agentMode.getAgentCommandLine());
        appendOptionToValues("caller-filter-file=", callerFilterFiles, cmdLine);
        appendOptionToValues("access-filter-file=", accessFilterFiles, cmdLine);
        cmdLine.add("builtin-caller-filter=" + builtinCallerFilter);
        cmdLine.add("builtin-heuristic-filter=" + builtinHeuristicFilter);
        cmdLine.add("experimental-class-define-support=" + experimentalPredefinedClasses);
        cmdLine.add("experimental-unsafe-allocation-support=" + experimentalUnsafeAllocationTracing);
        cmdLine.add("track-reflection-metadata=" + trackReflectionMetadata);
        return cmdLine;
    }

    public Collection<String> getAgentFiles() {
        List<String> files = new ArrayList<>(callerFilterFiles.size() + accessFilterFiles.size());
        files.addAll(callerFilterFiles);
        files.addAll(accessFilterFiles);
        files.addAll(agentMode.getInputFiles());
        return files;
    }

    public boolean isEnabled() {
        return !(agentMode instanceof DisabledAgentMode);
    }

    public static void appendOptionToValues(String option, Collection<String> values, Collection<String> target) {
        values.stream().map(value -> option + value).forEach(target::add);
    }

    public AgentMode getAgentMode() {
        return agentMode;
    }
}
