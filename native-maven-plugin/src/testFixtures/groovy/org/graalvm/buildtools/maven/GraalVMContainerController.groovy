package org.graalvm.buildtools.maven

import groovy.transform.CompileStatic
import groovy.transform.PackageScope
import org.spockframework.util.NotThreadSafe
import org.testcontainers.Testcontainers
import org.testcontainers.containers.BindMode

@CompileStatic
@NotThreadSafe
class GraalVMContainerController {
    private final GraalVMContainer container

    private boolean started

    int containerMaxAliveSeconds = 120
    Thread monitor

    static void exposeHostPort(int port) {
        Testcontainers.exposeHostPorts(port)
    }

    GraalVMContainerController(GraalVMContainer container) {
        this.container = container
    }

    @PackageScope
    DockerExecutionResult execute(String... command) {
        if (!started) {
            container.withCommand("tail", "-f", "/dev/null")
            startContainer()
        }
        return container.execute(command)
    }

    void startContainer() {
        started = true
        container.start()
        monitor = new Thread(new Runnable() {
            @Override
            void run() {
                try {
                    Thread.sleep(containerMaxAliveSeconds * 1000)
                    System.err.println("Stopping container because of timeout of ${containerMaxAliveSeconds}s")
                    stopContainer()
                } catch (InterruptedException ex) {
                    // nothing to do
                }
            }
        })
        monitor.start()
    }

    void stopContainer() {
        if (started) {
            started = false
            container.stop()
            monitor.interrupt()
        }
    }

    void addFileSystemBind(String hostPath, String containerPath, BindMode bindMode) {
        container.addFileSystemBind(hostPath, containerPath, bindMode)
    }
}
