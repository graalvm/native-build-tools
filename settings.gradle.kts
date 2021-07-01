rootProject.name = "native-build-tools"

includeBuild("common/junit-platform-native")
includeBuild("common/gradle-publishing")
includeBuild("native-gradle-plugin")
includeBuild("native-maven-plugin/gradle-bridge") {
    name = "native-maven-plugin"
}

