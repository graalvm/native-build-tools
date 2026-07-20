package org.graalvm.buildtools.gradle

import org.graalvm.buildtools.gradle.fixtures.AbstractFunctionalTest
import org.graalvm.buildtools.utils.SharedConstants
import spock.lang.Issue

import spock.lang.IgnoreIf

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption

class ToolchainDiscoveryTest extends AbstractFunctionalTest {

    private static final String WORKING_NATIVE_IMAGE_SCRIPT = '''#!/bin/sh
output_file=""
while [ $# -gt 0 ]; do
    case "$1" in
        --version)
            echo 'native-image 25.0.2 25.0.2 (Java Version 25.0.2+10) (GraalVM Community Edition)'
            exit 0
            ;;
        -o)
            output_file="$2"
            shift 2
            ;;
        *)
            shift
            ;;
    esac
done
if [ -n "$output_file" ]; then
    mkdir -p "$(dirname "$output_file")"
    echo '#!/bin/sh' > "$output_file"
    echo 'echo "Fake native-image executable"' >> "$output_file"
    chmod +x "$output_file"
fi
exit 0
'''

    private static final String WINDOWS_NATIVE_IMAGE_SCRIPT = '''@echo off
set OUTPUT_FILE=
:parse
if "%~1"=="--version" (
  echo native-image 25.0.2 25.0.2 (Java Version 25.0.2+10) (GraalVM Community Edition)
  exit /b 0
)
if "%~1"=="-o" (
  set OUTPUT_FILE=%~2
  shift
)
shift
if not "%~1"=="" goto parse
if not "%OUTPUT_FILE%"=="" (
  for %%F in ("%OUTPUT_FILE%") do set OUT_DIR=%%~dpF
  if not exist "%OUT_DIR%" mkdir "%OUT_DIR%"
  echo echo Fake native-image executable > "%OUTPUT_FILE%"
)
exit /b 0
'''

    private static final boolean IS_WINDOWS =
            System.getProperty("os.name", "unknown").contains("Windows")

    private static final String FAKE_NATIVE_IMAGE_SCRIPT = IS_WINDOWS
                    ? WINDOWS_NATIVE_IMAGE_SCRIPT
                    : WORKING_NATIVE_IMAGE_SCRIPT

    /**
     * A fake {@code gu} that always fails (mimicking a registry/dependency error). Cross-platform.
     */
    private static final String FAILING_GU_SCRIPT = IS_WINDOWS
            ? '''@echo off
echo gu error: package not found
exit /b 1
'''
            : '''#!/bin/sh
echo 'gu error: package not found'
exit 1
'''

    private static void setupWorkingNativeImage(File binDir) {
        File nativeImage = new File(binDir, SharedConstants.NATIVE_IMAGE_EXE)
        nativeImage.text = FAKE_NATIVE_IMAGE_SCRIPT
        nativeImage.setExecutable(true)
    }

    /**
     * Writes a fake {@code gu} into {@code binDir} that installs a working native-image next to it
     * when invoked as {@code gu install native-image}. The script matches the current OS so it is
     * executable whether {@code GU_EXE} resolves to {@code gu} or {@code gu.cmd}, and the emitted
     * native-image uses the correct {@code NATIVE_IMAGE_EXE} name (e.g. {@code native-image.cmd}).
     */
    private static void setupGuThatInstallsNativeImage(File binDir) {
        File gu = new File(binDir, SharedConstants.GU_EXE)
        File nativeImageTarget = new File(binDir, SharedConstants.NATIVE_IMAGE_EXE)
        if (IS_WINDOWS) {
            gu.text = WindowsGuBuilder.installsInto(nativeImageTarget)
        } else {
            gu.text = UnixGuBuilder.installsInto(nativeImageTarget)
        }
        gu.setExecutable(true)
    }

    /**
     * Writes a fake {@code gu} that always fails into {@code binDir}. Cross-platform.
     */
    private static void setupFailingGu(File binDir) {
        File gu = new File(binDir, SharedConstants.GU_EXE)
        gu.text = FAILING_GU_SCRIPT
        gu.setExecutable(true)
    }

    /**
     * Builds a POSIX {@code gu} script that, on {@code gu install native-image}, writes a working
     * {@code native-image} executable next to it. The emitted content is the cross-platform fake
     * native-image script (shell form).
     */
    private static final class UnixGuBuilder {
        static String installsInto(File nativeImageTarget) {
            return """#!/bin/sh
if [ "\$1" = "install" ] && [ "\$2" = "native-image" ]; then
  echo 'Native Image installed successfully.'
  GU_DIR=\$(cd "\$(dirname "\$0")" && pwd)
  cat > "\$GU_DIR/${nativeImageTarget.name}" << 'EOF'
$WORKING_NATIVE_IMAGE_SCRIPT
EOF
  chmod +x "\$GU_DIR/${nativeImageTarget.name}"
fi
exit 0
"""
        }
    }

