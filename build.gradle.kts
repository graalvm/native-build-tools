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

tasks.named("check") {
    gradle.includedBuilds.forEach {
        dependsOn(it.task(":check"))
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
            dependsOn(it.task(":$taskPrefix$repo"))
        }
    }
}

