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

val publishingTasks = tasks.withType<PublishToMavenRepository>()
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

// Get a handle on the software component factory
interface Services {
    @javax.inject.Inject
    fun getSoftwareComponentFactory(): SoftwareComponentFactory
}