    /**
     * Builds a Windows {@code gu.cmd} script that, on {@code gu install native-image}, installs a
     * working {@code native-image.cmd} batch next to it.
     *
     * <p>The working script is written directly (raw bytes) to a hidden template file in the same
     * {@code bin} directory and then {@code copy}-ed into place by {@code gu.cmd}. Writing it through
     * a {@code copy} avoids re-embedding the script through {@code echo} inside a batch, which would
     * otherwise require heavy {@code %}-and-caret escaping and is easy to get wrong. The installed
     * {@code native-image.cmd} is exactly {@link #WINDOWS_NATIVE_IMAGE_SCRIPT}, so it parses all
     * arguments and handles {@code -o} appearing anywhere in the command line, matching the fake
     * native-image which {@code setupWorkingNativeImage} installs directly.</p>
     */
    private static final class WindowsGuBuilder {
        static String installsInto(File nativeImageTarget) {
            String name = nativeImageTarget.name
            String template = ".native-image.template.cmd"
            File binDir = nativeImageTarget.parentFile
            new File(binDir, template).text = WINDOWS_NATIVE_IMAGE_SCRIPT
            return """@echo off
if "%~1"=="install" if "%~2"=="native-image" (
  echo Native Image installed successfully.
  copy /Y "%~dp0${template}" "%~dp0${name}" >nul
)
exit /b 0
"""
        }
    }

    /**
     * Builds a standalone JDK installation (real java/javac) that never contains
     * {@code bin/native-image} (or {@code bin/gu}). Files are hard-linked from the given
     * source JDK so the copy is cheap on disk but still a distinct, non-canonicalizable
     * installation path that Gradle toolchain detection treats as a separate JVM.
     *
     * <p>Used to deterministically reproduce "the convention-selected toolchain launcher
     * lacks native-image" (§FS-native-invocation.1.2) regardless of whether the JVM that
     * runs the functional tests is a GraalVM or an ordinary JDK.</p>
     */
    private static File createJdkWithoutNativeImage(File sourceJdk, File dest) throws IOException {
        // Resolve the source to its canonical (real) path before walking it: Files.walk() does not by
        // default follow a symlinked root, so when GRAALVM_HOME is a symlink the walk would descend into
        // nothing and fail because bin/java is missing. Resolving first makes the walk follow the symlink.
        File source = sourceJdk.getCanonicalFile()
        Set<String> excludedBin = [SharedConstants.NATIVE_IMAGE_EXE, SharedConstants.GU_EXE] as Set
        Files.walk(source.toPath()).forEach { src ->
            Path rel = source.toPath().relativize(src)
            if (Files.isDirectory(src)) {
                Files.createDirectories(dest.toPath().resolve(rel.toString()))
            } else {
                String parent = rel.parent == null ? "" : rel.parent.toString()
                if (parent == "bin" && src.fileName.toString() in excludedBin) {
                    return
                }
                Path dst = dest.toPath().resolve(rel.toString())
                Files.createDirectories(dst.parent)
                try {
                    Files.createLink(dst, src)
                } catch (UnsupportedOperationException | IOException ignored) {
                    Files.copy(src, dst, StandardCopyOption.REPLACE_EXISTING)
                }
            }
        }
        File fakeBin = new File(dest, "bin")
        assert new File(fakeBin, "java").exists(): "fake JDK must contain java: $dest"
        assert !new File(fakeBin, SharedConstants.NATIVE_IMAGE_EXE).exists():
                "fake JDK must not contain native-image: $dest"
        dest
    }

    /**
     * The major Java version of a JDK installation, read from its {@code release} file
     * (e.g. {@code JAVA_VERSION="25.0.2"} yields {@code 25}). Returns 0 when it cannot be parsed.
     */
    private static int majorVersion(File jdkHome) {
        File release = new File(jdkHome, "release")
        if (!release.exists()) {
            return 0
        }
        def m = release.text =~ /JAVA_VERSION="(\d+)\./
        m ? Integer.parseInt(m[0][1]) : 0
    }

