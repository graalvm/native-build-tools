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
import org.gradle.api.artifacts.ModuleVersionIdentifier;
import org.gradle.api.artifacts.component.ComponentIdentifier;
import org.gradle.api.artifacts.component.ModuleComponentIdentifier;
import org.gradle.api.artifacts.dsl.DependencyHandler;
import org.gradle.api.artifacts.result.DependencyResult;
import org.gradle.api.artifacts.result.ArtifactResult;
import org.gradle.api.artifacts.result.ComponentArtifactsResult;
import org.gradle.api.artifacts.result.ResolvedComponentResult;
import org.gradle.api.artifacts.result.ResolvedArtifactResult;
import org.gradle.api.artifacts.result.ResolvedDependencyResult;
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.provider.MapProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.provider.Provider;
import org.gradle.api.provider.SetProperty;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.InputFiles;
import org.gradle.api.tasks.Internal;
import org.gradle.api.tasks.Optional;
import org.gradle.api.tasks.OutputDirectory;
import org.gradle.api.tasks.PathSensitive;
import org.gradle.api.tasks.PathSensitivity;
import org.gradle.api.tasks.TaskAction;
import org.gradle.maven.MavenModule;
import org.gradle.maven.MavenPomArtifact;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;

/**
 * Collects reachability metadata for Gradle runtime dependencies. §FS-resources-and-metadata.3 §common/FS-common-libraries.5.3.
 * The output is consumed by native compile tasks through the generated configuration directory.
 */
public abstract class CollectReachabilityMetadata extends DefaultTask {

    public void setClasspath(Configuration classpath) {
        Provider<ResolvedComponentResult> rootComponent = classpath.getIncoming().getResolutionResult().getRootComponent();
        getRootComponent().set(rootComponent);
        DependencyHandler dependencies = getProject().getDependencies();
        getRelocationPoms().from(rootComponent.map(root -> resolveMavenPoms(root, dependencies)));
    }

    @Input
    @Optional
    protected abstract Property<ResolvedComponentResult> getRootComponent();

    @Internal
    public abstract Property<GraalVMReachabilityMetadataService> getMetadataService();

