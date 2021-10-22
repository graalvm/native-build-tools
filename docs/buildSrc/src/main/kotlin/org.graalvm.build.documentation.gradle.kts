/*
 * Copyright (c) 2020, 2021, Oracle and/or its affiliates. All rights reserved.
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

import org.gradle.api.DefaultTask
import org.gradle.api.attributes.Category
import org.gradle.api.attributes.DocsType
import org.gradle.api.file.ArchiveOperations
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.FileSystemOperations
import org.gradle.api.tasks.*
import org.gradle.kotlin.dsl.*
import java.io.File
import java.lang.RuntimeException
import javax.inject.Inject

plugins {
    id("org.asciidoctor.jvm.convert")
    id("org.ajoberstar.git-publish")
    java
}

val javadocs by configurations.creating {
    isCanBeConsumed = false
    isCanBeResolved = true
    attributes {
        attribute(Category.CATEGORY_ATTRIBUTE, objects.named(Category.DOCUMENTATION))
        attribute(DocsType.DOCS_TYPE_ATTRIBUTE, objects.named(DocsType.JAVADOC))
    }
}
// Prepares javadocs for publication by exploding the jars into a directory
// which will be included in docs
val resolveJavadocs = tasks.register<ResolveJavadocs>("resolveJavadocs") {
    elements.from(javadocs)
    outputDirectory.set(layout.buildDirectory.dir("exploded-javadocs"))
}

tasks {
    asciidoctor {
        baseDirFollowsSourceDir()
        resources {
            from("src/docs/asciidoc/highlight") {
                into("highlight")
            }
            from(resolveJavadocs) {
                into("javadocs")
            }
            from("src/docs/asciidoc/css") {
                into("css")
            }
            from("src/docs/asciidoc/js") {
                into("js")
            }
        }
    }
}

gitPublish {

    branch.set("gh-pages")
    sign.set(false)

    contents {
        from(tasks.asciidoctor) {
            into(providers.provider { "$version" })
        }
    }

    preserve {
        include("**")
        exclude(KotlinClosure1<FileVisitDetails, Boolean>({ name.equals("$version") }, this, this))
    }

    commitMessage.set(providers.provider {
        "Publishing documentation for version $version"
    })
}

@CacheableTask
abstract class ResolveJavadocs : DefaultTask() {
    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val elements: ConfigurableFileCollection

    @get:OutputDirectory
    abstract val outputDirectory: DirectoryProperty

    @get:Inject
    abstract val archiveOperations: ArchiveOperations

    @get:Inject
    abstract val fileSystemOperations: FileSystemOperations

    @TaskAction
    fun execute() {
        elements.files.forEach { docs ->
            fileSystemOperations.copy {
                into(outputDirectory.dir(docs.baseName))
                from(archiveOperations.zipTree(docs))
            }
        }
    }

    private val File.baseName
        get() = Regex("([a-zA-Z\\-]+)-([0-9].?(-[\\-a-zA-Z0-9]+)?)+-javadoc.jar")
                .matchEntire(name)!!.groups.get(1)!!.value
}
