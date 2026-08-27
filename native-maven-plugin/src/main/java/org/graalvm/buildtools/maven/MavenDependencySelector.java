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
package org.graalvm.buildtools.maven;

import org.apache.maven.artifact.Artifact;
import org.apache.maven.plugin.MojoExecutionException;

import java.util.Arrays;

/**
 * Shared Maven coordinate parsing and dependency-trail matching for layers and Preserve.
 * §FS-config-model.7. §FS-config-model.8.
 */
final class MavenDependencySelector {
    private MavenDependencySelector() {
    }

    static String[] parse(String selector, String feature) throws MojoExecutionException {
        String[] parts = selector == null ? new String[0] : selector.split(":", -1);
        if (parts.length < 2 || parts.length > 3 || Arrays.stream(parts).anyMatch(String::isBlank)) {
            throw new MojoExecutionException(
                feature + " must use groupId:artifactId[:version]: " + selector);
        }
        return parts;
    }

    static boolean matchesExactly(Artifact artifact, String[] parts) {
        return artifact.getGroupId().equals(parts[0])
            && artifact.getArtifactId().equals(parts[1])
            && (parts.length == 2 || artifact.getVersion().equals(parts[2]));
    }

    static boolean matches(Artifact artifact, String[] parts, boolean transitive) {
        boolean exact = matchesExactly(artifact, parts);
        if (exact || !transitive || artifact.getDependencyTrail() == null) {
            return exact;
        }
        // Version-qualified roots must match the same resolved trail before their closure is selected. §FS-config-model.8.
        return artifact.getDependencyTrail().stream().anyMatch(entry -> matchesTrailCoordinate(entry, parts));
    }

    private static boolean matchesTrailCoordinate(String entry, String[] parts) {
        String[] trailParts = entry.split(":", -1);
        return trailParts.length >= 4
            && trailParts[0].equals(parts[0])
            && trailParts[1].equals(parts[1])
            && (parts.length == 2 || trailParts[trailParts.length - 1].equals(parts[2]));
    }
}
