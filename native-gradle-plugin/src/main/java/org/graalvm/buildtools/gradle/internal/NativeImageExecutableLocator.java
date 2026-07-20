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
        return graalvmHomeProvider(providers, diagnostics, providers.provider(() -> System.getProperty("java.home")));
    }

    /**
     * Creates a provider for the GraalVM home path.
     * The search order is: GRAALVM_HOME, JAVA_HOME, then gradleJvmHome.
     * gradleJvmHome is the last resort when neither env var is set.
     */
    public static Provider<String> graalvmHomeProvider(ProviderFactory providers, Diagnostics diagnostics, Provider<String> gradleJvmHome) {
        return diagnostics.fromEnvVar("GRAALVM_HOME", providers)
                .orElse(diagnostics.fromEnvVar("JAVA_HOME", providers))
                .orElse(diagnostics.fromGradleJvm(gradleJvmHome));
    }

    /**
     * A candidate GraalVM home to probe when the primary home lacks native-image.
     * {@code home} is the resolved path (or null if unset) and {@code label} is the
     * diagnostic source label recorded when this home is selected
     * (see §FS-native-invocation.1.5).
     */
    public record VmHomeCandidate(String home, String label) {
    }

    /**
     * Builds the default cross-home fallback candidates from the process environment:
     * GRAALVM_HOME, JAVA_HOME, then the Gradle JVM home (java.home). Exposed so callers
     * can inject resolved candidates and keep {@link #findInOtherGraalVmHomes} deterministic
     * and testable (§FS-native-invocation.1.3).
     */
    public static List<VmHomeCandidate> defaultFallbackCandidates(ProviderFactory providers) {
        return List.of(
                new VmHomeCandidate(providers.environmentVariable("GRAALVM_HOME").getOrNull(), "GRAALVM_HOME"),
                new VmHomeCandidate(providers.environmentVariable("JAVA_HOME").getOrNull(), "JAVA_HOME"),
                new VmHomeCandidate(providers.provider(() -> System.getProperty("java.home")).getOrNull(), "Gradle JVM (java.home)")
        );
    }

    /**
     * Find the native-image executable from the given Java launcher.
     * <p>
     * Search order: launcher first, then environment variables.
     * <ol>
     *   <li>Configured Java launcher (toolchain): probe the installation for native-image; if {@code isExplicit},
     *      fail immediately when native-image is missing; otherwise fall through to step 2
     *   <li>Environment variables (GRAALVM_HOME or JAVA_HOME): probe and fall back to other GraalVM
     *      homes (including the Gradle JVM itself) if gu install fails
     *</ol>
     *
     * If native-image is not found and the GraalVM installation has the gu tool
     * available, attempts to install native-image automatically.
     *
     * @param launcher the resolved Java launcher to probe, or null if none available
     * @param isExplicit whether the launcher was explicitly set by the user
     * @param disableToolchainDetection provider to disable toolchain detection (used for diagnostics)
     * @param graalvmHomeProvider provider for GRAALVM_HOME/JAVA_HOME
     * @param execOperations exec operations for gu install
     * @param logger logger for messages
     * @param diagnostics diagnostics collector
     * @param fallbackVmHomeCandidates resolved cross-home fallback candidates
     * @return the native-image executable file
     * @throws GradleException if native-image cannot be found or installed,
     *     or if an explicitly configured launcher does not contain native-image
     */
    public static File findNativeImageExecutable(
            JavaLauncher launcher,
            boolean isExplicit,
            Provider<Boolean> disableToolchainDetection,
            Provider<String> graalvmHomeProvider,
            ExecOperations execOperations,
            GraalVMLogger logger,
            Diagnostics diagnostics,
            List<VmHomeCandidate> fallbackVmHomeCandidates) {
        File executablePath = null;

        // Try the configured toolchain launcher if provided
        if (launcher != null) {
            JavaInstallationMetadata metadata = launcher.getMetadata();
            diagnostics.withToolchain(metadata);
            try {
                executablePath = metadata.getInstallationPath().file("bin/" + NATIVE_IMAGE_EXE).getAsFile();
            } catch (Exception e) {
                // Probe failed, executablePath remains null
            }

            // If the launcher was explicitly set and native-image is still not found, fail immediately
            if (isExplicit && (executablePath == null || !executablePath.exists())) {
                String installPath;
                try {
                    installPath = metadata.getInstallationPath().getAsFile().getCanonicalPath();
                } catch (IOException e) {
                    installPath = metadata.getInstallationPath().getAsFile().getAbsolutePath();
                }
                throw new GradleException(
                        "The Java toolchain at " + installPath + " does not contain the 'native-image' executable. " +
                        "Please select a GraalVM-based Java toolchain that includes native-image in its bin/ directory, " +
                        "or remove the javaLauncher configuration to let the plugin fall back to GRAALVM_HOME/JAVA_HOME " +
                        "environment variables.");
            }
        }

        // If launcher not provided or convention launcher didn't find native-image, try environment variables
        if ((executablePath == null || !executablePath.exists()) && graalvmHomeProvider.isPresent()) {
            diagnostics.disableToolchainDetection();
            String graalvmHome = graalvmHomeProvider.get();
            executablePath = Paths.get(graalvmHome).resolve("bin/" + NATIVE_IMAGE_EXE).toFile();

            // Try to install native-image via gu if the executable doesn't exist yet
            tryInstallNativeImageViaGu(executablePath, execOperations, logger, diagnostics);

            // If gu install didn't help (binary still missing), try other GraalVM homes
            if (!executablePath.exists()) {
                String fallback = findInOtherGraalVmHomes(graalvmHome, logger, diagnostics, fallbackVmHomeCandidates);
                if (fallback != null) {
                    executablePath = Paths.get(fallback).resolve("bin/" + NATIVE_IMAGE_EXE).toFile();
                }
            }
        }

        // Fail if native-image executable still not found
        if (executablePath == null || !executablePath.exists()) {
            StringBuilder errorMessage = new StringBuilder();

            // Clarify when toolchain detection was disabled
            if (disableToolchainDetection.get()) {
                errorMessage.append("Toolchain detection was disabled, and ");
            }

            String graalvmHome = graalvmHomeProvider.getOrNull();
            if (graalvmHome != null) {
                String envVarName = diagnostics.getEnvVarName() != null
                        ? diagnostics.getEnvVarName()
                        : "the resolved GraalVM home";
                errorMessage.append("native-image was not found at " + envVarName + " (" + graalvmHome + "), even after attempting gu install.");
            } else {
                errorMessage.append("Neither GRAALVM_HOME nor JAVA_HOME is set, and no GraalVM toolchain was resolved.");
            }

            if (graalvmHome == null) {
                errorMessage.append(" The build is running with Gradle on a non-GraalVM JDK. ");
            }
            errorMessage.append("Please configure a GraalVM-based Java toolchain or set GRAALVM_HOME/JAVA_HOME to a GraalVM with native-image.");

            throw new GradleException(errorMessage.toString());
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

    /**
     * Finds native-image in alternative GraalVM locations when the primary candidate
     * lacks native-image even after a gu install attempt.
     * Checks GRAALVM_HOME, JAVA_HOME, and the Gradle JVM home - whichever is different
     * from the path that just failed.
     *
     * @param currentHome         the GraalVM home that just failed (skip this one)
     * @param logger              logger for diagnostic messages
     * @return the path to an alternative GraalVM home with native-image, or null if none found
     */
    private static String findInOtherGraalVmHomes(
            String currentHome,
            GraalVMLogger logger,
            Diagnostics diagnostics,
            List<VmHomeCandidate> vmHomeCandidates) {
        // Try alternative GraalVM locations. One of these is always the currentHome,
        // which gets skipped by the equals() check below to avoid re-checking the same path.
        // Each candidate is paired with the label used for diagnostic reporting so the
        // selected source is recorded accurately (see §FS-native-invocation.1.5).
        for (VmHomeCandidate vmHomeCandidate : vmHomeCandidates) {
            String home = vmHomeCandidate.home;
            String label = vmHomeCandidate.label;
            if (home == null || home.equals(currentHome)) {
                continue;
            }
            File candidateExe = Paths.get(home).resolve("bin/" + NATIVE_IMAGE_EXE).toFile();
            if (candidateExe.exists()) {
                logger.lifecycle("Using native-image from alternative GraalVM home: " + home);
                diagnostics.setLocationSource(label);
                return home;
            }
        }

        return null;
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

        public Provider<String> fromGradleJvm(Provider<String> gradleJvmHome) {
            // Records that the Gradle JVM (java.home) was the selected source so diagnostics
            // can distinguish it from GRAALVM_HOME / JAVA_HOME (see §FS-native-invocation.1.5).
            return gradleJvmHome.map(ConfigurationCacheSupport.serializableTransformerOf(value -> {
                this.envVar = "Gradle JVM (java.home)";
                return value;
            }));
        }

        /**
         * Records the GraalVM location source after native-image was found through an
         * alternative home (e.g. fallback when gu install failed). Keeps diagnostics in
         * line with the actually-used source (§FS-native-invocation.1.5).
         */
        public void setLocationSource(String source) {
            this.envVar = source;
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

        public String getEnvVarName() {
            return envVar;
        }

        public void withExecutablePath(File path) {
            executablePath = path;
        }

        public List<String> getDiagnostics() {
            List<String> diags = new ArrayList<>();
            diags.add("GraalVM Toolchain detection is " + (toolchainDetectionDisabled ? "disabled" : "enabled"));
            if (envVar != null) {
                diags.add("GraalVM location source: " + envVar);
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
