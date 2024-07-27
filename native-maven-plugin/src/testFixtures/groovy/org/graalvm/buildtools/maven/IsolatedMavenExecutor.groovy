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

import groovy.transform.CompileStatic

import java.util.concurrent.CompletableFuture

@CompileStatic
class IsolatedMavenExecutor {
    private final File javaExecutable
    private final File m2Home
    private final String classpath
    boolean debug

    IsolatedMavenExecutor(File javaExecutable, File m2Home, String classpath) {
        this.javaExecutable = javaExecutable
        this.m2Home = m2Home
        this.classpath = classpath
    }

    MavenExecutionResult execute(File rootProjectDirectory,
                                 Map<String, String> systemProperties,
                                 List<String> arguments,
                                 File settings) throws IOException, InterruptedException {
        List<String> cliArgs = new ArrayList<>()
        cliArgs.add(javaExecutable.getAbsolutePath())
        cliArgs.add("-cp")
        cliArgs.add(classpath)
        if (debug) {
            cliArgs.add("-agentlib:jdwp=transport=dt_socket,server=y,suspend=y,address=*:5005")
        }
        cliArgs.add("-Dmaven.multiModuleProjectDirectory=" + rootProjectDirectory.getAbsolutePath())
        cliArgs.add("org.apache.maven.cli.MavenCli")
        systemProperties.forEach((key, value) -> cliArgs.add("-D" + key + "=" + value))
        cliArgs.addAll(arguments)
        cliArgs.addAll(Arrays.asList("--settings", settings.getAbsolutePath()))

        ProcessBuilder builder = new ProcessBuilder()
                .directory(rootProjectDirectory)
                .command(cliArgs)

        def environment = builder.environment()
        String graalvmHome = System.getenv("GRAALVM_HOME")
        if (graalvmHome == null) {
            graalvmHome = javaExecutable.parentFile.parentFile.absolutePath
        }
        environment.put("GRAALVM_HOME", graalvmHome)
        environment.put("M2_HOME", m2Home.absolutePath)
        Process p = builder
                .redirectErrorStream(true)
                .start()
        CompletableFuture<String> sout = consumeStream(p.inputStream, System.out)
        CompletableFuture<String> serr = consumeStream(p.errorStream, System.err)
        p.waitFor()
        def result = new MavenExecutionResult(
                p.exitValue(),
                sout.get(),
                serr.get()
        )
        result
    }

    // Reduces the verbosity of Maven logs
    // This isn't quite correct since output is buffered
    // however this is only used for display, not capturing
    // the actual full log and it works quite well for this
    // purpose
    private static String filter(String input) {
        // adhoc filtering of Maven's download progress which is only
        // possible to silence since 3.6.5+
        return input.replaceAll("[0-9]+/[0-9]+ [KMG]?B\\s+", "")
                .replaceAll("^Download(ing|ed)( from)? : .+\$", "")
                .replaceAll("^Progress \\([0-9]+\\).+\$", "")
                .trim()
    }


    static CompletableFuture<String> consumeStream(InputStream is, PrintStream out) {
        String lineSeparator = System.lineSeparator()
        return CompletableFuture.supplyAsync(() -> {
            try (
                    InputStreamReader isr = new InputStreamReader(is)
                    BufferedReader br = new BufferedReader(isr)
            ) {
                StringBuilder res = new StringBuilder()
                String inputLine
                while ((inputLine = br.readLine()) != null) {
                    String line = filter(inputLine)
                    res.append(inputLine).append(lineSeparator)
                    if (line) {
                        out.append(line).append(lineSeparator)
                    }
                }
                return res.toString()
            } catch (Exception e) {
                throw new RuntimeException(e)
            }
        })
    }

}
