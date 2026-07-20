package org.graalvm.buildtools.maven

import com.github.openjson.JSONObject

import java.util.regex.Pattern

class JavaApplicationWithResourcesFunctionalTest extends AbstractGraalVMMavenFunctionalTest {

    def "can build an application which uses resources"() {

        given:
        withSample("java-application-with-resources")

        List<String> options = []
        if (detection) {
            options << '-Dresources.autodetection.enabled=true'
        }
        if (includedPatterns) {
            options << "-Dresources.includedPatterns=${joinForCliArg(includedPatterns)}".toString()
        }
        if (!restrictToModules) {
            options << '-Dresources.autodetection.restrictToModuleDependencies=false'
        }
        if (ignoreExistingResourcesConfig) {
            options << '-Dresources.autodetection.ignoreExistingResourcesConfig=true'
        }
        if (detectionExclusionPatterns) {
            options << "-Dresources.autodetection.detectionExclusionPatterns=${joinForCliArg(detectionExclusionPatterns)}".toString()
        }

        when:
        def resourcesFile = file("src/main/resources/META-INF/native-image/app/resource-config.json")
        resourcesFile.parentFile.mkdirs()
        resourcesFile << """
{
  "resources": {
    "includes": [],
    "excludes": []
  },
  "bundles": []
}
        """

        mvn(['-Pnative', '-DquickBuild', '-DskipTests', *options, 'package', 'exec:exec@native'])

        then:
        buildSucceeded
        outputContains "Hello, native!"

        and:
        if (ignoreExistingResourcesConfig) {
            matches(file("target/native/generated/generateResourceConfig/resource-config.json").text, '''{
  "resources": {
    "includes": [
      {
        "pattern" : "\\\\Qmessage.txt\\\\E"
      }
    ],
    "excludes": []
  },
  "bundles": []
}''')
        } else {
            matches(file("target/native/generated/generateResourceConfig/resource-config.json").text, '''{
  "resources": {
    "includes": [],
    "excludes": []
  },
  "bundles": []
}''')
        }

        where:
        detection | includedPatterns               | restrictToModules | detectionExclusionPatterns | ignoreExistingResourcesConfig
        false     | [Pattern.quote("message.txt")] | false             | []                         | true
        true      | []                             | false             | ["META-INF/.*"]            | true
        true      | []                             | true              | ["META-INF/.*"]            | true
        true      | []                             | true              | []                         | false
    }

    def "can test an application which uses test resources"() {
        given:
//        withDebug()
        withSample("java-application-with-resources")
        configureDynamicMainResourceLookup()

        List<String> options = []
        if (detection) {
            options << '-Dresources.autodetection.enabled=true'
        }
        if (includedPatterns) {
            options << "-Dresources.includedPatterns=${joinForCliArg(includedPatterns)}".toString()
        }
        if (!restrictToModules) {
            options << '-Dresources.autodetection.restrictToModuleDependencies=false'
        }
        if (detectionExclusionPatterns) {
            options << "-Dresources.autodetection.detectionExclusionPatterns=${joinForCliArg(detectionExclusionPatterns)}".toString()
        }

        when:
        mvn(['-Pnative', '-DquickBuild', '-DuseArgFile=true', 'test', *options])

        then:
        buildSucceeded

        and:
        // Autodetection scans processed main and test outputs, not raw resource sources. §FS-resources-and-metadata.1.
        assertResourcePatterns(file("target/native/generated/generateTestResourceConfig/resource-config.json"), expectedPatterns)

        and: "native:test uses Maven build outputs without adding raw resource directories"
        def argsFiles = file("target/tmp").listFiles().findAll {
            it.name.startsWith("native-image-") && it.name.endsWith(".args")
        }
        argsFiles.size() == 1
        def nativeImageArguments = normalizePaths(argsFiles.first().text)
        nativeImageArguments.contains(normalizePaths(file("target/classes").absolutePath))
        nativeImageArguments.contains(normalizePaths(file("target/test-classes").absolutePath))
        !nativeImageArguments.contains(normalizePaths(file("src/main/resources").absolutePath))
        !nativeImageArguments.contains(normalizePaths(file("src/test/resources").absolutePath))

        where:
        detection | includedPatterns                                                               | restrictToModules | detectionExclusionPatterns                         || expectedPatterns
        false     | [Pattern.quote("message.txt"), Pattern.quote("org/graalvm/demo/expected.txt")] | false             | []                                                 || [Pattern.quote("message.txt"), Pattern.quote("org/graalvm/demo/expected.txt")]
        true      | []                                                                             | false             | ["META-INF/.*", "junit-platform-unique-ids.*"]     || [Pattern.quote("message.txt"), Pattern.quote("org/graalvm/demo/expected.txt")]
        true      | []                                                                             | true              | ["META-INF/.*", "junit-platform-unique-ids.*"]     || [Pattern.quote("message.txt"), Pattern.quote("org/graalvm/demo/expected.txt")]
    }

