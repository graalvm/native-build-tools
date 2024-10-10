/*
 * Copyright (c) 2024, Oracle and/or its affiliates. All rights reserved.
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

import com.fasterxml.jackson.databind.node.ObjectNode
import org.graalvm.buildtools.maven.sbom.SBOMGenerator
import org.graalvm.buildtools.utils.NativeImageUtils
import spock.lang.Requires
import com.fasterxml.jackson.databind.ObjectMapper

class SBOMFunctionalTest extends AbstractGraalVMMavenFunctionalTest {
    private static boolean EE() {
        NativeCompileNoForkMojo.isOracleGraalVM(null)
    }

    private static boolean CE() {
        !EE()
    }

    private static boolean jdkVersionSupportsAugmentedSBOM() {
        NativeImageUtils.getMajorJDKVersion(NativeCompileNoForkMojo.getVersionInformation(null)) >= SBOMGenerator.requiredNativeImageVersion
    }

    private static boolean unsupportedJDKVersion() {
        !jdkVersionSupportsAugmentedSBOM()
    }

    private static boolean supportedAugmentedSBOMVersion() {
        EE() && jdkVersionSupportsAugmentedSBOM()
    }

    @Requires({ supportedAugmentedSBOMVersion() })
    def "sbom is created when buildArg '--enable-sbom=export,embed' is used"() {
        withSample 'java-application'

        when:
        /* The 'native-sbom' profile sets the '--enable-sbom' argument. */
        mvn '-Pnative-sbom', '-DquickBuild', '-DskipTests', 'package', 'exec:exec@native'

        def sbom = file("target/example-app.sbom.json")

        then:
        buildSucceeded
        outputContainsPattern".*CycloneDX SBOM with \\d+ component\\(s\\) is embedded in binary \\(.*?\\) and exported as JSON \\(see build artifacts\\)\\."
        outputDoesNotContain "Use '--enable-sbom' to assemble a Software Bill of Materials (SBOM)"
        validateSbom sbom
        !file(String.format("target/%s", SBOMGenerator.SBOM_FILENAME)).exists()
        outputContains "Hello, native!"
    }

    /**
     * If user sets {@link NativeCompileNoForkMojo#AUGMENTED_SBOM_PARAM_NAME} to true then an SBOM should be generated
     * with default SBOM arguments even if user did not explicitly specify '--enable-sbom' as a buildArg.
     */
    @Requires({ supportedAugmentedSBOMVersion() })
    def "sbom is created when only the augmented sbom parameter is used (but not the '--enable-sbom' buildArg)"() {
        withSample 'java-application'

        when:
        mvn '-Pnative-augmentedSBOM-only', '-DquickBuild', '-DskipTests', 'package', 'exec:exec@native'

        def sbom = file("target/example-app.sbom.json")

        then:
        buildSucceeded
        outputContainsPattern".*CycloneDX SBOM with \\d+ component\\(s\\) is embedded in binary \\(.*?\\)."
        outputDoesNotContain "Use '--enable-sbom' to assemble a Software Bill of Materials (SBOM)"
        validateSbom sbom
        !file(String.format("target/%s", SBOMGenerator.SBOM_FILENAME)).exists()
        outputContains "Hello, native!"
    }

    @Requires({ CE() })
    def "error is thrown when augmented sbom parameter is used with CE"() {
        withSample 'java-application'

        when:
        mvn  '-Pnative-augmentedSBOM-only', '-DquickBuild', '-DskipTests', 'package'

        then:
        buildFailed
    }

    @Requires({ EE() && unsupportedJDKVersion() })
    def "error is thrown when augmented sbom parameter is used with EE but not with an unsupported JDK version"() {
        withSample 'java-application'

        when:
        mvn '-Pnative-augmentedSBOM-only', '-DquickBuild', '-DskipTests', 'package'

        then:
        buildFailed
    }

    /**
     * Validates the SBOM produced from 'java-application'.
     * @param sbom path to the SBOM.
     * @return true if validation succeeded.
     */
    private static boolean validateSbom(File sbom) {
        try {
            if (!sbom.exists()) {
                println "SBOM not found: ${sbom}"
                return false
            }

            def mapper = new ObjectMapper()
            def rootNode = mapper.readTree(sbom)

            // Check root fields
            assert rootNode.has('bomFormat')
            assert rootNode.get('bomFormat').asText() == 'CycloneDX'
            assert rootNode.has('specVersion')
            assert rootNode.has('serialNumber')
            assert rootNode.has('version')
            assert rootNode.has('metadata')
            assert rootNode.has('components')
            assert rootNode.has('dependencies')

            // Check metadata/component
            def metadataComponent = rootNode.path('metadata').path('component')
            assert metadataComponent.has('group')
            assert metadataComponent.get('group').asText() == 'org.graalvm.buildtools.examples'
            assert metadataComponent.has('name')
            assert metadataComponent.get('name').asText() == 'maven'

            // Check that components and dependencies are non-empty
            assert !rootNode.get('components').isEmpty()
            assert !rootNode.get('dependencies').isEmpty()

            // Check that the main component has no dependencies
            def mainComponentId = metadataComponent.get('bom-ref').asText()
            def mainComponentDependency = rootNode.get('dependencies').find { it.get('ref').asText() == mainComponentId } as ObjectNode
            assert mainComponentDependency.get('dependsOn').isEmpty()

            // Check that the main component is not found in "components"
            assert !rootNode.get('components').any { it.get('bom-ref').asText() == mainComponentId }

            return true
        } catch (AssertionError | Exception e) {
            println "SBOM validation failed: ${e.message}"
            return false
        }
    }
}
