package org.graalvm.buildtools.maven.config.agent;

import org.apache.maven.plugins.annotations.Parameter;
import org.graalvm.buildtools.agent.*;

import java.util.Arrays;
import java.util.Properties;
import java.util.stream.Collectors;

public class AgentConfiguration extends org.graalvm.buildtools.agent.AgentConfiguration {

    @Parameter
    private boolean enabled;

    @Parameter
    private String defaultMode;

    @Parameter
    private ModesConfiguration modes;

    @Parameter
    private CommonOptionsConfiguration commonOptions;

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
        if (defaultMode.equalsIgnoreCase("standard")) {
            return new StandardAgentMode();
        }

        if (defaultMode.equalsIgnoreCase("conditional")) {
            Properties conditionalMode = modes.getConditional();
            if (conditionalMode != null) {
                return new ConditionalAgentMode(conditionalMode.getProperty("userCodeFilterPath"),
                                                conditionalMode.getProperty("extraFilterPath"),
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
        return defaultMode;
    }

    public ModesConfiguration getModes() {
        return modes;
    }

    public CommonOptionsConfiguration getCommonOptions() {
        return commonOptions;
    }
}
