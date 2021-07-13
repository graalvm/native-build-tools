/*
 * Copyright 2003-2012 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License")
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.graalvm.buildtools.maven

import groovy.transform.CompileStatic

import java.util.concurrent.CompletableFuture

@CompileStatic
class IsolatedMavenExecutor {
    private final File javaExecutable
    private final File m2Home
    private final String classpath

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
        cliArgs.add("-Dmaven.multiModuleProjectDirectory=" + rootProjectDirectory.getAbsolutePath())
        cliArgs.add("org.apache.maven.cli.MavenCli")
        systemProperties.forEach((key, value) -> cliArgs.add("-D" + key + "=" + value))
        cliArgs.addAll(arguments)
        cliArgs.addAll(Arrays.asList("--settings", settings.getAbsolutePath()))

        ProcessBuilder builder = new ProcessBuilder()
                .directory(rootProjectDirectory)
                .command(cliArgs)

        def environment = builder.environment()
        environment.put("GRAALVM_HOME", javaExecutable.parentFile.parentFile.absolutePath)
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
