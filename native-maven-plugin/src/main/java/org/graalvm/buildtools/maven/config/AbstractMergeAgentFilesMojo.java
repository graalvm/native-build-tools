/*
 * Copyright (c) 2020, 2022, Oracle and/or its affiliates. All rights reserved.
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

package org.graalvm.buildtools.maven.config;

import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.Component;
import org.codehaus.plexus.logging.Logger;
import org.graalvm.buildtools.utils.NativeImageConfigurationUtils;

import java.io.File;
import java.nio.file.Path;

import static org.graalvm.buildtools.utils.NativeImageUtils.nativeImageConfigureFileName;

public abstract class AbstractMergeAgentFilesMojo extends AbstractMojo {


    @Component
    protected Logger logger;

    private File mergerExecutable;

    public File getMergerExecutable() throws MojoExecutionException {
        if (mergerExecutable == null) {
            initializeMergerExecutable();
        }

        return mergerExecutable;
    }

    private void initializeMergerExecutable() throws MojoExecutionException {
        Path nativeImage = NativeImageConfigurationUtils.getNativeImage(logger);
        File nativeImageExecutable = nativeImage.toAbsolutePath().toFile();
        String nativeImageConfigureFileName = nativeImageConfigureFileName();
        File mergerExecutable = new File(nativeImageExecutable.getParentFile(), nativeImageConfigureFileName);
        if (!mergerExecutable.exists()) {
            throw new MojoExecutionException("The '" + nativeImageConfigureFileName + "' tool was not found in the GraalVM JDK at '" + nativeImageExecutable.getParentFile().getParentFile() + "'." +
                    "This probably means that you are using a GraalVM distribution that is not fully supported by the Native Build Tools. " +
                    "Please try again, for example, with Oracle GraalVM or GraalVM Community Edition."
            );
        }

        this.mergerExecutable = mergerExecutable;
    }
}
