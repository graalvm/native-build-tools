package org.graalvm.buildtools.maven


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
        mvn(['-Pnative', '-DquickBuild', '-DskipTests', *options, 'package', 'exec:exec@native'])

        then:
        buildSucceeded
        outputContains "Hello, native!"

        and:
        if (ignoreExistingResourcesConfig) {
            matches(file("target/native/generated/generateResourceConfig/resource-config.json").text, '''{
  "resources" : {
    "includes" : [ {
      "pattern" : "\\\\Qmessage.txt\\\\E"
    } ],
    "excludes" : [ ]
  },
  "bundles" : [ ]
}''')
        } else {
            matches(file("target/native/generated/generateResourceConfig/resource-config.json").text, '''{
  "resources" : {
    "includes" : [ ],
    "excludes" : [ ]
  },
  "bundles" : [ ]
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
        mvn(['-Pnative', '-DquickBuild', 'test', *options])

        then:
        buildSucceeded

        and:
        if (ignoreExistingResourcesConfig) {
            matches(file("target/native/generated/generateTestResourceConfig/resource-config.json").text, '''{
  "resources" : {
    "includes" : [ {
      "pattern" : "\\\\Qmessage.txt\\\\E"
    }, {
      "pattern" : "\\\\Qorg/graalvm/demo/expected.txt\\\\E"
    } ],
    "excludes" : [ ]
  },
  "bundles" : [ ]
}''')
        } else {
            matches(file("target/native/generated/generateResourceConfig/resource-config.json").text, '''{
  "resources" : {
    "includes" : [ ],
    "excludes" : [ ]
  },
  "bundles" : [ ]
}''')
        }

        where:
        detection | includedPatterns                                                               | restrictToModules | detectionExclusionPatterns                     | ignoreExistingResourcesConfig
        false     | [Pattern.quote("message.txt"), Pattern.quote("org/graalvm/demo/expected.txt")] | false             | []                                             | true
        true      | []                                                                             | false             | []                                             | false
        true      | []                                                                             | false             | ["META-INF/.*", "junit-platform-unique-ids.*"] | true
        true      | []                                                                             | true              | ["META-INF/.*", "junit-platform-unique-ids.*"] | true
    }

    private static String joinForCliArg(List<String> patterns) {
        patterns.join(",")
    }
}
