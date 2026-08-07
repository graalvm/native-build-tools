package org.graalvm.buildtools.gradle

import org.graalvm.buildtools.gradle.tasks.StageNativeImageLayerRuntimeFilesTask
import org.gradle.testfixtures.ProjectBuilder
import spock.lang.Specification
import spock.lang.TempDir

import java.nio.file.Path

// Runtime staging keeps selected layers isolated for deployment. §FS-plugin-model.2.
class StageNativeImageLayerRuntimeFilesTaskTest extends Specification {
    @TempDir
    Path temporaryDirectory

    def "stages every runtime library without overwriting files from another layer"() {
        given:
        def project = ProjectBuilder.builder().withProjectDir(temporaryDirectory.toFile()).build()
        def first = layer("first", "same.so", "first")
        def second = layer("second", "same.so", "second")
        def task = project.tasks.create("stageLayers", StageNativeImageLayerRuntimeFilesTask)
        task.layerFiles.from(first.resolve("first.nil"), second.resolve("second.nil"))
        task.layerDirectories.from(first, second)
        task.destinationDirectory.set(project.layout.buildDirectory.dir("staged"))

        when:
        task.stage()

        then:
        project.file("build/staged/first/same.so").text == "first"
        project.file("build/staged/second/same.so").text == "second"
        !project.file("build/staged/first/first.nil").exists()
    }

    private Path layer(String name, String runtimeName, String contents) {
        def directory = temporaryDirectory.resolve(name)
        directory.toFile().mkdirs()
        directory.resolve("${name}.nil").toFile().text = "nil"
        directory.resolve(runtimeName).toFile().text = contents
        directory
    }
}
