/*
 * Copyright (c) 2026, Oracle and/or its affiliates. All rights reserved.
 *
 * The Universal Permissive License (UPL), Version 1.0
 *
 * Subject to the condition set forth below, permission is hereby granted to any
 * person obtaining a copy of this software and associated documentation files
 * (the "Software"), to deal in the Software without restriction, including
 * without limitation the rights to use, copy, modify, merge, publish,
 * distribute, sublicense, and/or sell copies of the Software, and to permit
 * persons to whom the Software is furnished to do so, subject to the following
 * conditions:
 *
 * The above copyright notice and either this complete permission notice or at
 * a minimum a reference to the UPL must be included in all copies or substantial
 * portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package org.graalvm.buildtools.gradle.tasks;

import org.gradle.api.DefaultTask;
import org.gradle.api.GradleException;
import org.gradle.api.provider.ListProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.TaskAction;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Validates logical layer selections before their producers can execute. §FS-plugin-model.2.
 */
public abstract class ValidateNativeImageLayerSelectionTask extends DefaultTask {
    @Input
    public abstract Property<String> getBinaryName();

    @Input
    public abstract ListProperty<String> getLayerNames();

    @TaskAction
    public void validate() {
        List<String> layerNames = getLayerNames().getOrElse(List.of());
        Set<String> distinctLayerNames = new HashSet<>(layerNames);
        if (distinctLayerNames.size() != layerNames.size()) {
            throw new GradleException("Native Image binary '" + getBinaryName().get()
                + "' selects a layer more than once: " + layerNames);
        }
    }
}
