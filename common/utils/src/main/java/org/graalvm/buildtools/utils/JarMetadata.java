/*
 * Copyright 2003-2021 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.graalvm.buildtools.utils;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Properties;

public class JarMetadata {
    private final List<String> packageList;

    public JarMetadata(List<String> packageList) {
        this.packageList = packageList;
    }

    public List<String> getPackageList() {
        return packageList;
    }

    public static JarMetadata readFrom(Path propertiesFile) {
        Properties props = new Properties();
        try (InputStream is = Files.newInputStream(propertiesFile)) {
            props.load(is);
        } catch (Exception e) {
            throw new RuntimeException("Unable to read metadata from properties file " + propertiesFile, e);
        }
        String packages = (String) props.get("packages");
        List<String> packageList = packages == null ? List.of() : Arrays.asList(packages.split(","));
        return new JarMetadata(packageList);
    }
}
