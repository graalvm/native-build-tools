package org.graalvm.buildtools.maven

import groovy.transform.Canonical
import groovy.transform.CompileStatic

@CompileStatic
@Canonical
class DockerExecutionResult {
    final Long exitCode
    final String stdOut
    final String stdErr
}
