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
package org.graalvm.nativeimage.gradle.tasks;

import org.graalvm.nativeimage.gradle.GradleUtils;
import org.graalvm.nativeimage.gradle.dsl.NativeImageOptions;
import org.gradle.api.plugins.ApplicationPlugin;
import org.gradle.api.tasks.AbstractExecTask;

import java.io.File;

@SuppressWarnings("unused")
public class NativeRunTask extends AbstractExecTask<NativeRunTask> {
    public static final String TASK_NAME = "nativeRun";

    protected NativeImageOptions options;

    public NativeRunTask() {
        super(NativeRunTask.class);
        dependsOn(NativeBuildTask.TASK_NAME);
        setWorkingDir(getProject().getBuildDir());
        setDescription("Runs this project as a native-image application");
        setGroup(ApplicationPlugin.APPLICATION_GROUP);

        options = getProject().getExtensions().findByType(NativeImageOptions.class);
    }

    @Override
    public void exec() {
        setExecutable(new File(GradleUtils.getTargetDir(getProject()).toFile(), options.getImageName().get()));
        args(options.getRuntimeArgs());
        super.exec();
    }
}
