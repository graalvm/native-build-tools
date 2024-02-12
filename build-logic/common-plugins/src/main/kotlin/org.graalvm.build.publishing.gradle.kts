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

/**
 * This convention plugin automatically registers a publishing repository which
 * will be at the root of a composite.
 *
 * That is to say that if the project is built in isolation, the repository will
 * be in the project build directory, but if it's a member of a composite, then
 * we look for the root of the composite and use that directory instead.
 */
import org.graalvm.build.MavenExtension

plugins {
    id("maven-publish")
}

val mavenExtension = project.extensions.create<MavenExtension>("maven").also {
    it.name.convention(project.name)
    it.description.convention(project.description)
}

val publishingTasks = tasks.withType<AbstractPublishToMaven>()
        .matching { it.name.endsWith("ToCommonRepository") }

val repositoryElements by configurations.creating {
    // Configure an outgoing configuration which artifact
    // is going to be the local Maven repository we generate
    isCanBeConsumed = true
    isCanBeResolved = false
    attributes {
        attribute(Category.CATEGORY_ATTRIBUTE, objects.named("repository"))
    }
    outgoing {
        artifact(repoDirectory) {
            builtBy(publishingTasks)
        }
    }
}

// Register a new software component which is used to "publish" the repository
// and is visible via composite builds
val softwareComponentFactory = objects.newInstance(Services::class.java).getSoftwareComponentFactory()
val adhoc = softwareComponentFactory.adhoc("commonRepository")
adhoc.addVariantsFromConfiguration(repositoryElements) {
    mapToOptional()
}

publishing {
    repositories {
        maven {
            name = "common"
            url = uri(repoDirectory)
        }
        maven {
            name = "snapshots"
            url = uri(snapshotsDirectory)
        }
        val nexusUrl = providers.gradleProperty("graalvm.nexus.url")
        if (nexusUrl.isPresent) {
            maven {
                name = "nexus"
                url = uri(nexusUrl.get())
                isAllowInsecureProtocol = true
                credentials {
                    username = providers.gradleProperty("graalvm.nexus.username").get()
                    password = providers.gradleProperty("graalvm.nexus.password").get()
                }
            }
        }
    }
    publications.withType<MavenPublication>().configureEach {
        pom {
            name.set(mavenExtension.name)
            description.set(mavenExtension.description)
            url.set("https://github.com/graalvm/native-build-tools")

            licenses {
                license {
                    name.set("Universal Permissive License Version 1.0")
                    url.set("http://oss.oracle.com/licenses/upl")
                }
            }

            developers {
                developer {
                    name.set("SubstrateVM developers")
                    email.set("graal-dev@openjdk.java.net")
                    organization.set("Graal")
                    organizationUrl.set("http://openjdk.java.net/projects/graal")
                }
            }

            scm {
                connection.set("scm:git:git://github.com/graalvm/native-build-tools.git")
                developerConnection.set("scm:git:ssh://github.com:graalvm/native-build-tools.git")
                url.set("https://github.com/graalvm/native-build-tools/tree/master")
            }
        }
    }
}

plugins.withId("java-test-fixtures") {
    components.configureEach {
        if (this is AdhocComponentWithVariants) {
            withVariantsFromConfiguration(configurations.getByName("testFixturesApiElements")) {
                skip()
            }
            withVariantsFromConfiguration(configurations.getByName("testFixturesRuntimeElements")) {
                skip()
            }
        }
    }
}

val publicationCoordinatesCollector = gradle.sharedServices.registerIfAbsent("publicationCoordinatesCollector", PublicationCoordinatesCollector::class.java) {
}

val showPublications by tasks.registering {
    usesService(publicationCoordinatesCollector)
    doLast {
        publishing.publications.all {
            val pub = this as MavenPublication
            publicationCoordinatesCollector.get().addPublication(pub)
        }
    }
}

// Get a handle on the software component factory
interface Services {
    @Inject
    fun getSoftwareComponentFactory(): SoftwareComponentFactory
}

abstract class PublicationCoordinatesCollector: BuildService<BuildServiceParameters.None>, AutoCloseable {
    val publications = mutableSetOf<String>()

    fun addPublication(publication: MavenPublication) {
        publications.add("${publication.groupId}:${publication.artifactId}:${publication.version}")
    }

    override fun close() {
        publications.sorted().forEach { println(it) }
    }
}
