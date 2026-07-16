/*
 * Copyright (c) 2026, 2026 Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * The Universal Permissive License (UPL), Version 1.0
 *
 * Subject to the condition set forth below, permission is hereby granted to any
 * person obtaining a copy of this software, associated documentation and/or
 * data (collectively the "Software"), free of charge and under any and all
 * copyright rights in the Software, and any and all patent rights owned or
 * freely licensable by each licensor hereunder covering either (i) the
 * unmodified Software as contributed to or provided by such licensor, or (ii)
 * the Larger Works (as defined below), to deal in both
 *
 * (a) the Software, and
 *
 * (b) any piece of software and/or hardware listed in the lrgrwrks.txt file if
 * one is included with the Software each a "Larger Work" to which the Software
 * is contributed by such licensors),
 *
 * without restriction, including without limitation the rights to copy, create
 * derivative works of, display, perform, and distribute the Software and make,
 * use, sell, offer for sale, import, export, have made, and have sold the
 * Software and the Larger Work(s), and to sublicense the foregoing rights on
 * either these or other terms.
 *
 * This license is subject to the following condition:
 *
 * The above copyright notice and either this complete permission notice or at a
 * minimum a reference to the UPL must be included in all copies or substantial
 * portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */

package org.graalvm.buildtools.maven

import org.apache.maven.execution.MavenSession
import org.apache.maven.model.Build
import org.apache.maven.model.Plugin
import org.apache.maven.model.PluginExecution
import org.apache.maven.project.MavenProject
import org.codehaus.plexus.util.cli.CommandLineUtils
import org.codehaus.plexus.util.xml.Xpp3Dom
import spock.lang.Specification

class NativeExtensionTest extends Specification {
    private static final String JUNIT_TRACKING_ENABLED = "junit.platform.listeners.uid.tracking.enabled"
    private static final String JUNIT_TRACKING_OUTPUT_DIR = "junit.platform.listeners.uid.tracking.output.dir"

    def "configures JUnit listener properties through non-deprecated systemPropertyVariables"() {
        given:
        def build = new Build()
        build.directory = "target"
        build.addPlugin(plugin("native-maven-plugin"))
        def surefire = plugin("maven-surefire-plugin")
        def failsafe = plugin("maven-failsafe-plugin")
        build.addPlugin(surefire)
        build.addPlugin(failsafe)

        def project = new MavenProject()
        project.build = build

        def session = Stub(MavenSession) {
            getProjects() >> [project]
            getSystemProperties() >> new Properties()
        }

        when:
        new NativeExtension().afterProjectsRead(session)

        then:
        [surefire, failsafe].each { testPlugin ->
            def configuration = testPlugin.executions[0].configuration as Xpp3Dom
            def systemPropertyVariables = configuration.getChild("systemPropertyVariables")

            assert configuration.getChild("systemProperties") == null
            assert systemPropertyVariables != null
            assert systemPropertyVariables.getChild(JUNIT_TRACKING_ENABLED).value == "true"
            assert systemPropertyVariables.getChild(JUNIT_TRACKING_OUTPUT_DIR).value == NativeExtension.testIdsDirectory("target")
        }
    }

    void "test agent argument is quoted for Surefire argLine when output path contains spaces"() {
        given:
        def agentArgument = NativeExtension.buildAgentArgument(
                "/tmp/path with spaces/target",
                NativeExtension.Context.test,
                ["config-output-dir={output_dir}"]
        )

        when:
        def quotedAgentArgument = NativeExtension.quoteAgentArgumentForArgLine(agentArgument)

        then:
        quotedAgentArgument == "\"${agentArgument}\""
        CommandLineUtils.translateCommandline(quotedAgentArgument).toList() == [agentArgument]
    }

    void "test agent argument is unchanged for Surefire argLine when output path has no spaces"() {
        given:
        def agentArgument = NativeExtension.buildAgentArgument(
                "/tmp/path-without-spaces/target",
                NativeExtension.Context.test,
                ["config-output-dir={output_dir}"]
        )

        expect:
        NativeExtension.quoteAgentArgumentForArgLine(agentArgument) == agentArgument
    }

    private static Plugin plugin(String artifactId) {
        def plugin = new Plugin()
        plugin.artifactId = artifactId
        plugin.addExecution(new PluginExecution())
        plugin.configuration = new Xpp3Dom("configuration")
        plugin
    }
}
