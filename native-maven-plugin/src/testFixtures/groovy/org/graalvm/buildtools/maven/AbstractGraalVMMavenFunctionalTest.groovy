package org.graalvm.buildtools.maven

import org.testcontainers.containers.BindMode
import org.testcontainers.spock.Testcontainers
import spock.lang.Specification
import spock.lang.TempDir

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption

@Testcontainers
abstract class AbstractGraalVMMavenFunctionalTest extends Specification {
    final GraalVMContainerController host = new GraalVMContainerController(
            new GraalVMContainer("graalvm/maven-functional-testing:latest")
    )

    @TempDir
    Path testDirectory

    DockerExecutionResult result

    protected void withSample(String name) {
        File sampleDir = new File("../samples/$name")
        copySample(sampleDir.toPath(), testDirectory)
        host.addFileSystemBind(
                testDirectory.toFile().getAbsolutePath(),
                "/sample",
                BindMode.READ_WRITE
        )
    }

    private static void copySample(Path from, Path into) {
        Files.walk(from).forEach(sourcePath -> {
            Path target = into.resolve(from.relativize(sourcePath))
            Files.copy(sourcePath, target, StandardCopyOption.REPLACE_EXISTING)
        })
    }

    private static List<String> getInjectedSystemProperties() {
        ["junit.jupiter.version",
         "native.maven.plugin.version",
         "junit.platform.native.version"
        ].collect {
            "-D${it}=${System.getProperty(it)}".toString()
        }
    }

    void mvn(String... args) {
        result = host.execute(
                "mvn",
                "-Dcommon.repo.uri=file:///bootstrap/repo",
                *injectedSystemProperties,
                *args
        )
        System.out.println(result.stdOut)
        System.err.println(result.stdErr)
    }

    boolean isBuildSucceeded() {
        result.stdOut.contains("BUILD SUCCESS")
    }

    boolean isBuildFailed() {
        !isBuildSucceeded()
    }

    boolean outputContains(String text) {
        result.stdOut.contains(text)
    }

    boolean outputDoesNotContain(String text) {
        !result.stdOut.contains(text)
    }

}
