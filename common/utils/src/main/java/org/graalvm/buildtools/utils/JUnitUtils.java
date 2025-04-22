package org.graalvm.buildtools.utils;

import java.util.ArrayList;
import java.util.List;

public final class JUnitUtils {

    public static List<String> excludeJUnitClassInitializationFiles() {
        List<String> args = new ArrayList<>();
        args.add("--exclude-config");
        args.add(".*.jar");
        args.add("META-INF\\/native-image\\/org.junit.*.properties.*");

        return args;
    }

}
