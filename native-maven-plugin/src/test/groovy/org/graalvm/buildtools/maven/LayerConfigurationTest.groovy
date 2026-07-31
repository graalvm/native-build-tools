/*
 * Copyright (c) 2026, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * The Universal Permissive License (UPL), Version 1.0
 *
 * Subject to the condition set forth below, permission is hereby granted to any
 * person obtaining a copy of this software, associated documentation and/or
 * data (collectively the "Software"), free of charge and under any and all
 * copyright rights in the Software and any and all patent rights owned or
 * freely licensable by each licensor hereunder covering either (i) the
 * unmodified Software as contributed to or provided by such licensor, or (ii)
 * the Larger Works (as defined below), to deal in both.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND.
 */
package org.graalvm.buildtools.maven

import org.apache.maven.artifact.DefaultArtifact
import org.apache.maven.artifact.handler.DefaultArtifactHandler
import org.graalvm.buildtools.maven.config.LayerConfiguration
import org.graalvm.buildtools.maven.config.LayerDependencyConfiguration
import spock.lang.Specification

// Verifies Maven XML mapping for layer selection. §FS-config-model.7.
class LayerConfigurationTest extends Specification {
    def "includeDependencies all maps to the neutral all selector"() {
        given:
        def configuration = new LayerConfiguration()
        configuration.includeDependencies = "all"

        expect:
        configuration.all
    }

    def "dependency selection is transitive by default and configurable"() {
        given:
        def dependency = new LayerDependencyConfiguration()
        dependency.artifact = "com.acme:extension"

        expect:
        dependency.transitive

        when:
        dependency.transitive = false

        then:
        !dependency.transitive
    }

    def "version-qualified transitive selection requires the resolved root version"() {
        given:
        def artifact = new DefaultArtifact(
            "com.acme", "extension", "2.0", "runtime", "jar", null, new DefaultArtifactHandler("jar"))
        artifact.dependencyTrail = ["org.example:application:jar:1.0", "com.acme:extension:jar:2.0"]

        expect:
        !LayerCreateMojo.matches(artifact, ["com.acme", "extension", "1.0"] as String[], true)
        LayerCreateMojo.matches(artifact, ["com.acme", "extension", "2.0"] as String[], true)
    }

    def "transitive selection follows the exactly matched root dependency trail"() {
        given:
        def artifact = new DefaultArtifact(
            "com.acme", "support", "3.0", "runtime", "jar", null, new DefaultArtifactHandler("jar"))
        artifact.dependencyTrail = [
            "org.example:application:jar:1.0",
            "com.acme:extension:jar:2.0",
            "com.acme:support:jar:3.0"
        ]

        expect:
        LayerCreateMojo.matches(artifact, ["com.acme", "extension", "2.0"] as String[], true)
        !LayerCreateMojo.matches(artifact, ["com.acme", "extension", "1.0"] as String[], true)
    }
}
