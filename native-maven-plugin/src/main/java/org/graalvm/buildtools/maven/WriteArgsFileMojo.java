/*
 * Copyright (c) 2020, 2023, Oracle and/or its affiliates. All rights reserved.
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

package org.graalvm.buildtools.maven;

import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.ResolutionScope;
import org.graalvm.buildtools.utils.NativeImageUtils;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

/**
 * Persists the arguments file to be used by the native-image command. This can be useful in situations where
 * Native Build Tools plugin is not available, for example, when running native-image in a Docker container.
 *
 * The path to the args file is stored in the project properties under the key {@code graalvm.native-image.args-file}.
 *
 * @author Alvaro Sanchez-Mariscal
 * @since 0.9.21
 */
@Mojo(name = WriteArgsFileMojo.NAME, requiresDependencyResolution = ResolutionScope.RUNTIME, requiresDependencyCollection = ResolutionScope.RUNTIME)
public class WriteArgsFileMojo extends NativeCompileNoForkMojo {

    public static final String NAME = "write-args-file";
    public static final String PROPERTY_NAME = "graalvm.native-image.args-file";

    @Override
    public void execute() throws MojoExecutionException {
        List<String> args = getBuildArgs();

        getLog().debug("Cleaning old native image build args");

        try (Stream<Path> listStream = Files.list(outputDirectory.toPath())) {
            listStream.map(path -> path.getFileName().toString())
                    .filter(f -> f.startsWith("native-image") && f.endsWith("args"))
                    .map(outputDirectory.toPath()::resolve)
                    .forEach(file -> {
                        try {
                            Files.delete(file);
                        } catch (IOException e) {
                            throw new RuntimeException(e);
                        }
                    });
        } catch (IOException e) {
            throw new MojoExecutionException(e);
        }

        List<String> conversionResult = NativeImageUtils.convertToArgsFile(args, outputDirectory.toPath());
        if (conversionResult.size() == 1) {
            String argsFileName = conversionResult.get(0).replace("@", "");
            getLog().info("Args file written to: " + argsFileName);
            File argsFile = new File(argsFileName);
            project.getProperties().setProperty(PROPERTY_NAME, argsFile.getAbsolutePath());
        } else {
            throw new MojoExecutionException("Error writing args file");
        }
    }
}
