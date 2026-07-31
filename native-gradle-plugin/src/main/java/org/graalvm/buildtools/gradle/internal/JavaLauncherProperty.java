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

package org.graalvm.buildtools.gradle.internal;

import org.gradle.api.Transformer;
import org.gradle.api.provider.Property;
import org.gradle.api.provider.Provider;
import org.gradle.api.specs.Spec;
import org.gradle.jvm.toolchain.JavaLauncher;

import java.util.function.BiFunction;

/**
 * A {@link Property} that records whether its current value was assigned explicitly.
 *
 * <p>Once a Gradle property has consulted its convention, {@link #isPresent()} can no longer
 * distinguish the convention-supplied value from one assigned by the user. This wrapper tracks
 * the current assignment instead: {@link #set(Object)} / {@link #value(Object)} mark the value
 * explicit, {@link #unset()} restores convention provenance, and installing a convention never
 * affects the flag. §FS-native-invocation.1.1 §FS-native-invocation.1.2</p>
 */
final class JavaLauncherProperty implements Property<JavaLauncher> {
    private final Property<JavaLauncher> delegate;
    private volatile boolean explicit;

    JavaLauncherProperty(Property<JavaLauncher> delegate) {
        this.delegate = delegate;
    }

    boolean isExplicit() {
        return explicit;
    }

    @Override
    public void set(JavaLauncher value) {
        if (value == null) {
            delegate.unset();
            explicit = false;
        } else {
            delegate.set(value);
            explicit = true;
        }
    }

    @Override
    public void set(Provider<? extends JavaLauncher> provider) {
        delegate.set(provider);
        explicit = true;
    }

    @Override
    public Property<JavaLauncher> value(JavaLauncher value) {
        set(value);
        return this;
    }

    @Override
    public Property<JavaLauncher> value(Provider<? extends JavaLauncher> provider) {
        set(provider);
        return this;
    }

    @Override
    public Property<JavaLauncher> unset() {
        delegate.unset();
        explicit = false;
        return this;
    }

    @Override
    public Property<JavaLauncher> convention(JavaLauncher value) {
        delegate.convention(value);
        return this;
    }

    @Override
    public Property<JavaLauncher> convention(Provider<? extends JavaLauncher> valueProvider) {
        delegate.convention(valueProvider);
        return this;
    }

    @Override
    public Property<JavaLauncher> unsetConvention() {
        delegate.unsetConvention();
        return this;
    }

    @Override
    public void finalizeValue() {
        delegate.finalizeValue();
    }

    @Override
    public void finalizeValueOnRead() {
        delegate.finalizeValueOnRead();
    }

    @Override
    public void disallowChanges() {
        delegate.disallowChanges();
    }

    @Override
    public void disallowUnsafeRead() {
        delegate.disallowUnsafeRead();
    }

    @Override
    public JavaLauncher get() {
        return delegate.get();
    }

    @Override
    public JavaLauncher getOrNull() {
        return delegate.getOrNull();
    }

    @Override
    public JavaLauncher getOrElse(JavaLauncher defaultValue) {
        return delegate.getOrElse(defaultValue);
    }

    @Override
    public <O> Provider<O> map(Transformer<? extends O, ? super JavaLauncher> transformer) {
        return delegate.map(transformer);
    }

    @Override
    public Provider<JavaLauncher> filter(Spec<? super JavaLauncher> spec) {
        return delegate.filter(spec);
    }

    @Override
    public <O> Provider<O> flatMap(Transformer<? extends Provider<? extends O>, ? super JavaLauncher> transformer) {
        return delegate.flatMap(transformer);
    }

    @Override
    public boolean isPresent() {
        return delegate.isPresent();
    }

    @Override
    public Provider<JavaLauncher> orElse(JavaLauncher value) {
        return delegate.orElse(value);
    }

    @Override
    public Provider<JavaLauncher> orElse(Provider<? extends JavaLauncher> valueProvider) {
        return delegate.orElse(valueProvider);
    }

    @Override
    public <U, R> Provider<R> zip(Provider<U> rightSide, BiFunction<? super JavaLauncher, ? super U, ? extends R> combiner) {
        return delegate.zip(rightSide, combiner);
    }
}
