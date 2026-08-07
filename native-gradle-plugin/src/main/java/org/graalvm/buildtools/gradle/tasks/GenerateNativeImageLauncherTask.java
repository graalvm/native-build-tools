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
package org.graalvm.buildtools.gradle.tasks;

import org.gradle.api.DefaultTask;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.OutputDirectory;
import org.gradle.api.tasks.TaskAction;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Generates a distribution launcher that configures the native layer loader path. §FS-plugin-model.2.
 */
public abstract class GenerateNativeImageLauncherTask extends DefaultTask {
    @Input
    public abstract Property<String> getExecutableName();

    @Input
    public abstract Property<String> getLauncherName();

    @OutputDirectory
    public abstract DirectoryProperty getOutputDirectory();

    @TaskAction
    public void generate() throws IOException {
        Path output = getOutputDirectory().get().getAsFile().toPath();
        Files.createDirectories(output);
        if (System.getProperty("os.name", "").startsWith("Windows")) {
            Path launcher = output.resolve(getLauncherName().get() + ".bat");
            Files.writeString(launcher, windowsLauncher());
        } else {
            Path launcher = output.resolve(getLauncherName().get());
            Files.writeString(launcher, unixLauncher());
            launcher.toFile().setExecutable(true, false);
        }
    }

    private String unixLauncher() {
        String variable = System.getProperty("os.name", "").startsWith("Mac")
            ? "DYLD_LIBRARY_PATH" : "LD_LIBRARY_PATH";
        return "#!/bin/sh\n"
            + "APP_HOME=$(CDPATH= cd -- \"$(dirname -- \"$0\")/..\" && pwd)\n"
            + "for layer_dir in \"$APP_HOME\"/lib/native-layers/*; do\n"
            + "  [ -d \"$layer_dir\" ] || continue\n"
            + "  " + variable + "=\"$layer_dir${" + variable + ":+:$" + variable + "}\"\n"
            + "done\n"
            + "export " + variable + "\n"
            + "exec \"$APP_HOME/lib/native/" + getExecutableName().get() + "\" \"$@\"\n";
    }

    private String windowsLauncher() {
        return "@echo off\r\n"
            + "set \"APP_HOME=%~dp0..\"\r\n"
            + "for /D %%D in (\"%APP_HOME%\\lib\\native-layers\\*\") do set \"PATH=%%~fD;%PATH%\"\r\n"
            + "\"%APP_HOME%\\lib\\native\\" + getExecutableName().get() + "\" %*\r\n";
    }
}
