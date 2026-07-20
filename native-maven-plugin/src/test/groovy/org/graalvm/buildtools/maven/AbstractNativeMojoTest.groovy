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

package org.graalvm.buildtools.maven

import org.codehaus.plexus.logging.Logger
import org.graalvm.buildtools.VersionInfo
import spock.lang.Specification
import spock.lang.TempDir

import java.nio.file.Files
import java.nio.file.Path

import static org.graalvm.buildtools.utils.SharedConstants.METADATA_REPO_URL_TEMPLATE

// Protects Maven reachability metadata fallback resolution. §FS-resources-and-metadata.2.
class AbstractNativeMojoTest extends Specification {
    @TempDir
    Path testDirectory

    void "default repository falls back to release URL when Maven artifact cannot be resolved"() {
        given:
        def mojo = new MetadataFallbackMojo(testDirectory)
        mojo.reachabilityMetadataOutputDirectory = testDirectory.resolve("metadata").toFile()
        mojo.metadataRepositoryConfiguration = null
        mojo.logger = Mock(Logger)

        when:
        mojo.configureMetadataRepository()

        then:
        mojo.downloadedUrl.toString() == String.format(METADATA_REPO_URL_TEMPLATE, VersionInfo.METADATA_REPO_VERSION)
        mojo.metadataRepository != null
        mojo.metadataRepositoryConfiguration == null
    }

    private static class MetadataFallbackMojo extends AbstractNativeMojo {
        private final Path archive

        URL downloadedUrl

        private MetadataFallbackMojo(Path testDirectory) {
            archive = Files.createFile(testDirectory.resolve("repo.zip"))
        }

        @Override
        protected void executeInternal() {
        }

        @Override
        protected URL resolveDefaultMetadataRepositoryUrl() {
            null
        }

        @Override
        protected Optional<Path> downloadMetadata(URL url, Path destination) {
            downloadedUrl = url
            Optional.of(archive)
        }

        @Override
        protected Path unzipLocalMetadata(Path localPath, Path destination) {
            Files.createDirectories(destination.resolve("schemas"))
            Files.createFile(destination.resolve("schemas/metadata-library-index-schema-v2.0.0.json"))
            Files.createFile(destination.resolve("schemas/library-and-framework-list-schema-v1.0.0.json"))
            Files.createFile(destination.resolve("schemas/reachability-metadata-schema-v1.2.0.json"))
            destination
        }
    }
}
