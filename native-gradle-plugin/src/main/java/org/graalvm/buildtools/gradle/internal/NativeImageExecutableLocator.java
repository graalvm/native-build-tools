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

/**
 * Finds GraalVM executables for Gradle tasks. §FS-native-invocation.1.
 */
public class NativeImageExecutableLocator {

    public static Provider<String> graalvmHomeProvider(ProviderFactory providers) {
        return graalvmHomeProvider(providers, new Diagnostics());
    }

    public static Provider<String> graalvmHomeProvider(ProviderFactory providers, Diagnostics diagnostics) {
        return diagnostics.fromEnvVar("GRAALVM_HOME", providers)
                .orElse(diagnostics.fromEnvVar("JAVA_HOME", providers));
    }

    /**
     * Find the native-image executable from the given Java launcher.
     *
     * Search order:
     * 1. Configured Java toolchain (if toolchain detection is enabled)
     * 2. GRAALVM_HOME or JAVA_HOME environment variables
     *
     * If native-image is not found and the GraalVM installation has the gu tool
     * available, attempts to install native-image automatically.
     *
     * @param javaLauncher              the Java launcher to probe
     * @param disableToolchainDetection provider to disable toolchain detection
     * @param graalvmHomeProvider       provider for GRAALVM_HOME/JAVA_HOME
     * @param execOperations            exec operations for gu install
     * @param logger                    logger for messages
     * @param diagnostics               diagnostics collector
     * @return the native-image executable file
     * @throws GradleException if native-image cannot be found or installed
     */
    public static File findNativeImageExecutable(Property<JavaLauncher> javaLauncher,
                                                 Provider<Boolean> disableToolchainDetection,
                                                 Provider<String> graalvmHomeProvider,
                                                 ExecOperations execOperations,
                                                 GraalVMLogger logger,
                                                 Diagnostics diagnostics) {
        File executablePath = null;
        boolean toolchainDetectionIsDisabled = disableToolchainDetection.get();

        // First, try the configured toolchain if enabled and present
        if (!toolchainDetectionIsDisabled && javaLauncher.isPresent()) {
            JavaInstallationMetadata metadata = javaLauncher.get().getMetadata();
            diagnostics.withToolchain(metadata);
            try {
                executablePath = metadata.getInstallationPath().file("bin/" + NATIVE_IMAGE_EXE).getAsFile();
            } catch (Exception e) {
                // Probe failed, executablePath remains null - will fall back to environment variables
            }
            // Try to install native-image via gu if the executable doesn't exist yet
            tryInstallNativeImageViaGu(executablePath, execOperations, logger, diagnostics);
        }

        // If toolchain not found or detection disabled, try environment variables
        if ((executablePath == null || !executablePath.exists()) && graalvmHomeProvider.isPresent()) {
            diagnostics.disableToolchainDetection();
            String graalvmHome = graalvmHomeProvider.get();
            executablePath = Paths.get(graalvmHome).resolve("bin/" + NATIVE_IMAGE_EXE).toFile();

            // Try to install native-image via gu if the executable doesn't exist yet
            tryInstallNativeImageViaGu(executablePath, execOperations, logger, diagnostics);
        }

        // Fail if native-image executable still not found
        if (executablePath == null || !executablePath.exists()) {
            StringBuilder pathDescription = new StringBuilder("native-image executable not found.");

            // Add details about what paths were attempted
            if (disableToolchainDetection.get()) {
                pathDescription.append(" Toolchain detection was disabled.");
            }

            String graalvmHome = graalvmHomeProvider.getOrNull();
            if (graalvmHome != null) {
                pathDescription.append(" GRAALVM_HOME was set to: ").append(graalvmHome);
            } else {
                pathDescription.append(" GRAALVM_HOME/JAVA_HOME were not set.");
            }

            throw new GradleException(pathDescription + " " +
                    "Please configure either a GraalVM-based Java toolchain, " +
                    "or set GRAALVM_HOME/JAVA_HOME environment variable to point to " +
                    "a GraalVM installation that includes native-image in its bin/ directory.");
        }

        diagnostics.withExecutablePath(executablePath);
        return executablePath;
    }

    /**
     * Attempts to install native-image via gu if:
     * - The executablePath is non-null
     * - The executable doesn't exist
     * - The gu tool is available in the same bin/ directory
     *
     * Logs a message and updates diagnostics on successful installation.
     *
     * @param executablePath path to the expected native-image location
     * @param execOperations exec operations for running gu
     * @param logger logger for status messages
     * @param diagnostics diagnostics collector to track installation
     */
    private static void tryInstallNativeImageViaGu(
            File executablePath,
            ExecOperations execOperations,
            GraalVMLogger logger,
            Diagnostics diagnostics) {
        if (executablePath == null) {
            return;
        }

        File graalVmHomeGuess = executablePath.getParentFile();
        if (graalVmHomeGuess == null) {
            return;
        }

        File guPath = graalVmHomeGuess.toPath().resolve(GU_EXE).toFile();
        if (!guPath.exists() || executablePath.exists()) {
            return;
        }

        logger.lifecycle("Native Image executable wasn't found. Installing via gu...");
        ExecResult res = execOperations.exec(spec -> {
            spec.args("install", "native-image");
            spec.setExecutable(Paths.get(graalVmHomeGuess.getAbsolutePath(), GU_EXE));
            spec.setIgnoreExitValue(true);
        });
        if (res.getExitValue() != 0) {
            throw new GradleException("gu tool failed to install native-image. " +
                    "Please install native-image manually via 'gu install native-image' " +
                    "or configure a GraalVM installation that already includes native-image.");
        }
        logger.lifecycle("Native Image installed successfully.");
        diagnostics.withGuInstall();
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
