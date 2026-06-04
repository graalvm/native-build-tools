package org.graalvm.buildtools.gradle

import org.graalvm.buildtools.gradle.fixtures.AbstractFunctionalTest
import spock.lang.Issue

class ToolchainDiscoveryTest extends AbstractFunctionalTest {

    @Issue("https://github.com/graalvm/native-build-tools/issues/845")
    def "toolchain takes precedence over GRAALVM_HOME env var when running nativeCompile"() {
        debug = true

        given:
        withSample("java-application")

        // Create a fake GRAALVM_HOME that would provide a different native-image
        File fakeGraalvm = testDirectory.resolve("fake-graalvm").toFile()
        fakeGraalvm.mkdirs()
        File fakeBin = new File(fakeGraalvm, "bin")
        fakeBin.mkdirs()
        File fakeNativeImage = new File(fakeBin, "native-image")
        fakeNativeImage.text = "#!/bin/sh\necho 'This is the fake GraalVM from GRAALVM_HOME'"
        new File(fakeBin, "native-image").setExecutable(true)

        buildFile << """
            graalvmNative.toolchainDetection = true
            java {
                toolchain {
                    languageVersion = JavaLanguageVersion.of(JavaVersion.current().majorVersion)
                }
            }
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

    @Issue("https://github.com/graalvm/native-build-tools/issues/845")
    def "disabling toolchainDetection uses GRAALVM_HOME fallback"() {
        debug = true

        given:
        withSample("java-application")

        // Create a fake GRAALVM_HOME
        File fakeGraalvm = testDirectory.resolve("fake-graalvm").toFile()
        fakeGraalvm.mkdirs()
        File fakeBin = new File(fakeGraalvm, "bin")
        fakeBin.mkdirs()
        File nativeImage = new File(fakeBin, "native-image")
        nativeImage.text = '''#!/bin/sh
if [ "$1" = "--version" ]; then
    echo "native-image 21.0.0"
    exit 0
fi
output_file=""
while [ $# -gt 0 ]; do
    case "$1" in
        -o)
            if [ -n "$2" ]; then
                output_file="$2"
                shift 2
            else
                echo "Error: -o requires an argument" >&2
                exit 1
            fi
            ;;
        *)
            shift
            ;;
    esac
done
if [ -n "$output_file" ]; then
    mkdir -p "$(dirname "$output_file")"
    cat > "$output_file" << 'EXEC_EOF'
#!/bin/sh
echo "Fake native-image executable"
exit 0
EXEC_EOF
    chmod +x "$output_file"
fi
exit 0
'''
new File(fakeBin, "native-image").setExecutable(true)

        buildFile << """
            java {
                toolchain {
                    languageVersion = JavaLanguageVersion.of(JavaVersion.current().majorVersion)
                }
            }
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
        outputContains("GraalVM location read from environment variable: GRAALVM_HOME")
    }

    @Issue("https://github.com/graalvm/native-build-tools/issues/845")
    def "explicit javaLauncher overrides toolchain"() {
        debug = true

        given:
        withSample("java-application")

        // Create a fake GRAALVM_HOME with native-image
        File fakeGraalvm = testDirectory.resolve("fake-graalvm").toFile()
        fakeGraalvm.mkdirs()
        File fakeBin = new File(fakeGraalvm, "bin")
        fakeBin.mkdirs()
        File nativeImage = new File(fakeBin, "native-image")
        nativeImage.text = "#!/bin/sh\necho 'Fake native-image from GRAALVM_HOME'"
        new File(fakeBin, "native-image").setExecutable(true)

        buildFile << """
            java {
                toolchain {
                    languageVersion = JavaLanguageVersion.of(JavaVersion.current().majorVersion)
                }
            }
            graalvmNative.binaries.all {
                buildArgs.add("-Ob")
                javaLauncher.set(javaToolchains.launcherFor {
                    languageVersion.set(JavaLanguageVersion.of(JavaVersion.current().majorVersion))
                    vendor.set(JvmVendorSpec.matching("Oracle"))
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

    @Issue("https://github.com/graalvm/native-build-tools/issues/845")
    def "gu installs native-image when not found"() {
        debug = true

        given:
        withSample("java-application")

        // Create a GRAALVM_HOME directory WITHOUT native-image (but with gu)
        File fakeGraalvm = testDirectory.resolve("fake-graalvm").toFile()
        fakeGraalvm.mkdirs()
        File fakeBin = new File(fakeGraalvm, "bin")
        fakeBin.mkdirs()
        File gu = new File(fakeBin, "gu")
        gu.text = '''#!/bin/sh
if [ "$1" = "install" ] && [ "$2" = "native-image" ]; then
  echo 'Native Image installed successfully.'
  GU_DIR=$(cd "$(dirname "$0")" && pwd)
  cat > "${GU_DIR}/native-image" << 'NATIVE_IMAGE_EOF'
#!/bin/sh
# Fake native-image that handles --version and -o
if [ "$1" = "--version" ]; then
    echo "native-image 21.0.0"
    exit 0
fi
output_file=""
while [ $# -gt 0 ]; do
    case "$1" in
        -o)
            if [ -n "$2" ]; then
                output_file="$2"
                shift 2
            else
                echo "Error: -o requires an argument" >&2
                exit 1
            fi
            ;;
        *)
            shift
            ;;
    esac
done
if [ -n "$output_file" ]; then
    mkdir -p "$(dirname "$output_file")"
    cat > "$output_file" << 'EXEC_EOF'
#!/bin/sh
echo "Fake native-image executable"
exit 0
EXEC_EOF
    chmod +x "$output_file"
    echo "Fake native-image built: $output_file"
fi
exit 0
NATIVE_IMAGE_EOF
  chmod +x "${GU_DIR}/native-image"
fi
'''
        new File(fakeBin, "gu").setExecutable(true)

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
        outputContains("Native Image executable wasn't found. Installing via gu...")
        outputContains("Native Image installed successfully.")
        outputContains("GraalVM location read from environment variable: GRAALVM_HOME")
    }

    @Issue("https://github.com/graalvm/native-build-tools/issues/845")
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
        new File(fakeBin, "gu").setExecutable(true)

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
