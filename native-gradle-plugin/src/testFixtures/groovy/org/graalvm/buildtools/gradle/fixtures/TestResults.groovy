package org.graalvm.buildtools.gradle.fixtures

import groovy.transform.EqualsAndHashCode
import groovy.xml.XmlSlurper

@EqualsAndHashCode
class TestResults {
    final int tests
    final int skipped
    final int failures
    final int errors

    TestResults(int tests, int skipped, int failures, int errors) {
        this.tests = tests
        this.skipped = skipped
        this.failures = failures
        this.errors = errors
    }

    static TestResults from(File xmlResultsFile) {
        def testsuite = new XmlSlurper().parse(xmlResultsFile)
        new TestResults(
                testsuite.@tests.toInteger(),
                testsuite.@skipped.toInteger(),
                testsuite.@failures.toInteger(),
                testsuite.@errors.toInteger()
        )
    }

    String toString() {
        "$tests tests, $failures failures, $skipped skipped, $errors errors"
    }
}
