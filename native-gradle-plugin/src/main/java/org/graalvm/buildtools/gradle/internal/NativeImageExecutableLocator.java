/*
 * Copyright (c) 2020, 2022 Oracle and/or its affiliates. All rights reserved.
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

import org.gradle.api.GradleException;
import org.gradle.api.provider.Property;
import org.gradle.api.provider.Provider;
import org.gradle.api.provider.ProviderFactory;
import org.gradle.jvm.toolchain.JavaInstallationMetadata;
import org.gradle.jvm.toolchain.JavaLauncher;
import org.gradle.process.ExecOperations;
import org.gradle.process.ExecResult;

import java.io.File;
import java.nio.file.Paths;

import static org.graalvm.buildtools.utils.SharedConstants.GU_EXE;
import static org.graalvm.buildtools.utils.SharedConstants.NATIVE_IMAGE_EXE;

public class NativeImageExecutableLocator {

    public static Provider<String> graalvmHomeProvider(ProviderFactory providers) {
        return providers.environmentVariable("GRAALVM_HOME")
                .forUseAtConfigurationTime()
                .orElse(providers.environmentVariable("JAVA_HOME").forUseAtConfigurationTime());
    }

    public static File findNativeImageExecutable(Property<JavaLauncher> javaLauncher,
                                                 Provider<Boolean> disableToolchainDetection,
                                                 Provider<String> graalvmHomeProvider,
                                                 ExecOperations execOperations,
                                                 GraalVMLogger logger) {
        File executablePath = null;
        if (disableToolchainDetection.get() || !javaLauncher.isPresent()) {
            if (graalvmHomeProvider.isPresent()) {
                String graalvmHome = graalvmHomeProvider.get();
                logger.lifecycle("Toolchain detection is disabled, will use GraalVM from {}.", graalvmHome);
                executablePath = Paths.get(graalvmHome).resolve("bin/" + NATIVE_IMAGE_EXE).toFile();
            }
        }
        if (executablePath == null) {
            JavaInstallationMetadata metadata = javaLauncher.get().getMetadata();
            executablePath = metadata.getInstallationPath().file("bin/" + NATIVE_IMAGE_EXE).getAsFile();
            if (!executablePath.exists() && graalvmHomeProvider.isPresent()) {
                executablePath = Paths.get(graalvmHomeProvider.get()).resolve("bin").resolve(NATIVE_IMAGE_EXE).toFile();
            }
        }

        try {
            if (!executablePath.exists()) {
                logger.log("Native Image executable wasn't found. We will now try to download it. ");
                File graalVmHomeGuess = executablePath.getParentFile();

                if (!graalVmHomeGuess.toPath().resolve(GU_EXE).toFile().exists()) {
                    throw new GradleException("'" + GU_EXE + "' tool wasn't found. This probably means that JDK at isn't a GraalVM distribution.");
                }
                ExecResult res = execOperations.exec(spec -> {
                    spec.args("install", "native-image");
                    spec.setExecutable(Paths.get(graalVmHomeGuess.getAbsolutePath(), GU_EXE));
                });
                if (res.getExitValue() != 0) {
                    throw new GradleException("Native Image executable wasn't found, and '" + GU_EXE + "' tool failed to install it.");
                }
            }
        } catch (GradleException e) {
            throw new GradleException("Determining GraalVM installation failed with message: " + e.getMessage() + "\n\n"
                    + "Make sure to declare the GRAALVM_HOME environment variable or install GraalVM with " +
                    "native-image in a standard location recognized by Gradle Java toolchain support");
        }
        return executablePath;
    }
}
