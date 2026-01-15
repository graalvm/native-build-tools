/*
 * Copyright (c) 2020, 2021, Oracle and/or its affiliates. All rights reserved.
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

package org.graalvm.junit.platform.config.jupiter;

import org.graalvm.junit.platform.JUnitPlatformFeatureUtils;
import org.graalvm.junit.platform.config.core.PluginConfigProvider;
import org.graalvm.junit.platform.config.util.AnnotationUtils;
import org.graalvm.junit.platform.config.util.Utils;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.IndicativeSentencesGeneration;
import org.junit.jupiter.api.TestMethodOrder;
import org.junit.jupiter.api.condition.DisabledIf;
import org.junit.jupiter.api.condition.EnabledIf;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.aggregator.AggregateWith;
import org.junit.jupiter.params.converter.ConvertWith;
import org.junit.jupiter.params.provider.ArgumentsSource;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.FieldSource;
import org.junit.jupiter.params.provider.MethodSource;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

import static org.graalvm.junit.platform.JUnitPlatformFeatureUtils.debug;

public class JupiterConfigProvider extends PluginConfigProvider {

    @Override
    public void onLoad() {
        /* Provide support for Timeout annotation */
        JUnitPlatformFeatureUtils.registerAllClassMembersForReflection(Utils.toClasses(
                "org.junit.jupiter.engine.extension.TimeoutExtension$ExecutorResource",
                "org.junit.jupiter.engine.extension.TimeoutInvocationFactory$SingleThreadExecutorResource"));
    }

    @Override
    public void onTestClassRegistered(Class<?> testClass) {
        /* Provide support for various annotations */

        /* Annotations from org.junit.jupiter.api */
        try {
            AnnotationUtils.registerClassesFromAnnotationForReflection(testClass, TestMethodOrder.class, TestMethodOrder::value);
            AnnotationUtils.registerClassesFromAnnotationForReflection(testClass, ExtendWith.class, ExtendWith::value);
            AnnotationUtils.registerClassesFromAnnotationForReflection(testClass, DisplayNameGeneration.class, DisplayNameGeneration::value);
            AnnotationUtils.registerClassesFromAnnotationForReflection(testClass, IndicativeSentencesGeneration.class, IndicativeSentencesGeneration::generator);
            AnnotationUtils.registerClassesFromAnnotationForReflection(testClass, EnabledIf.class, JupiterConfigProvider::handleEnabledIf);
            AnnotationUtils.registerClassesFromAnnotationForReflection(testClass, DisabledIf.class, JupiterConfigProvider::handleDisabledIf);
        } catch (NoClassDefFoundError e) {
            debug("Cannot register annotations %s from 'org.junit.jupiter.api'. " +
                            "Please verify that you have dependency that includes 'org.junit.jupiter.api' if you want to use these annotations.",
                    List.of("TestMethodOrder", "ExtendWith", "DisplayNameGeneration", "IndicativeSentencesGeneration", "EnabledIf", "DisabledIf"));
        }

        /* Annotations from org.junit.jupiter.params */
        try {
            AnnotationUtils.registerClassesFromAnnotationForReflection(testClass, ArgumentsSource.class, ArgumentsSource::value);
            AnnotationUtils.forEachAnnotatedMethodParameter(testClass, ConvertWith.class, annotation -> JUnitPlatformFeatureUtils.registerAllClassMembersForReflection(annotation.value()));
            AnnotationUtils.forEachAnnotatedMethodParameter(testClass, AggregateWith.class, annotation -> JUnitPlatformFeatureUtils.registerAllClassMembersForReflection(annotation.value()));
            AnnotationUtils.forEachAnnotatedMethod(testClass, EnumSource.class, (m, annotation) -> handleEnumSource(m, annotation));
            AnnotationUtils.registerClassesFromAnnotationForReflection(testClass, MethodSource.class, JupiterConfigProvider::handleMethodSource);

            // special case because the class might not be available because the annotation was introduced in JUnit 5.13
            try {
                AnnotationUtils.registerClassesFromAnnotationForReflection(testClass, FieldSource.class, JupiterConfigProvider::handleFieldSource);
            } catch (NoClassDefFoundError e) {
                debug("Cannot register @FieldSource annotation from org.junit.jupiter.params. " +
                        "Please verify that you have this dependency (with version greater than JUnit 5.13) if you want to use this annotation.");
            }
        } catch (NoClassDefFoundError e) {
            debug("Cannot register annotations %s from 'org.junit.jupiter.params'. " +
                            "Please verify that you have dependency that includes 'org.junit.jupiter.params' if you want to use these annotations.",
                    List.of("ArgumentsSource", "ConvertWith", "AggregateWith", "EnumSource", "MethodSource", "FieldSource"));
        }

    }

    private static Class<?>[] handleMethodSource(MethodSource annotation) {
        return handleMethodReference(annotation.value());
    }

    private static Class<?>[] handleFieldSource(FieldSource annotation) {
        return handleMethodReference(annotation.value());
    }

    private static Class<?>[] handleEnabledIf(EnabledIf annotation) {
        return handleMethodReference(annotation.value());
    }

    private static Class<?>[] handleDisabledIf(DisabledIf annotation) {
        return handleMethodReference(annotation.value());
    }

    private static Class<?>[] handleMethodReference(String... methodNames) {
        List<Class<?>> classList = new ArrayList<>();
        for (String methodName : methodNames) {
            String[] parts = methodName.split("#");
            /*
             * If the method used as an argument source resides in a different class than the test class, it must be specified
             * by the fully qualified class name, followed by a # and the method name
             */
            debug("Found method reference: %s", methodName);
            if (parts.length == 2) {
                String className = parts[0];
                debug("Processing method reference from another class: %s", className);
                try {
                    classList.add(Class.forName(className));
                } catch (ClassNotFoundException e) {
                    debug("Failed to register method reference for reflection: %s Reason: %s", className, e);
                }
            } else {
                debug("Skipping method reference as it originates in the same class as the test: %s", methodName);
            }
        }
        return classList.toArray(new Class<?>[0]);
    }

    public static void handleEnumSource(Method method, EnumSource source) {
        JUnitPlatformFeatureUtils.registerAllClassMembersForReflection(source.value());
        if (method.getParameterCount() > 0) {
            Class<?>[] parameterTypes = method.getParameterTypes();
            /* EnumSource annotated methods without an enum in the annotation value must have the enum as the first parameter. */
            Class<?> enumParameterType = parameterTypes[0];
            if (enumParameterType.isEnum()) {
                debug("Registering method enum parameter for reflection. Method: %s Parameter: %s", method, parameterTypes[0]);
                JUnitPlatformFeatureUtils.registerAllClassMembersForReflection(enumParameterType);
            } else {
                debug("First parameter of method not an enum - skipping. Method: %s", method);
            }
        } else {
            debug("Method doesn't have at least 1 parameter - skipping enum registration. Method: %s", method);
        }
    }
}
