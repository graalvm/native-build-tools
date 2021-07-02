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

package org.graalvm.buildtools.gradle.fixtures

import groovy.transform.CompileStatic

@CompileStatic
class TeeWriter extends Writer {
    private final Writer one
    private final Writer two

    static TeeWriter of(Writer one, Writer two) {
        new TeeWriter(one, two)
    }

    private TeeWriter(Writer one, Writer two) {
        this.one = one
        this.two = two
    }

    @Override
    void write(int c) throws IOException {
        try {
            one.write(c)
        } finally {
            two.write(c)
        }
    }

    @Override
    void write(char[] cbuf) throws IOException {
        try {
            one.write(cbuf)
        } finally {
            two.write(cbuf)
        }
    }

    @Override
    void write(char[] cbuf, int off, int len) throws IOException {
        try {
            one.write(cbuf, off, len)
        } finally {
            two.write(cbuf, off, len)
        }
    }

    @Override
    void write(String str) throws IOException {
        try {
            one.write(str)
        } finally {
            two.write(str)
        }
    }

    @Override
    void write(String str, int off, int len) throws IOException {
        try {
            one.write(str, off, len)
        } finally {
            two.write(str, off, len)
        }
    }

    @Override
    void flush() throws IOException {
        try {
            one.flush()
        } finally {
            two.flush()
        }
    }

    @Override
    void close() throws IOException {
        try {
            one.close()
        } finally {
            two.close()
        }
    }
}
