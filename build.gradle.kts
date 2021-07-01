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

mapOf(
        "publishTo" to "MavenLocal",
        "publishAllPublicationsTo" to "CommonRepository"
).forEach { entry ->
    val (taskPrefix, repo) = entry
    tasks.register("$taskPrefix$repo") {
        description = "Publishes all artifacts to the ${repo.decapitalize()} repository"
        group = PublishingPlugin.PUBLISH_TASK_GROUP
        gradle.includedBuilds.forEach {
            if (it.name in setOf("junit-platform-native", "native-gradle-plugin", "native-maven-plugin")) {
                dependsOn(it.task(":$taskPrefix$repo"))
            }
        }
    }
}

