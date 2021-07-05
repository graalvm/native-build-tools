package org.graalvm.build.samples

import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.provider.MapProperty
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputDirectory
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.TaskAction
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.util.regex.Pattern

abstract class SamplesUpdateTask : DefaultTask() {
    @get:InputDirectory
    abstract val inputDirectory: DirectoryProperty

    @get:Input
    abstract val versions: MapProperty<String, String>

    @TaskAction
    fun processSamples() {
        inputDirectory.get().asFile.listFiles()?.forEach(::processSample)
    }

    private fun processSample(sample: File) {
        processPomFile(File(sample, "pom.xml"))
        processGradleFile(File(sample, "gradle.properties"))
    }

    private fun processGradleFile(gradleFile: File) {
        if (gradleFile.exists()) {
            gradleFile.process { key ->
                val quoted = Pattern.quote(key)
                Regex("($quoted(?:\\s*)=(?:\\s*))(.+)()")
            }
        }
    }

    private fun processPomFile(pomFile: File) {
        if (pomFile.exists()) {
            pomFile.process { key ->
                val quoted = Pattern.quote(key)
                Regex("(.*?<$quoted>)(.+?)(</$quoted>.*)")
            }
        }
    }

    private fun File.process(matcher: (String) -> Regex) {
        val allVersions = versions.get()
        val matchers = allVersions.keys.map { key -> Pair(key, matcher(key)) }
        val stringWriter = StringWriter()
        PrintWriter(stringWriter).use { writer ->
            forEachLine { str ->
                val line = matchers.find { (_, matcher) -> matcher.matches(str) }?.let { (key, matcher) ->
                    str.replace(matcher, "$1${allVersions.get(key)}$3")
                } ?: str
                writer.println(line)
            }
        }
        this.writeText(stringWriter.toString())
    }
}
