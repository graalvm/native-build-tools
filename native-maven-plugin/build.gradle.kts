import org.graalvm.build.maven.GeneratePluginDescriptor

plugins {
    `java-library`
    id("org.graalvm.build.java")
    id("org.graalvm.build.publishing")
}

maven {
    name.set("GraalVM Native Maven Plugin")
    description.set("Plugin that provides support for building and testing of GraalVM native images (ahead-of-time compiled Java code)")
}

val mavenEmbedder by configurations.creating

dependencies {
    implementation(libs.utils)
    compileOnly(libs.maven.pluginApi)
    compileOnly(libs.maven.core)
    compileOnly(libs.maven.artifact)
    compileOnly(libs.maven.pluginAnnotations)

    mavenEmbedder(libs.maven.embedder)
    mavenEmbedder(libs.maven.aether.connector)
    mavenEmbedder(libs.maven.aether.wagon)
    mavenEmbedder(libs.maven.wagon.http)
    mavenEmbedder(libs.maven.wagon.file)
    mavenEmbedder(libs.maven.wagon.provider)
    mavenEmbedder(libs.maven.compat)
    mavenEmbedder(libs.slf4j.simple)
}

publishing {
    publications {
        create<MavenPublication>("mavenPlugin") {
            from(components["java"])
            pom {
                packaging = "maven-plugin"
            }
        }
    }
}

val generatePluginDescriptor = tasks.register<GeneratePluginDescriptor>("generatePluginDescriptor") {
    dependsOn(gradle.includedBuild("utils").task(":publishAllPublicationsToCommonRepository"))
    projectDirectory.set(project.layout.projectDirectory)
    commonRepository.set(gradle.rootLayout.buildDirectory.dir("common-repo"))
    pluginClasses.from(sourceSets.getByName("main").output.classesDirs)
    settingsFile.set(project.layout.projectDirectory.file("config/settings.xml"))
    pomFile.set(tasks.withType<GenerateMavenPom>().named("generatePomFileForMavenPluginPublication").map { pom ->
        project.objects.fileProperty().also { it.set(pom.destination) }.get()
    })
    mavenEmbedderClasspath.from(mavenEmbedder)
    outputDirectory.set(project.layout.buildDirectory.dir("generated/maven-plugin"))
}

tasks {
    jar {
        from(generatePluginDescriptor)
    }
}