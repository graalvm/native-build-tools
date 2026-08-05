/*
 * Copyright 2003-2012 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.graalvm.build.maven;

import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.tasks.CacheableTask;
import org.gradle.api.tasks.Internal;
import org.gradle.api.tasks.LocalState;
import org.gradle.process.JavaExecSpec;

import java.io.File;
import java.util.Arrays;
import java.util.List;

/**
 * Generates the plugin descriptor with a task-local Maven repository. §E2E-functional-tests.4
 */
@CacheableTask
public abstract class GeneratePluginDescriptor extends MavenTask {

    @Internal
    public abstract DirectoryProperty getCommonRepository();

    @Internal
    public abstract DirectoryProperty getLocalRepository();

    @LocalState
    public abstract DirectoryProperty getMavenLocalRepository();

    public GeneratePluginDescriptor() {
        getArguments().set(Arrays.asList("-q", "org.apache.maven.plugins:maven-plugin-plugin:3.6.1:descriptor"));
        getMavenLocalRepository().convention(getProject().getLayout().getBuildDirectory().dir("maven-local/" + getName()));
    }

    @Override
    protected void prepareSpec(JavaExecSpec spec) {
        spec.systemProperty("common.repo.uri", getCommonRepository().getAsFile().get().toURI().toString());
        spec.systemProperty("seed.repo.uri", getLocalRepository().get().getAsFile().toURI().toASCIIString());
    }

    @Override
    protected void prepareArguments(List<String> arguments) {
        File repository = getMavenLocalRepository().getAsFile().get();
        getFileSystemOperations().delete(spec -> spec.delete(
                getProject().fileTree(repository, files -> files.include("**/*.lastUpdated"))));
        arguments.add("-Dmaven.repo.local=" + repository.getAbsolutePath());
    }

    @Override
    protected void extractOutput(File tmpDir, File outputDirectory) {
        getFileSystemOperations().copy(spec -> {
            File file = new File(tmpDir, "target/classes");
            spec.from(file).include("META-INF/maven/**").into(outputDirectory);
        });
    }
}