    @Issue("https://github.com/graalvm/native-build-tools/issues/542")
    // §FS-native-invocation.1.1 — explicit launcher overrides convention and env
    def "explicit javaLauncher overrides toolchain"() {
        debug = true

        given:
        withSample("java-application")
        // Pin the explicit launcher's toolchain to the build JDK GraalVM (JAVA_HOME)
        // via an explicit directory repository so resolution is deterministic and never
        // auto-provisions a vendor-specific JDK.
        // Pin toolchain resolution to the local GraalVM (JAVA_HOME) and disable
        // auto-provisioning so the build is deterministic and offline-friendly.
        file("gradle.properties") << """
            org.gradle.java.installations.auto-download=false
            org.gradle.java.installations.paths=${System.getProperty("java.home")}
        """.stripIndent()

        // Create a fake GRAALVM_HOME with a working native-image
        File fakeGraalvm = testDirectory.resolve("fake-graalvm").toFile()
        fakeGraalvm.mkdirs()
        File fakeBin = new File(fakeGraalvm, "bin")
        fakeBin.mkdirs()
        setupWorkingNativeImage(fakeBin)

        buildFile << """
            java {
                toolchain {
                    languageVersion = JavaLanguageVersion.of(JavaVersion.current().majorVersion)
                }
            }
            graalvmNative.metadataRepository.enabled = false
            graalvmNative.binaries.all {
                buildArgs.add("-Ob")
                javaLauncher.set(javaToolchains.launcherFor {
                    languageVersion.set(JavaLanguageVersion.of(JavaVersion.current().majorVersion))
                })
            }
        """.stripIndent()

        when:
        runWithEnv(['GRAALVM_HOME': fakeGraalvm.absolutePath], 'nativeCompile')

        then:
        tasks {
            succeeded ':jar', ':nativeCompile'
        }

        and:
        getExecutableFile("build/native/nativeCompile/java-application").exists()

        and:
        // Verify that the explicit javaLauncher was used (not GRAALVM_HOME)
        outputContains("Native Image executable path:")
        // The path should NOT contain the fake GRAALVM_HOME path
        outputDoesNotContain("fake-graalvm")
    }
    @Issue("https://github.com/graalvm/native-build-tools/issues/542")
    // §FS-native-invocation.1.2 — convention launcher: toolchain detection ON, toolchain wins over env
    def "toolchain takes precedence over GRAALVM_HOME env var when running nativeCompile"() {
        debug = true

        given:
        withSample("java-application")
        // Pin the toolchain to the build JDK GraalVM (JAVA_HOME) via an explicit
        // directory repository so detection is deterministic and never falls back to
        // the fake GRAALVM_HOME that runWithEnv injects.
        // Pin toolchain resolution to the local GraalVM (JAVA_HOME) and disable
        // auto-provisioning so the build is deterministic and offline-friendly.
        file("gradle.properties") << """
            org.gradle.java.installations.auto-download=false
            org.gradle.java.installations.paths=${System.getProperty("java.home")}
        """.stripIndent()

        // Create a fake GRAALVM_HOME that would provide a different native-image
        File fakeGraalvm = testDirectory.resolve("fake-graalvm").toFile()
        fakeGraalvm.mkdirs()
        File fakeBin = new File(fakeGraalvm, "bin")
        fakeBin.mkdirs()
        setupWorkingNativeImage(fakeBin)

        buildFile << """
            graalvmNative.toolchainDetection = true
            java {
                toolchain {
                    languageVersion = JavaLanguageVersion.of(JavaVersion.current().majorVersion)
                }
            }
            graalvmNative.metadataRepository.enabled = false
            graalvmNative.binaries.all {
                buildArgs.add("-Ob")
            }
        """.stripIndent()

        when:
        runWithEnv(['GRAALVM_HOME': fakeGraalvm.absolutePath], 'nativeCompile')

        then:
        tasks {
            succeeded ':jar', ':nativeCompile'
        }

        and:
        getExecutableFile("build/native/nativeCompile/java-application").exists()

        and:
        // Verify that the toolchain was used (not the fake GRAALVM_HOME)
        outputContains("Native Image executable path:")
        outputContains("GraalVM Toolchain detection is enabled")
        // The path should NOT contain the fake GRAALVM_HOME path
        outputDoesNotContain("fake-graalvm")
    }

    @Issue("https://github.com/graalvm/native-build-tools/issues/542")
    // §FS-native-invocation.1.3 — env fallback: GRAALVM_HOME when toolchain detection disabled
    def "disabling toolchainDetection uses GRAALVM_HOME fallback"() {
        debug = true

        given:
        withSample("java-application")

        // Create a fake GRAALVM_HOME with a working native-image
        File fakeGraalvm = testDirectory.resolve("fake-graalvm").toFile()
        fakeGraalvm.mkdirs()
        File fakeBin = new File(fakeGraalvm, "bin")
        fakeBin.mkdirs()
        setupWorkingNativeImage(fakeBin)

        buildFile << """
            java {
                toolchain {
                    languageVersion = JavaLanguageVersion.of(JavaVersion.current().majorVersion)
                }
            }
            graalvmNative.metadataRepository.enabled = false
            graalvmNative.binaries.all {
                buildArgs.add("-Ob")
            }
            tasks.withType(org.graalvm.buildtools.gradle.tasks.BuildNativeImageTask).configureEach {
                disableToolchainDetection = true
            }
        """.stripIndent()

        when:
        runWithEnv(['GRAALVM_HOME': fakeGraalvm.absolutePath], 'nativeCompile')

        then:
        tasks {
            succeeded ':jar', ':nativeCompile'
        }

        and:
        getExecutableFile("build/native/nativeCompile/java-application").exists()

        and:
        // Verify that GRAALVM_HOME was used (toolchain detection was disabled)
        outputContains("GraalVM Toolchain detection is disabled")
        // The path should NOT contain the fake GRAALVM_HOME path since system GRAALVM_HOME is set
        // but we verify the detection is disabled which triggers env var fallback
        outputContains("GraalVM location source: GRAALVM_HOME")
    }

