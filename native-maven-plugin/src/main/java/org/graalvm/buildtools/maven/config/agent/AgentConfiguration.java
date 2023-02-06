/*
 * Copyright (c) 2020, 2022, Oracle and/or its affiliates. All rights reserved.
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

package org.graalvm.buildtools.maven.config.agent;

import org.apache.maven.plugins.annotations.Parameter;
import org.graalvm.buildtools.agent.AgentMode;
import org.graalvm.buildtools.agent.ConditionalAgentMode;
import org.graalvm.buildtools.agent.DirectAgentMode;
import org.graalvm.buildtools.agent.StandardAgentMode;

import java.util.Arrays;
import java.util.Properties;
import java.util.stream.Collectors;

public class AgentConfiguration extends org.graalvm.buildtools.agent.AgentConfiguration {

    @Parameter
    private boolean enabled;

    @Parameter(defaultValue = "standard")
    private String defaultMode;

    @Parameter
    private ModesConfiguration modes;

    @Parameter
    private AgentOptionsConfiguration options;

    @Parameter
    private MetadataCopyConfiguration metadataCopy;

    public MetadataCopyConfiguration getMetadataCopyConfiguration() {
        if (metadataCopy != null) {
            return metadataCopy;
        } else {
            return new MetadataCopyConfiguration();
        }
    }

    @Override
    public boolean isEnabled() {
        return enabled;
    }

    public AgentMode getAgentMode() {
        // if default mode is not declared, we return Standard mode as default
        if (defaultMode == null) {
            return new StandardAgentMode();
        }

        if (defaultMode.equalsIgnoreCase("standard")) {
            return new StandardAgentMode();
        }

        if (defaultMode.equalsIgnoreCase("conditional")) {
            Properties conditionalMode = modes.getConditional();
            if (conditionalMode != null) {
                return new ConditionalAgentMode(conditionalMode.getProperty("userCodeFilterPath"),
                                                conditionalMode.getProperty("extraFilterPath", ""),
                                                Boolean.parseBoolean(conditionalMode.getProperty("parallel")));
            }
        }

        if (defaultMode.equalsIgnoreCase("direct")) {
            String directMode = modes.getDirect();
            if (directMode != null) {
                return new DirectAgentMode(Arrays.stream(directMode.split(" ")).collect(Collectors.toList()));
            }
        }

        throw new IllegalArgumentException("Default agent mode " + defaultMode + ", provided in pom.xml is not supported. Please" +
                " choose one of the following modes: standard, conditional or direct");
    }

    public String getDefaultMode() {
        return defaultMode != null ? defaultMode : "standard";
    }

    public ModesConfiguration getModes() {
        return modes;
    }

    public AgentOptionsConfiguration getOptions() {
        return options;
    }
}
