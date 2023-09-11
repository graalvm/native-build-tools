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
    groovy
    `java-test-fixtures`
    id("org.graalvm.build.maven-embedder")
}

val functionalTest by sourceSets.creating

val functionalTestCommonRepository by configurations.creating {
    // This configuration will trigger the composite build
    // which builds the JUnit native library, and publish it to a repository
    // which can then be injected into tests
    isCanBeResolved = true
    isCanBeConsumed = false
    attributes {
        attribute(Category.CATEGORY_ATTRIBUTE, objects.named("repository"))
    }
}

configurations {
    "functionalTestImplementation" {
        extendsFrom(testImplementation.get())
    }
}

val graalVmHomeProvider = providers.environmentVariable("GRAALVM_HOME")

// Add a task to run the functional tests
tasks.register<Test>("functionalTest") {
    // Any change to samples invalidates functional tests
    inputs.files(files("../samples"))
    inputs.files(files("reproducers"))
    inputs.files(functionalTestCommonRepository)
    systemProperty("common.repo.url", functionalTestCommonRepository.incoming.files.files.first())
    testClassesDirs = functionalTest.output.classesDirs
    classpath = functionalTest.runtimeClasspath
}

tasks.named("check") {
    // Run the functional tests as part of `check`
    dependsOn(tasks.named("functionalTest"))
}

tasks.withType<Test>().configureEach {
    useJUnitPlatform()
    if (graalVmHomeProvider.isPresent) {
        val graalvmHome = graalVmHomeProvider.get()
        inputs.property("GRAALVM_HOME", graalvmHome)
        environment("GRAALVM_HOME", graalvmHome)
        println("Task $name will use GRAALVM_HOME = $graalvmHome")
    }
}