    @Issue("https://github.com/graalvm/native-build-tools/issues/542")
    // §FS-native-invocation.1.3 — cross-home fallback: native-image found in JAVA_HOME when GRAALVM_HOME lacks it and gu is unavailable
    def "native-image found in alternative GraalVM home when GRAALVM_HOME has no native-image and no gu"() {
        debug = true

        given:
        withSample("java-application")

        // GRAALVM_HOME: a fake GraalVM WITHOUT native-image and WITHOUT a gu tool
        // (gu install is skipped, so the locator falls back to other GraalVM homes)
        File graalvmHome = testDirectory.resolve("fake-graalvm").toFile()
        graalvmHome.mkdirs()
        File graalvmBin = new File(graalvmHome, "bin")
        graalvmBin.mkdirs()

        // JAVA_HOME: a SECOND fake GraalVM that already HAS a working native-image
        File javaHome = testDirectory.resolve("fake-jdk").toFile()
        javaHome.mkdirs()
        File javaHomeBin = new File(javaHome, "bin")
        javaHomeBin.mkdirs()
        setupWorkingNativeImage(javaHomeBin)

        buildFile << """
        graalvmNative.toolchainDetection = false
        java {
            toolchain {
                languageVersion = JavaLanguageVersion.of(JavaVersion.current().majorVersion)
            }
        }
        tasks.withType(org.graalvm.buildtools.gradle.tasks.BuildNativeImageTask).configureEach {
            disableToolchainDetection = true
        }
        graalvmNative.metadataRepository.enabled = false
        graalvmNative.binaries.all {
            buildArgs.add("-Ob")
        }
    """.stripIndent()

        when:
        runWithEnv(['GRAALVM_HOME': graalvmHome.absolutePath, 'JAVA_HOME': javaHome.absolutePath], 'nativeCompile')

        then:
        tasks {
            succeeded ':jar', ':nativeCompile'
        }

        and:
        getExecutableFile("build/native/nativeCompile/java-application").exists()

        and:
        // The executable was found via the alternative (JAVA_HOME) GraalVM home
        outputContains("Using native-image from alternative GraalVM home: " + javaHome.absolutePath)
        // The resolved source must be JAVA_HOME, not the failed GRAALVM_HOME
        // (fake-graalvm now appears in Probed paths diagnostics so we assert the source label instead)
        outputContains("GraalVM location source: JAVA_HOME")
        // The resolved native-image path should include fake-jdk (JAVA_HOME), not fake-graalvm
        outputContains("fake-jdk")
    }

    @Issue("https://github.com/graalvm/native-build-tools/issues/542")
    // §FS-native-invocation.1.4 — gu-based installation
    def "gu installs native-image when not found"() {
        debug = true

        given:
        withSample("java-application")

        // Create a GRAALVM_HOME directory WITHOUT native-image (but with gu that installs it)
        File fakeGraalvm = testDirectory.resolve("fake-graalvm").toFile()
        fakeGraalvm.mkdirs()
        File fakeBin = new File(fakeGraalvm, "bin")
        fakeBin.mkdirs()
        setupGuThatInstallsNativeImage(fakeBin)

        buildFile << """
        graalvmNative.toolchainDetection = false
        java {
            toolchain {
                languageVersion = JavaLanguageVersion.of(JavaVersion.current().majorVersion)
            }
        }
        tasks.withType(org.graalvm.buildtools.gradle.tasks.BuildNativeImageTask).configureEach {
            disableToolchainDetection = true
        }
        graalvmNative.metadataRepository.enabled = false
        graalvmNative.binaries.all {
            buildArgs.add("-Ob")
        }
    """.stripIndent()

        when:
        runWithEnv(['GRAALVM_HOME': fakeGraalvm.absolutePath], 'nativeCompile')

        then:
        tasks {
            succeeded ':jar', ':nativeCompile'
        }

        and:
        getExecutableFile("build/native/nativeCompile/java-application").exists()

        and:
        // Under configuration cache the build runs twice (store, then reuse). On the reuse
        // run nativeCompile is up-to-date, so the gu-install code path is not re-executed and
        // the install log lines are absent. The executable still exists and resolves from
        // GRAALVM_HOME, which proves the gu fallback installed native-image. §FS-native-invocation.1.5
        outputContains("GraalVM location source: GRAALVM_HOME")
    }

