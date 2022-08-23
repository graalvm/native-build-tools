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

import org.gradle.api.Action;
import org.gradle.api.provider.ListProperty;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.Nested;

public abstract class NativeResourcesOptions {

    @Nested
    public abstract ResourceDetectionOptions getDetectionOptions();

    @SuppressWarnings("unused")
    public void autodetection(Action<? super ResourceDetectionOptions> spec) {
        spec.execute(getDetectionOptions());
    }

    /**
     * Enables auto-detection of resources, using the default detection parameters.
     */
    public void autodetect() {
        autodetection(options -> options.getEnabled().set(true));
    }

    /**
     * The list of bundles to include in the generated resources file.
     * The contents of this property is used to generate the "bundles"
     * section of the resource-config.json file
     *
     * @return the list of bundles
     */
    @Input
    public abstract ListProperty<String> getBundles();

    /**
     * The list of resources to include, as Java regular expressions.
     * The contents of this property is used to generate the "resources" : "includes"
     * section of the resource-config.json file.
     * It will be merged with detected resources, if any.
     *
     * @return the list of resources to include
     */
    @Input
    public abstract ListProperty<String> getIncludedPatterns();

    /**
     * The list of resources to exclude, as Java regular expressions.
     * The contents of this property is used to generate the "resources" : "excludes"
     * section of the resource-config.json file.
     * It will be merged with detected resources, if any.
     *
     * @return the list of resources to exclude
     */
    @Input
    public abstract ListProperty<String> getExcludedPatterns();

}
