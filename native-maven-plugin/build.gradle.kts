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

import org.graalvm.build.maven.SeedMavenRepository
import org.graalvm.build.maven.ValidateMavenRepositorySeed

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
    implementation(libs.cyclonedx.maven.plugin)
    implementation(libs.plugin.executor.maven)

    compileOnly(libs.plexus.utils)
    compileOnly(libs.plexus.xml)
    compileOnly(libs.maven.pluginApi)
    compileOnly(libs.maven.core)
    compileOnly(libs.maven.artifact)
    compileOnly(libs.maven.pluginAnnotations)

    mavenEmbedder(libs.maven.embedder)
    mavenEmbedder(libs.maven.resolver.basic)
    mavenEmbedder(libs.maven.resolver.transport.http)
    mavenEmbedder(libs.maven.resolver.transport.file)
    mavenEmbedder(libs.maven.compat)
    mavenEmbedder(libs.slf4j.simple)

    testImplementation(libs.test.spock)
    testImplementation(libs.maven.core)
    testImplementation(libs.maven.artifact)
    testImplementation(libs.jetty.server)
    testRuntimeOnly(libs.test.junit.platform.launcher)
    testRuntimeOnly(libs.slf4j.simple.test)

    testFixturesImplementation(libs.test.spock)
    testFixturesImplementation(libs.jetty.server)

    functionalTestCommonRepository(libs.utils)
    functionalTestCommonRepository(libs.junitPlatformNative)
    functionalTestCommonRepository(libs.jvmReachabilityMetadata)
    functionalTestCommonRepository("org.graalvm.internal:library-with-reflection")

    functionalTestImplementation(libs.test.spock)
    functionalTestRuntimeOnly(libs.test.junit.platform.launcher)
    functionalTestRuntimeOnly(libs.slf4j.simple.test)
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

val seedingDir = project.layout.buildDirectory.dir("maven-seeding")

val prepareSeedingProject = tasks.register<Sync>("prepareSeedingProject") {
    from(files("src/seeding-build"))
    into(seedingDir)
    outputs.upToDateWhen { false }
}

// Online seed production stages and atomically publishes a complete repository. §E2E-functional-tests.4
val prepareMavenLocalRepo = tasks.register<SeedMavenRepository>("prepareMavenLocalRepo") {
    dependsOn(prepareSeedingProject)
    projectDirectory.set(prepareSeedingProject.map { seedingDir.get() })
    settingsFile.set(layout.projectDirectory.file("config/settings.xml"))
    pomFile.set(seedingDir.map { it.file("pom.xml") })
    mavenEmbedderClasspath.from(configurations.mavenEmbedder)
    outputDirectory.set(localRepositoryDir)
    updateSnapshots.set(true)
    seedProperties.put("junit.jupiter.version", libs.versions.junitJupiter)
    seedProperties.put("native.maven.plugin.version", libs.versions.nativeBuildTools)
    seedProperties.put("junit.platform.native.version", libs.versions.nativeBuildTools)
    seedProperties.put("exec.mainClass", "org.graalvm.demo.Application")
    seedProperties.put("openjson.version", libs.versions.openjson)
    seedProperties.put("cyclonedx.maven.version", libs.versions.cyclonedxMaven)
    seedProperties.put("plugin.executor.maven.version", libs.versions.pluginExecutorMaven)
    arguments.set(listOf(
            "-q",
            "-Djunit.jupiter.version=${libs.versions.junitJupiter.get()}",
            "-Dnative.maven.plugin.version=${libs.versions.nativeBuildTools.get()}",
            "-Djunit.platform.native.version=${libs.versions.nativeBuildTools.get()}",
            "-Dexec.mainClass=org.graalvm.demo.Application",
            "-Dopenjson.version=${libs.versions.openjson.get()}",
            "-Dcyclonedx.maven.version=${libs.versions.cyclonedxMaven.get()}",
            "-Dplugin.executor.maven.version=${libs.versions.pluginExecutorMaven.get()}",
            "package",
            "test",
            "install",
            "exec:java",
            "help:effective-pom"
    )
    )
}

// Offline prerequisites validate the existing seed without writes or Maven execution. §E2E-functional-tests.4
val validateMavenLocalRepo = tasks.register<ValidateMavenRepositorySeed>(
    "validateMavenLocalRepo",
    prepareMavenLocalRepo.get()
)
val mavenRepositoryPrerequisite = if (gradle.startParameter.isOffline) {
    validateMavenLocalRepo
} else {
    prepareMavenLocalRepo
}

tasks {
    generatePluginDescriptor {
        dependsOn(mavenRepositoryPrerequisite)
        commonRepository.set(repoDirectory)
        localRepository.set(localRepositoryDir)
        offline.set(gradle.startParameter.isOffline)
    }
}

val launcher = javaToolchains.launcherFor {
    languageVersion.set(
        providers.gradleProperty("mavenFunctionalTestJavaVersion")
            .orElse(providers.gradleProperty("javaToolchainVersion"))
            .orElse("17")
            .map(String::toInt)
            .map(JavaLanguageVersion::of)
    )
}

tasks {
    functionalTest {
        javaLauncher.set(launcher)
        dependsOn(mavenRepositoryPrerequisite, publishAllPublicationsToCommonRepository)
        systemProperty("graalvm.version", libs.versions.graalvm.get())
        systemProperty("junit.jupiter.version", libs.versions.junitJupiter.get())
        systemProperty("native.maven.plugin.version", libs.versions.nativeBuildTools.get())
        systemProperty("junit.platform.native.version", libs.versions.nativeBuildTools.get())
        systemProperty("common.repo.uri", repoDirectory.get().asFile.toURI().toASCIIString())
        systemProperty("seed.repo.uri", localRepositoryDir.get().asFile.toURI().toASCIIString())
        systemProperty("maven.settings", layout.projectDirectory.file("config/settings.xml").asFile.absolutePath)
        systemProperty("maven.offline", gradle.startParameter.isOffline)
        systemProperty("java.executable", javaLauncher.get().executablePath.asFile.absolutePath)
        inputs.files(configurations.mavenEmbedder)
        doFirst {
            systemProperty("maven.classpath", configurations.mavenEmbedder.get().asPath)
        }
    }
}

tasks.withType<Checkstyle>().configureEach {
    configFile = layout.projectDirectory.dir("../config/checkstyle.xml").asFile
    // generated code
    exclude("**/RuntimeMetadata*")
}

tasks {
    withType<Javadoc>().configureEach { options.encoding = "UTF-8" }
}
