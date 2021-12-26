/*
 * Copyright (c) 2020, 2021, Oracle and/or its affiliates. All rights reserved.
 * Copyright (c) 2021, 2021, Alibaba Group Holding Limited. All rights reserved.
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

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import com.oracle.svm.hosted.FeatureImpl.BeforeAnalysisAccessImpl;
import com.oracle.svm.hosted.ImageClassLoader;
import org.graalvm.nativeimage.hosted.Feature.BeforeAnalysisAccess;

import junit.framework.TestCase;
import org.junit.platform.engine.discovery.DiscoverySelectors;
import org.junit.platform.engine.discovery.UniqueIdSelector;

public class JUnitTestCaseScanner {
    private static final String JUNIT3_RUNNER_LABEL = "[engine:junit-vintage]/[runner:";
    private static final String JUNIT3_TEST_CASE_LABEL = "/[test:";
    private static final String JUNIT3_TEST_METHOD_NAME_PREFIX = "test";

    private JUnitPlatformFeature junitFeature;
    private BeforeAnalysisAccessImpl access;
    private ImageClassLoader classLoader;

    public JUnitTestCaseScanner(JUnitPlatformFeature feature, BeforeAnalysisAccess a) {
        this.junitFeature = feature;
        this.access = (BeforeAnalysisAccessImpl) a;
        this.classLoader = access.getImageClassLoader();
    }

    public List<UniqueIdSelector> tryDiscoverTestClasses() {
        List<UniqueIdSelector> selectors = new ArrayList<>();
        tryDiscoverJunit3TestClasses(selectors);
        return selectors;
    }

    public boolean tryDiscoverJunit3TestClasses(List<UniqueIdSelector> selectors) {
        int beforeDiscoverSize = selectors.size();
        classLoader.findSubclasses(TestCase.class, true)
                   .stream()
                   .filter(JUnitTestCaseScanner::isJunit3TestClass)
                   .forEach(clazz -> {
                        Arrays.stream(clazz.getDeclaredMethods())
                              .filter(JUnitTestCaseScanner::isJunit3TestMethod)
                              .map(JUnitTestCaseScanner::generateJunit3Selector)
                              .forEach(selectors::add);
                        junitFeature.registerTestClassForReflection(clazz);
                    });
        return selectors.size() > beforeDiscoverSize;
    }

    private static boolean isJunit3TestClass(Class<? extends TestCase> clazz) {
        int mod = clazz.getModifiers();
        return !Modifier.isAbstract(mod) && Modifier.isPublic(mod);
    }

    private static boolean isJunit3TestMethod(Method method) {
        int mod = method.getModifiers();
        if (!Modifier.isPublic(mod) || Modifier.isAbstract(mod)) {
            return false;
        }
        if (!method.getName().startsWith(JUNIT3_TEST_METHOD_NAME_PREFIX)) {
            return false;
        }
        if (method.getTypeParameters().length != 0 || !method.getReturnType().equals(void.class)) {
            return false;
        }
        return true;
    }

    // generate junit3 selector format: [engine:junit-vintage]/[runner:com.test.class.Name]/[test:testMehtodName(com.test.class.Name)]
    private static UniqueIdSelector generateJunit3Selector(Method method) {
        Class<?> clazz = method.getDeclaringClass();
        StringBuffer buff = new StringBuffer();
        buff.append(JUNIT3_RUNNER_LABEL).append(clazz.getName()).append("]");
        buff.append(JUNIT3_TEST_CASE_LABEL).append(method.getName());
        buff.append("(").append(clazz.getName()).append(")]");
        return DiscoverySelectors.selectUniqueId(buff.toString());
    }
}
