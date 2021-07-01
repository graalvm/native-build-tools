import gradle.kotlin.dsl.accessors._e98ba513b34f86980a981ef4cafb3d49.publishing

/**
 * This convention plugin automatically registers a publishing repository which
 * will be at the root of a composite.
 *
 * That is to say that if the project is built in isolation, the repository will
 * be in the project build directory, but if it's a member of a composite, then
 * we look for the root of the composite and use that directory instead.
 */

plugins {
    id("maven-publish").apply(false)
}

val Gradle.rootGradle: Gradle
    get() {
        var cur = this
        while (cur.parent != null) {
            cur = cur.parent!!
        }
        return cur
    }

val Project.compositeRootBuildDirectory
    get() = gradle.rootGradle.rootProject.layout.buildDirectory


plugins.withId("maven-publish") {

    val repoDirectory = compositeRootBuildDirectory.dir("common-repo")
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
    }

    // This is a performance optimization: publishing is only required
    // if we actually changed the artifact
    publishingTasks.configureEach {
        outputs.upToDateWhen {
            !tasks.findByName("jar")!!.didWork && file(repoDirectory.get()).exists()
        }
    }
}

// Get a handle on the software component factory
interface Services {
    @javax.inject.Inject
    fun getSoftwareComponentFactory(): SoftwareComponentFactory
}
