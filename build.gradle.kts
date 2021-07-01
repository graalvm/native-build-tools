plugins {
    base
}

tasks.named("clean") {
    gradle.includedBuilds.forEach {
        dependsOn(it.task(":clean"))
    }
}

tasks.register("publishToCommonRepository") {
    description = "Publishes all artifacts to the local common repository"
    group = PublishingPlugin.PUBLISH_TASK_GROUP
    gradle.includedBuilds.forEach {
        if (it.name in setOf("junit-platform-native", "native-gradle-plugin")) {
            dependsOn(it.task(":publishAllPublicationsToCommonRepository"))
        }
    }
}