    @Issue("https://github.com/graalvm/native-build-tools/issues/542")
    // §FS-native-invocation.1.5 — gu failure is non-fatal: warns and falls back to alternative homes
    def "gu installation failure falls back to error message"() {
        debug = true

        given:
        withSample("java-application")

        // Create a GRAALVM_HOME directory WITHOUT native-image and with a failing gu
        File fakeGraalvm = testDirectory.resolve("fake-graalvm").toFile()
        fakeGraalvm.mkdirs()
        File fakeBin = new File(fakeGraalvm, "bin")
        fakeBin.mkdirs()
        setupFailingGu(fakeBin)

        buildFile << """
        graalvmNative.toolchainDetection = false
        java {
            toolchain {
                languageVersion = JavaLanguageVersion.of(JavaVersion.current().majorVersion)
            }
        }
        tasks.withType(org.graalvm.buildtools.gradle.tasks.BuildNativeImageTask).configureEach {
            disableToolchainDetection = true
        }
        graalvmNative.metadataRepository.enabled = false
        graalvmNative.binaries.all {
            buildArgs.add("-Ob")
        }
    """.stripIndent()

        when:
        runWithEnv(['GRAALVM_HOME': fakeGraalvm.absolutePath], 'nativeCompile')

        then:
        // gu failure is non-fatal — warning is logged instead of throwing.
        // Build outcome is environment-dependent (Gradle JVM fallback may find native-image),
        // so we only verify the warning message which is always produced.
        outputContains("gu tool failed to install native-image")
    }

    @Issue("https://github.com/graalvm/native-build-tools/issues/542")
    // §FS-native-invocation.1.2 — convention-selected launcher: no explicit set, convention provides native-image
    def "convention launcher provides native-image when no explicit launcher set"() {
        debug = true

        given:
        withSample("java-application")
        file("gradle.properties") << """
            org.gradle.java.installations.auto-download=false
            org.gradle.java.installations.paths=${System.getProperty("java.home")}
        """.stripIndent()

        // Create a fake GRAALVM_HOME with a working native-image
        File fakeGraalvm = testDirectory.resolve("fake-graalvm").toFile()
        fakeGraalvm.mkdirs()
        File fakeBin = new File(fakeGraalvm, "bin")
        fakeBin.mkdirs()
        setupWorkingNativeImage(fakeBin)

        buildFile << """
            java {
                toolchain {
                    languageVersion = JavaLanguageVersion.of(JavaVersion.current().majorVersion)
                }
            }
            graalvmNative.metadataRepository.enabled = false
            graalvmNative.toolchainDetection = true
            graalvmNative.binaries.all {
                buildArgs.add("-Ob")
                // No explicit javaLauncher — relies on convention from toolchain
            }
        """.stripIndent()

        when:
        runWithEnv(['GRAALVM_HOME': fakeGraalvm.absolutePath], 'nativeCompile')

        then:
        tasks {
            succeeded ':jar', ':nativeCompile'
        }

        and:
        getExecutableFile("build/native/nativeCompile/java-application").exists()

        and:
        // Toolchain detection was used — no explicit launcher set, convention resolved via toolchain
        outputContains("GraalVM Toolchain detection is enabled")
    }

    @Issue("https://github.com/graalvm/native-build-tools/issues/542")
    // §FS-native-invocation.1.6 — toolchain detection interaction: compatibility mode uses convention fallback
    def "compatibility mode detects and uses convention fallback launcher"() {
        debug = true

        given:
        withSample("java-application")
        file("gradle.properties") << """
            org.gradle.java.installations.auto-download=false
            org.gradle.java.installations.paths=${System.getProperty("java.home")}
        """.stripIndent()

        File fakeGraalvm = testDirectory.resolve("fake-graalvm").toFile()
        fakeGraalvm.mkdirs()
        File fakeBin = new File(fakeGraalvm, "bin")
        fakeBin.mkdirs()
        setupWorkingNativeImage(fakeBin)

        buildFile << """
            java {
                toolchain {
                    languageVersion = JavaLanguageVersion.of(JavaVersion.current().majorVersion)
                }
            }
            graalvmNative.metadataRepository.enabled = false
            graalvmNative.toolchainDetection = true
            graalvmNative.binaries.all {
                buildArgs.add("-Ob")
            }
        """.stripIndent()

        when:
        // Set -H:+CompatibilityMode via NATIVE_IMAGE_OPTIONS env var
        // so computeCompatibilityModeEnabledProvider detects it
        // without passing the flag to native-image in buildArgs.
        runWithEnv(['GRAALVM_HOME': fakeGraalvm.absolutePath,
                    'NATIVE_IMAGE_OPTIONS': '-H:+CompatibilityMode'],
                   'nativeCompile')

        then:
        // Compatibility Mode is detected during project evaluation (afterEvaluate),
        // which only fires on the store run when using configuration cache.
        if (hasConfigurationCache) {
            configurationCacheStoreOutputContains('Compatibility Mode detected')
        } else {
            outputContains('Compatibility Mode detected')
        }
        // Toolchain detection is active and convention fallback was used (task-time message, fires on both runs)
        outputContains('GraalVM Toolchain detection is enabled')
    }

