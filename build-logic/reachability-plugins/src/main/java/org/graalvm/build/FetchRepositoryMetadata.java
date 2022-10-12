/*
 * Copyright 2003-2021 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.graalvm.build;

import org.gradle.api.DefaultTask;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.file.FileSystemOperations;
import org.gradle.api.file.RegularFile;
import org.gradle.api.provider.Property;
import org.gradle.api.provider.Provider;
import org.gradle.api.tasks.CacheableTask;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.Internal;
import org.gradle.api.tasks.OutputDirectory;
import org.gradle.api.tasks.TaskAction;

import javax.inject.Inject;
import java.io.File;
import java.io.FileOutputStream;
import java.net.URI;
import java.nio.channels.Channels;
import java.nio.channels.FileChannel;
import java.nio.channels.ReadableByteChannel;

@CacheableTask
public abstract class FetchRepositoryMetadata extends DefaultTask {
    @Input
    public abstract Property<String> getVersion();

    @Input
    public abstract Property<String> getRepositoryUrlTemplate();

    @OutputDirectory
    public abstract DirectoryProperty getOutputDirectory();

    @Inject
    protected abstract FileSystemOperations getFileSystemOperations();

    @Internal
    public Provider<RegularFile> getOutputFile() {
        return getOutputDirectory().file("repository.zip");
    }

    public FetchRepositoryMetadata() {
        getOutputs().doNotCacheIf("Snapshot version", task -> getVersion().get().endsWith("-SNAPSHOT"));
        getRepositoryUrlTemplate().convention("https://github.com/oracle/graalvm-reachability-metadata/releases/download/%1$s/graalvm-reachability-metadata-%1$s.zip");
    }

    @TaskAction
    public void fetchMetadataRepository() {
        String url = String.format(getRepositoryUrlTemplate().get(), getVersion().get());
        File tmpFile = new File(getTemporaryDir(), "repository.zip");
        try (ReadableByteChannel channel = Channels.newChannel(new URI(url).toURL().openStream())) {
            try (FileChannel outChannel = new FileOutputStream(tmpFile).getChannel()) {
                outChannel.transferFrom(channel, 0, Long.MAX_VALUE);
            }
        } catch (Exception e) {
            throw new RuntimeException("Could not download repository metadata from " + url, e);
        }
        getFileSystemOperations().copy(spec -> {
            spec.from(tmpFile);
            spec.into(getOutputDirectory());
        });
        tmpFile.delete();
    }
}
