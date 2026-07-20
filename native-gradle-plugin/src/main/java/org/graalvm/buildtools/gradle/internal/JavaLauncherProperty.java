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

import org.gradle.api.provider.Property;
import org.gradle.api.provider.Provider;
import org.gradle.api.provider.ProviderFactory;
import org.gradle.jvm.toolchain.JavaLauncher;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;

/**
 * A holder for a {@link Property}{@code <JavaLauncher>} that records whether its value was assigned
 * explicitly by the user rather than supplied by the plugin-installed convention.
 *
 * <p>The underlying {@link Property} is produced as a runtime {@link Proxy} over the real property
 * returned by Gradle's {@code ObjectFactory}. This avoids compiling a concrete implementation of the
 * evolving {@code Property} interface against a fixed Gradle API: the proxy matches whatever
 * {@code Property} looks like in the running Gradle version, staying binary-compatible from Gradle
 * 8.4 to current without referencing {@code org.gradle.api.provider.SupportsConvention}.</p>
 *
 * <p>Provenance is tracked by intercepting the property's mutating methods:
 * {@code set}/{@code value} mark the value <em>explicit</em> while delegating the mutation;
 * {@code unset} clears it back to <em>not</em> explicit. Installing or removing a {@code convention}
 * never marks the value explicit, and consulting it (reading the value) does not either, so a user
 * re-assigning the same value the convention would supply stays explicit. In particular
 * {@code unsetConvention()} (which only removes the convention and never touches an explicitly set
 * value), provenance is left untouched, so an explicitly assigned launcher remains explicit after the
 * convention is dropped.</p>
 *
 * <p>To preserve Gradle's mutation and exception semantics the interceptor first delegates every call
 * to the real property and only updates the provenance flag once the call has succeeded. This keeps
 * provenance consistent with Gradle's actual state even when a mutation is rejected by the backing
 * property (for example a finalized value that must not be reassigned), letting Gradle's own exception
 * propagate untouched. Fluent/chaining methods ({@code value}, {@code convention}, {@code unset},
 * {@code unsetConvention}) return the proxy itself so that subsequent mutations remain intercepted.</p>
 */
public final class JavaLauncherProperty {
    private final Property<JavaLauncher> property;
    private final Provider<Boolean> explicit;

    private JavaLauncherProperty(Property<JavaLauncher> delegate, ProviderFactory providers) {
        final boolean[] explicitFlag = {false};
        InvocationHandler handler = (proxy, method, args) ->
                handle(proxy, method, args, delegate, explicitFlag);
        this.property = (Property<JavaLauncher>) Proxy.newProxyInstance(
                JavaLauncher.class.getClassLoader(), new Class<?>[]{Property.class}, handler);
        this.explicit = providers.provider(() -> property.isPresent() && explicitFlag[0]);
    }

    public static JavaLauncherProperty of(Property<JavaLauncher> delegate, ProviderFactory providers) {
        return new JavaLauncherProperty(delegate, providers);
    }

    /**
     * The proxied {@link Property} to expose via {@code @Nested} inputs and to users.
     */
    public Property<JavaLauncher> getProperty() {
        return property;
    }

    /**
     * Whether the resolved launcher was assigned explicitly by the user rather than supplied by the
     * convention. Read at execution time (resolves the property, which may trigger toolchain detection).
     * §FS-native-invocation.1.1, §FS-native-invocation.1.2.
     */
    public Provider<Boolean> explicit() {
        return explicit;
    }

    private static Object handle(Object proxy, Method method, Object[] args,
                                 Property<JavaLauncher> delegate, boolean[] explicitFlag)
            throws Throwable {
        Object[] actualArgs = args != null ? args : new Object[0];
        switch (method.getName()) {
            case "set":
                // set(value) marks the value explicit; set(null) is the idiomatic unset and clears it.
                // Delegate first so a failed call cannot corrupt the provenance flag, and return the
                // delegate's (void) result verbatim.
                Object setResult = invokeDelegated(method, delegate, actualArgs);
                explicitFlag[0] = !(actualArgs.length == 1 && actualArgs[0] == null);
                return setResult;
            case "value":
                // Delegate first so a rejected mutation propagates Gradle's exception and leaves
                // provenance untouched. A null argument is treated by Gradle as a no-op (the value
                // keeps coming from the convention), so it must not mark the value explicit, matching
                // Gradle's own semantics. A non-null argument marks the value explicit. Return the
                // proxy so a subsequent chained mutation still passes through this interceptor.
                invokeDelegated(method, delegate, actualArgs);
                if (actualArgs.length == 1 && actualArgs[0] != null) {
                    explicitFlag[0] = true;
                }
                return proxy;
            case "unset":
                // Clearing the explicit value restores convention provenance.
                invokeDelegated(method, delegate, actualArgs);
                explicitFlag[0] = false;
                return proxy;
            case "convention":
            case "unsetConvention":
                // Installing or removing a convention never marks the value explicit and neither touches
                // an explicitly set value, so leave the provenance flag untouched. Delegate first and
                // return the proxy so chained mutations stay intercepted.
                invokeDelegated(method, delegate, actualArgs);
                return proxy;
            case "equals":
                // By default this would delegate to delegate.equals(proxy), which is false even for
                // proxy.equals(proxy). Preserve referential identity, consistent with hashCode/toString.
                return proxy == actualArgs[0];
            case "hashCode":
                return System.identityHashCode(proxy);
            case "toString":
                return JavaLauncherProperty.class.getName() + "@"
                        + Integer.toHexString(System.identityHashCode(proxy));
            default:
                return invokeDelegated(method, delegate, actualArgs);
        }
    }

    /**
     * Delegates a call to the real property, unwrapping a Java reflection {@link InvocationTargetException}
     * so the caller receives Gradle's own exception (e.g. an {@code IllegalStateException} from mutating a
     * finalized value) rather than a {@code UndeclaredThrowableException} wrapping it.
     */
    private static Object invokeDelegated(Method method, Object target, Object[] args) throws Throwable {
        try {
            return method.invoke(target, args);
        } catch (InvocationTargetException e) {
            throw e.getCause();
        }
    }
}
