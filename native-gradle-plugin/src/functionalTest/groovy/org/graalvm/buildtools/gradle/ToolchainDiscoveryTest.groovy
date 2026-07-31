package org.graalvm.buildtools.gradle

import org.graalvm.buildtools.gradle.fixtures.AbstractFunctionalTest
import spock.lang.Issue

import spock.lang.IgnoreIf
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

    private static void setupWorkingNativeImage(File binDir) {
        File nativeImage = new File(binDir, "native-image")
        nativeImage.text = WORKING_NATIVE_IMAGE_SCRIPT
        nativeImage.setExecutable(true)
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
        File gu = new File(fakeBin, "gu")
        gu.text = """#!/bin/sh
if [ "\$1" = "install" ] && [ "\$2" = "native-image" ]; then
  echo 'Native Image installed successfully.'
  GU_DIR=\$(cd "\$(dirname "\$0")" && pwd)
  cat > "\${GU_DIR}/native-image" << 'EOF'
$WORKING_NATIVE_IMAGE_SCRIPT
EOF
  chmod +x "\${GU_DIR}/native-image"
fi
exit 0
"""
        gu.setExecutable(true)

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
        File gu = new File(fakeBin, "gu")
        gu.text = '''#!/bin/sh
echo 'gu error: package not found'
exit 1'''
        gu.setExecutable(true)

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
}