    /**
     * Maven POMs from the runtime graph used to verify relocations. §FS-resources-and-metadata.3.
     */
    @InputFiles
    @PathSensitive(PathSensitivity.NONE)
    public abstract ConfigurableFileCollection getRelocationPoms();

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
            Map<String, ResolvedComponentResult> components = collectComponents(getRootComponent().get());
            Map<String, String> relocations = verifiedRelocations(components, readRelocations(getRelocationPoms().getFiles()));
            Map<String, Set<ModuleVersionIdentifier>> relocationSources = relocationSourcesByCanonical(components, relocations);
            for (Map.Entry<String, ResolvedComponentResult> entry : components.entrySet()) {
                if (!relocations.containsKey(entry.getKey())) {
                    copyMetadata(entry.getValue(), relocationSources.getOrDefault(entry.getKey(), Collections.emptySet()),
                            service, excludedModules, forcedVersions);
                }
            }
        }
    }

    private static Set<File> resolveMavenPoms(ResolvedComponentResult root, DependencyHandler dependencies) {
        Set<ComponentIdentifier> componentIds = new LinkedHashSet<>();
        collectComponentIds(root, componentIds, new LinkedHashSet<>());
        Set<File> poms = new LinkedHashSet<>();
        for (ComponentArtifactsResult component : dependencies.createArtifactResolutionQuery()
                .forComponents(componentIds)
                .withArtifacts(MavenModule.class, MavenPomArtifact.class)
                .execute()
                .getResolvedComponents()) {
            for (ArtifactResult artifact : component.getArtifacts(MavenPomArtifact.class)) {
                if (artifact instanceof ResolvedArtifactResult) {
                    poms.add(((ResolvedArtifactResult) artifact).getFile());
                }
            }
        }
        return poms;
    }

    private static void collectComponentIds(ResolvedComponentResult component,
                                            Set<ComponentIdentifier> componentIds,
                                            Set<ComponentIdentifier> visited) {
        if (visited.add(component.getId())) {
            if (component.getId() instanceof ModuleComponentIdentifier) {
                componentIds.add(component.getId());
            }
            for (DependencyResult dependency : component.getDependencies()) {
                if (dependency instanceof ResolvedDependencyResult) {
                    collectComponentIds(((ResolvedDependencyResult) dependency).getSelected(), componentIds, visited);
                }
            }
        }
    }

    static Map<String, ResolvedComponentResult> collectComponents(ResolvedComponentResult root) {
        Map<String, ResolvedComponentResult> components = new LinkedHashMap<>();
        collectComponents(root, components, new LinkedHashSet<>());
        return components;
    }

    private static void collectComponents(ResolvedComponentResult component,
                                          Map<String, ResolvedComponentResult> components,
                                          Set<ComponentIdentifier> visited) {
        if (visited.add(component.getId())) {
            if (component.getModuleVersion() != null) {
                components.put(coordinatesWithVersion(component.getModuleVersion()), component);
            }
            for (DependencyResult dependency : component.getDependencies()) {
                if (dependency instanceof ResolvedDependencyResult) {
                    collectComponents(((ResolvedDependencyResult) dependency).getSelected(), components, visited);
                }
            }
        }
    }

    static Map<String, String> verifiedRelocations(Map<String, ResolvedComponentResult> components,
                                                   Set<Relocation> candidates) {
        Map<String, String> relocations = new LinkedHashMap<>();
        for (Relocation candidate : candidates) {
            ResolvedComponentResult source = components.get(candidate.sourceCoordinates());
            if (source != null && directlyDependsOn(source, candidate.targetCoordinates())) {
                relocations.put(candidate.sourceCoordinates(), candidate.targetCoordinates());
            }
        }
        return relocations;
    }

    private static boolean directlyDependsOn(ResolvedComponentResult source, String targetCoordinates) {
        for (DependencyResult dependency : source.getDependencies()) {
            if (dependency instanceof ResolvedDependencyResult) {
                ModuleVersionIdentifier selected = ((ResolvedDependencyResult) dependency).getSelected().getModuleVersion();
                if (selected != null && coordinatesWithVersion(selected).equals(targetCoordinates)) {
                    return true;
                }
            }
        }
        return false;
    }

    private static Map<String, Set<ModuleVersionIdentifier>> relocationSourcesByCanonical(
            Map<String, ResolvedComponentResult> components,
            Map<String, String> relocations) {
        Map<String, Set<ModuleVersionIdentifier>> sources = new LinkedHashMap<>();
        for (String source : relocations.keySet()) {
            String target = finalRelocationTarget(source, relocations);
            ResolvedComponentResult sourceComponent = components.get(source);
            if (sourceComponent != null && !source.equals(target)) {
                sources.computeIfAbsent(target, ignored -> new LinkedHashSet<>()).add(sourceComponent.getModuleVersion());
            }
        }
        return sources;
    }

    private static String finalRelocationTarget(String source, Map<String, String> relocations) {
        Set<String> visited = new LinkedHashSet<>();
        String target = source;
        while (visited.add(target) && relocations.containsKey(target)) {
            target = relocations.get(target);
        }
        return target;
    }

    private void copyMetadata(ResolvedComponentResult component,
                              Set<ModuleVersionIdentifier> relocationSources,
                              GraalVMReachabilityMetadataService service,
                              Set<String> excludedModules,
                              Map<String, String> forcedVersions) throws IOException {
        ModuleVersionIdentifier selected = component.getModuleVersion();
        Set<DirectoryConfiguration> configurations = service.findConfigurationsFor(excludedModules, forcedVersions, selected);
        if (configurations.isEmpty() && !isExcluded(selected, excludedModules)) {
            for (ModuleVersionIdentifier source : relocationSources) {
                configurations = service.findConfigurationsFor(excludedModules, forcedVersions, source);
                if (!configurations.isEmpty()) {
                    break;
                }
            }
        }
        DirectoryConfiguration.copy(configurations, getInto().get().getAsFile().toPath());
    }

    static Set<Relocation> readRelocations(Set<File> poms) {
        Set<Relocation> relocations = new LinkedHashSet<>();
        for (File pom : poms) {
            try {
                DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
                factory.setNamespaceAware(true);
                factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
                factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
                factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
                Document document = factory.newDocumentBuilder().parse(pom);
                Element project = document.getDocumentElement();
                Element distributionManagement = directChild(project, "distributionManagement");
                Element relocation = directChild(distributionManagement, "relocation");
                if (relocation != null) {
                    String sourceGroup = projectValue(project, "groupId", parentValue(project, "groupId"));
                    String sourceArtifact = projectValue(project, "artifactId", "");
                    String sourceVersion = projectValue(project, "version", parentValue(project, "version"));
                    if (!sourceGroup.isEmpty() && !sourceArtifact.isEmpty() && !sourceVersion.isEmpty()) {
                        relocations.add(new Relocation(sourceGroup, sourceArtifact, sourceVersion,
                                relocationValue(relocation, "groupId", sourceGroup),
                                relocationValue(relocation, "artifactId", sourceArtifact),
                                relocationValue(relocation, "version", sourceVersion)));
                    }
                }
            } catch (Exception exception) {
                // A malformed or unreadable POM cannot verify a relocation. §FS-resources-and-metadata.3.
            }
        }
        return relocations;
    }

    private static String relocationValue(Element relocation, String name, String defaultValue) {
        Element valueElement = directChild(relocation, name);
        String value = valueElement == null ? "" : valueElement.getTextContent().trim();
        return value.isEmpty() ? defaultValue : value;
    }

    private static String projectValue(Element project, String name, String defaultValue) {
        Element valueElement = directChild(project, name);
        String value = valueElement == null ? "" : valueElement.getTextContent().trim();
        return value.isEmpty() ? defaultValue : value;
    }

    private static String parentValue(Element project, String name) {
        return projectValue(directChild(project, "parent"), name, "");
    }

    private static Element directChild(Element parent, String name) {
        if (parent != null) {
            for (Node child = parent.getFirstChild(); child != null; child = child.getNextSibling()) {
                if (child.getNodeType() == Node.ELEMENT_NODE
                        && name.equals(child.getLocalName() == null ? child.getNodeName() : child.getLocalName())) {
                    return (Element) child;
                }
            }
        }
        return null;
    }

    static boolean isExcluded(ModuleVersionIdentifier module, Set<String> excludedModules) {
        return excludedModules.contains(coordinates(module));
    }

    private static String coordinates(ModuleVersionIdentifier module) {
        return module.getGroup() + ":" + module.getName();
    }

    private static String coordinatesWithVersion(ModuleVersionIdentifier module) {
        return coordinates(module) + ":" + module.getVersion();
    }

    static final class Relocation {
        private final String sourceGroup;
        private final String sourceArtifact;
        private final String sourceVersion;
        private final String targetGroup;
        private final String targetArtifact;
        private final String targetVersion;

        Relocation(String sourceGroup, String sourceArtifact, String sourceVersion,
                   String targetGroup, String targetArtifact, String targetVersion) {
            this.sourceGroup = sourceGroup;
            this.sourceArtifact = sourceArtifact;
            this.sourceVersion = sourceVersion;
            this.targetGroup = targetGroup;
            this.targetArtifact = targetArtifact;
            this.targetVersion = targetVersion;
        }

        String sourceCoordinates() {
            return sourceGroup + ":" + sourceArtifact + ":" + sourceVersion;
        }

        String targetCoordinates() {
            return targetGroup + ":" + targetArtifact + ":" + targetVersion;
        }
    }

}
