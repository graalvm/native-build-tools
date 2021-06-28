/*
 * Copyright (c) 2021, 2021 Oracle and/or its affiliates. All rights reserved.
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

package org.graalvm.buildtools.gradle.tasks

import org.graalvm.buildtools.gradle.dsl.NativeResourcesOptions
import org.gradle.api.Project
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.RegularFile
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.TaskProvider
import org.gradle.testfixtures.ProjectBuilder
import spock.lang.Specification
import spock.lang.TempDir

import java.nio.file.FileVisitResult
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.SimpleFileVisitor
import java.nio.file.attribute.BasicFileAttributes
import java.util.jar.JarEntry
import java.util.jar.JarOutputStream

class GenerateResourcesConfigFileTest extends Specification {
    @TempDir
    Path testDirectory

    private int resourceCount = 0

    def "generates an empty resource-config.json file"() {
        def project = buildProject()

        when:
        project.execute()

        then:
        with(project) {
            outputFile.exists()
            outputFile.text == '''{
    "resources": {
        "includes": [
            
        ],
        "excludes": [
            
        ]
    },
    "bundles": [
        
    ]
}'''
        }
    }

    def "generates a resource-config file with bundles"() {
        def project = buildProject {
            bundles 'my.bundle', 'other.bundle'
        }

        when:
        project.execute()

        then:
        with(project) {
            outputFile.exists()
            outputFile.text == '''{
    "resources": {
        "includes": [
            
        ],
        "excludes": [
            
        ]
    },
    "bundles": [
        {
            "name": "my.bundle"
        },
        {
            "name": "other.bundle"
        }
    ]
}'''
        }
    }

    def "generates a resource-config file with includes and excludes"() {
        def project = buildProject {
            includes 'pattern', '[a-z]+'
            excludes 'META-INF/.*', '.*[.]class'
        }

        when:
        project.execute()

        then:
        with(project) {
            outputFile.exists()
            outputFile.text == '''{
    "resources": {
        "includes": [
            {
                "pattern": "pattern"
            },
            {
                "pattern": "[a-z]+"
            }
        ],
        "excludes": [
            {
                "pattern": "META-INF/.*"
            },
            {
                "pattern": ".*[.]class"
            }
        ]
    },
    "bundles": [
        
    ]
}'''
        }
    }

    def "doesn't infer resources by default"() {
        def project = buildProject {
            withResource(newResourcesDirectory())
        }

        when:
        project.execute()

        then:
        with(project) {
            outputFile.exists()
            outputFile.text == '''{
    "resources": {
        "includes": [
            
        ],
        "excludes": [
            
        ]
    },
    "bundles": [
        
    ]
}'''
        }
    }

    def "can infer resources on classpath"() {
        def project = buildProject {
            withResource(newResourcesDirectory())
            enableInference()
        }

        when:
        project.execute()

        then:
        with(project) {
            outputFile.exists()
            outputFile.text == '''{
    "resources": {
        "includes": [
            {
                "pattern": "\\\\Qorg/foo/some/resource.txt\\\\E"
            }
        ],
        "excludes": [
            
        ]
    },
    "bundles": [
        
    ]
}'''
        }
    }

    def "can exclude resources from inferred elements"() {
        def project = buildProject {
            withResource(newResourcesDirectory())
            enableInference()
            excludeFromInference 'META-INF/.*'
        }

        when:
        project.execute()

        then:
        with(project) {
            outputFile.exists()
            outputFile.text == '''{
    "resources": {
        "includes": [
            {
                "pattern": "\\\\Qorg/foo/some/resource.txt\\\\E"
            }
        ],
        "excludes": [
            
        ]
    },
    "bundles": [
        
    ]
}'''
        }
    }

    def "can infer resources from jars on classpath"() {
        def project = buildProject {
            withResource(newResourcesJar())
            enableInference()
        }

        when:
        project.execute()

        then:
        with(project) {
            outputFile.exists()
            outputFile.text == '''{
    "resources": {
        "includes": [
            {
                "pattern": "\\\\Qorg/foo/some/resource.txt\\\\E"
            }
        ],
        "excludes": [
            
        ]
    },
    "bundles": [
        
    ]
}'''
        }
    }

    def "jars which have a native-image directory do not contribute to inferred resources"() {
        def project = buildProject {
            withResource(newResourcesJar([
                    'META-INF/native-image/foo': 'foo'
            ]))
            enableInference()
        }

        when:
        project.execute()

        then:
        with(project) {
            outputFile.exists()
            outputFile.text == '''{
    "resources": {
        "includes": [
            
        ],
        "excludes": [
            
        ]
    },
    "bundles": [
        
    ]
}'''
        }
    }

    def "can infer resources ignoring transitive jars on classpath"() {
        def project = buildProject {
            withResource(newResourcesJar())
            restrictInferenceToProjects()
        }

        when:
        project.execute()

        then:
        with(project) {
            outputFile.exists()
            outputFile.text == '''{
    "resources": {
        "includes": [
            
        ],
        "excludes": [
            
        ]
    },
    "bundles": [
        
    ]
}'''
        }
    }

    private Fixture buildProject(@DelegatesTo(value=Fixture, strategy = Closure.DELEGATE_FIRST) Closure<?> spec = {}) {
        def fixture = new Fixture()
        spec.delegate = fixture
        spec.resolveStrategy = Closure.DELEGATE_FIRST
        spec()
        fixture
    }

    private Project newProject() {
        ProjectBuilder.builder()
                .withProjectDir(testDirectory.resolve("test-project").toFile())
                .build()
    }

    private File newResourcesDirectory() {
        def dir = testDirectory.resolve("exploded${resourceCount++}").toFile()
        new FileTreeBuilder(dir) {
            'META-INF' {
                'INDEX.LIST'('dummy')
            }
            'org' {
                'foo' {
                    'some' {
                        'resource.txt'('resource text')
                    }
                }
            }
        }
        dir
    }

    private File newResourcesJar(Map<String, String> extraResources = [:]) {
        File resourcesDirectory = newResourcesDirectory()
        extraResources.each { path, contents -> {
            def resource = new File(resourcesDirectory, path)
            if (resource.getParentFile().directory || resource.getParentFile().mkdirs()) {
                resource << contents
            }
        }}
        File jar = new File(testDirectory.toFile(), "resources-${resourceCount}.jar")
        Path resourcesPath = resourcesDirectory.toPath()

        try (JarOutputStream out = new JarOutputStream(new FileOutputStream(jar))) {
            Files.walkFileTree(resourcesPath, new SimpleFileVisitor<Path>() {
                JarEntry entry

                @Override
                FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                    entry = new JarEntry(resourcesPath.relativize(file).toString())
                    out.putNextEntry(entry)
                    out.write(file.toFile().bytes)
                    out.closeEntry()
                    return super.visitFile(file, attrs)
                }
            })
        }
        jar
    }

    private class Fixture {
        private final Project project = newProject()
        private final NativeResourcesOptions options = project.objects.newInstance(NativeResourcesOptions)
        private final Provider<RegularFile> outputFileProvider = project.layout.buildDirectory.file("resource-config.json")
        private final ConfigurableFileCollection classpath = project.objects.fileCollection()
        private final TaskProvider<GenerateResourcesConfigFile> task = project.tasks.register('underTest', GenerateResourcesConfigFile) {
            it.options.set(Fixture.this.options)
            it.classpath.from(Fixture.this.classpath)
            it.outputFile.set(outputFileProvider)
        }

        Fixture enableInference() {
            options.inferenceOptions.enabled.set(true)
            options.inferenceOptions.restrictToProjectDependencies.set(false)
            this
        }

        Fixture restrictInferenceToProjects() {
            options.inferenceOptions.enabled.set(true)
            options.inferenceOptions.restrictToProjectDependencies.set(true)
            this
        }

        Fixture excludeFromInference(String... patterns) {
            options.inferenceOptions.inferenceExclusionPatterns.addAll(patterns)
            this
        }

        Fixture withResource(File dir) {
            classpath.from(dir)
            this
        }

        Fixture bundles(String... bundles) {
            options.bundles.addAll(bundles)
            this
        }

        Fixture includes(String... includes) {
            options.includedPatterns.addAll(includes)
            this
        }

        Fixture excludes(String... excludes) {
            options.excludedPatterns.addAll(excludes)
            this
        }

        File getOutputFile() {
            outputFileProvider.get().asFile
        }

        void execute() {
            task.get().generate()
        }
    }

}
