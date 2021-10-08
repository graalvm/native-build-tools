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

listOf(
        "publishTo" to "MavenLocal",
        "publishAllPublicationsTo" to "CommonRepository",
        "publishAllPublicationsTo" to "SnapshotsRepository",
).forEach { entry ->
    val (taskPrefix, repo) = entry
    tasks.register("$taskPrefix$repo") {
        description = "Publishes all artifacts to the ${repo.decapitalize()} repository"
        group = PublishingPlugin.PUBLISH_TASK_GROUP
        gradle.includedBuilds.forEach {
            if (it.name != "docs") {
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

tasks.register<Zip>("releaseZip") {
    dependsOn(pruneCommonRepo, "publishAllPublicationsToCommonRepository")
    from(commonRepo) {
        exclude("**/*.sha256")
        exclude("**/*.sha512")
    }
}

tasks.register<org.graalvm.build.samples.SamplesUpdateTask>("updateSamples") {
    inputDirectory.set(layout.projectDirectory.dir("samples"))
    versions.put("native.gradle.plugin.version", libs.versions.nativeBuildTools.get())
    versions.put("native.maven.plugin.version", libs.versions.nativeBuildTools.get())
    versions.put("junit.jupiter.version", libs.versions.junitJupiter.get())
    versions.put("junit.platform.version", libs.versions.junitPlatform.get())
    versions.put("junit.platform.native.version", libs.versions.nativeBuildTools.get())
}

val cloneSnapshots = tasks.register<org.graalvm.build.tasks.GitClone>("cloneSnapshotRepository") {
    repositoryUri.set("git@github.com:graalvm/native-build-tools.git")
//    repositoryUri.set(file(".").absolutePath)
    repositoryDirectory.set(layout.buildDirectory.dir("snapshots"))
    branch.set("snapshots")
}

val prepareRepository = tasks.register<org.graalvm.build.tasks.GitReset>("resetHead") {
    dependsOn(cloneSnapshots)
    repositoryDirectory.set(layout.buildDirectory.dir("snapshots"))
    mode.set(org.eclipse.jgit.api.ResetCommand.ResetType.HARD)
    ref.set("25ecdec020f57dbe980eeb052c71659ccd0d9bcc")
}

val addSnapshots = tasks.register<org.graalvm.build.tasks.GitAdd>("addSnapshots") {
    dependsOn(prepareRepository)
    repositoryDirectory.set(layout.buildDirectory.dir("snapshots"))
    pattern.set("org/")
}

val commitSnapshots = tasks.register<org.graalvm.build.tasks.GitCommit>("commitSnapshots") {
    dependsOn(addSnapshots)
    repositoryDirectory.set(layout.buildDirectory.dir("snapshots"))
    message.set("Publishing new snapshot")
    amend.set(false)
}

val pushSnapshots = tasks.register<org.graalvm.build.tasks.GitPush>("pushSnapshots") {
    dependsOn(commitSnapshots)
    repositoryDirectory.set(layout.buildDirectory.dir("snapshots"))
    force.set(true)
}

tasks.named("publishAllPublicationsToSnapshotsRepository") {
    dependsOn(prepareRepository)
    finalizedBy(pushSnapshots)
    onlyIf {
        libs.versions.nativeBuildTools.get().endsWith("-SNAPSHOT")
    }
}
