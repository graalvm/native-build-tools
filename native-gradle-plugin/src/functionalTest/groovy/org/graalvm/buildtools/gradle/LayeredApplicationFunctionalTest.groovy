/*
 * Copyright (c) 2021, Oracle and/or its affiliates. All rights reserved.
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

package org.graalvm.buildtools.gradle

import org.graalvm.buildtools.gradle.fixtures.AbstractFunctionalTest
import org.graalvm.buildtools.gradle.fixtures.GraalVMSupport
import org.graalvm.buildtools.utils.NativeImageUtils
import spock.lang.Ignore
import spock.lang.IgnoreIf
import spock.lang.Requires
import spock.util.concurrent.PollingConditions

import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.charset.StandardCharsets

// Exercises the top-level layer model, named task wiring, and configuration-cache isolation.
// §FS-plugin-model.2 §FS-native-tasks.1 §FS-native-invocation.3.
class LayeredApplicationFunctionalTest extends AbstractFunctionalTest {
    // GraalVM 25.0.x can fail during LayerUse processing.
    // §E2E-functional-tests.3.6.
    private static boolean hasLayerConsumptionBug() {
        !NativeImageUtils.isGraalVMVersionAtLeast(GraalVMSupport.getGraalVMHomeVersionString(), 25, 1)
    }

    def "configures a named layer outside the binary container"() {
        given:
        withSample("layered-java-application")

        when:
        run 'tasks', '--all'

        then:
        outputContains "nativeDependenciesLayer"
        outputContains "Builds the dependencies Native Image layer."
    }

    def "rejects an empty named layer before invoking Native Image"() {
        given:
        withSample("layered-java-application")
        buildFile << """
            graalvmNative.layers.create("empty")
        """

        when:
        fails 'nativeEmptyLayer'

        then:
        errorOutputContains "Layer 'empty' has no contents"
    }

    def "rejects duplicate provider layer selections before the producer runs"() {
        given:
        withSample("layered-java-application")
        // Replace the sample's happy-path consumer so this test isolates direct/provider duplicates. §FS-plugin-model.2.
        buildFile.text = buildFile.text.replace('''        main {
            usesLayer('dependencies')
        }
''', '''        main {
            useLayer(graalvmNative.layers.getByName('dependencies'))
            useLayer(providers.provider { graalvmNative.layers.getByName('dependencies') })
        }
''')

        when:
        fails 'nativeCompile', '--configuration-cache'

        then:
        errorOutputContains "Native Image binary 'main' selects a layer more than once"
        errorOutputContains "dependencies"
        tasks {
            failed ':validateNativeCompileLayerSelection'
            doesNotContain ':nativeDependenciesLayer'
        }
    }

    // Layers are disabled on Darwin and Windows CI platforms. §E2E-functional-tests.3.6.
    @Requires(
            { NativeImageUtils.getMajorJDKVersion(GraalVMSupport.getGraalVMHomeVersionString()) >= 25 }
    )
    @IgnoreIf({ os.windows || os.macOs })
    def "can build a native image using layers"() {
        def nativeApp = getExecutableFile("build/native/nativeCompile/layered-java-application")

        given:
        withSample("layered-java-application")
        buildFile << """
            // Layered-image configuration-cache coverage isolates layer task inputs.
            // §E2E-functional-tests.3.6, §E2E-functional-tests.4.
            graalvmNative.metadataRepository.enabled = false
        """.stripIndent()

        when:
        runAndReloadConfigurationCache 'nativeDependenciesLayer'

        then:
        if (hasConfigurationCache) {
            configurationCacheStoreTasks {
                succeeded ':nativeDependenciesLayer'
            }
            tasks {
                upToDate ':nativeDependenciesLayer'
            }
        } else {
            tasks {
                succeeded ':nativeDependenciesLayer'
            }
        }
        if (hasConfigurationCache) {
            configurationCacheStoreOutputContains "'-H:LayerCreate' (origin(s): command line)"
        } else {
            outputContains "'-H:LayerCreate' (origin(s): command line)"
        }

        when:
        runAndReloadConfigurationCache 'nativeRun', '-Pmessage="Hello, layered application!"'

        then:
        if (hasConfigurationCache) {
            configurationCacheStoreTasks {
                upToDate ':nativeDependenciesLayer'
                succeeded ':nativeCompile'
            }
            tasks {
                upToDate ':nativeDependenciesLayer', ':nativeCompile'
            }
        } else {
            tasks {
                upToDate ':nativeDependenciesLayer'
                succeeded ':nativeCompile'
            }
        }
        nativeApp.exists()

        and:
        outputContains "Hello, layered application!"

        and:
        if (hasConfigurationCache) {
            configurationCacheStoreOutputContains "- '-H:LayerUse' (origin(s): command line)"
        } else {
            outputContains "- '-H:LayerUse' (origin(s): command line)"
        }

        when: "Updating the application without changing the dependencies"
        file("src/main/java/org/graalvm/demo/Application.java").text = """
package org.graalvm.demo;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Application {
    private static final Logger LOGGER = LoggerFactory.getLogger(Application.class);

    public static void main(String[] args) {
        LOGGER.info("App started with args {}", String.join(", ", args));
    }

}

"""
        runAndReloadConfigurationCache 'nativeRun', '-Pmessage="Hello, layered application!"'

        then:
        if (hasConfigurationCache) {
            configurationCacheStoreTasks {
                // Base layer is not rebuilt
                upToDate ':nativeDependenciesLayer'
                // Application layer is recompiled
                succeeded ':nativeCompile'
            }
            tasks {
                upToDate ':nativeDependenciesLayer', ':nativeCompile'
            }
        } else {
            tasks {
                // Base layer is not rebuilt
                upToDate ':nativeDependenciesLayer'
                // Application layer is recompiled
                succeeded ':nativeCompile'
            }
        }

        if (hasConfigurationCache) {
            configurationCacheStoreOutputContains "- '-H:LayerUse' (origin(s): command line)"
        } else {
            outputContains "- '-H:LayerUse' (origin(s): command line)"
        }
    }

    @Requires(
            { NativeImageUtils.getMajorJDKVersion(GraalVMSupport.getGraalVMHomeVersionString()) >= 25 }
    )
    @IgnoreIf({ os.windows || os.macOs })
    def "builds a layer with a package selector"() {
        given:
        withSample("layered-java-application")
        buildFile.text = buildFile.text.replace('''                modules("java.base")
                fromConfiguration(configurations.runtimeClasspath)
''', '''                packages("org.slf4j")
''')
        buildFile << '''
            graalvmNative {
                metadataRepository.enabled = false
                layers.dependencies.verbose = true
            }
        '''.stripIndent()

        when:
        run 'nativeDependenciesLayer'

        then:
        tasks {
            succeeded ':nativeDependenciesLayer'
        }
        outputContains "-H:LayerCreate=dependencies.nil,package=org.slf4j"
        file("build/native/layers/dependencies/dependencies.nil").isFile()
    }

    @Requires(
            { NativeImageUtils.getMajorJDKVersion(GraalVMSupport.getGraalVMHomeVersionString()) >= 25 }
    )
    @IgnoreIf({ os.windows || os.macOs || hasLayerConsumptionBug() })
    def "builds and runs an all selector layer"() {
        def nativeApp = getExecutableFile("build/native/nativeCompile/layered-java-application")

        given:
        withSample("layered-java-application")
        buildFile.text = buildFile.text.replace('''                modules("java.base")
                fromConfiguration(configurations.runtimeClasspath)
''', '''                all = true
''')
        buildFile << '''
            graalvmNative.metadataRepository.enabled = false
            graalvmNative.layers.dependencies.verbose = true
        '''.stripIndent()

        when:
        run 'nativeRun', '-Pmessage="all selector"'

        then:
        tasks {
            succeeded ':nativeDependenciesLayer', ':nativeCompile', ':nativeRun'
        }
        outputContains "-H:LayerCreate=dependencies.nil"
        outputContains "- '-H:LayerUse' (origin(s): command line)"
        file("build/native/layers/dependencies/dependencies.nil").isFile()
        nativeApp.isFile()
        outputContains "all selector"
    }

    @Requires(
            { NativeImageUtils.getMajorJDKVersion(GraalVMSupport.getGraalVMHomeVersionString()) >= 25 }
    )
    @IgnoreIf({ os.windows || os.macOs })
    def "builds and runs a layer from explicit paths"() {
        given:
        withSample("layered-java-application")
        buildFile.text = buildFile.text.replace('''                fromConfiguration(configurations.runtimeClasspath)
''', '''                from(configurations.runtimeClasspath)
''')
        buildFile << '''
            graalvmNative.metadataRepository.enabled = false
            graalvmNative.layers.dependencies.verbose = true
        '''.stripIndent()

        when:
        run 'nativeRun', '-Pmessage="path selector"'

        then:
        tasks {
            succeeded ':nativeDependenciesLayer', ':nativeCompile', ':nativeRun'
        }
        outputContains "-H:LayerCreate=dependencies.nil,module=java.base,path="
        outputContains "- '-H:LayerUse' (origin(s): command line)"
        file("build/native/layers/dependencies/dependencies.nil").isFile()
        outputContains "path selector"
    }

    @Requires(
            { NativeImageUtils.getMajorJDKVersion(GraalVMSupport.getGraalVMHomeVersionString()) >= 25 }
    )
    @IgnoreIf({ os.windows || os.macOs })
    def "native test binaries can consume named layers"() {
        given:
        withSample("layered-java-application")
        buildFile << '''
            graalvmNative {
                metadataRepository.enabled = false
                layers.dependencies.contents.modules('java.sql')
                binaries.test {
                    usesLayer('dependencies')
                }
            }
        '''.stripIndent()

        when:
        run 'nativeTest'

        then:
        tasks {
            succeeded ':nativeDependenciesLayer', ':nativeTestCompile', ':nativeTest'
        }
        outputContains "- '-H:LayerUse' (origin(s): command line)"
        getExecutableFile("build/native/nativeTestCompile/layered-java-application-tests").exists()
        outputContains "[         1 tests successful      ]"
    }

    @Requires(
            { NativeImageUtils.getMajorJDKVersion(GraalVMSupport.getGraalVMHomeVersionString()) >= 25 }
    )
    @IgnoreIf({ os.windows || os.macOs })
    def "shared library binaries can consume named layers"() {
        given:
        withSample("layered-java-application")
        buildFile << '''
            graalvmNative {
                metadataRepository.enabled = false
                binaries.main.sharedLibrary = true
            }
        '''.stripIndent()

        when:
        run 'nativeCompile'

        then:
        tasks {
            succeeded ':nativeDependenciesLayer', ':nativeCompile'
        }
        outputContains "- '-H:LayerUse' (origin(s): command line)"
        file("build/native/nativeCompile/layered-java-application${IS_WINDOWS ? '.dll' : IS_MAC ? '.dylib' : '.so'}").exists()
    }

    @Requires(
            { NativeImageUtils.getMajorJDKVersion(GraalVMSupport.getGraalVMHomeVersionString()) >= 25 }
    )
    @IgnoreIf({ os.windows || os.macOs || hasLayerConsumptionBug() })
    def "builds and runs a three-level layer stack"() {
        given:
        withSample("layered-java-application")
        buildFile << '''
            graalvmNative {
                metadataRepository.enabled = false
                layers {
                    framework {
                        usesLayer('dependencies')
                        contents.modules('java.sql')
                        verbose = true
                    }
                }
                binaries.main {
                    usesLayer('framework')
                }
                layers.dependencies.verbose = true
            }
        '''.stripIndent()

        when:
        run 'nativeRun', '-Pmessage="three levels"'

        then:
        tasks {
            succeeded ':nativeDependenciesLayer', ':nativeFrameworkLayer', ':nativeCompile', ':nativeRun'
        }
        outputContains "-H:LayerCreate=framework.nil,module=java.sql"
        output.count("- '-H:LayerUse' (origin(s): command line)") >= 2
        file("build/native/layers/framework/framework.nil").isFile()
        outputContains "three levels"
    }

    @Ignore("Disable test temporarily because of a problem on GraalVM side")
    @Requires(
            { NativeImageUtils.getMajorJDKVersion(GraalVMSupport.getGraalVMHomeVersionString()) >= 25 }
    )
    def "can build a layered Micronaut application"() {
        given:
        withSample("layered-mn-application")

        when:
        run 'nativeCompile'

        then:
        tasks {
            succeeded ':nativeDependenciesLayer', ':nativeCompile'
        }

        when:
        def builder = new ProcessBuilder()
            .directory(testDirectory.toFile())
            .inheritIO()
            .command("build/native/nativeCompile/layered-mn-app${IS_WINDOWS?".exe":""}")
        def env = builder.environment()
        env["LD_LIBRARY_PATH"] = testDirectory.resolve("build/native/layers/dependencies").toString()
        def process = builder.start()
        def client = HttpClient.newHttpClient()
        def request = HttpRequest.newBuilder()
            .GET()
            .uri(new URI("http://localhost:8080/"))
            .build()
        def conditions = new PollingConditions()

        then:
        conditions.within(10) {
            def response = client.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8)).body()
            response == "Hello, layered images!"
        }

        cleanup:
        process.destroy()
    }
}
