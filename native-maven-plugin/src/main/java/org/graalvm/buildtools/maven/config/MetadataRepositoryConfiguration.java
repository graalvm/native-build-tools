/*
 * Copyright (c) 2020, 2021, Oracle and/or its affiliates. All rights reserved.
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

package org.graalvm.buildtools.maven.config;

import org.apache.maven.artifact.Artifact;
import org.apache.maven.model.Dependency;
import org.apache.maven.plugins.annotations.Parameter;

import java.io.File;
import java.net.URL;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

public class MetadataRepositoryConfiguration {

    @Parameter(defaultValue = "true")
    private boolean enabled = true;

    @Parameter
    private String version;

    @Parameter
    private File localPath;

    @Parameter
    private URL url;

    @Parameter
    private List<DependencyConfiguration> dependencies;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getVersion() {
        return version;
    }

    public void setVersion(String version) {
        this.version = version;
    }

    public File getLocalPath() {
        return localPath;
    }

    public void setLocalPath(File localPath) {
        this.localPath = localPath;
    }

    public URL getUrl() {
        return url;
    }

    public void setUrl(URL url) {
        this.url = url;
    }

    public List<DependencyConfiguration> getDependencies() {
        return dependencies;
    }

    public void setDependencies(List<DependencyConfiguration> dependencies) {
        this.dependencies = dependencies;
    }

    public boolean isArtifactExcluded(Artifact artifact) {
        if (this.dependencies == null || this.dependencies.isEmpty()) {
            return false;
        } else {
            return dependencies.stream()
                    .filter(MetadataRepositoryConfiguration.DependencyConfiguration::isExcluded)
                    .anyMatch(d -> d.getGroupId().equals(artifact.getGroupId()) && d.getArtifactId().equals(artifact.getArtifactId()));
        }
    }

    public Optional<String> getMetadataVersion(Artifact artifact) {
        if (this.dependencies == null || this.dependencies.isEmpty()) {
            return Optional.empty();
        } else {
            return dependencies.stream()
                    .filter(d -> d.getGroupId().equals(artifact.getGroupId()) && d.getArtifactId().equals(artifact.getArtifactId()))
                    .map(DependencyConfiguration::getMetadataVersion)
                    .filter(Objects::nonNull)
                    .findFirst();
        }
    }

    public static class DependencyConfiguration extends Dependency {

        public DependencyConfiguration() {
        }

        public DependencyConfiguration(String groupId, String artifactId, boolean excluded) {
            setGroupId(groupId);
            setArtifactId(artifactId);
            this.excluded = excluded;
        }

        public DependencyConfiguration(String groupId, String artifactId, String metadataVersion) {
            setGroupId(groupId);
            setArtifactId(artifactId);
            this.metadataVersion = metadataVersion;
        }

        @Parameter(defaultValue = "false")
        private boolean excluded;

        @Parameter
        private String metadataVersion;

        public boolean isExcluded() {
            return excluded;
        }

        public void setExcluded(boolean excluded) {
            this.excluded = excluded;
        }

        public String getMetadataVersion() {
            return metadataVersion;
        }

        public void setMetadataVersion(String metadataVersion) {
            this.metadataVersion = metadataVersion;
        }
    }
}
