/*
 * Copyright (c) 2022, Oracle and/or its affiliates. All rights reserved.
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
import org.graalvm.reachability.DirectoryConfiguration;
import org.gradle.api.DefaultTask;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.artifacts.component.ComponentSelector;
import org.gradle.api.artifacts.component.ModuleComponentSelector;
import org.gradle.api.artifacts.ModuleVersionIdentifier;
import org.gradle.api.artifacts.result.DependencyResult;
import org.gradle.api.artifacts.result.ResolvedComponentResult;
import org.gradle.api.artifacts.result.ResolvedDependencyResult;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.provider.MapProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.provider.SetProperty;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.Internal;
import org.gradle.api.tasks.Optional;
import org.gradle.api.tasks.OutputDirectory;
import org.gradle.api.tasks.TaskAction;

import java.io.IOException;
import java.io.File;
import java.net.URI;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

/**
 * Collects reachability metadata for Gradle runtime dependencies. §FS-resources-and-metadata.3 §common/FS-common-libraries.5.3.
 * The output is consumed by native compile tasks through the generated configuration directory.
 */
public abstract class CollectReachabilityMetadata extends DefaultTask {

    public void setClasspath(Configuration classpath) {
        getRootComponent().set(classpath.getIncoming().getResolutionResult().getRootComponent());
    }

    @Input
    @Optional
    protected abstract Property<ResolvedComponentResult> getRootComponent();

    @Internal
    public abstract Property<GraalVMReachabilityMetadataService> getMetadataService();

    /**
     * A URI pointing to a GraalVM reachability metadata repository. This must
     * either be a local file or a remote URI. In case of remote
     * files, only zip or tarballs are supported.
     * @return the uri property
     */
    @Input
    @Optional
    public abstract Property<URI> getUri();

    /**
     * An optional version of the remote repository: if specified,
     * and that no URI is provided, it will automatically use a
     * published repository from the official GraalVM reachability
     * metadata repository.
     *
     * @return the version of the repository to use
     */
    @Input
    @Optional
    public abstract Property<String> getVersion();

    /**
     * The set of modules for which we don't want to use the
     * configuration found in the repository. Modules must be
     * declared with the `groupId:artifactId` syntax.
     *
     * @return the set of excluded modules
     */
    @Input
    @Optional
    public abstract SetProperty<String> getExcludedModules();

    /**
     * A map from a module (org.group:artifact) to configuration
     * repository config version.
     *
     * @return the map of modules to forced configuration versions
     */
    @Input
    @Optional
    public abstract MapProperty<String, String> getModuleToConfigVersion();

    @OutputDirectory
    @Optional
    public abstract DirectoryProperty getInto();

    @TaskAction
    void copyReachabilityMetadata() throws IOException {
        if (getRootComponent().isPresent()) {
            GraalVMReachabilityMetadataService service = getMetadataService().get();
            Set<String> excludedModules = getExcludedModules().getOrElse(Collections.emptySet());
            Map<String, String> forcedVersions = getModuleToConfigVersion().getOrElse(Collections.emptyMap());
            Map<ResolvedComponentResult, Set<ModuleComponentSelector>> requestedBySelected = new LinkedHashMap<>();
            collectRequestedComponents(getRootComponent().get(), requestedBySelected, new HashSet<>());
            for (Map.Entry<ResolvedComponentResult, Set<ModuleComponentSelector>> entry : requestedBySelected.entrySet()) {
                copyMetadata(entry.getKey(), entry.getValue(), service, excludedModules, forcedVersions);
            }
        }
    }

