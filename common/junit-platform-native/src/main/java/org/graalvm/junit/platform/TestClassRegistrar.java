/*
 * Copyright (c) 2020, 2025, Oracle and/or its affiliates. All rights reserved.
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

package org.graalvm.junit.platform;

import java.util.HashSet;
import java.util.Set;
import java.util.function.Consumer;

class TestClassRegistrar {

    private final Set<Class<?>> registeredClasses = new HashSet<>();
    private final Consumer<Class<?>> registrationCallback;

    TestClassRegistrar(Consumer<Class<?>> registrationCallback) {
        this.registrationCallback = registrationCallback;
    }

    void registerTestClassForReflection(Class<?> clazz) {
        if (!shouldRegisterClass(clazz)) {
            return;
        }

        registrationCallback.accept(clazz);

        Class<?>[] declaredClasses = clazz.getDeclaredClasses();
        for (Class<?> declaredClass : declaredClasses) {
            registerTestClassForReflection(declaredClass);
        }

        Class<?>[] interfaces = clazz.getInterfaces();
        for (Class<?> inter : interfaces) {
            registerTestClassForReflection(inter);
        }

        Class<?> superClass = clazz.getSuperclass();
        if (superClass != null && superClass != Object.class) {
            registerTestClassForReflection(superClass);
        }
    }

    private boolean shouldRegisterClass(Class<?> clazz) {
        /* avoid registering java internal classes */
        if (ModuleLayer.boot().modules().contains(clazz.getModule())) {
            return false;
        }

        /* avoid loops (possible case: class B is inner class of A, and B extends A) */
        if (registeredClasses.contains(clazz)) {
            return false;
        }
        registeredClasses.add(clazz);

        return true;
    }
}
