pluginManagement {
    includeBuild("../build-logic")
}

plugins {
    id("org.graalvm.build.common")
}

rootProject.name = "native-maven-plugin"

includeBuild("../common/junit-platform-native")
includeBuild("../common/utils")
