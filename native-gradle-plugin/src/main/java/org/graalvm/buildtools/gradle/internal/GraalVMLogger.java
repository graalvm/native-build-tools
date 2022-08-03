/*
 * Copyright (c) 2021, Oracle and/or its affiliates. All rights reserved.
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

package org.graalvm.buildtools.gradle.internal;

import org.gradle.api.logging.Logger;

import java.util.HashSet;
import java.util.Set;

/**
 * Wraps the Gradle logger with a minimal API surface.
 */
public final class GraalVMLogger {
    private static final Set<String> LOGGED_MESSAGES = new HashSet<>();

    public static void newBuild() {
        synchronized (LOGGED_MESSAGES) {
            LOGGED_MESSAGES.clear();
        }
    }

    private final Logger delegate;

    public static GraalVMLogger of(Logger delegate) {
        return new GraalVMLogger(delegate);
    }

    private GraalVMLogger(Logger delegate) {
        this.delegate = delegate;
    }

    public void log(String s) {
        delegate.info("[native-image-plugin] {}", s);
    }

    public void log(String pattern, Object... args) {
        delegate.info("[native-image-plugin] " + pattern, args);
    }

    public void lifecycle(String s) {
        delegate.lifecycle("[native-image-plugin] {}", s);
    }

    public void lifecycle(String pattern, Object... args) {
        delegate.lifecycle("[native-image-plugin] " + pattern, args);
    }

    public void error(String s) {
        delegate.error("[native-image-plugin] {}", s);
    }

    public void warn(String s) {
        delegate.warn("[native-image-plugin] {}", s);
    }

    public void logOnce(String message) {
        synchronized (LOGGED_MESSAGES) {
            if (LOGGED_MESSAGES.add(message)) {
                lifecycle(message);
            }
        }
    }
}