    private void collectRequestedComponents(ResolvedComponentResult component,
                                            Map<ResolvedComponentResult, Set<ModuleComponentSelector>> requestedBySelected,
                                            Set<ResolvedComponentResult> visited) {
        if (visited.add(component)) {
            requestedBySelected.computeIfAbsent(component, ignored -> new LinkedHashSet<>());
            for (DependencyResult dependency : component.getDependencies()) {
                if (dependency instanceof ResolvedDependencyResult) {
                    ResolvedDependencyResult resolvedDependency = (ResolvedDependencyResult) dependency;
                    ResolvedComponentResult selected = resolvedDependency.getSelected();
                    ComponentSelector requested = resolvedDependency.getRequested();
                    if (requested instanceof ModuleComponentSelector) {
                        requestedBySelected.computeIfAbsent(selected, ignored -> new LinkedHashSet<>())
                                .add((ModuleComponentSelector) requested);
                    }
                    collectRequestedComponents(selected, requestedBySelected, visited);
                }
            }
        }
    }

    private void copyMetadata(ResolvedComponentResult component,
                              Set<ModuleComponentSelector> requestedComponents,
                              GraalVMReachabilityMetadataService service,
                              Set<String> excludedModules,
                              Map<String, String> forcedVersions) throws IOException {
        ModuleVersionIdentifier selected = component.getModuleVersion();
        Set<DirectoryConfiguration> configurations = service.findConfigurationsFor(excludedModules, forcedVersions, selected);
        if (configurations.isEmpty()) {
            for (ModuleComponentSelector requested : requestedComponents) {
                if (isMavenRelocationTo(requested, selected)) {
                    configurations = findConfigurations(service, excludedModules, forcedVersions, requested);
                    if (!configurations.isEmpty()) {
                        break;
                    }
                }
            }
        }
        DirectoryConfiguration.copy(configurations, getInto().get().getAsFile().toPath());
    }

    private Set<DirectoryConfiguration> findConfigurations(GraalVMReachabilityMetadataService service,
                                                            Set<String> excludedModules,
                                                            Map<String, String> forcedVersions,
                                                            ModuleComponentSelector requested) {
        String groupAndArtifact = requested.getGroup() + ":" + requested.getModule();
        return service.findConfigurationsFor(query -> {
            if (!excludedModules.contains(groupAndArtifact)) {
                query.forArtifact(artifact -> {
                    artifact.gav(groupAndArtifact + ":" + requested.getVersionConstraint().getRequiredVersion());
                    if (forcedVersions.containsKey(groupAndArtifact)) {
                        artifact.forceConfigVersion(forcedVersions.get(groupAndArtifact));
                    }
                });
            }
            query.useLatestConfigWhenVersionIsUntested();
        });
    }

    private boolean isMavenRelocationTo(ModuleComponentSelector requested, ModuleVersionIdentifier selected) {
        String version = requested.getVersionConstraint().getRequiredVersion();
        if (version.isEmpty()) {
            return false;
        }
        try {
            Configuration configuration = getProject().getConfigurations().detachedConfiguration(
                    getProject().getDependencies().create(requested.getGroup() + ":" + requested.getModule() + ":" + version + "@pom")
            );
            configuration.setTransitive(false);
            File pom = configuration.resolve().stream().findFirst().orElse(null);
            if (pom == null) {
                return false;
            }
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setNamespaceAware(true);
            factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
            Document document = factory.newDocumentBuilder().parse(pom);
            NodeList relocations = document.getElementsByTagNameNS("*", "relocation");
            if (relocations.getLength() == 0) {
                return false;
            }
            Element relocation = (Element) relocations.item(0);
            return relocationValue(relocation, "groupId", requested.getGroup()).equals(selected.getGroup())
                    && relocationValue(relocation, "artifactId", requested.getModule()).equals(selected.getName())
                    && relocationValue(relocation, "version", version).equals(selected.getVersion());
        } catch (Exception exception) {
            getLogger().debug("Unable to inspect Maven relocation for {}:{}:{}", requested.getGroup(), requested.getModule(), version, exception);
            return false;
        }
    }

    private static String relocationValue(Element relocation, String name, String defaultValue) {
        NodeList values = relocation.getElementsByTagNameNS("*", name);
        if (values.getLength() == 0) {
            return defaultValue;
        }
        String value = values.item(0).getTextContent().trim();
        return value.isEmpty() ? defaultValue : value;
    }

}
