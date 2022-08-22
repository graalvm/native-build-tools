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
package org.graalvm.buildtools.gradle.dsl;

import org.gradle.api.provider.MapProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.provider.SetProperty;

import java.io.File;
import java.net.URI;
import java.net.URISyntaxException;

/**
 * Extension used to configure the GraalVM reachability metadata repository.
 */
public interface GraalVMReachabilityMetadataRepositoryExtension {
    /**
     * Property used to determine if the reachability metadata
     * repository should be used.
     *
     * @return the enabled property
     */
    Property<Boolean> getEnabled();

    /**
     * A URI pointing to a GraalVM reachability metadata repository. This must
     * either be a local file or a remote URI. In case of remote
     * files, only zip or tarballs are supported.
     * @return the uri property
     */
    Property<URI> getUri();

    /**
     * An optional version of the remote repository: if specified,
     * and that no URI is provided, it will automatically use a
     * published repository from the official GraalVM reachability
     * metadata repository.
     *
     * @return the version of the repository to use
     */
    Property<String> getVersion();

    /**
     * The set of modules for which we don't want to use the
     * configuration found in the repository. Modules must be
     * declared with the `groupId:artifactId` syntax.
     *
     * @return the set of excluded modules
     */
    SetProperty<String> getExcludedModules();

    /**
     * A map from a module (org.group:artifact) to configuration
     * repository config version.
     *
     * @return the map of modules to forced configuration versions
     */
    MapProperty<String, String> getModuleToConfigVersion();

    /**
     * Convenience method to use a String for the URI
     * property.
     *
     * @param uri the URI
     * @throws URISyntaxException if URL is malformed
     */
    default void uri(String uri) throws URISyntaxException {
        getUri().set(new URI(uri));
    }

    /**
     * Convenience method to use a URI for the property.
     *
     * @param file a file
     */
    default void uri(File file) {
        getUri().set(file.toURI());
    }
}
