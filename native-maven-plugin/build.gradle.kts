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

import org.graalvm.build.maven.MavenTask
import org.gradle.util.GFileUtils

plugins {
    `java-library`
    groovy
    checkstyle
    `java-test-fixtures`
    id("org.graalvm.build.java")
    id("org.graalvm.build.publishing")
    id("org.graalvm.build.maven-plugin")
    id("org.graalvm.build.maven-functional-testing")
    id("org.graalvm.build.github-actions-helper")
}

maven {
    name.set("GraalVM Native Maven Plugin")
    description.set("Plugin that provides support for building and testing of GraalVM native images (ahead-of-time compiled Java code)")
}

dependencies {
    implementation(libs.utils)
    implementation(libs.openjson)
    implementation(libs.jvmReachabilityMetadata)
    implementation(libs.plexus.utils)
    implementation(libs.plexus.xml)
    implementation(libs.cyclonedx.maven.plugin)
    implementation(libs.plugin.executor.maven)

    compileOnly(libs.maven.pluginApi)
    compileOnly(libs.maven.core)
    compileOnly(libs.maven.artifact)
    compileOnly(libs.maven.pluginAnnotations)

    mavenEmbedder(libs.maven.embedder)
    mavenEmbedder(libs.maven.aether.connector)
    mavenEmbedder(libs.maven.aether.wagon)
    mavenEmbedder(libs.maven.wagon.http)
    mavenEmbedder(libs.maven.wagon.file)
    mavenEmbedder(libs.maven.wagon.provider)
    mavenEmbedder(libs.maven.compat)
    mavenEmbedder(libs.slf4j.simple)

    testImplementation(libs.test.spock)
    testImplementation(libs.maven.core)
    testImplementation(libs.maven.artifact)
    testImplementation(libs.jetty.server)

    testFixturesImplementation(libs.test.spock)
    testFixturesImplementation(libs.jetty.server)

    functionalTestCommonRepository(libs.utils)
    functionalTestCommonRepository(libs.junitPlatformNative)
    functionalTestCommonRepository(libs.jvmReachabilityMetadata)
    functionalTestCommonRepository("org.graalvm.internal:library-with-reflection")

    functionalTestImplementation(libs.test.spock)
    functionalTestRuntimeOnly(libs.slf4j.simple)
}

publishing {
    publications {
        create<MavenPublication>("mavenPlugin") {
            from(components["java"])
            pom {
                packaging = "maven-plugin"
            }
        }
    }
}

val localRepositoryDir = project.layout.buildDirectory.dir("maven-seeded-repo")

tasks {
    generatePluginDescriptor {
        dependsOn(prepareMavenLocalRepo)
        commonRepository.set(repoDirectory)
        localRepository.set(localRepositoryDir)
    }
}

val seedingDir = project.layout.buildDirectory.dir("maven-seeding")

val prepareSeedingProject = tasks.register<Sync>("prepareSeedingProject") {
    from(files("src/seeding-build"))
    into(seedingDir)
    outputs.upToDateWhen { false }
}

val prepareMavenLocalRepo = tasks.register<MavenTask>("prepareMavenLocalRepo") {
    dependsOn(prepareSeedingProject)
    projectDirectory.set(prepareSeedingProject.map { seedingDir.get() })
    settingsFile.set(layout.projectDirectory.file("config/settings.xml"))
    pomFile.set(seedingDir.map { it.file("pom.xml") })
    mavenEmbedderClasspath.from(configurations.mavenEmbedder)
    outputDirectory.set(localRepositoryDir)
    arguments.set(listOf(
            "-q",
            "-Dproject.build.directory=${File(temporaryDir, "target")}",
            "-Dmaven.repo.local=${localRepositoryDir.get().asFile.absolutePath}",
            "-Djunit.jupiter.version=${libs.versions.junitJupiter.get()}",
            "-Dnative.maven.plugin.version=${libs.versions.nativeBuildTools.get()}",
            "-Djunit.platform.native.version=${libs.versions.nativeBuildTools.get()}",
            "-Dexec.mainClass=org.graalvm.demo.Application",
            "package",
            "test",
            "install",
            "exec:java"
    )
    )

    doFirst {
        delete { delete { localRepositoryDir.get().asFile } }
    }
}

val launcher = javaToolchains.launcherFor {
    languageVersion.set(JavaLanguageVersion.of(11))
}

tasks {
    functionalTest {
        javaLauncher.set(launcher)
        dependsOn(prepareMavenLocalRepo, publishAllPublicationsToCommonRepository)
        systemProperty("graalvm.version", libs.versions.graalvm.get())
        systemProperty("junit.jupiter.version", libs.versions.junitJupiter.get())
        systemProperty("native.maven.plugin.version", libs.versions.nativeBuildTools.get())
        systemProperty("junit.platform.native.version", libs.versions.nativeBuildTools.get())
        systemProperty("common.repo.uri", repoDirectory.get().asFile.toURI().toASCIIString())
        systemProperty("seed.repo.uri", localRepositoryDir.get().asFile.toURI().toASCIIString())
        systemProperty("maven.classpath", configurations.mavenEmbedder.get().asPath)
        systemProperty("maven.settings", layout.projectDirectory.file("config/settings.xml").asFile.absolutePath)
        systemProperty("java.executable", javaLauncher.get().executablePath.asFile.absolutePath)
    }
}

tasks.withType<Checkstyle>().configureEach {
    configFile = layout.projectDirectory.dir("../config/checkstyle.xml").asFile
    // generated code
    exclude("**/RuntimeMetadata*")
}

