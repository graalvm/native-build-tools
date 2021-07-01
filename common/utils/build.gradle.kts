plugins {
    id("org.graalvm.build.java")
    id("org.graalvm.build.publishing")
}

maven {
    name.set("Utilities for GraalVM native image plugins")
    description.set("Contains code which is shared by both by the Maven and Gradle plugins")
}

val generateVersionInfo = tasks.register("generateVersionInfo", org.graalvm.build.GenerateVersionClass::class.java) {
    versions.put("junitPlatformNative", libs.versions.junitPlatformNative)
    outputDirectory.set(layout.buildDirectory.dir("generated/sources/versions"))
}

sourceSets {
    main {
        java {
            srcDir(generateVersionInfo)
        }
    }
}

publishing {
    publications {
        create<MavenPublication>("maven") {
            from(components["java"])
        }
    }
}
