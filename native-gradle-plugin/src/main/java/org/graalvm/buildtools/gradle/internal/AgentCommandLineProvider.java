/*
 * Copyright (c) 2021, Oracle and/or its affiliates. All rights reserved.
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

package org.graalvm.buildtools.gradle.internal;

import org.graalvm.buildtools.utils.SharedConstants;
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.provider.ListProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.InputFiles;
import org.gradle.api.tasks.Optional;
import org.gradle.api.tasks.OutputDirectory;
import org.gradle.api.tasks.PathSensitive;
import org.gradle.api.tasks.PathSensitivity;
import org.gradle.process.CommandLineArgumentProvider;

import javax.inject.Inject;
import java.io.File;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

import static org.graalvm.buildtools.utils.SharedConstants.AGENT_OUTPUT_DIRECTORY_MARKER;

public abstract class AgentCommandLineProvider implements CommandLineArgumentProvider {

    @Inject
    @SuppressWarnings("checkstyle:redundantmodifier")
    public AgentCommandLineProvider() {
    }

    @Input
    public abstract Property<Boolean> getEnabled();

    @OutputDirectory
    public abstract DirectoryProperty getOutputDirectory();

    @InputFiles
    @PathSensitive(PathSensitivity.RELATIVE)
    public abstract ConfigurableFileCollection getInputFiles();

    @Input
    public abstract ListProperty<String> getFilterableEntries();

    @Input
    public abstract Property<String> getAgentMode();

    @Input
    @Optional
    public abstract ListProperty<String> getAgentOptions();

    @Override
    public Iterable<String> asArguments() {
        if (getEnabled().get()) {
            String outputDirPath = getOutputDirectory().getAsFile().get().getAbsolutePath() + File.separator + SharedConstants.AGENT_SESSION_SUBDIR;
            List<String> agentOptions = getAgentOptions().get().stream().map(opt -> opt.replace(AGENT_OUTPUT_DIRECTORY_MARKER, outputDirPath)).collect(Collectors.toList());
            return Arrays.asList(
                    "-agentlib:native-image-agent=" + String.join(",", agentOptions),
                    "-Dorg.graalvm.nativeimage.imagecode=agent"
            );
        }
        return Collections.emptyList();
    }
}