    @Issue("https://github.com/graalvm/native-build-tools/issues/542")
    // §FS-native-invocation.1.3 — the env fallback candidates are task inputs: switching JAVA_HOME
    // between builds (GRAALVM_HOME unchanged) must re-run nativeCompile, not leave it UP-TO-DATE
    // with the previous home's executable. Skipped under configuration cache: runWithEnv's second
    // run forces --rerun-tasks, which masks the UP-TO-DATE/SUCCESS distinction this test asserts.
    @IgnoreIf({ Boolean.getBoolean("config.cache") })
    def "changing JAVA_HOME re-runs nativeCompile when native-image comes from JAVA_HOME fallback"() {
        given:
        withSample("java-application")

        // GRAALVM_HOME: a fake GraalVM WITHOUT native-image and WITHOUT a gu tool
        // (gu install is skipped, so the locator falls back to the alternative homes)
        File graalvmHome = testDirectory.resolve("fake-graalvm").toFile()
        graalvmHome.mkdirs()
        new File(graalvmHome, "bin").mkdirs()

        // Two distinct fake JAVA_HOME homes, each with a working native-image
        File javaHome1 = testDirectory.resolve("fake-jdk1").toFile()
        File javaHome1Bin = new File(javaHome1, "bin")
        javaHome1Bin.mkdirs()
        setupWorkingNativeImage(javaHome1Bin)

        File javaHome2 = testDirectory.resolve("fake-jdk2").toFile()
        File javaHome2Bin = new File(javaHome2, "bin")
        javaHome2Bin.mkdirs()
        setupWorkingNativeImage(javaHome2Bin)

        buildFile << """
        graalvmNative.toolchainDetection = false
        java {
            toolchain {
                languageVersion = JavaLanguageVersion.of(JavaVersion.current().majorVersion)
            }
        }
        tasks.withType(org.graalvm.buildtools.gradle.tasks.BuildNativeImageTask).configureEach {
            disableToolchainDetection = true
        }
        graalvmNative.metadataRepository.enabled = false
        graalvmNative.binaries.all {
            buildArgs.add("-Ob")
        }
    """.stripIndent()

        when: "the first build resolves native-image from JAVA_HOME=fake-jdk1"
        runWithEnv(['GRAALVM_HOME': graalvmHome.absolutePath, 'JAVA_HOME': javaHome1.absolutePath], 'nativeCompile')

        then: "the build succeeds and the executable is produced"
        tasks {
            succeeded ':jar', ':nativeCompile'
        }
        getExecutableFile("build/native/nativeCompile/java-application").exists()

        when: "a second build keeps GRAALVM_HOME but switches JAVA_HOME to fake-jdk2"
        runWithEnv(['GRAALVM_HOME': graalvmHome.absolutePath, 'JAVA_HOME': javaHome2.absolutePath], 'nativeCompile')

        then: "nativeCompile re-runs — it must NOT be UP-TO-DATE with the stale fake-jdk1 executable"
        tasks {
            succeeded ':nativeCompile'
        }
    }

