/*
 * Copyright (c) 2026, Oracle and/or its affiliates. All rights reserved.
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

import org.graalvm.buildtools.utils.NativeImageUtils
import org.graalvm.buildtools.utils.NativeImageLayerRuntime
import spock.lang.Ignore
import spock.lang.IgnoreIf
import spock.lang.Issue
import spock.lang.Requires

import java.util.zip.ZipFile

// Exercises Maven reactor production and consumption of nil layer artifacts. §E2E-functional-tests.3.8.
class LayeredApplicationFunctionalTest extends AbstractGraalVMMavenFunctionalTest {
    // GraalVM 25.0.x can fail while building layer consumers after loading a valid layer.
    // §E2E-functional-tests.3.8.
    private static boolean hasLayerConsumptionBug() {
        !NativeImageUtils.isGraalVMVersionAtLeast(GraalVMSupport.getGraalVMHomeVersionString(), 25, 1)
    }

    @Issue("https://github.com/graalvm/native-build-tools/issues/1031")
    def "layer is optional in the shared Maven plugin descriptor"() {
        given:
        withSample("layered-maven-application")

        when:
        mvn 'help:describe',
            "-Dplugin=org.graalvm.buildtools:native-maven-plugin:${System.getProperty('native.maven.plugin.version')}",
            '-Dgoal=layer-create', '-Ddetail'

        then:
        buildSucceeded
        def help = result.stdOut.replaceAll(/\u001B\[[;\d]*m/, '').replace('\r\n', '\n')
        help =~ /(?s)\n\s+layer\s*\n\s+\(no description available\)\s*\n\s*\n\s+mainClass\s*\n/
    }

    def "loads the nil artifact handler and reactor model"() {
        given:
        withSample("layered-maven-application")

        when:
        mvn 'help:effective-pom', '-DskipTests'

        then:
        buildSucceeded
        outputContains "<type>nil</type>"
        outputContains "<goal>layer-create</goal>"
    }

    @Requires({ NativeImageUtils.getMajorJDKVersion(GraalVMSupport.getGraalVMHomeVersionString()) >= 25 })
    @IgnoreIf({ os.windows || os.macOs })
    def "builds and consumes a layer artifact in one reactor"() {
        given:
        withSample("layered-maven-application")

        when:
        mvn '-DquickBuild', '-DskipTests', 'package'

        then:
        buildSucceeded
        file("base-layer/target/native/layers/base/base.nil").isFile()
        outputContains "-H:LayerCreate=base.nil"
        def application = file("application/target/application")
        assert application.isFile()
        assert outputContains("-H:LayerUse=")
        def environment = System.getenv().collect { key, value -> "${key}=${value}" }
        def layerDirectory = file("base-layer/target/native/layers/base").absolutePath
        def inheritedLibraryPath = System.getenv("LD_LIBRARY_PATH")
        environment << "LD_LIBRARY_PATH=${inheritedLibraryPath ? inheritedLibraryPath + File.pathSeparator : ''}${layerDirectory}"
        def execution = [application.absolutePath].execute(environment, application.parentFile)
        assert execution.waitFor() == 0
        assert execution.in.text.contains("Hello, layered application!")
    }

    @Requires({ NativeImageUtils.getMajorJDKVersion(GraalVMSupport.getGraalVMHomeVersionString()) >= 25 })
    @IgnoreIf({ os.windows || os.macOs })
    def "builds and consumes a layer from an individual dependency selector"() {
        given:
        withSample("layered-maven-application")
        def basePom = file("base-layer/pom.xml")
        basePom.text = basePom.text
            .replace('''    <artifactId>base-layer</artifactId>
''', '''    <artifactId>base-layer</artifactId>
    <dependencies>
        <dependency>
            <groupId>org.apache.commons</groupId>
            <artifactId>commons-lang3</artifactId>
            <version>3.17.0</version>
            <scope>runtime</scope>
        </dependency>
    </dependencies>
''')
            .replace('''                                <modules>
                                    <module>java.base</module>
                                </modules>
''', '''                                <dependencies>
                                    <dependency>
                                        <artifact>org.apache.commons:commons-lang3</artifact>
                                    </dependency>
                                </dependencies>
''')
        def applicationPom = file("application/pom.xml")
        applicationPom.text = applicationPom.text.replace('''                    <mainClass>org.graalvm.demo.Application</mainClass>
''', '''                    <mainClass>org.graalvm.demo.Application</mainClass>
                    <metadataRepository>
                        <enabled>false</enabled>
                    </metadataRepository>
''')

        when:
        mvn '-DquickBuild', '-DskipTests', 'package'

        then:
        buildSucceeded
        file("base-layer/target/native/layers/base/base.nil").isFile()
        outputContains "-H:LayerCreate=base.nil,path="
        def application = file("application/target/application")
        assert application.isFile()
        assert outputContains("-H:LayerUse=")
        def environment = System.getenv().collect { key, value -> "${key}=${value}" }
        def layerDirectory = file("base-layer/target/native/layers/base").absolutePath
        def inheritedLibraryPath = System.getenv("LD_LIBRARY_PATH")
        environment << "LD_LIBRARY_PATH=${inheritedLibraryPath ? inheritedLibraryPath + File.pathSeparator : ''}${layerDirectory}"
        def execution = [application.absolutePath].execute(environment, application.parentFile)
        assert execution.waitFor() == 0
        assert execution.in.text.contains("Hello, layered application!")
    }

    @Requires({ NativeImageUtils.getMajorJDKVersion(GraalVMSupport.getGraalVMHomeVersionString()) >= 25 })
    @IgnoreIf({ os.windows || os.macOs })
    def "builds a modules-only layer without leaking the project classpath"() {
        given:
        withSample("layered-maven-application")
        when:
        mvn '-DquickBuild', '-DskipTests', '-pl', 'base-layer', 'package'

        then:
        buildSucceeded
        file("base-layer/target/native/layers/base/base.nil").isFile()
        def invocation = result.stdOut.readLines().find {
            it.contains("Executing:") && it.contains("-H:LayerCreate=base.nil,module=java.base")
        }
        invocation != null
        !invocation.contains(" -cp ")
    }

    @Requires({ NativeImageUtils.getMajorJDKVersion(GraalVMSupport.getGraalVMHomeVersionString()) >= 25 })
    @IgnoreIf({ os.windows || os.macOs })
    def "builds a layer with a package selector"() {
        given:
        withSample("layered-maven-application")
        def basePom = file("base-layer/pom.xml")
        basePom.text = basePom.text
            .replace('''    <artifactId>base-layer</artifactId>
''', '''    <artifactId>base-layer</artifactId>
    <dependencies>
        <dependency>
            <groupId>org.slf4j</groupId>
            <artifactId>slf4j-api</artifactId>
            <version>2.0.17</version>
        </dependency>
    </dependencies>
''')
            .replace('''                                <modules>
                                    <module>java.base</module>
                                </modules>
''', '''                                <packages>
                                    <package>org.slf4j</package>
                                </packages>
''')

        when:
        mvn '-DquickBuild', '-DskipTests', '-pl', 'base-layer', 'package'

        then:
        buildSucceeded
        file("base-layer/target/native/layers/base/base.nil").isFile()
        outputContains "-H:LayerCreate=base.nil,package=org.slf4j"
    }

    @Requires({ NativeImageUtils.getMajorJDKVersion(GraalVMSupport.getGraalVMHomeVersionString()) >= 25 })
    @IgnoreIf({ os.windows || os.macOs || hasLayerConsumptionBug() })
    def "builds and runs an all selector layer in the reactor"() {
        given:
        withSample("layered-maven-application")
        def basePom = file("base-layer/pom.xml")
        basePom.text = basePom.text.replace('''                                <modules>
                                    <module>java.base</module>
                                </modules>
''', '''                                <all>true</all>
''')

        when:
        mvn '-DquickBuild', '-DskipTests', 'package'

        then:
        buildSucceeded
        file("base-layer/target/native/layers/base/base.nil").isFile()
        outputContains "-H:LayerCreate=base.nil"
        outputContains "-H:LayerUse="
        def application = file("application/target/application")
        def layerDirectory = file("base-layer/target/native/layers/base").absolutePath
        def environment = System.getenv().collect { key, value -> "${key}=${value}" }
        environment << "LD_LIBRARY_PATH=${layerDirectory}"
        def execution = [application.absolutePath].execute(environment, application.parentFile)
        assert execution.waitFor() == 0
        assert execution.in.text.contains("Hello, layered application!")
    }

    @Requires({ NativeImageUtils.getMajorJDKVersion(GraalVMSupport.getGraalVMHomeVersionString()) >= 25 })
    @IgnoreIf({ os.windows || os.macOs })
    def "builds a layer from an explicit path"() {
        given:
        withSample("layered-maven-application")
        def baseLayerType = file("base-layer/src/main/java/org/graalvm/demo/BaseLayerType.java")
        baseLayerType.parentFile.mkdirs()
        baseLayerType.text = '''
            package org.graalvm.demo;
            public final class BaseLayerType {
                private BaseLayerType() { }
            }
        '''.stripIndent()
        def basePom = file("base-layer/pom.xml")
        basePom.text = basePom.text.replace('''                                <modules>
                                    <module>java.base</module>
                                </modules>
''', '''                                <paths>
                                    <path>\${project.build.outputDirectory}</path>
                                </paths>
''')

        when:
        mvn '-DquickBuild', '-DskipTests', '-pl', 'base-layer', 'package'

        then:
        buildSucceeded
        file("base-layer/target/native/layers/base/base.nil").isFile()
        outputContains "-H:LayerCreate=base.nil,path="
    }

    @Requires({ NativeImageUtils.getMajorJDKVersion(GraalVMSupport.getGraalVMHomeVersionString()) >= 25 })
    @IgnoreIf({ os.windows || os.macOs || hasLayerConsumptionBug() })
    def "builds a shared library using a layer artifact"() {
        given:
        withSample("layered-maven-application")
        def applicationPom = file("application/pom.xml")
        applicationPom.text = applicationPom.text.replace('''                    <mainClass>org.graalvm.demo.Application</mainClass>
''', '''                    <sharedLibrary>true</sharedLibrary>
''')

        when:
        mvn '-DquickBuild', '-DskipTests', 'package'

        then:
        buildSucceeded
        outputContains "-H:LayerUse="
        file("application/target/application${IS_WINDOWS ? '.dll' : IS_MAC ? '.dylib' : '.so'}").isFile()
    }

    @Requires({ NativeImageUtils.getMajorJDKVersion(GraalVMSupport.getGraalVMHomeVersionString()) >= 25 })
    @IgnoreIf({ os.windows || os.macOs })
    def "native tests consume layers from their own execution"() {
        given:
        withSample("layered-maven-application")
        def basePom = file("base-layer/pom.xml")
        basePom.text = basePom.text.replace('''                                    <module>java.base</module>
''', '''                                    <module>java.base</module>
                                    <module>java.management</module>
''')
        def applicationTest = file("application/src/test/java/org/graalvm/demo/ApplicationTest.java")
        applicationTest.parentFile.mkdirs()
        applicationTest.text = '''
            package org.graalvm.demo;
            import org.junit.jupiter.api.Test;
            import static org.junit.jupiter.api.Assertions.assertTrue;
            class ApplicationTest {
                @Test void runs() { assertTrue(true); }
            }
        '''.stripIndent()
        def applicationPom = file("application/pom.xml")
        applicationPom.text = applicationPom.text
            .replace('''    <dependencies>
''', '''    <dependencies>
        <dependency>
            <groupId>org.junit.jupiter</groupId>
            <artifactId>junit-jupiter</artifactId>
            <version>5.12.0</version>
            <scope>test</scope>
        </dependency>
''')
            .replace('''                </executions>
''', '''                    <execution>
                        <id>test-with-layer</id>
                        <phase>test</phase>
                        <goals><goal>test</goal></goals>
                        <configuration>
                            <useLayers>
                                <useLayer>
                                    <artifact>org.graalvm.buildtools.samples:base-layer</artifact>
                                </useLayer>
                            </useLayers>
                        </configuration>
                    </execution>
                </executions>
''')

        when:
        // The producer's layer-create goal is bound to package, so complete that phase before
        // resolving the consumer's native-test execution. §E2E-functional-tests.3.8.
        mvn '-DquickBuild', 'package'

        then:
        buildSucceeded
        outputContains "-H:LayerUse="
        outputContains "[         1 tests successful      ]"
    }

    @Requires({ NativeImageUtils.getMajorJDKVersion(GraalVMSupport.getGraalVMHomeVersionString()) >= 25 })
    @IgnoreIf({ os.windows || os.macOs })
    def "repository consumer stages and runs a POM-produced layer without producer outputs"() {
        given:
        withSample("layered-maven-application")
        def basePom = file("base-layer/pom.xml")
        basePom.text = basePom.text
            .replace('''    <artifactId>base-layer</artifactId>
''', '''    <artifactId>base-layer</artifactId>
    <packaging>pom</packaging>
''')
            .replace('''                                    <module>java.base</module>
''', '''                                    <module>java.base</module>
                                    <module>java.sql</module>
                                    <module>java.management</module>
''')
        def applicationTest = file("application/src/test/java/org/graalvm/demo/ApplicationTest.java")
        applicationTest.parentFile.mkdirs()
        applicationTest.text = '''
            package org.graalvm.demo;
            import java.sql.Driver;
            import org.junit.jupiter.api.Test;
            import static org.junit.jupiter.api.Assertions.assertNotNull;
            class ApplicationTest {
                @Test void runs() { assertNotNull(Driver.class); }
            }
        '''.stripIndent()
        def applicationPom = file("application/pom.xml")
        applicationPom.text = applicationPom.text
            .replace('''    <dependencies>
''', '''    <dependencies>
        <dependency>
            <groupId>org.junit.jupiter</groupId>
            <artifactId>junit-jupiter</artifactId>
            <version>5.12.0</version>
            <scope>test</scope>
        </dependency>
''')
            .replace('''                </executions>
''', '''                    <execution>
                        <id>test-with-repository-layer</id>
                        <phase>test</phase>
                        <goals><goal>test</goal></goals>
                    </execution>
                </executions>
''')

        when: "the POM-only producer is installed into the isolated repository"
        mvn '-DquickBuild', '-DskipTests', '-pl', 'base-layer', '-am', 'install'

        then:
        buildSucceeded
        def producerOutput = file("base-layer/target/native/layers/base")
        def expectedRuntimeFiles = producerOutput.listFiles()
            .findAll { it.isFile() && NativeImageLayerRuntime.isRuntimeLibrary(it.name) }
            .collect { it.name }
            .sort()
        !expectedRuntimeFiles.empty
        def repositoryDirectory = file("local-repo/org/graalvm/buildtools/samples/base-layer/1.0-SNAPSHOT")
        def nilArtifact = new File(repositoryDirectory, "base-layer-1.0-SNAPSHOT.nil")
        def runtimeArchive = repositoryDirectory.listFiles().find {
            it.name.startsWith("base-layer-1.0-SNAPSHOT-layer-runtime-") && it.name.endsWith(".zip")
        }
        nilArtifact.isFile()
        runtimeArchive?.isFile()
        def archivedRuntimeFiles
        new ZipFile(runtimeArchive).withCloseable { zip ->
            archivedRuntimeFiles = zip.entries().collect { it.name }.sort()
        }
        archivedRuntimeFiles == expectedRuntimeFiles

        when: "the producer build output is removed and the consumer builds outside the reactor"
        assert file("base-layer/target").deleteDir()
        mvn '-DquickBuild', '-f', 'application/pom.xml', 'package'

        then:
        buildSucceeded
        outputContains "[         1 tests successful      ]"
        def stagedRoot = file("application/target/native/layer-runtime")
        def stagedRuntimeFiles = stagedRoot.directorySize() > 0
        stagedRuntimeFiles
        def stagedDirectories = []
        stagedRoot.eachDirRecurse { directory ->
            if (directory.listFiles()?.any { NativeImageLayerRuntime.isRuntimeLibrary(it.name) }) {
                stagedDirectories << directory.absolutePath
            }
        }
        !stagedDirectories.empty
        def application = file("application/target/application")
        application.isFile()
        def environment = System.getenv().collect { key, value -> "${key}=${value}" }
        environment << "LD_LIBRARY_PATH=${stagedDirectories.join(File.pathSeparator)}"
        def execution = [application.absolutePath].execute(environment, application.parentFile)
        assert execution.waitFor() == 0
        assert execution.in.text.contains("Hello, layered application!")
    }

    // Retain this probe for a future Native Image release that supports chained shared layers.
    @Ignore("GraalVM Native Image currently supports one shared base layer and a final application executable, not chained shared layers.")
    @Requires({ NativeImageUtils.getMajorJDKVersion(GraalVMSupport.getGraalVMHomeVersionString()) >= 25 })
    @IgnoreIf({ os.windows || os.macOs })
    def "builds and runs a chained reactor layer stack"() {
        given:
        withSample("layered-maven-application")
        def frameworkPom = file("framework-layer/pom.xml")
        frameworkPom.parentFile.mkdirs()
        frameworkPom.text = '''
            <project xmlns="http://maven.apache.org/POM/4.0.0">
                <modelVersion>4.0.0</modelVersion>
                <parent>
                    <groupId>org.graalvm.buildtools.samples</groupId>
                    <artifactId>layered-maven-application</artifactId>
                    <version>1.0-SNAPSHOT</version>
                </parent>
                <artifactId>framework-layer</artifactId>
                <dependencies>
                    <dependency>
                        <groupId>org.graalvm.buildtools.samples</groupId>
                        <artifactId>base-layer</artifactId>
                        <version>\${project.version}</version>
                        <type>nil</type>
                        <scope>runtime</scope>
                    </dependency>
                    <dependency>
                        <groupId>org.apache.commons</groupId>
                        <artifactId>commons-lang3</artifactId>
                        <version>3.17.0</version>
                        <scope>runtime</scope>
                    </dependency>
                </dependencies>
                <build><plugins><plugin>
                    <groupId>org.graalvm.buildtools</groupId>
                    <artifactId>native-maven-plugin</artifactId>
                    <executions><execution><id>create-framework-layer</id><phase>package</phase>
                        <goals><goal>layer-create</goal></goals>
                        <configuration>
                            <useLayers><useLayer><artifact>org.graalvm.buildtools.samples:base-layer</artifact></useLayer></useLayers>
                            <layer>
                                <name>framework</name>
                                <dependencies><dependency><artifact>org.apache.commons:commons-lang3</artifact></dependency></dependencies>
                            </layer>
                        </configuration>
                    </execution></executions>
                </plugin></plugins></build>
            </project>
        '''.stripIndent()
        def rootPom = file("pom.xml")
        rootPom.text = rootPom.text.replace('''        <module>application</module>
''', '''        <module>framework-layer</module>
        <module>application</module>
''')
        def applicationPom = file("application/pom.xml")
        applicationPom.text = applicationPom.text
            .replace('''    <dependencies>
''', '''    <dependencies>
        <dependency>
            <groupId>org.graalvm.buildtools.samples</groupId>
            <artifactId>framework-layer</artifactId>
            <version>\${project.version}</version>
            <type>nil</type>
            <scope>runtime</scope>
        </dependency>
        <dependency>
            <groupId>org.apache.commons</groupId>
            <artifactId>commons-lang3</artifactId>
            <version>3.17.0</version>
            <scope>runtime</scope>
        </dependency>
''')
            .replace('''                    <useLayers>
''', '''                    <useLayers>
                        <useLayer>
                            <artifact>org.graalvm.buildtools.samples:framework-layer</artifact>
                        </useLayer>
''')

        when:
        mvn '-DquickBuild', '-DskipTests', 'package'

        then:
        buildSucceeded
        file("framework-layer/target/native/layers/framework/framework.nil").isFile()
        outputContains "-H:LayerCreate=framework.nil,path="
        output.count("-H:LayerUse=") >= 2
        def application = file("application/target/application")
        def environment = System.getenv().collect { key, value -> "${key}=${value}" }
        environment << "LD_LIBRARY_PATH=${file('base-layer/target/native/layers/base').absolutePath}:${file('framework-layer/target/native/layers/framework').absolutePath}"
        def execution = [application.absolutePath].execute(environment, application.parentFile)
        assert execution.waitFor() == 0
        assert execution.in.text.contains("Hello, layered application!")
    }
}
