/*
 * Copyright (c) 2020, 2022 Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.reachability.internal.index.artifacts;

import java.util.Set;
import java.util.regex.Pattern;

public class Artifact {
    private final String module;
    private final Set<String> versions;
    private final String directory;
    private final boolean latest;
    private final boolean override;
    private final Pattern defaultForPattern;

    public Artifact(String module, Set<String> versions, String directory,
                    boolean latest, boolean override, String defaultFor) {
        this.module = module;
        this.versions = versions;
        this.directory = directory;
        this.latest = latest;
        this.override = override;
        this.defaultForPattern = (defaultFor == null ? null : Pattern.compile(defaultFor));
    }

    public String getModule() {
        return module;
    }

    public Set<String> getVersions() {
        return versions;
    }

    public String getDirectory() {
        return directory;
    }

    public boolean isLatest() {
        return latest;
    }

    public boolean isOverride() {
        return override;
    }

    public boolean isDefaultFor(String version) {
        return defaultForPattern != null && defaultForPattern.matcher(version).matches();
    }
}
