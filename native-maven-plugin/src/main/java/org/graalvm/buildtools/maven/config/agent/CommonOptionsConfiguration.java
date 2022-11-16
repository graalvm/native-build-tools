package org.graalvm.buildtools.maven.config.agent;

import org.apache.maven.plugins.annotations.Parameter;

import java.util.List;

public class CommonOptionsConfiguration {

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
