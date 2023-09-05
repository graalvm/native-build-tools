plugins {
    `java-library`
    id("org.graalvm.build.publishing")
}

group = "org.graalvm.internal"
version = "1.5"

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(11))
    }
}

publishing {
    publications {
        create<MavenPublication>("maven") {
            from(components["java"])
        }
    }
}
