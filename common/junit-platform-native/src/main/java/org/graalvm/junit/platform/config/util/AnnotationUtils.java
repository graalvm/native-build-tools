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

package org.graalvm.junit.platform.config.util;

import org.graalvm.junit.platform.config.core.NativeImageConfiguration;
import org.junit.platform.commons.support.AnnotationSupport;

import java.lang.annotation.Annotation;
import java.lang.annotation.Repeatable;
import java.lang.reflect.AnnotatedElement;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Function;

public class AnnotationUtils {

    public static <A extends Annotation> void forEachAnnotationOnClassMembers(Class<?> clazz, Class<A> annotationType, Consumer<A> consumer) {
        for (A annotation : getAnnotations(clazz, annotationType)) {
            consumer.accept(annotation);
        }

        forEachMethod(clazz, method -> {
            for (A annotation : getAnnotations(method, annotationType)) {
                consumer.accept(annotation);
            }
        });
    }

    public static <A extends Annotation> void forEachAnnotatedMethod(Class<?> clazz, Class<A> annotationType, BiConsumer<Method, A> consumer) {
        forEachMethod(clazz, method -> {
            for (A annotation : getAnnotations(method, annotationType)) {
                consumer.accept(method, annotation);
            }
        });
    }

    public static <A extends Annotation> void forEachAnnotatedMethodParameter(Class<?> clazz, Class<A> annotationType, Consumer<A> consumer) {
        forEachMethod(clazz, method -> {
            for (Parameter parameter : method.getParameters()) {
                for (A annotation: getAnnotations(parameter, annotationType)) {
                    consumer.accept(annotation);
                }
            }
        });
    }

    private static void forEachMethod(Class<?> clazz, Consumer<Method> consumer) {
        for (Method method : clazz.getDeclaredMethods()) {
            consumer.accept(method);
        }
    }

    private static <A extends Annotation> List<A> getAnnotations(AnnotatedElement element, Class<A> annotation) {
        if (annotation.getAnnotation(Repeatable.class) != null) {
            return AnnotationSupport.findRepeatableAnnotations(element, annotation);
        } else {
            Optional<A> optionalAnnotation = AnnotationSupport.findAnnotation(element, annotation);
            List<A> annotationList = new ArrayList<>();
            optionalAnnotation.ifPresent(annotationList::add);
            return annotationList;
        }

    }

    public interface ClassProvider<T extends Annotation> extends Function<T, Class<?>> {
    }

    public interface ClassArrayProvider<T extends Annotation> extends Function<T, Class<?>[]> {
    }

    public static <T extends Annotation> void registerClassesFromAnnotationForReflection(Class<?> testClass, NativeImageConfiguration config, Class<T> annotation, ClassArrayProvider<T> classProvider) {
        forEachAnnotationOnClassMembers(testClass, annotation, a -> {
            Class<?>[] reflectivelyAccessedClasses = classProvider.apply(a);
            config.registerAllClassMembersForReflection(reflectivelyAccessedClasses);
        });
    }

    public static <T extends Annotation> void registerClassesFromAnnotationForReflection(Class<?> testClass, NativeImageConfiguration config, Class<T> annotation, ClassProvider<T> classProvider) {
        forEachAnnotationOnClassMembers(testClass, annotation, a -> {
            Class<?> reflectivelyAccessedClass = classProvider.apply(a);
            config.registerAllClassMembersForReflection(reflectivelyAccessedClass);
        });
    }
}