    def "test resource autodetection follows Maven's processed outputs"() {
        given:
        withSample("java-application-with-resources")
        configureDynamicMainResourceLookup()
        configureProcessedTestResources()

        when:
        mvn '-Pnative', '-DskipNativeTests', '-Dresources.autodetection.enabled=true',
                '-Dresources.autodetection.restrictToModuleDependencies=true',
                '-Dresources.autodetection.detectionExclusionPatterns=META-INF/.*,junit-platform-unique-ids.*',
                'test'

        then:
        buildSucceeded
        file("target/test-classes/generated-only.txt").isFile()
        !file("target/test-classes/excluded-source-only.txt").exists()

        and: "only resources that Maven put in the processed test output are detected"
        assertResourcePatterns(file("target/native/generated/generateTestResourceConfig/resource-config.json"), [
                Pattern.quote("message.txt"),
                Pattern.quote("org/graalvm/demo/expected.txt"),
                Pattern.quote("generated-only.txt")
        ])
    }

    private void configureDynamicMainResourceLookup() {
        def source = file("src/main/java/org/graalvm/demo/Application.java")
        def staticLookup = 'Application.class.getResourceAsStream("/message.txt")'
        def dynamicLookup = 'Application.class.getResourceAsStream(System.getProperty("org.graalvm.buildtools.test.resource", "/message.txt"))'
        assert source.text.contains(staticLookup)
        source.text = source.text.replace(staticLookup, dynamicLookup)
    }

    private void configureProcessedTestResources() {
        def excluded = file("src/test/resources/excluded-source-only.txt")
        excluded.parentFile.mkdirs()
        excluded.text = "must not be detected"

        def generated = file("src/generated-test-resources/generated-only.txt")
        generated.parentFile.mkdirs()
        generated.text = "must be detected"

        def pom = file("pom.xml")
        def buildStart = '''    <build>
        <finalName>${project.artifactId}</finalName>
        <plugins>'''
        def configuredBuildStart = '''    <build>
        <finalName>${project.artifactId}</finalName>
        <testResources>
            <testResource>
                <directory>src/test/resources</directory>
                <excludes>
                    <exclude>excluded-source-only.txt</exclude>
                </excludes>
            </testResource>
        </testResources>
        <plugins>
            <plugin>
                <groupId>org.apache.maven.plugins</groupId>
                <artifactId>maven-resources-plugin</artifactId>
                <version>3.3.1</version>
                <executions>
                    <execution>
                        <id>copy-generated-test-resources</id>
                        <phase>process-test-resources</phase>
                        <goals>
                            <goal>copy-resources</goal>
                        </goals>
                        <configuration>
                            <outputDirectory>${project.build.testOutputDirectory}</outputDirectory>
                            <resources>
                                <resource>
                                    <directory>src/generated-test-resources</directory>
                                </resource>
                            </resources>
                        </configuration>
                    </execution>
                </executions>
            </plugin>'''
        def pomText = pom.text.replace('\r\n', '\n')
        assert pomText.contains(buildStart)
        pom.text = pomText.replace(buildStart, configuredBuildStart)
    }

    private static void assertResourcePatterns(File configFile, List<String> expectedPatterns) {
        def config = new JSONObject(configFile.text)
        def resources = config.getJSONObject("resources")
        def actualPatterns = resources.getJSONArray("includes").iterator().collect { it.getString("pattern") } as Set
        assert actualPatterns == expectedPatterns as Set
        assert resources.getJSONArray("excludes").isEmpty()
        assert config.getJSONArray("bundles").isEmpty()
    }

    private static String joinForCliArg(List<String> patterns) {
        patterns.join(",")
    }

    private static String normalizePaths(String value) {
        value.replace('\\\\', '\\').replace('\\', '/')
    }
}
