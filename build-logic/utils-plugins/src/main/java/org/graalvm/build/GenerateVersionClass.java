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
package org.graalvm.build;

import org.gradle.api.DefaultTask;
import org.gradle.api.GradleException;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.provider.MapProperty;
import org.gradle.api.tasks.CacheableTask;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.OutputDirectory;
import org.gradle.api.tasks.TaskAction;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Map;
import java.util.stream.Collectors;

@CacheableTask
public abstract class GenerateVersionClass extends DefaultTask {
    @Input
    public abstract MapProperty<String, String> getVersions();

    @OutputDirectory
    public abstract DirectoryProperty getOutputDirectory();

    @TaskAction
    protected void generate() throws IOException {
        File outputDirectory = getOutputDirectory().getAsFile().get();
        Map<String, String> versions = getVersions().get();
        File packageDir = new File(outputDirectory, "org/graalvm/buildtools");
        if (packageDir.isDirectory() || packageDir.mkdirs()) {
            try (PrintWriter writer = new PrintWriter(
                    new OutputStreamWriter(new FileOutputStream(new File(packageDir, "VersionInfo.java")), StandardCharsets.UTF_8)
            )) {
                appendLicenseHeaderTo(writer);
                writer.println();
                writer.println("package org.graalvm.buildtools;");
                writer.println();
                writer.println("public class VersionInfo {");
                for (Map.Entry<String, String> entry : versions.entrySet()) {
                    String key = entry.getKey();
                    String version = entry.getValue();
                    writer.println("    public static final String " + toConstantName(key) + " = \"" + version + "\";");
                }
                writer.println("}");
            }
        } else {
            throw new GradleException("Unable to generate versions info class");
        }
    }

    private static void appendLicenseHeaderTo(PrintWriter writer) throws IOException {
        try (InputStreamReader reader = new InputStreamReader(GenerateVersionClass.class.getResourceAsStream("header.txt"))) {
            char[] buffer = new char[4096];
            int read;
            while ((read = reader.read(buffer, 0, 4096)) >= 0) {
                writer.write(buffer, 0, read);
            }
        }
    }

    private static String toConstantName(String name) {
        return Arrays.stream(name.split("(?=[A-Z])"))
                .map(String::toUpperCase)
                .collect(Collectors.joining("_")) + "_VERSION";
    }
}
