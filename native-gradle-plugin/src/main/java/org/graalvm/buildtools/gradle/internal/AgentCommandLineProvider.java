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

import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.provider.ListProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.Optional;
import org.gradle.api.tasks.OutputDirectory;
import org.gradle.process.CommandLineArgumentProvider;

import javax.inject.Inject;
import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public abstract class AgentCommandLineProvider implements CommandLineArgumentProvider {

    public static final String SESSION_SUBDIR = "session-{pid}-{datetime}";

    @Inject
    @SuppressWarnings("checkstyle:redundantmodifier")
    public AgentCommandLineProvider() {

    }

    @Input
    public abstract Property<Boolean> getEnabled();

    @OutputDirectory
    public abstract DirectoryProperty getOutputDirectory();

    @Input
    @Optional
    public abstract ListProperty<String> getAgentOptions();

    @Override
    public Iterable<String> asArguments() {
        if (getEnabled().get()) {
            File outputDir = getOutputDirectory().getAsFile().get();
            List<String> agentOptions = new ArrayList<>(getAgentOptions().getOrElse(Collections.emptyList()));
            if (agentOptions.stream().map(s -> s.split("=")[0]).anyMatch(s -> s.contains("config-output-dir"))) {
                throw new IllegalStateException("config-output-dir cannot be supplied as an agent option");
            }
            agentOptions.add("config-output-dir=" + outputDir.getAbsolutePath() + File.separator + SESSION_SUBDIR);
            return Arrays.asList(
                    "-agentlib:native-image-agent=" + String.join(",", agentOptions),
                    "-Dorg.graalvm.nativeimage.imagecode=agent"
            );
        }
        return Collections.emptyList();
    }
}
