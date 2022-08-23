/*
 * Copyright (c) 2021, 2021 Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.buildtools.gradle.dsl;

import org.graalvm.buildtools.utils.SharedConstants;
import org.gradle.api.provider.Property;
import org.gradle.api.provider.SetProperty;
import org.gradle.api.tasks.Input;

public abstract class ResourceDetectionOptions {
    /**
     * Determines if the resources should be detected from classpath.
     * If this property is set to true, then Gradle will automatically
     * detect resources to include from conventional places like src/main/resources.
     *
     * @return if resources should be detected from the classpath
     */
    @Input
    public abstract Property<Boolean> getEnabled();

    /**
     * Determines if detection should be limited to project dependencies, in
     * which case external dependencies will not be scanned.
     * Default value is true.
     *
     * @return if detection should be limited to the project dependencies
     */
    @Input
    public abstract Property<Boolean> getRestrictToProjectDependencies();

    /**
     * If set to true, then if a classpath entry contains a META-INF/native-image
     * resources file, then we would still try to detect resources contained in
     * that classpath entry (e.g jar). By default, this behavior is set to false,
     * meaning that if such a file is present, detection is disabled for this
     * particular classpath entry.
     *
     * @return the ignore property
     */
    @Input
    public abstract Property<Boolean> getIgnoreExistingResourcesConfigFile();

    /**
     * Returns the list of regular expressions which will be used to exclude
     * resources from detection.
     *
     * @return a list of regular expressions for resources exclusion
     */
    @Input
    public abstract SetProperty<String> getDetectionExclusionPatterns();

    /**
     * Adds the default resource excludes for detection, which can be useful if
     * you want to add more excludes but still want the conventional ones to be
     * added.
     *
     * @return resource detection options
     */
    public ResourceDetectionOptions addDefaultDetectionExclusions() {
        getDetectionExclusionPatterns().addAll(SharedConstants.DEFAULT_EXCLUDES_FOR_RESOURCE_DETECTION);
        return this;
    }


    public ResourceDetectionOptions() {
        getEnabled().convention(false);
        getRestrictToProjectDependencies().convention(true);
        getIgnoreExistingResourcesConfigFile().convention(false);
        getDetectionExclusionPatterns().convention(SharedConstants.DEFAULT_EXCLUDES_FOR_RESOURCE_DETECTION);
    }
}
