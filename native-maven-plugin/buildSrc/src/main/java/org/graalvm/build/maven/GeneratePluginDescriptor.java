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

import org.gradle.api.DefaultTask;
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.file.FileSystemOperations;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.tasks.CacheableTask;
import org.gradle.api.tasks.Classpath;
import org.gradle.api.tasks.InputFile;
import org.gradle.api.tasks.InputFiles;
import org.gradle.api.tasks.Internal;
import org.gradle.api.tasks.OutputDirectory;
import org.gradle.api.tasks.PathSensitive;
import org.gradle.api.tasks.PathSensitivity;
import org.gradle.api.tasks.TaskAction;
import org.gradle.process.ExecOperations;

import javax.inject.Inject;
import java.io.File;

@CacheableTask
public abstract class GeneratePluginDescriptor extends DefaultTask {
    @Inject
    public abstract ExecOperations getExecOperations();

    @Inject
    public abstract FileSystemOperations getFileSystemOperations();

    @Internal
    public abstract DirectoryProperty getProjectDirectory();

    @Internal
    public abstract DirectoryProperty getCommonRepository();

    @InputFile
    @PathSensitive(PathSensitivity.RELATIVE)
    public abstract RegularFileProperty getPomFile();

    @InputFile
    @PathSensitive(PathSensitivity.RELATIVE)
    public abstract RegularFileProperty getSettingsFile();

    @InputFiles
    @Classpath
    public abstract ConfigurableFileCollection getMavenEmbedderClasspath();

    @InputFiles
    @PathSensitive(PathSensitivity.RELATIVE)
    public abstract ConfigurableFileCollection getPluginClasses();

    @OutputDirectory
    public abstract DirectoryProperty getOutputDirectory();

    @TaskAction
    protected void generatePluginDescriptor() {
        File pomFile = getPomFile().getAsFile().get();
        File settingsFile = getSettingsFile().getAsFile().get();
        File outputDirectory = getOutputDirectory().getAsFile().get();
        File projectdir = getProjectDirectory().getAsFile().get();
        File tmpDir = getTemporaryDirFactory().create();
        FileSystemOperations fileSystemOperations = getFileSystemOperations();
        fileSystemOperations.copy(spec -> {
            getPluginClasses().getFiles().forEach(dir -> {
                spec.from(dir).into(new File(tmpDir, "target/classes"));
            });
        });
        fileSystemOperations.copy(spec -> spec.into(tmpDir).from(pomFile));
        getExecOperations().javaexec(spec -> {
            spec.setClasspath(getMavenEmbedderClasspath());
            spec.setMain("org.apache.maven.cli.MavenCli");
            spec.systemProperty("maven.multiModuleProjectDirectory", projectdir.getAbsolutePath());
            spec.systemProperty("common.repo.uri", getCommonRepository().getAsFile().get().toURI().toString());
            spec.args(
                    "--errors",
                    "-U",
                    "--batch-mode",
                    "--settings", settingsFile.getAbsolutePath(),
                    "--file", new File(tmpDir, pomFile.getName()),
                    "org.apache.maven.plugins:maven-plugin-plugin:3.6.1:descriptor"
            );
        });
        fileSystemOperations.copy(spec -> {
            File file = new File(tmpDir, "target/classes");
            spec.from(file).include("META-INF/maven/**").into(outputDirectory);
        });
    }
}
