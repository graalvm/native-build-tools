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

class GenerateResourcesConfigFileTest extends AbstractPluginTest {
    def "generates an empty resource-config.json file"() {
        def project = buildProject()

        when:
        project.generateResourcesConfig()

        then:
        with(project) {
            outputFile.exists()
            matches(outputFile.text, '''{
  "resources": {
    "includes": [],
    "excludes": []
  },
  "bundles": []
}''')
        }
    }

    def "generates a resource-config file with bundles"() {
        def project = buildProject {
            bundles 'my.bundle', 'other.bundle'
        }

        when:
        project.generateResourcesConfig()

        then:
        with(project) {
            outputFile.exists()
            matches(outputFile.text, '''{
  "resources": {
    "includes": [],
    "excludes": []
  },
  "bundles": [
    {
      "name": "my.bundle"
    },
    {
      "name": "other.bundle"
    }
  ]
}''')
        }
    }

    def "generates a resource-config file with includes and excludes"() {
        def project = buildProject {
            includes 'pattern', '[a-z]+'
            excludes 'META-INF/.*', '.*[.]class'
        }

        when:
        project.generateResourcesConfig()

        then:
        with(project) {
            outputFile.exists()
            matches(outputFile.text, '''{
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
  "bundles": []
}''')
        }
    }

    def "doesn't detect resources by default"() {
        def project = buildProject {
            withResource(newResourcesDirectory())
        }

        when:
        project.generateResourcesConfig()

        then:
        with(project) {
            outputFile.exists()
            matches(outputFile.text, '''{
  "resources": {
    "includes": [],
    "excludes": []
  },
  "bundles": []
}''')
        }
    }

    def "can detect resources on classpath"() {
        def project = buildProject {
            withResource(newResourcesDirectory())
            enableDetection()
        }

        when:
        project.generateResourcesConfig()

        then:
        with(project) {
            outputFile.exists()
            matches(outputFile.text, '''{
  "resources": {
    "includes": [
      {
        "pattern": "\\\\Qorg/foo/some/resource.txt\\\\E"
      }
    ],
    "excludes": []
  },
  "bundles": []
}''')
        }
    }

    def "can exclude resources from detected elements"() {
        def project = buildProject {
            withResource(newResourcesDirectory())
            enableDetection()
            excludeFromDetection 'META-INF/.*'
        }

        when:
        project.generateResourcesConfig()

        then:
        with(project) {
            outputFile.exists()
            matches(outputFile.text, '''{
  "resources": {
    "includes": [
      {
        "pattern": "\\\\Qorg/foo/some/resource.txt\\\\E"
      }
    ],
    "excludes": []
  },
  "bundles": []
}''')
        }
    }

    def "can detect resources from jars on classpath"() {
        def project = buildProject {
            withResource(newResourcesJar())
            enableDetection()
        }

        when:
        project.generateResourcesConfig()

        then:
        with(project) {
            outputFile.exists()
            matches(outputFile.text, '''{
  "resources": {
    "includes": [
      {
        "pattern": "\\\\Qorg/foo/some/resource.txt\\\\E"
      }
    ],
    "excludes": []
  },
  "bundles": []
}''')
        }
    }

    def "jars which have a native-image resource config file do not contribute to detected resources"() {
        def project = buildProject {
            withResource(newResourcesJar([
                    'META-INF/native-image/resource-config.json': '{}'
            ]))
            enableDetection()
        }

        when:
        project.generateResourcesConfig()

        then:
        with(project) {
            outputFile.exists()
            matches(outputFile.text, '''{
  "resources": {
    "includes": [],
    "excludes": []
  },
  "bundles": []
}''')
        }
    }

    def "can detect resources ignoring transitive jars on classpath"() {
        def project = buildProject {
            withResource(newResourcesJar())
            restrictDetectionToProjects()
        }

        when:
        project.generateResourcesConfig()

        then:
        with(project) {
            outputFile.exists()
            matches(outputFile.text, '''{
  "resources": {
    "includes": [],
    "excludes": []
  },
  "bundles": []
}''')
        }
    }

}
