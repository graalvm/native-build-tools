/*
 * Copyright (c) 2020, 2022, Oracle and/or its affiliates. All rights reserved.
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

package org.graalvm.buildtools.maven.config.agent;

import org.apache.maven.plugins.annotations.Parameter;

import java.util.List;

public class AgentOptionsConfiguration {

    @Parameter
    private List<String> callerFilterFiles;

    @Parameter
    private List<String> accessFilterFiles;

    @Parameter
    private boolean builtinCallerFilter;

    @Parameter
    private boolean builtinHeuristicFilter;

    @Parameter
    private boolean enableExperimentalPredefinedClasses;

    @Parameter
    private boolean enableExperimentalUnsafeAllocationTracing;

    @Parameter
    private boolean trackReflectionMetadata;

    public List<String> getCallerFilterFiles() {
        return callerFilterFiles;
    }

    public void setCallerFilterFiles(List<String> callerFilterFiles) {
        this.callerFilterFiles = callerFilterFiles;
    }

    public List<String> getAccessFilterFiles() {
        return accessFilterFiles;
    }

    public void setAccessFilterFiles(List<String> accessFilterFiles) {
        this.accessFilterFiles = accessFilterFiles;
    }

    public boolean isBuiltinCallerFilter() {
        return builtinCallerFilter;
    }

    public void setBuiltinCallerFilter(boolean builtinCallerFilter) {
        this.builtinCallerFilter = builtinCallerFilter;
    }

    public boolean isBuiltinHeuristicFilter() {
        return builtinHeuristicFilter;
    }

    public void setBuiltinHeuristicFilter(boolean builtinHeuristicFilter) {
        this.builtinHeuristicFilter = builtinHeuristicFilter;
    }

    public boolean isEnableExperimentalPredefinedClasses() {
        return enableExperimentalPredefinedClasses;
    }

    public void setEnableExperimentalPredefinedClasses(boolean enableExperimentalPredefinedClasses) {
        this.enableExperimentalPredefinedClasses = enableExperimentalPredefinedClasses;
    }

    public boolean isEnableExperimentalUnsafeAllocationTracing() {
        return enableExperimentalUnsafeAllocationTracing;
    }

    public void setEnableExperimentalUnsafeAllocationTracing(boolean enableExperimentalUnsafeAllocationTracing) {
        this.enableExperimentalUnsafeAllocationTracing = enableExperimentalUnsafeAllocationTracing;
    }

    public boolean isTrackReflectionMetadata() {
        return trackReflectionMetadata;
    }

    public void setTrackReflectionMetadata(boolean trackReflectionMetadata) {
        this.trackReflectionMetadata = trackReflectionMetadata;
    }
}
