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
import java.io.IOException;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.graalvm.buildtools.utils.SharedConstants.GU_EXE;
import static org.graalvm.buildtools.utils.SharedConstants.NATIVE_IMAGE_EXE;

public class NativeImageExecutableLocator {

    public static Provider<String> graalvmHomeProvider(ProviderFactory providers) {
        return graalvmHomeProvider(providers, new Diagnostics());
    }

    public static Provider<String> graalvmHomeProvider(ProviderFactory providers, Diagnostics diagnostics) {
        return diagnostics.fromEnvVar("GRAALVM_HOME", providers)
                .orElse(diagnostics.fromEnvVar("JAVA_HOME", providers));
    }

    public static File findNativeImageExecutable(Property<JavaLauncher> javaLauncher,
                                                 Provider<Boolean> disableToolchainDetection,
                                                 Provider<String> graalvmHomeProvider,
                                                 ExecOperations execOperations,
                                                 GraalVMLogger logger,
                                                 Diagnostics diagnostics) {
        File executablePath = null;
        boolean toolchainDetectionIsDisabled = Boolean.TRUE.equals(disableToolchainDetection.get());
        if (toolchainDetectionIsDisabled || !javaLauncher.isPresent()) {
            if (graalvmHomeProvider.isPresent()) {
                diagnostics.disableToolchainDetection();
                String graalvmHome = graalvmHomeProvider.get();
                executablePath = Paths.get(graalvmHome).resolve("bin/" + NATIVE_IMAGE_EXE).toFile();
            }
        }
        if (executablePath == null) {
            JavaInstallationMetadata metadata = javaLauncher.get().getMetadata();
            diagnostics.withToolchain(metadata);
            executablePath = metadata.getInstallationPath().file("bin/" + NATIVE_IMAGE_EXE).getAsFile();
        }

        File graalVmHomeGuess = executablePath.getParentFile();
        File guPath = graalVmHomeGuess.toPath().resolve(GU_EXE).toFile();
        if (guPath.exists() && !executablePath.exists()) {
            logger.log("Native Image executable wasn't found. We will now try to download it. ");

            ExecResult res = execOperations.exec(spec -> {
                spec.args("install", "native-image");
                spec.setExecutable(Paths.get(graalVmHomeGuess.getAbsolutePath(), GU_EXE));
            });
            if (res.getExitValue() != 0) {
                throw new GradleException("Native Image executable wasn't found, and '" + GU_EXE + "' tool failed to install it.\n" +
                        "Make sure to declare the GRAALVM_HOME or JAVA_HOME environment variable or install GraalVM with " +
                        "native-image in a standard location recognized by Gradle Java toolchain support");
            }
            diagnostics.withGuInstall();
        }

        if (!executablePath.exists()) {
            throw new GradleException(executablePath + " wasn't found. This probably means that JDK isn't a GraalVM distribution.\n" +
                    "Make sure to declare the GRAALVM_HOME or JAVA_HOME environment variable or install GraalVM with" +
                    "native-image in a standard location recognized by Gradle Java toolchain support");
        }

        diagnostics.withExecutablePath(executablePath);
        return executablePath;
    }

    public static final class Diagnostics {
        private boolean toolchainDetectionDisabled;
        private String envVar;
        private boolean guInstall;
        private File executablePath;
        private JavaInstallationMetadata toolchain;

        public Provider<String> fromEnvVar(String envVar, ProviderFactory factory) {
            return factory.environmentVariable(envVar)
                    // required for older Gradle versions support
                    .map(ConfigurationCacheSupport.serializableTransformerOf(value -> {
                        this.envVar = envVar;
                        return value;
                    }));
        }

        public void withToolchain(JavaInstallationMetadata toolchain) {
            this.toolchain = toolchain;
            this.envVar = null;
        }

        public void disableToolchainDetection() {
            toolchainDetectionDisabled = true;
        }

        public void withGuInstall() {
            guInstall = true;
        }

        public void withExecutablePath(File path) {
            executablePath = path;
        }

        public List<String> getDiagnostics() {
            List<String> diags = new ArrayList<>();
            diags.add("GraalVM Toolchain detection is " + (toolchainDetectionDisabled ? "disabled" : "enabled"));
            if (envVar != null) {
                diags.add("GraalVM location read from environment variable: " + envVar);
            }
            if (guInstall) {
                diags.add("Native Image executable was installed using 'gu' tool");
            }
            if (toolchain != null) {
                diags.add("GraalVM uses toolchain detection. Selected:");
                diags.add("   - language version: " + toolchain.getLanguageVersion());
                diags.add("   - vendor: " + toolchain.getVendor());
                diags.add("   - runtime version: " + toolchain.getJavaRuntimeVersion());
            }
            if (executablePath != null) {
                try {
                    diags.add("Native Image executable path: " + executablePath.getCanonicalPath());
                } catch (IOException e) {
                    diags.add("Native Image executable path: " + executablePath.getAbsolutePath());
                }
            }
            return Collections.unmodifiableList(diags);
        }
    }
}