    @Issue("https://github.com/graalvm/native-build-tools/issues/542")
    // §FS-native-invocation.1.3 — control: with an unchanged environment the second build is
    // UP-TO-DATE, proving the JAVA_HOME swap in the sibling test is what forces the re-run.
    // Skipped under configuration cache: runWithEnv's second run forces --rerun-tasks (see above).
    @IgnoreIf({ Boolean.getBoolean("config.cache") })
    def "unchanged environment keeps nativeCompile UP-TO-DATE"() {
        given:
        withSample("java-application")

        // GRAALVM_HOME: a fake GraalVM WITHOUT native-image and WITHOUT a gu tool
        File graalvmHome = testDirectory.resolve("fake-graalvm").toFile()
        graalvmHome.mkdirs()
        new File(graalvmHome, "bin").mkdirs()

        // A single fake JAVA_HOME home with a working native-image
        File javaHome = testDirectory.resolve("fake-jdk").toFile()
        File javaHomeBin = new File(javaHome, "bin")
        javaHomeBin.mkdirs()
        setupWorkingNativeImage(javaHomeBin)

        buildFile << """
        graalvmNative.toolchainDetection = false
        java {
            toolchain {
                languageVersion = JavaLanguageVersion.of(JavaVersion.current().majorVersion)
            }
        }
        tasks.withType(org.graalvm.buildtools.gradle.tasks.BuildNativeImageTask).configureEach {
            disableToolchainDetection = true
        }
        graalvmNative.metadataRepository.enabled = false
        graalvmNative.binaries.all {
            buildArgs.add("-Ob")
        }
    """.stripIndent()

        when: "the first build resolves native-image from JAVA_HOME"
        runWithEnv(['GRAALVM_HOME': graalvmHome.absolutePath, 'JAVA_HOME': javaHome.absolutePath], 'nativeCompile')

        then: "the build succeeds"
        tasks {
            succeeded ':jar', ':nativeCompile'
        }

        when: "a second build with the identical environment"
        runWithEnv(['GRAALVM_HOME': graalvmHome.absolutePath, 'JAVA_HOME': javaHome.absolutePath], 'nativeCompile')

        then: "nativeCompile is UP-TO-DATE"
        tasks {
            upToDate ':nativeCompile'
        }
    }

    @Issue("https://github.com/graalvm/native-build-tools/issues/542")
    // §FS-native-invocation.1.3 — a change to a *shadowed* environment source (one earlier in the
    // resolution order but NOT the one that supplies native-image) MUST re-run nativeCompile too.
    // Here GRAALVM_HOME is set but lacks native-image/no-gu, so JAVA_HOME wins; despite that, changing
    // GRAALVM_HOME is a distinct task input (getGraalvmHomeEnvInput) and must not leave the task
    // UP-TO-DATE with the java-home-resolved executable. Skipped under config cache (see sibling test).
    @IgnoreIf({ Boolean.getBoolean("config.cache") })
    def "changing a shadowed GRAALVM_HOME re-runs nativeCompile even when JAVA_HOME supplies native-image"() {
        given:
        withSample("java-application")

        // GRAALVM_HOME: a fake GraalVM WITHOUT native-image and WITHOUT a gu tool. It is set but is
        // NOT the source that supplies native-image, so it is "shadowed" by JAVA_HOME in the order.
        File graalvmHome1 = testDirectory.resolve("fake-graalvm1").toFile()
        graalvmHome1.mkdirs()
        new File(graalvmHome1, "bin").mkdirs()

        File graalvmHome2 = testDirectory.resolve("fake-graalvm2").toFile()
        graalvmHome2.mkdirs()
        new File(graalvmHome2, "bin").mkdirs()

        // The single fake JAVA_HOME home that actually supplies native-image (unchanged across builds)
        File javaHome = testDirectory.resolve("fake-jdk").toFile()
        File javaHomeBin = new File(javaHome, "bin")
        javaHomeBin.mkdirs()
        setupWorkingNativeImage(javaHomeBin)

        buildFile << """
        graalvmNative.toolchainDetection = false
        java {
            toolchain {
                languageVersion = JavaLanguageVersion.of(JavaVersion.current().majorVersion)
            }
        }
        tasks.withType(org.graalvm.buildtools.gradle.tasks.BuildNativeImageTask).configureEach {
            disableToolchainDetection = true
        }
        graalvmNative.metadataRepository.enabled = false
        graalvmNative.binaries.all {
            buildArgs.add("-Ob")
        }
    """.stripIndent()

        when: "build 1 resolves native-image from JAVA_HOME (GRAALVM_HOME lacks native-image/no-gu)"
        runWithEnv(['GRAALVM_HOME': graalvmHome1.absolutePath, 'JAVA_HOME': javaHome.absolutePath], 'nativeCompile')

        then: "the build succeeds and JAVA_HOME is the source"
        tasks {
            succeeded ':jar', ':nativeCompile'
        }
        getExecutableFile("build/native/nativeCompile/java-application").exists()
        outputContains("GraalVM location source: JAVA_HOME")

        when: "build 2 keeps the same JAVA_HOME but changes the shadowed GRAALVM_HOME"
        runWithEnv(['GRAALVM_HOME': graalvmHome2.absolutePath, 'JAVA_HOME': javaHome.absolutePath], 'nativeCompile')

        then: "nativeCompile re-runs — the shadowed GRAALVM_HOME is a task input, so it must not be UP-TO-DATE"
        tasks {
            succeeded ':nativeCompile'
        }
    }

