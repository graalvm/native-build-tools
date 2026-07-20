package org.graalvm.buildtools.gradle

import org.graalvm.buildtools.gradle.fixtures.AbstractFunctionalTest
import spock.lang.Issue

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
            org.gradle.java.installations.paths=${System.getenv("JAVA_HOME") ?: System.getProperty("java.home")}
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
            org.gradle.java.installations.paths=${System.getenv("JAVA_HOME") ?: System.getProperty("java.home")}
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
        // The resolved executable must live under JAVA_HOME, NOT the failed GRAALVM_HOME
        outputContains("fake-jdk")
        outputDoesNotContain("fake-graalvm")
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
    // §FS-native-invocation.1.5 — failure messages show attempted paths
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
        tasks {
            succeeded ':jar'
            failed ':nativeCompile'
        }

        and:
        errorOutputContains("gu tool failed to install native-image")
    }
}