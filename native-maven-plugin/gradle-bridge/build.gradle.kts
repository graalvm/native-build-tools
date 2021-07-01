plugins {
    id("org.graalvm.build.maven-interop")
}

val junitPlatformNative = gradle.includedBuilds.find {
    it.name == "junit-platform-native"
}!!

maven {
    bridge("publishAllPublicationsToCommonRepository") {
        dependsOn(junitPlatformNative.task(":publishAllPublicationsToCommonRepository"))
        arguments.set(listOf("deploy"))
    }

    bridge("test") {
        dependsOn(junitPlatformNative.task(":publishAllPublicationsToCommonRepository"))
    }

    bridge("clean")
}

