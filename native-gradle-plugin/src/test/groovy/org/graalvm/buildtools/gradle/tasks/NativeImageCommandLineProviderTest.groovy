/*
 * Copyright (c) 2026, Oracle and/or its affiliates. All rights reserved.
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

package org.graalvm.buildtools.gradle.tasks

import org.graalvm.buildtools.gradle.NativeImagePlugin
import org.graalvm.buildtools.gradle.dsl.GraalVMExtension
import org.graalvm.buildtools.gradle.internal.NativeImageCommandLineProvider
import org.graalvm.buildtools.model.resources.NativeImageFlags
import org.gradle.api.plugins.ApplicationPlugin
import spock.lang.Issue

// Protects Gradle native-image argument construction for special image modes. §FS-native-invocation.3.
class NativeImageCommandLineProviderTest extends AbstractPluginTest {
    @Issue("https://github.com/graalvm/native-build-tools/issues/668")
    def "does not wrap system property values in quotes"() {
        given:
        def project = newProject()
        project.plugins.apply(ApplicationPlugin)
        project.plugins.apply(NativeImagePlugin)
        def options = project.extensions.getByType(GraalVMExtension).binaries.getByName("main")
        options.systemProperty("propertyName", "value")
        options.systemProperty("spacedProperty", "value with spaces")
        options.excludeConfigArgs.set([])
        options.configurationFileDirectories.setFrom([])

        when:
        def args = new NativeImageCommandLineProvider(
                project.provider { options },
                project.provider { "main" },
                project.provider { testDirectory.toString() },
                project.provider { testDirectory.toString() },
                project.objects.fileProperty(),
                project.provider { false },
                project.provider { 25 },
                project.provider { false },
                project.provider { false }
        ).asArguments()

        then:
        args.contains("-DpropertyName=value")
        args.contains("-DspacedProperty=value with spaces")
        !args.contains('-DpropertyName="value"')
        !args.contains('-DspacedProperty="value with spaces"')
    }

    // GraalVM 25.1 removes only the plugin-generated compatibility flag. §FS-native-invocation.3.
    @Issue("https://github.com/graalvm/native-build-tools/issues/991")
    def "#label the generated no-fallback argument"() {
        given:
        def project = newProject()
        project.plugins.apply(ApplicationPlugin)
        project.plugins.apply(NativeImagePlugin)
        def options = project.extensions.getByType(GraalVMExtension).binaries.getByName("main")
        options.excludeConfigArgs.set([])
        options.configurationFileDirectories.setFrom([])

        when:
        def args = new NativeImageCommandLineProvider(
                project.provider { options },
                project.provider { "main" },
                project.provider { testDirectory.toString() },
                project.provider { testDirectory.toString() },
                project.objects.fileProperty(),
                project.provider { false },
                project.provider { 25 },
                project.provider { fallbackRemoved },
                project.provider { false }
        ).asArguments()

        then:
        args.contains(NativeImageFlags.NO_FALLBACK) == expected

        where:
        label       | fallbackRemoved | expected
        "retains"   | false           | true
        "suppresses" | true            | false
    }

    def "retains an explicit no-fallback argument after fallback removal"() {
        given:
        def project = newProject()
        project.plugins.apply(ApplicationPlugin)
        project.plugins.apply(NativeImagePlugin)
        def options = project.extensions.getByType(GraalVMExtension).binaries.getByName("main")
        options.buildArgs.add(NativeImageFlags.NO_FALLBACK)
        options.excludeConfigArgs.set([])
        options.configurationFileDirectories.setFrom([])

        when:
        def args = new NativeImageCommandLineProvider(
                project.provider { options },
                project.provider { "main" },
                project.provider { testDirectory.toString() },
                project.provider { testDirectory.toString() },
                project.objects.fileProperty(),
                project.provider { false },
                project.provider { 25 },
                project.provider { true },
                project.provider { false }
        ).asArguments()

        then:
        args.count { it == NativeImageFlags.NO_FALLBACK } == 1
    }

    @Issue("https://github.com/graalvm/native-build-tools/issues/892")
    def "does not add classpath for layer-create build with empty classpath"() {
        given:
        def project = newProject()
        project.plugins.apply(ApplicationPlugin)
        project.plugins.apply(NativeImagePlugin)
        def options = project.extensions.getByType(GraalVMExtension).binaries.create("libbase")
        options.createLayer {
            it.modules.add("java.base")
        }
        options.excludeConfigArgs.set([])
        options.configurationFileDirectories.setFrom([])

        when:
        def args = new NativeImageCommandLineProvider(
                project.provider { options },
                project.provider { "libbase" },
                project.provider { testDirectory.toString() },
                project.provider { testDirectory.toString() },
                project.objects.fileProperty(),
                project.provider { false },
                project.provider { 25 },
                project.provider { false },
                project.provider { false }
        ).asArguments()

        then:
        !args.contains("-cp")
        args.contains(NativeImageFlags.UNLOCK_EXPERIMENTAL_VMOPTIONS)
        args.any { it.startsWith("${NativeImageFlags.LAYER_CREATE}=libbase.nil") && it.contains("module=java.base") }
    }

    // Uses declared layer JARs instead of the inherited binary classpath. §FS-native-invocation.3.
    @Issue("https://github.com/graalvm/native-build-tools/issues/957")
    def "uses layer jars instead of inherited binary classpath for layer-create build"() {
        given:
        def project = newProject()
        project.plugins.apply(ApplicationPlugin)
        project.plugins.apply(NativeImagePlugin)
        def appJar = testDirectory.resolve("app.jar").toFile()
        def dependencyJar = testDirectory.resolve("dependency.jar").toFile()
        appJar.text = "application"
        dependencyJar.text = "dependency"
        def options = project.extensions.getByType(GraalVMExtension).binaries.create("libdependencies")
        options.classpath.from(appJar)
        options.createLayer {
            it.modules.add("java.base")
            it.jars.from(dependencyJar)
        }
        options.excludeConfigArgs.set([])
        options.configurationFileDirectories.setFrom([])

        when:
        def args = new NativeImageCommandLineProvider(
                project.provider { options },
                project.provider { "libdependencies" },
                project.provider { testDirectory.toString() },
                project.provider { testDirectory.toString() },
                project.objects.fileProperty(),
                project.provider { false },
                project.provider { 25 },
                project.provider { false },
                project.provider { false }
        ).asArguments()

        then:
        options.classpath.files.contains(appJar)
        !args.contains(appJar.absolutePath)
        args.any { it.startsWith("${NativeImageFlags.LAYER_CREATE}=libdependencies.nil") && it.contains("path=${dependencyJar}") }
        args.contains("-cp")
        args.contains(dependencyJar.absolutePath)
    }

    def "retains binary classpath for package-based layer-create build"() {
        given:
        def project = newProject()
        project.plugins.apply(ApplicationPlugin)
        project.plugins.apply(NativeImagePlugin)
        def appJar = testDirectory.resolve("app.jar").toFile()
        appJar.text = "application"
        def options = project.extensions.getByType(GraalVMExtension).binaries.create("libpackages")
        options.classpath.from(appJar)
        options.createLayer {
            it.modules.add("java.base")
            it.packages.add("org.graalvm.demo")
        }
        options.excludeConfigArgs.set([])
        options.configurationFileDirectories.setFrom([])

        when:
        def args = new NativeImageCommandLineProvider(
                project.provider { options },
                project.provider { "libpackages" },
                project.provider { testDirectory.toString() },
                project.provider { testDirectory.toString() },
                project.objects.fileProperty(),
                project.provider { false },
                project.provider { 25 },
                project.provider { false },
                project.provider { false }
        ).asArguments()

        then:
        options.classpath.files.contains(appJar)
        args.any { it.startsWith("${NativeImageFlags.LAYER_CREATE}=libpackages.nil") && it.contains("package=org.graalvm.demo") }
        args.contains("-cp")
        args.contains(appJar.absolutePath)
    }

    // Keeps the logical bundle name separate from the native shared-library image name. §FS-native-tasks.1.
    def "layer-create build preserves its native library output name"() {
        given:
        def project = newProject()
        project.plugins.apply(ApplicationPlugin)
        project.plugins.apply(NativeImagePlugin)
        def options = project.extensions.getByType(GraalVMExtension).binaries.create("libdependencies")
        options.createLayer {
            it.layerName.set("dependencies")
            it.modules.add("java.base")
        }
        options.excludeConfigArgs.set([])
        options.configurationFileDirectories.setFrom([])

        when:
        def args = commandLineArguments(project, options, "libdependencies")
        def outputOption = args.indexOf("-o")

        then:
        args.any { it.startsWith("${NativeImageFlags.LAYER_CREATE}=dependencies.nil") }
        outputOption >= 0
        args[outputOption + 1] == testDirectory.resolve("libdependencies").toString()
    }

    def "renders typed layer files through the shared layer argument utility"() {
        given:
        def project = newProject()
        project.plugins.apply(ApplicationPlugin)
        project.plugins.apply(NativeImagePlugin)
        def layerFile = testDirectory.resolve("base.nil").toFile()
        layerFile.text = "layer"
        def options = project.extensions.getByType(GraalVMExtension).binaries.getByName("main")
        options.layerFiles.from(layerFile)
        options.excludeConfigArgs.set([])
        options.configurationFileDirectories.setFrom([])

        when:
        def args = new NativeImageCommandLineProvider(
                project.provider { options },
                project.provider { "main" },
                project.provider { testDirectory.toString() },
                project.provider { testDirectory.toString() },
                project.objects.fileProperty(),
                project.provider { false },
                project.provider { 25 },
                project.provider { false },
                project.provider { false }
        ).asArguments()

        then:
        args.contains(NativeImageFlags.UNLOCK_EXPERIMENTAL_VMOPTIONS)
        args.contains("${NativeImageFlags.LAYER_USE}=${layerFile.absolutePath}".toString())
    }

    // Dependency Preserve paths are shared-rendered before user build arguments. §FS-native-invocation.3.
    @Issue("https://github.com/graalvm/native-build-tools/issues/978")
    def "renders one path-only Preserve argument for a configured binary"() {
        given:
        def project = newProject()
        project.plugins.apply(ApplicationPlugin)
        project.plugins.apply(NativeImagePlugin)
        def repositoryDirectory = testDirectory.resolve("repository with spaces").toFile()
        repositoryDirectory.mkdirs()
        def directJar = new File(repositoryDirectory, "direct-dependency.jar")
        def transitiveJar = new File(repositoryDirectory, "transitive-dependency.jar")
        directJar.text = "direct"
        transitiveJar.text = "transitive"
        project.repositories.flatDir { repository ->
            repository.dirs(repositoryDirectory)
        }
        def options = project.extensions.getByType(GraalVMExtension).binaries.getByName("main")
        options.preserve {
            it.dependencies("test:direct:dependency")
            it.dependencies("test:transitive:dependency")
        }
        options.buildArgs.add("--user-option")
        options.excludeConfigArgs.set([])
        options.configurationFileDirectories.setFrom([])

        when:
        def args = commandLineArguments(project, options, "main")
        def preserve = args.find { it.startsWith(NativeImageFlags.PRESERVE + "=") }

        then:
        !args.contains(NativeImageFlags.UNLOCK_EXPERIMENTAL_VMOPTIONS)
        preserve.contains("path=${directJar}")
        preserve.contains("path=${transitiveJar}")
        args.indexOf(preserve) < args.indexOf("--user-option")
    }

    def "omits Preserve arguments when the binary does not configure Preserve"() {
        given:
        def project = newProject()
        project.plugins.apply(ApplicationPlugin)
        project.plugins.apply(NativeImagePlugin)
        def options = project.extensions.getByType(GraalVMExtension).binaries.getByName("main")
        options.excludeConfigArgs.set([])
        options.configurationFileDirectories.setFrom([])

        when:
        def args = commandLineArguments(project, options, "main")

        then:
        !args.any { it.startsWith(NativeImageFlags.PRESERVE + "=") }
        !args.contains(NativeImageFlags.UNLOCK_EXPERIMENTAL_VMOPTIONS)
    }

    def "rejects a configured empty Preserve selection before execution"() {
        given:
        def project = newProject()
        project.plugins.apply(ApplicationPlugin)
        project.plugins.apply(NativeImagePlugin)
        def options = project.extensions.getByType(GraalVMExtension).binaries.getByName("main")
        options.preserve { }

        when:
        BuildNativeImageTask.validatePreserveConfiguration(options)

        then:
        def error = thrown(org.gradle.api.GradleException)
        error.message.contains("Preserve has no resolved dependencies")
    }

    // Retains the deprecated binary-scoped layer producer and string consumer as a working adapter. §FS-native-invocation.3.
    def "legacy layer adapters wire producer and consumer and render layer arguments"() {
        given:
        def project = newProject()
        project.plugins.apply(ApplicationPlugin)
        project.plugins.apply(NativeImagePlugin)
        def extension = project.extensions.getByType(GraalVMExtension)
        def producer = extension.binaries.create("liblegacy")
        producer.createLayer {
            it.modules.add("java.base")
        }
        producer.excludeConfigArgs.set([])
        producer.configurationFileDirectories.setFrom([])
        def consumer = extension.binaries.getByName("main")
        consumer.useLayer("liblegacy")
        consumer.excludeConfigArgs.set([])
        consumer.configurationFileDirectories.setFrom([])
        def producerTask = project.tasks.getByName("nativeLiblegacyCompile") as BuildNativeImageTask
        def consumerTask = project.tasks.getByName("nativeCompile") as BuildNativeImageTask
        def layerFile = producerTask.createdLayerFile.get().asFile

        when:
        def producerArgs = commandLineArguments(project, producer, "liblegacy")
        def consumerArgs = commandLineArguments(project, consumer, "main")

        then:
        consumer.layerFiles.files == [layerFile] as Set
        consumerTask.taskDependencies.getDependencies(consumerTask).contains(producerTask)
        producerArgs.any {
            it.startsWith("${NativeImageFlags.LAYER_CREATE}=liblegacy.nil") && it.contains("module=java.base")
        }
        producerArgs[producerArgs.indexOf("-o") + 1].endsWith(File.separator + "liblegacy")
        consumerArgs.contains("${NativeImageFlags.LAYER_USE}=${layerFile.absolutePath}".toString())
    }

    // Plain Gradle consoles disable Native Image colors with version-appropriate arguments. §FS-native-invocation.3
    @Issue("https://github.com/graalvm/native-build-tools/issues/366")
    def "disables colorful Native Image output for Gradle's plain console with JDK #nativeImageVersion"() {
        given:
        def project = newProject()
        project.plugins.apply(ApplicationPlugin)
        project.plugins.apply(NativeImagePlugin)
        def options = project.extensions.getByType(GraalVMExtension).binaries.getByName("main")
        options.richOutput.set(true)
        options.excludeConfigArgs.set([])
        options.configurationFileDirectories.setFrom([])

        when:
        def args = new NativeImageCommandLineProvider(
                project.provider { options },
                project.provider { "main" },
                project.provider { testDirectory.toString() },
                project.provider { testDirectory.toString() },
                project.objects.fileProperty(),
                project.provider { false },
                project.provider { nativeImageVersion },
                project.provider { false },
                project.provider { true }
        ).asArguments()

        then:
        args.contains(disabledColorArgument)
        !args.contains(NativeImageFlags.BUILD_OUTPUT_COLORFUL)

        where:
        nativeImageVersion | disabledColorArgument
        17                 | NativeImageFlags.BUILD_OUTPUT_COLORLESS
        21                 | "--color=never"
    }

    // Rich Gradle output enables Native Image colors with version-appropriate arguments. §FS-native-invocation.3
    @Issue("https://github.com/graalvm/native-build-tools/issues/366")
    def "uses the rich-output setting when Gradle enables colors with JDK #nativeImageVersion"() {
        given:
        def project = newProject()
        project.plugins.apply(ApplicationPlugin)
        project.plugins.apply(NativeImagePlugin)
        def options = project.extensions.getByType(GraalVMExtension).binaries.getByName("main")
        options.richOutput.set(true)
        options.excludeConfigArgs.set([])
        options.configurationFileDirectories.setFrom([])

        when:
        def args = new NativeImageCommandLineProvider(
                project.provider { options },
                project.provider { "main" },
                project.provider { testDirectory.toString() },
                project.provider { testDirectory.toString() },
                project.objects.fileProperty(),
                project.provider { false },
                project.provider { nativeImageVersion },
                project.provider { false },
                project.provider { false }
        ).asArguments()

        then:
        args.contains(enabledColorArgument)
        !args.contains(NativeImageFlags.BUILD_OUTPUT_COLORLESS)

        where:
        nativeImageVersion | enabledColorArgument
        17                 | NativeImageFlags.BUILD_OUTPUT_COLORFUL
        21                 | "--color=always"
    }

    private List<String> commandLineArguments(project, options, String imageName) {
        new NativeImageCommandLineProvider(
                project.provider { options },
                project.provider { imageName },
                project.provider { testDirectory.toString() },
                project.provider { testDirectory.toString() },
                project.objects.fileProperty(),
                project.provider { false },
                project.provider { 25 },
                project.provider { false },
                project.provider { false }
        ).asArguments()
    }
}
