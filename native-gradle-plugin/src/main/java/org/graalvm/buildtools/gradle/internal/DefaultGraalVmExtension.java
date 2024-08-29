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

import org.graalvm.buildtools.gradle.NativeImagePlugin;
import org.graalvm.buildtools.gradle.dsl.GraalVMExtension;
import org.graalvm.buildtools.gradle.dsl.NativeImageOptions;
import org.graalvm.buildtools.gradle.dsl.agent.AgentOptions;
import org.gradle.api.Action;
import org.gradle.api.JavaVersion;
import org.gradle.api.NamedDomainObjectContainer;
import org.gradle.api.Project;
import org.gradle.api.provider.Property;
import org.gradle.jvm.toolchain.JavaLanguageVersion;
import org.gradle.jvm.toolchain.JavaLauncher;
import org.gradle.jvm.toolchain.JavaToolchainService;

import javax.inject.Inject;

public abstract class DefaultGraalVmExtension implements GraalVMExtension {
    private final transient NamedDomainObjectContainer<NativeImageOptions> nativeImages;
    private final transient NativeImagePlugin plugin;
    private final transient Project project;
    private final Property<JavaLauncher> defaultJavaLauncher;

    @Inject
    public DefaultGraalVmExtension(NamedDomainObjectContainer<NativeImageOptions> nativeImages,
                                   NativeImagePlugin plugin,
                                   Project project) {
        this.nativeImages = nativeImages;
        this.plugin = plugin;
        this.project = project;
        this.defaultJavaLauncher = project.getObjects().property(JavaLauncher.class);
        getToolchainDetection().convention(false);
        nativeImages.configureEach(options -> options.getJavaLauncher().convention(defaultJavaLauncher));
        getTestSupport().convention(true);
        AgentOptions agentOpts = getAgent();
        agentOpts.getDefaultMode().convention("standard");
        agentOpts.getEnabled().convention(false);
        agentOpts.getModes().getConditional().getParallel().convention(true);
        agentOpts.getMetadataCopy().getMergeWithExisting().convention(false);
        agentOpts.getBuiltinHeuristicFilter().convention(true);
        agentOpts.getBuiltinCallerFilter().convention(true);
        agentOpts.getEnableExperimentalPredefinedClasses().convention(false);
        agentOpts.getEnableExperimentalUnsafeAllocationTracing().convention(true);
        agentOpts.getTrackReflectionMetadata().convention(true);
        configureToolchain();
    }

    private void configureToolchain() {
        defaultJavaLauncher.convention(
                getToolchainDetection().flatMap(enabled -> {
                    if (enabled) {
                        JavaToolchainService toolchainService = project.getExtensions().findByType(JavaToolchainService.class);
                        if (toolchainService != null) {
                            return toolchainService.launcherFor(spec -> {
                                spec.getLanguageVersion().set(JavaLanguageVersion.of(JavaVersion.current().getMajorVersion()));
                            });
                        }
                    }
                    return null;
                })
        );
    }

    @Override
    public NamedDomainObjectContainer<NativeImageOptions> getBinaries() {
        return nativeImages;
    }

    @Override
    public void agent(Action<? super AgentOptions> spec) {
        spec.execute(getAgent());
    }

    @Override
    public void binaries(Action<? super NamedDomainObjectContainer<NativeImageOptions>> spec) {
        spec.execute(nativeImages);
    }

    @Override
    public void registerTestBinary(String name, Action<? super TestBinaryConfig> spec) {
        DefaultTestBinaryConfig config = project.getObjects().newInstance(DefaultTestBinaryConfig.class, name);
        spec.execute(config);
        plugin.registerTestBinary(project, this, config);
    }
}
