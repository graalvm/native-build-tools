import org.gradle.api.artifacts.VersionCatalogsExtension
import org.gradle.api.publish.plugins.PublishingPlugin
import org.gradle.api.tasks.Delete
import org.gradle.api.tasks.bundling.Zip
import org.gradle.kotlin.dsl.*
import java.nio.file.Files.createTempDirectory

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

plugins {
    base
}

tasks.named("clean") {
    gradle.includedBuilds.forEach {
        dependsOn(it.task(":clean"))
    }
}

tasks.register("test") {
    gradle.includedBuilds.forEach {
        dependsOn(it.task(":test"))
    }
}

tasks.register("inspections") {
    gradle.includedBuilds.forEach {
        dependsOn(it.task(":inspections"))
    }
}

tasks.named("check") {
    gradle.includedBuilds.forEach {
        dependsOn(it.task(":check"))
    }
}

tasks.register("showPublications") {
    gradle.includedBuilds.forEach {
        if (it.name != "docs" && !it.projectDir.path.contains("build-logic")) {
            dependsOn(it.task(":showPublications"))
        }
    }
}

listOf(
    "publishTo" to "MavenLocal",
    "publishAllPublicationsTo" to "CommonRepository",
    "publishAllPublicationsTo" to "SnapshotsRepository",
    "publishAllPublicationsTo" to "NexusRepository",
).forEach { entry ->
    val (taskPrefix, repo) = entry
    tasks.register("$taskPrefix$repo") {
        description = "Publishes all artifacts to the ${repo.decapitalize()} repository"
        group = PublishingPlugin.PUBLISH_TASK_GROUP
        gradle.includedBuilds.forEach {
            if (it.name != "docs" && !it.projectDir.path.contains("build-logic")) {
                dependsOn(it.task(":$taskPrefix$repo"))
            }
        }
        doFirst {
            if (gradle.startParameter.isParallelProjectExecutionEnabled) {
                throw RuntimeException("Publishing should be done using --no-parallel")
            }
        }
    }
}

val commonRepo = layout.buildDirectory.dir("common-repo")
val snapshotsRepo = layout.buildDirectory.dir("snapshots")

val pruneCommonRepo = tasks.register<Delete>("pruneCommonRepository") {
    delete(commonRepo)
}

val catalogs = extensions.getByType<VersionCatalogsExtension>()
val libs = catalogs.named("libs")

val nativeBuildToolsVersion = libs.findVersion("nativeBuildTools").get().requiredVersion
val junitJupiterVersion = libs.findVersion("junitJupiter").get().requiredVersion
val junitPlatformVersion = libs.findVersion("junitPlatform").get().requiredVersion

tasks.register<Zip>("releaseZip") {
    archiveVersion.set(nativeBuildToolsVersion)
    dependsOn(pruneCommonRepo, "publishAllPublicationsToCommonRepository")
    from(commonRepo) {
        exclude("**/*.sha256")
        exclude("**/*.sha512")
    }
}

val updateSamples by tasks.registering

mapOf(
    "updateSamplesDir" to "samples",
    "updateMavenReprosDir" to "native-maven-plugin/reproducers"
).forEach { (taskName, dir) ->
    val t = tasks.register<org.graalvm.build.samples.SamplesUpdateTask>(taskName) {
        inputDirectory.set(layout.projectDirectory.dir(dir))
        versions.put("native.gradle.plugin.version", nativeBuildToolsVersion)
        versions.put("native.maven.plugin.version", nativeBuildToolsVersion)
        versions.put("junit.jupiter.version", junitJupiterVersion)
        versions.put("junit.platform.version", junitPlatformVersion)
        versions.put("junit.platform.native.version", nativeBuildToolsVersion)
    }
    updateSamples.configure {
        dependsOn(t)
    }
}

val snapshotDir: File = createTempDirectory("snapshot-repo").toFile()
// val snapshotDir: File = snapshotsRepo.get().asFile.toPath().resolve("native-build-tools").toFile()
// Having nested git directories tend to break for me, so for now we'll use temp directory every time.

val cloneSnapshots = tasks.register<org.graalvm.build.tasks.GitClone>("cloneSnapshotRepository") {
    repositoryUri.set("git@github.com:graalvm/native-build-tools.git")
    repositoryDirectory.set(snapshotDir)
    branch.set("snapshots")
}

val prepareRepository = tasks.register<org.graalvm.build.tasks.GitReset>("resetHead") {
    dependsOn(cloneSnapshots)
    repositoryDirectory.set(snapshotDir)
    mode.set("hard")
    ref.set("25ecdec020f57dbe980eeb052c71659ccd0d9bcc")
}

val copySnapshots = tasks.register<Copy>("copySnapshots") {
    dependsOn(prepareRepository)
    from(snapshotsRepo.get().asFile.toPath())
    into(snapshotDir)
    include("org/**")
}

val addSnapshots = tasks.register<org.graalvm.build.tasks.GitAdd>("addSnapshots") {
    dependsOn(copySnapshots)
    repositoryDirectory.set(snapshotDir)
    pattern.set("*")
}

val commitSnapshots = tasks.register<org.graalvm.build.tasks.GitCommit>("commitSnapshots") {
    dependsOn(addSnapshots)
    repositoryDirectory.set(snapshotDir)
    message.set("Publishing new snapshot")
    amend.set(false)
}

val pushSnapshots = tasks.register<org.graalvm.build.tasks.GitPush>("pushSnapshots") {
    dependsOn(commitSnapshots)
    repositoryDirectory.set(snapshotDir)
    force.set(true)
}

tasks.named("publishAllPublicationsToSnapshotsRepository") {
    dependsOn(prepareRepository)
    finalizedBy(pushSnapshots)
    onlyIf {
        nativeBuildToolsVersion.endsWith("-SNAPSHOT")
    }
}
