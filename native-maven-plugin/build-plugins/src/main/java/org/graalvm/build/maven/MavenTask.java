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
import org.gradle.api.provider.ListProperty;
import org.gradle.api.tasks.CacheableTask;
import org.gradle.api.tasks.Classpath;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.InputDirectory;
import org.gradle.api.tasks.InputFile;
import org.gradle.api.tasks.InputFiles;
import org.gradle.api.tasks.OutputDirectory;
import org.gradle.api.tasks.PathSensitive;
import org.gradle.api.tasks.PathSensitivity;
import org.gradle.api.tasks.TaskAction;
import org.gradle.process.ExecOperations;
import org.gradle.process.JavaExecSpec;

import javax.inject.Inject;
import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

@CacheableTask
public abstract class MavenTask extends DefaultTask {
    @Inject
    public abstract ExecOperations getExecOperations();

    @Inject
    public abstract FileSystemOperations getFileSystemOperations();

    @InputDirectory
    @PathSensitive(PathSensitivity.RELATIVE)
    public abstract DirectoryProperty getProjectDirectory();

    @InputFile
    @PathSensitive(PathSensitivity.RELATIVE)
    public abstract RegularFileProperty getPomFile();

    @InputFile
    @PathSensitive(PathSensitivity.RELATIVE)
    public abstract RegularFileProperty getSettingsFile();

    @InputFiles
    @Classpath
    public abstract ConfigurableFileCollection getMavenEmbedderClasspath();

    @Input
    public abstract ListProperty<String> getArguments();

    @OutputDirectory
    public abstract DirectoryProperty getOutputDirectory();

    protected void extractOutput(File tmpDir, File outputDirectory) {
        getFileSystemOperations().copy(spec -> {
            File targetDir = new File(tmpDir, "target");
            spec.from(targetDir).into(outputDirectory);
        });
    }

    protected void prepareSpec(JavaExecSpec spec) {
    }

    @TaskAction
    protected void executeMaven() {
        File pomFile = getPomFile().getAsFile().get();
        File settingsFile = getSettingsFile().getAsFile().get();
        File outputDirectory = getOutputDirectory().getAsFile().get();
        File projectdir = getProjectDirectory().getAsFile().get();
        getExecOperations().javaexec(spec -> {
            spec.setClasspath(getMavenEmbedderClasspath());
            spec.getMainClass().set("org.apache.maven.cli.MavenCli");
            spec.systemProperty("maven.multiModuleProjectDirectory", projectdir.getAbsolutePath());
            spec.systemProperty("org.slf4j.simpleLogger.log.org.apache.maven.cli.transfer.Slf4jMavenTransferListener", "warn");
            prepareSpec(spec);
            List<String> arguments = new ArrayList<>();
            arguments.addAll(Arrays.asList(
                    "--errors",
                    "-U",
                    "--batch-mode",
                    "--settings", settingsFile.getAbsolutePath(),
                    "--file", pomFile.getAbsolutePath()
            ));
            arguments.addAll(getArguments().get());
            spec.args(arguments);
            getLogger().lifecycle("Invoking Maven with arguments " + arguments);
        });
        extractOutput(projectdir, outputDirectory);
    }
}
