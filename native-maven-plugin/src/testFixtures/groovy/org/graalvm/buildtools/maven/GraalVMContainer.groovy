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
            @Override
            void accept(OutputFrame outputFrame) {
                super.accept(outputFrame)
                println(outputFrame.utf8String)
            }
        }
        def stderrConsumer = new ToStringConsumer() {
            @Override
            void accept(OutputFrame outputFrame) {
                super.accept(outputFrame)
                System.err.println(outputFrame.utf8String)
            }
        }
        FrameConsumerResultCallback callback = new FrameConsumerResultCallback()
        callback.addConsumer(OutputFrame.OutputType.STDOUT, stdoutConsumer)
        callback.addConsumer(OutputFrame.OutputType.STDERR, stderrConsumer)
        dockerClient.execStartCmd(execCreateCmdResponse.getId()).exec(callback).awaitCompletion()
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
