package org.graalvm.buildtools.maven;

class IntegrationTest extends AbstractGraalVMMavenFunctionalTest {
    def "run integration tests with failsafe plugin"() {
        withSample("integration-test")

        when:
        mvn '-Pnative', 'verify'

        then:
        buildSucceeded
        file("target/failsafe-reports").exists()
        file("target/failsafe-reports/org.graalvm.demo.CalculatorTestIT.txt").readLines().any(line -> line.contains("Tests run: 6, Failures: 0, Errors: 0, Skipped: 0"))
        outputContains "Using configuration from maven-failsafe-plugin"
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
