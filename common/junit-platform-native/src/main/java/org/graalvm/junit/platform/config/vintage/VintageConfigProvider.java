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

package org.graalvm.junit.platform.config.vintage;

import org.graalvm.junit.platform.config.core.NativeImageConfiguration;
import org.graalvm.junit.platform.config.core.PluginConfigProvider;

public class VintageConfigProvider implements PluginConfigProvider {

    @Override
    public void onLoad(NativeImageConfiguration config) {
        config.initializeAtBuildTime(
                "org.junit.vintage.engine.descriptor.RunnerTestDescriptor",
                "org.junit.vintage.engine.support.UniqueIdReader",
                "org.junit.vintage.engine.support.UniqueIdStringifier",
                "org.junit.runner.Description",
                "org.junit.runners.BlockJUnit4ClassRunner",
                "org.junit.runners.JUnit4",
                /* Workaround until we can register serializable classes from a native-image feature */
                "org.junit.runner.Result"
        );

        if (getMajorJDKVersion() >= 21) {
            /* new with simulated class initialization */
            config.initializeAtBuildTime(
                    "java.lang.annotation.Annotation",
                    "org.junit.runners.model.FrameworkMethod",
                    "org.junit.runners.model.TestClass",
                    "org.junit.runners.ParentRunner$1",
                    "org.junit.Test",
                    "org.junit.vintage.engine.descriptor.VintageEngineDescriptor",
                    "org.junit.vintage.engine.VintageTestEngine"
            );
        }
    }

    @Override
    public void onTestClassRegistered(Class<?> testClass, NativeImageConfiguration registry) {
    }
}
