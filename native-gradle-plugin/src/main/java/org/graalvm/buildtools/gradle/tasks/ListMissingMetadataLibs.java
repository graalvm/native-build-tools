/*
 * Copyright (c) 2026, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.buildtools.gradle.tasks;

import org.graalvm.buildtools.gradle.internal.GraalVMReachabilityMetadataService;
import org.graalvm.reachability.MissingMetadataCommandSupport;
import org.gradle.api.DefaultTask;
import org.gradle.api.GradleException;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.artifacts.ModuleVersionIdentifier;
import org.gradle.api.artifacts.component.ModuleComponentIdentifier;
import org.gradle.api.artifacts.result.DependencyResult;
import org.gradle.api.artifacts.result.ResolvedComponentResult;
import org.gradle.api.artifacts.result.ResolvedDependencyResult;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.provider.MapProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.provider.SetProperty;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.Internal;
import org.gradle.api.tasks.Optional;
import org.gradle.api.tasks.OutputFile;
import org.gradle.api.tasks.TaskAction;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public abstract class ListMissingMetadataLibs extends DefaultTask {
    public ListMissingMetadataLibs() {
        getOutputs().upToDateWhen(task -> false);
    }

    public void setClasspath(Configuration classpath) {
        getRootComponent().set(classpath.getIncoming().getResolutionResult().getRootComponent());
    }

    @Input
    @Optional
    protected abstract Property<ResolvedComponentResult> getRootComponent();

    @Internal
    public abstract Property<GraalVMReachabilityMetadataService> getMetadataService();

    @Input
    public abstract Property<Boolean> getMetadataRepositoryEnabled();

    @Input
    @Optional
    public abstract Property<String> getMetadataRepositoryUri();

    @Input
    public abstract Property<Boolean> getCreateIssues();

    @Internal
    public abstract Property<String> getGithubToken();

    @Input
    public abstract Property<String> getTargetRepository();

    @Input
    public abstract Property<String> getGithubApiUrl();

    @Input
    public abstract Property<String> getProjectName();

    @Input
    @Optional
    public abstract SetProperty<String> getExcludedModules();

    @Input
    @Optional
    public abstract MapProperty<String, String> getModuleToConfigVersion();

    @OutputFile
    public abstract RegularFileProperty getReportFile();

    @TaskAction
    void listMissingMetadataLibs() throws IOException {
        if (!Boolean.TRUE.equals(getMetadataRepositoryEnabled().get())) {
            throw new GradleException("GraalVM reachability metadata repository is disabled.");
        }
        if (!getRootComponent().isPresent()) {
            throw new GradleException("Runtime classpath resolution result is unavailable.");
        }
        GraalVMReachabilityMetadataService service = getMetadataService().get();
        List<MissingMetadataCommandSupport.DependencyCoordinate> dependencies = directExternalRuntimeDependencies(getRootComponent().get());
        MissingMetadataCommandSupport.Report report = MissingMetadataCommandSupport.run(
            dependencies,
            service,
            getExcludedModules().getOrElse(Collections.emptySet()),
            getModuleToConfigVersion().getOrElse(Collections.emptyMap()),
            new MissingMetadataCommandSupport.Options(
                "gradle",
                getProjectName().get(),
                getMetadataRepositoryUri().getOrNull(),
                Boolean.TRUE.equals(getCreateIssues().getOrElse(false)),
                resolveGithubToken(),
                getTargetRepository().get(),
                getGithubApiUrl().get(),
                null
            )
        );
        getLogger().lifecycle(report.renderConsoleOutput());
        writeReport(report.toJsonString());
    }

    private List<MissingMetadataCommandSupport.DependencyCoordinate> directExternalRuntimeDependencies(ResolvedComponentResult rootComponent) {
        List<MissingMetadataCommandSupport.DependencyCoordinate> dependencies = new ArrayList<>();
        for (DependencyResult dependency : rootComponent.getDependencies()) {
            if (dependency instanceof ResolvedDependencyResult) {
                ResolvedComponentResult selected = ((ResolvedDependencyResult) dependency).getSelected();
                if (selected.getId() instanceof ModuleComponentIdentifier) {
                    ModuleVersionIdentifier moduleVersion = selected.getModuleVersion();
                    dependencies.add(new MissingMetadataCommandSupport.DependencyCoordinate(
                        moduleVersion.getGroup(),
                        moduleVersion.getName(),
                        moduleVersion.getVersion()
                    ));
                }
            }
        }
        return dependencies;
    }

    private String resolveGithubToken() {
        return getGithubToken().getOrNull();
    }

    private void writeReport(String reportJson) throws IOException {
        if (getReportFile().get().getAsFile().getParentFile() != null) {
            Files.createDirectories(getReportFile().get().getAsFile().getParentFile().toPath());
        }
        Files.writeString(getReportFile().get().getAsFile().toPath(), reportJson, StandardCharsets.UTF_8);
    }
}
