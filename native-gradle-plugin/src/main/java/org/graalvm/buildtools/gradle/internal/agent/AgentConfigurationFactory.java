/*
 * Copyright (c) 2022, 2022 Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.buildtools.gradle.internal.agent;

import org.graalvm.buildtools.agent.AgentConfiguration;
import org.graalvm.buildtools.agent.AgentMode;
import org.graalvm.buildtools.agent.ConditionalAgentMode;
import org.graalvm.buildtools.agent.DirectAgentMode;
import org.graalvm.buildtools.agent.DisabledAgentMode;
import org.graalvm.buildtools.agent.StandardAgentMode;
import org.graalvm.buildtools.gradle.dsl.agent.AgentOptions;
import org.graalvm.buildtools.gradle.dsl.agent.ConditionalAgentModeOptions;
import org.gradle.api.GradleException;
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.file.Directory;
import org.gradle.api.file.ProjectLayout;
import org.gradle.api.provider.Provider;

import java.io.File;
import java.util.Collection;
import java.util.Collections;
import java.util.stream.Collectors;

import static org.graalvm.buildtools.gradle.internal.ConfigurationCacheSupport.serializableTransformerOf;
import static org.graalvm.buildtools.utils.SharedConstants.AGENT_OUTPUT_FOLDER;

public class AgentConfigurationFactory {
    public static Provider<AgentConfiguration> getAgentConfiguration(Provider<String> modeName, AgentOptions options) {
        return modeName.map(serializableTransformerOf(name -> {
            AgentMode agentMode;
            ConfigurableFileCollection callerFilterFiles = options.getCallerFilterFiles();
            ConfigurableFileCollection accessFilterFiles = options.getAccessFilterFiles();
            switch (name) {
                case "standard":
                    agentMode = new StandardAgentMode();
                    break;
                case "disabled":
                    agentMode = new DisabledAgentMode();
                    break;
                case "conditional":
                    ConditionalAgentModeOptions opts = options.getModes().getConditional();
                    if (!opts.getUserCodeFilterPath().isPresent()) {
                        throw new GradleException("Missing property userCodeFilterPath in agent conditional configuration");
                    }
                    agentMode = new ConditionalAgentMode(opts.getUserCodeFilterPath().get(), opts.getExtraFilterPath().getOrElse(""), opts.getParallel().get());
                    break;
                case "direct":
                    agentMode = new DirectAgentMode(options.getModes().getDirect().getOptions().getOrElse(Collections.emptyList()));
                    break;
                default:
                    throw new GradleException("Unknown agent mode selected: " + name);
            }
            return new AgentConfiguration(getFilePaths(callerFilterFiles),
                    getFilePaths(accessFilterFiles),
                    options.getBuiltinCallerFilter().get(),
                    options.getBuiltinHeuristicFilter().get(),
                    options.getEnableExperimentalPredefinedClasses().get(),
                    options.getEnableExperimentalUnsafeAllocationTracing().get(),
                    options.getTrackReflectionMetadata().get(),
                    agentMode);
        }));
    }

    private static Collection<String> getFilePaths(ConfigurableFileCollection configurableFileCollection) {
        return configurableFileCollection.getFiles().stream().map(File::getAbsolutePath).collect(Collectors.toList());
    }

    public static Provider<Directory> getAgentOutputDirectoryForTask(ProjectLayout layout, String taskName) {
        return layout.getBuildDirectory().dir(AGENT_OUTPUT_FOLDER + "/" + taskName);
    }
}
