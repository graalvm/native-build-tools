import com.bmuschko.gradle.docker.tasks.image.DockerBuildImage
import com.bmuschko.gradle.docker.tasks.image.Dockerfile
import org.graalvm.build.maven.GeneratePluginDescriptor

plugins {
    `java-library`
    groovy
    `java-test-fixtures`
    id("org.graalvm.build.java")
    id("org.graalvm.build.publishing")
    id("org.graalvm.build.maven-functional-testing")
    id("com.bmuschko.docker-remote-api") version "7.1.0"
}

maven {
    name.set("GraalVM Native Maven Plugin")
    description.set("Plugin that provides support for building and testing of GraalVM native images (ahead-of-time compiled Java code)")
}

val mavenEmbedder by configurations.creating

val testingBase by configurations.creating {
    isCanBeConsumed = false
    isCanBeResolved = false
    configurations.testImplementation.get().extendsFrom(this)
    configurations.testFixturesImplementation.get().extendsFrom(this)
}

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

    testingBase(libs.test.spock)
    testingBase(platform(libs.test.testcontainers.bom))
    testingBase(libs.test.testcontainers.core)
    testingBase(libs.test.testcontainers.spock)

    functionalTestCommonRepository(libs.utils)
    functionalTestCommonRepository(libs.junitPlatformNative)

    functionalTestRuntimeOnly(libs.slf4j.simple)
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

val prepareBuildContext = tasks.register("prepareBuildContext", Copy::class.java) {
    dependsOn(":publishAllPublicationsToCommonRepository")
    destinationDir = layout.buildDirectory.dir("docker/context").get().asFile
    from(layout.projectDirectory.dir("../samples/java-application")) {
        into("java-application")
    }
    from(dockerFileForFunctionalTests.map { it.destDir })
    from(file("config/settings.xml"))
    into("maven-repo") {
        from(configurations.functionalTestCommonRepository.get())
    }
}

val dockerFileForFunctionalTests = tasks.register("dockerFileForFunctionalTests", Dockerfile::class.java) {
    inputs.property("graalVmVersion", libs.versions.graalvm)
    inputs.property("mavenVersion", libs.versions.maven)
    from(providers.provider {
        Dockerfile.From("ghcr.io/graalvm/graalvm-ce:java11-${libs.versions.graalvm.get()}")
    })
    runCommand("gu install native-image")
    runCommand(providers.provider {
        val mvnVersion = libs.versions.maven.get()
        """curl https://archive.apache.org/dist/maven/maven-3/$mvnVersion/binaries/apache-maven-$mvnVersion-bin.tar.gz --output apache-maven-$mvnVersion-bin.tar.gz && \
  tar -zxvf apache-maven-$mvnVersion-bin.tar.gz && \
  rm apache-maven-$mvnVersion-bin.tar.gz && \
  mv apache-maven-$mvnVersion /usr/lib/mvn"""
    })
    addFile("java-application", "/bootstrap")
    addFile("maven-repo", "/bootstrap/repo")
    addFile("settings.xml", "/root/.m2/")
    val mvn = "cd /bootstrap && /usr/lib/mvn/bin/mvn -X -Dcommon.repo.uri=file:///bootstrap/repo -Djunit.jupiter.version=${libs.versions.junitJupiter.get()} -Dnative.maven.plugin.version=${libs.versions.nativeMavenPlugin.get()} -Djunit.platform.native.version=${libs.versions.junitPlatformNative.get()}"
    runCommand("$mvn install")
    runCommand("$mvn package exec:exec || true")
    environmentVariable("MAVEN_HOME", "/usr/lib/mvn")
    environmentVariable("PATH", "\$MAVEN_HOME/bin:\$PATH")
    workingDir("/sample")
}

val dockerImageForFunctionalTests = tasks.register("dockerImageForFunctionalTests", DockerBuildImage::class.java) {
    inputDir.set(prepareBuildContext.map { ctx -> objects.directoryProperty().also { it.set(ctx.destinationDir) }.get() })
    images.add("graalvm/maven-functional-testing:latest")
    onlyIf {
        // prevent execution of this task if jar hasn't changed
        // this isn't quite right but otherwise the publishing is done on every
        // execution and this triggers rebuild of a full image
        tasks.jar.get().state.outcome in setOf(
                org.gradle.api.internal.tasks.TaskExecutionOutcome.EXECUTED,
                org.gradle.api.internal.tasks.TaskExecutionOutcome.FROM_CACHE,
        )
    }
}

tasks {
    jar {
        from(generatePluginDescriptor)
    }
    functionalTest {
        dependsOn(dockerImageForFunctionalTests)
        systemProperty("graalvm.version", libs.versions.graalvm.get())
        systemProperty("junit.jupiter.version", libs.versions.junitJupiter.get())
        systemProperty("native.maven.plugin.version", libs.versions.nativeMavenPlugin.get())
        systemProperty("junit.platform.native.version", libs.versions.junitPlatformNative.get())
    }
}