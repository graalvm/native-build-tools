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
package org.graalvm.build.maven;

import org.gradle.api.DefaultTask;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.provider.MapProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.OutputDirectory;
import org.gradle.api.tasks.TaskAction;

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

public abstract class GenerateRuntimeMetadata extends DefaultTask {
    @Input
    public abstract Property<String> getClassName();

    @Input
    public abstract MapProperty<String, String> getMetadata();

    @OutputDirectory
    public abstract DirectoryProperty getOutputDirectory();

    @TaskAction
    public void generateClass() throws IOException {
        String fqcn = getClassName().get();
        Map<String, String> metadata = getMetadata().get();
        File outputDir = getOutputDirectory().getAsFile().get();
        String packageName = fqcn.substring(0, fqcn.lastIndexOf("."));
        String packagePath = packageName.replace(".", "/");
        String className = fqcn.substring(fqcn.lastIndexOf(".") + 1);
        Path outputPath = outputDir.toPath().resolve(packagePath);
        Files.createDirectories(outputPath);
        Path outputFile = outputPath.resolve(className + ".java");
        try (PrintWriter writer = new PrintWriter(outputFile.toFile(), StandardCharsets.UTF_8)) {
            writer.println("package " + packageName + ";");
            writer.println();
            writer.println("public abstract class " + className + " {");
            writer.println("    private " + className + "() { }");
            writer.println();
            for (Map.Entry<String, String> entry : metadata.entrySet()) {
                String key = entry.getKey();
                String value = entry.getValue();
                writer.println("   public static final String " + key + " = \"" + value + "\";");
            }
            writer.println();
            writer.println("}");
        }
    }
}
