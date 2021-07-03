/*
 * Copyright (c) 2020, 2021, Oracle and/or its affiliates. All rights reserved.
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

import com.github.dockerjava.api.command.InspectContainerResponse
import com.github.dockerjava.api.exception.DockerException
import com.github.dockerjava.zerodep.shaded.org.apache.commons.codec.Charsets
import groovy.transform.CompileStatic
import org.testcontainers.DockerClientFactory
import org.testcontainers.containers.GenericContainer
import org.testcontainers.containers.output.FrameConsumerResultCallback
import org.testcontainers.containers.output.OutputFrame
import org.testcontainers.containers.output.ToStringConsumer

@CompileStatic
class GraalVMContainer extends GenericContainer<GraalVMContainer> {
    GraalVMContainer(String image) {
        super(image)
    }

    @Override
    void close() {
        stop()
    }

    // this code is mostly copied from ExecInContainerPattern
    DockerExecutionResult execute(String... command) {
        if (!isRunning(containerInfo)) {
            throw new IllegalStateException("execInContainer can only be used while the Container is running");
        }
        def containerId = containerInfo.id
        def dockerClient = DockerClientFactory.instance().client()
        dockerClient.execCreateCmd(containerId).withCmd(command)
        def execCreateCmdResponse = dockerClient
                .execCreateCmd(containerId)
                .withAttachStdout(true)
                .withAttachStderr(true)
                .withCmd(command)
                .exec()
        def stdoutConsumer = new ToStringConsumer() {
            String previous = ""
            @Override
            void accept(OutputFrame outputFrame) {
                super.accept(outputFrame)
                def string = filter(outputFrame.utf8String)
                if (string) {
                    print(string)
                }
                previous = string
            }

            // Reduces the verbosity of Maven logs
            // This isn't quite correct since output is buffered
            // however this is only used for display, not capturing
            // the actual full log and it works quite well for this
            // purpose
            private String filter(String input) {
                // adhoc filtering of Maven's download progress which is only
                // possible to silence since 3.6.5+
                String filtered = input.replaceAll("[0-9]+/[0-9]+ [KMG]?B\\s+", "")
                        .replaceAll("Download(ing|ed): .+", "")
                        .trim()
                if (filtered.empty) {
                    if (input == "\n" && previous != "\n") {
                        return "\n"
                    }
                    return ""
                }
                if (input.endsWith("\n")) {
                    return "$filtered\n"
                }
                filtered
            }
        }

        def stderrConsumer = new ToStringConsumer() {
            @Override
            void accept(OutputFrame outputFrame) {
                super.accept(outputFrame)
                System.err.println(outputFrame.utf8String)
            }
        }
        try (FrameConsumerResultCallback callback = new FrameConsumerResultCallback()) {
            callback.addConsumer(OutputFrame.OutputType.STDOUT, stdoutConsumer)
            callback.addConsumer(OutputFrame.OutputType.STDERR, stderrConsumer)
            dockerClient.execStartCmd(execCreateCmdResponse.getId()).exec(callback).awaitCompletion()
        }
        Long exitCode = dockerClient.inspectExecCmd(execCreateCmdResponse.getId()).exec().exitCodeLong
        return new DockerExecutionResult(exitCode, stdoutConsumer.toString(Charsets.UTF_8), stderrConsumer.toString(Charsets.UTF_8))
    }

    private static boolean isRunning(InspectContainerResponse containerInfo) {
        try {
            return containerInfo != null && containerInfo.getState().getRunning()
        } catch (DockerException e) {
            return false
        }
    }
}
