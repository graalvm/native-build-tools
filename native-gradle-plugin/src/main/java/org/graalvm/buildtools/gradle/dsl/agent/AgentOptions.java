/*
 * Copyright (c) 2020, 2021 Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.buildtools.gradle.dsl.agent;

import org.gradle.api.Action;
import org.gradle.api.Task;
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.provider.ListProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.InputFiles;
import org.gradle.api.tasks.Internal;
import org.gradle.api.tasks.Nested;
import org.gradle.api.tasks.Optional;

import java.util.function.Predicate;

@SuppressWarnings({"unused"})
public interface AgentOptions {
    /**
     * Contains configuration of supported agent modes.
     */
    @Nested
    AgentModeOptions getModes();

    default void modes(Action<? super AgentModeOptions> spec) {
        spec.execute(getModes());
    }

    /**
     * The default agent mode name when the agent is in use.
     */
    @Input
    @Optional
    Property<String> getDefaultMode();

    /**
     * Enables the agent.
     */
    @Input
    @Optional
    Property<Boolean> getEnabled();

    /**
     * Caller-filter files that will be passed to the agent.
     */
    @InputFiles
    @Optional
    ConfigurableFileCollection getCallerFilterFiles();

    /**
     * Access-filter files that will be passed to the agent.
     */
    @InputFiles
    @Optional
    ConfigurableFileCollection getAccessFilterFiles();

    /**
     * Toggles the builtin agent caller filter.
     */
    @Optional
    Property<Boolean> getBuiltinCallerFilter();

    /**
     * Toggles the builtin agent heuristic filter.
     */
    @Optional
    Property<Boolean> getBuiltinHeuristicFilter();


    /**
     * Toggles the experimental support for predefined classes.
     */
    @Optional
    Property<Boolean> getEnableExperimentalPredefinedClasses();


    /**
     * Toggles the experimental support for unsafe allocation tracing.
     */
    @Optional
    Property<Boolean> getEnableExperimentalUnsafeAllocationTracing();


    /**
     * Toggles the distinction between queried and used metadata.
     */
    @Optional
    Property<Boolean> getTrackReflectionMetadata();

    /**
     * Configuration of the metadata copy task.
     */
    @Nested
    MetadataCopyOptions getMetadataCopy();

    default void metadataCopy(Action<? super MetadataCopyOptions> spec) {
        spec.execute(getMetadataCopy());
    }

    /**
     * Specifies prefixes that will be used to further filter files produced by the agent.
     */
    @Input
    @Optional
    ListProperty<String> getFilterableEntries();

    /**
     * Selects tasks that should be instrumented with the agent.
     *
     * @return Task predicate that accepts tasks during task configuration.
     */
    @Internal
    Property<Predicate<? super Task>> getTasksToInstrumentPredicate();

}
