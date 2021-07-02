package org.graalvm.buildtools.maven

class JavaApplicationWithTestsFunctionalTest extends AbstractGraalVMMavenFunctionalTest {
    def "can run tests in a native image with the Maven plugin"() {
        withSample("java-application-with-tests")

        when:
        mvn '-Pnative', 'test'

        then:
        buildSucceeded
        outputContains "[junit-platform-native] Running in 'test listener' mode"
        outputContains """
[         3 containers found      ]
[         0 containers skipped    ]
[         3 containers started    ]
[         0 containers aborted    ]
[         3 containers successful ]
[         0 containers failed     ]
[         6 tests found           ]
[         0 tests skipped         ]
[         6 tests started         ]
[         0 tests aborted         ]
[         6 tests successful      ]
[         0 tests failed          ]
""".trim()
    }
}
