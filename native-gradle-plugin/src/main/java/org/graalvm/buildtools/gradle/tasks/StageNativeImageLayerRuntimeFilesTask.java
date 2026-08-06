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

import org.graalvm.buildtools.utils.NativeImageLayerRuntime;
import org.gradle.api.DefaultTask;
import org.gradle.api.GradleException;
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.file.FileSystemOperations;
import org.gradle.api.tasks.CacheableTask;
import org.gradle.api.tasks.InputFiles;
import org.gradle.api.tasks.Internal;
import org.gradle.api.tasks.OutputDirectory;
import org.gradle.api.tasks.PathSensitive;
import org.gradle.api.tasks.PathSensitivity;
import org.gradle.api.tasks.TaskAction;

import javax.inject.Inject;
import java.io.File;
import java.util.HashSet;
import java.util.Set;

/**
 * Stages selected layer runtime libraries in a packaging-friendly directory. §FS-plugin-model.2.
 */
@CacheableTask
public abstract class StageNativeImageLayerRuntimeFilesTask extends DefaultTask {
    @Internal
    public abstract ConfigurableFileCollection getLayerFiles();

    @InputFiles
    @PathSensitive(PathSensitivity.RELATIVE)
    public abstract ConfigurableFileCollection getLayerDirectories();

    @OutputDirectory
    public abstract DirectoryProperty getDestinationDirectory();

    @Inject
    protected abstract FileSystemOperations getFileSystemOperations();

    @TaskAction
    public void stage() {
        File destination = getDestinationDirectory().get().getAsFile();
        getFileSystemOperations().delete(spec -> spec.delete(destination));
        Set<String> stagedNames = new HashSet<>();
        for (File layerFile : getLayerFiles()) {
            String fileName = layerFile.getName();
            String layerName = fileName.endsWith(".nil")
                ? fileName.substring(0, fileName.length() - ".nil".length())
                : fileName;
            if (!stagedNames.add(layerName)) {
                throw new GradleException("Multiple selected layers would use the runtime directory '" + layerName + "'");
            }
            File sourceDirectory = layerFile.getParentFile();
            File layerDestination = new File(destination, layerName);
            getFileSystemOperations().copy(spec -> {
                spec.from(sourceDirectory);
                spec.into(layerDestination);
                spec.setIncludeEmptyDirs(false);
                spec.eachFile(details -> {
                    if (!NativeImageLayerRuntime.isRuntimeLibrary(details.getName())) {
                        details.exclude();
                    }
                });
            });
        }
    }
}
