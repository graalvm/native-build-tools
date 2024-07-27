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

package org.graalvm.buildtools.maven;

import org.apache.maven.artifact.Artifact;
import org.apache.maven.model.ConfigurationContainer;
import org.apache.maven.model.Plugin;
import org.apache.maven.model.PluginExecution;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.PluginParameterExpressionEvaluator;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.plugins.annotations.ResolutionScope;
import org.codehaus.plexus.component.configurator.expression.ExpressionEvaluationException;
import org.codehaus.plexus.util.xml.Xpp3Dom;

import java.util.Arrays;
import java.util.List;
import java.util.function.BiFunction;


/**
 * This goal runs native builds. It functions the same as the native:compile goal, but it
 * does not fork the build, so it is suitable for attaching to the build lifecycle.
 */
@Mojo(name = "compile-no-fork", defaultPhase = LifecyclePhase.PACKAGE,
        requiresDependencyResolution = ResolutionScope.RUNTIME,
        requiresDependencyCollection = ResolutionScope.RUNTIME)
public class NativeCompileNoForkMojo extends AbstractNativeImageMojo {

    @Parameter(property = "skipNativeBuild", defaultValue = "false")
    private boolean skip;

    @Parameter(property = "skipNativeBuildForPom", defaultValue = "false")
    private boolean skipNativeBuildForPom;

    private PluginParameterExpressionEvaluator evaluator;

    @Override
    protected List<String> getDependencyScopes() {
        return Arrays.asList(Artifact.SCOPE_COMPILE,
                Artifact.SCOPE_RUNTIME,
                Artifact.SCOPE_COMPILE_PLUS_RUNTIME
        );
    }

    @Override
    public void execute() throws MojoExecutionException {
        if (skip) {
            logger.info("Skipping native-image generation (parameter 'skipNativeBuild' is true).");
            return;
        }

        if(skipNativeBuildForPom && project.getPackaging().equals("pom")) {
            logger.info("Skipping native-image generation (parameter 'skipNativeBuildForPom' is true).");
            return;
        }

        evaluator = new PluginParameterExpressionEvaluator(session, mojoExecution);
        maybeSetMainClassFromPlugin(this::consumeExecutionsNodeValue, "org.apache.maven.plugins:maven-shade-plugin", "transformers", "transformer", "mainClass");
        maybeSetMainClassFromPlugin(this::consumeConfigurationNodeValue, "org.apache.maven.plugins:maven-assembly-plugin", "archive", "manifest", "mainClass");
        maybeSetMainClassFromPlugin(this::consumeConfigurationNodeValue, "org.apache.maven.plugins:maven-jar-plugin", "archive", "manifest", "mainClass");
        maybeAddGeneratedResourcesConfig(buildArgs);
        buildImage();
    }

    private String consumeConfigurationNodeValue(String pluginKey, String... nodeNames) {
        Plugin selectedPlugin = project.getPlugin(pluginKey);
        if (selectedPlugin == null) {
            return null;
        }
        return getConfigurationNodeValue(selectedPlugin, nodeNames);
    }

    private String consumeExecutionsNodeValue(String pluginKey, String... nodeNames) {
        Plugin selectedPlugin = project.getPlugin(pluginKey);
        if (selectedPlugin == null) {
            return null;
        }
        for (PluginExecution execution : selectedPlugin.getExecutions()) {
            String value = getConfigurationNodeValue(execution, nodeNames);
            if (value != null) {
                return value;
            }
        }
        return null;
    }

    private String getConfigurationNodeValue(ConfigurationContainer container, String... nodeNames) {
        if (container != null && container.getConfiguration() instanceof Xpp3Dom) {
            Xpp3Dom node = (Xpp3Dom) container.getConfiguration();
            for (String nodeName : nodeNames) {
                node = node.getChild(nodeName);
                if (node == null) {
                    return null;
                }
            }
            String value = node.getValue();
            return evaluateValue(value);
        }
        return null;
    }

    private String evaluateValue(String value) {
        if (value != null) {
            try {
                Object evaluatedValue = evaluator.evaluate(value);
                if (evaluatedValue instanceof String) {
                    return (String) evaluatedValue;
                }
            } catch (ExpressionEvaluationException ignored) {
            }
        }

        return null;
    }

    private void maybeSetMainClassFromPlugin(BiFunction<String, String[], String> mainClassProvider, String pluginName, String... nodeNames) {
        if (mainClass == null) {
            mainClass = mainClassProvider.apply(pluginName, nodeNames);

            if (mainClass != null) {
                logger.info("Obtained main class from plugin " + pluginName + " with the following path: " + String.join(" -> ", nodeNames));
            }
        }
    }
}