    @Issue("https://github.com/graalvm/native-build-tools/issues/542")
    // §FS-native-invocation.1.2 — switching a launcher from convention (GRAALVM_HOME fallback when
    // it lacks native-image) to an explicit assignment of the same launcher re-runs nativeCompile
    // and, per §1.1, fails. The fake, native-free JDK is sourced from GRAALVM_HOME (the GraalVM under
    // test, Java 25 in CI), so it is version-distinct from the Java-17 test daemon: the toolchain
    // service then has no other candidate at that version, making builds 1 and 2 deterministic even
    // though the daemon GraalVM 17 carries native-image. Runs only when GRAALVM_HOME is set.
    @IgnoreIf({ System.getenv("GRAALVM_HOME") == null })
    def "switching the launcher from convention to explicit re-runs nativeCompile and fails"() {
        given:
        withSample("java-application")

        // The fake JDK is a standalone real GraalVM (from GRAALVM_HOME) with no native-image, so the
        // convention-selected (and later explicitly-set) launcher deterministically lacks native-image,
        // while the Java-17 test daemon cannot satisfy a toolchain request at the fake's higher version.
        File fakeJdk = testDirectory.resolve("fake-jdk").toFile()
        createJdkWithoutNativeImage(new File(System.getenv("GRAALVM_HOME")), fakeJdk)
        final int FAKE_JDK_MAJOR = majorVersion(fakeJdk)

        // A fake GRAALVM_HOME that supplies a working native-image (the fallback target).
        File fakeGraalvm = testDirectory.resolve("fake-graalvm").toFile()
        fakeGraalvm.mkdirs()
        File fakeBin = new File(fakeGraalvm, "bin")
        fakeBin.mkdirs()
        setupWorkingNativeImage(fakeBin)

        file("gradle.properties") << """
            // Keep the Gradle daemon on its own JVM (do NOT set org.gradle.java.home): this test
            // must run on the workflow Gradle version, including Gradle 8.4, which cannot run on the
            // fake JDK's higher Java version. The fake JDK is only used as a *toolchain* at its own
            // (version-distinct) level below, and the toolchain request resolves to it deterministically.
            // Register only the fake JDK as a candidate installation and disable automatic discovery
            // so neither the original GraalVM running the tests nor any other install can be picked.
            // The daemon JVM is still auto-registered by Gradle, but at a lower Java version it cannot
            // satisfy the toolchain request, so the fake JDK is the sole matching installation.
            org.gradle.java.installations.auto-detect=false
            org.gradle.java.installations.auto-download=false
            org.gradle.java.installations.paths=${fakeJdk.absolutePath}
        """.stripIndent()

        buildFile << """
            java {
                toolchain {
                    languageVersion = JavaLanguageVersion.of($FAKE_JDK_MAJOR)
                }
            }
            graalvmNative.metadataRepository.enabled = false
            graalvmNative.toolchainDetection = true
            // No explicit javaLauncher: the toolchain launcher (fake JDK, no native-image) is
            // selected by convention and falls back to GRAALVM_HOME.
            graalvmNative.binaries.all {
                buildArgs.add("-Ob")
            }
        """.stripIndent()

        when: "build 1: convention-selected launcher lacks native-image, so GRAALVM_HOME supplies it"
        runWithEnv(['GRAALVM_HOME': fakeGraalvm.absolutePath, 'JAVA_HOME': fakeJdk.absolutePath], 'nativeCompile')

        then: "the build succeeds via the GRAALVM_HOME fallback"
        tasks {
            succeeded ':jar', ':nativeCompile'
        }
        getExecutableFile("build/native/nativeCompile/java-application").exists()

        and: "the convention launcher lacked native-image, so GRAALVM_HOME was selected as the source"
        outputContains("Probed paths:")
        outputContains("GraalVM location source: GRAALVM_HOME")
        outputContains(new File(fakeGraalvm, "bin/${SharedConstants.NATIVE_IMAGE_EXE}").absolutePath)

        when: "build 2: the same launcher is now assigned explicitly"
        buildFile << """
            // Same launcher the convention supplied, but now assigned directly by the user.
            graalvmNative.binaries.all {
                javaLauncher.set(javaToolchains.launcherFor {
                    languageVersion = JavaLanguageVersion.of($FAKE_JDK_MAJOR)
                })
            }
        """.stripIndent()
        runWithEnv(['GRAALVM_HOME': fakeGraalvm.absolutePath, 'JAVA_HOME': fakeJdk.absolutePath], 'nativeCompile')

        then: "nativeCompile re-runs (provenance is a task input) and fails per §FS-native-invocation.1.1"
        tasks {
            failed ':nativeCompile'
        }
        errorOutputContains("does not contain the 'native-image' executable")
    }
}
