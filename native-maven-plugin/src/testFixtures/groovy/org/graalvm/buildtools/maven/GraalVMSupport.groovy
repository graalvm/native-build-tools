package org.graalvm.buildtools.maven

class GraalVMSupport {

    static String getJavaHomeVersionString() {
        String javaHomeLocation = System.getenv("JAVA_HOME")
        return extractVersionString(javaHomeLocation)
    }

    static String getGraalVMHomeVersionString() {
        String graalvmHomeLocation = System.getenv("GRAALVM_HOME")
        return extractVersionString(graalvmHomeLocation)
    }

    private static String extractVersionString(String location) {
        def sout = new StringBuilder(), serr = new StringBuilder()
        String command = getSystemBasedCommand(location);
        def proc = command.execute()
        proc.consumeProcessOutput(sout, serr)
        proc.waitForOrKill(1000)
        assert serr.toString().isEmpty()

        return sout.toString()
    }

    private static String getSystemBasedCommand(String location) {
        if (System.getProperty("os.name", "unknown").contains("Windows")) {
            return location + '\\bin\\native-image.cmd --version'
        } else {
            return location + '/bin/native-image --version'
        }
    }
}
