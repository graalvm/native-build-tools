package org.graalvm.buildtools.maven.config;

import org.apache.maven.plugin.descriptor.MojoDescriptor;
import org.apache.maven.plugin.descriptor.PluginDescriptor;
import org.apache.maven.plugin.descriptor.PluginDescriptorBuilder;
import org.apache.maven.plugins.annotations.Parameter;
import org.codehaus.plexus.configuration.PlexusConfigurationException;
import org.codehaus.plexus.util.ReaderFactory;
import org.codehaus.plexus.util.xml.Xpp3Dom;

import java.beans.PropertyEditor;
import java.beans.PropertyEditorManager;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.ParameterizedType;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * This is utility class that provides a way to parse and/or persist configuration options
 * during the extension time.
 * Normally in Maven, the parameters are bound from XML to values during the Mojo creation and there is no way
 * to access / modify those values other than manually traversing DOM tree.
 * .
 * TBH, this should probably be a part of a separate utility library.
 */
public class ExtensionTimeConfigurationUtility {

    public final String PLUGIN_DESCRIPTOR_LOCATION = "/META-INF/maven/plugin.xml";
    public final PluginDescriptorBuilder builder = new PluginDescriptorBuilder();

    /**
     * As Maven loves to complicate things, the @Parameter annotation isn't preserved until runtime :)
     * The idea is that there is а plugin descriptor XML file that is generated during plugin publishing and embedded into
     * each plugin jar. That file - when parsed - is later used (among other things) to map `pom.xml` parameters to Mojos.
     *
     * @return plugin descriptor
     */
    public PluginDescriptor parsePluginDescriptor() {
        try (InputStream xmlDescriptor = ClassLoader.class.getResourceAsStream(PLUGIN_DESCRIPTOR_LOCATION)) {
            Reader reader = ReaderFactory.newXmlReader(xmlDescriptor);
            return builder.build(reader, null);
        } catch (IOException | PlexusConfigurationException e) {
            throw new RuntimeException(e);
        }
    }

    public Object populateMojo(Xpp3Dom configuration, Class<?> mojoClass) {
        PluginDescriptor pd = parsePluginDescriptor();
        MojoDescriptor descriptor = pd.getMojos().stream()
                .filter(mojoDescriptor ->
                        mojoDescriptor.getImplementationClass().getCanonicalName().equals(mojoClass.getCanonicalName()))
                .findFirst().orElse(null);
        if (descriptor == null) {
            return null;
        }

        Object instance;
        try {
            instance = mojoClass.getConstructor().newInstance();
        } catch (InstantiationException | IllegalAccessException |
                 InvocationTargetException | NoSuchMethodException e) {
            throw new RuntimeException(e);
        }

        Map<String, org.apache.maven.plugin.descriptor.Parameter> paramMap
                = descriptor.getParameterMap();

        getAllFields(mojoClass).forEach(field -> {
            if (!paramMap.containsKey(field.getName())) {
                return;
            }

            field.setAccessible(true);

            /*
                TODO:
                 ... aaand here we should try to map types from the plugin descriptor to the fields
                and then map everything to the plugin configuration block, and then convert all the
                values from the plugin configuration block to their respective types (possibly using
                similar idea as the one present in the `loadFromXml` method)...
             */

        });
        return instance;
    }

    /**
     * Given XML configuration and class instance injects values in @Parameter annotated fields.
     * .
     * IGNORE THIS METHOD. This approach doesn't work since Parameter annotation isn't retained
     * until the runtime... I didn't remove this since it might contain some useful snippets for
     * implementation of the `populateMojo` method.
     *
     * @param configuration XML configuration node
     * @param instance      class instance
     */
    public static void loadFromXml(Xpp3Dom configuration, Object instance) {
        getAllFields(instance.getClass()).forEach(field -> {
            Parameter annotation = field.getAnnotation(Parameter.class);
            if (annotation == null) {
                return;
            }

            String property = annotation.property().isEmpty() ? field.getName() : annotation.property();
            String alias = annotation.alias();
            Xpp3Dom element = configuration.getChild(property);
            if (element == null && alias != null) {
                element = configuration.getChild(alias);
            }
            if (element == null) {
                return;
            }

            field.setAccessible(true);

            if (element.getChildCount() == 0) {
                // Now we've found the value, so we need to convert it, and inject it into field of a corresponding class.
                injectValueToField(element, instance, field);
                return;
            }

            if (List.class.isAssignableFrom(field.getType())) {
                ParameterizedType listType = (ParameterizedType) field.getGenericType();
                Class<?> listElementType = (Class<?>) listType.getActualTypeArguments()[0];

                ArrayList<Object> list = new ArrayList<>();
                Arrays.stream(element.getChildren()).forEach(entry -> {
                    list.add(convertFromString(listElementType, entry.getValue()));
                });
                setFieldValue(instance, field, list);
            } else if (Map.class.isAssignableFrom(field.getType())) {
                ParameterizedType mapType = (ParameterizedType) field.getGenericType();
                Class<?> mapKeyType = (Class<?>) mapType.getActualTypeArguments()[0];
                Class<?> mapValueType = (Class<?>) mapType.getActualTypeArguments()[1];

                HashMap<Object, Object> map = new HashMap<>();
                Arrays.stream(element.getChildren()).forEach(entry -> {
                    map.put(convertFromString(mapKeyType, entry.getName()),
                            convertFromString(mapValueType, entry.getValue()));
                });
                setFieldValue(instance, field, map);
            } else {
                // This is probably a complex type, so we should try going in recursively.
                try {
                    Object nested = field.getType().getConstructor().newInstance();
                    loadFromXml(element, nested);
                    setFieldValue(instance, field, nested);
                } catch (InstantiationException | IllegalAccessException | InvocationTargetException |
                         NoSuchMethodException e) {
                    throw new RuntimeException(e);
                }
            }
        });
    }

    /**
     * Sets a field in a given instance to a given value
     *
     * @param instance target instance
     * @param field    field of instance class
     * @param value    value to be set
     */
    public static void setFieldValue(Object instance, Field field, Object value) {
        try {
            field.set(instance, value);
        } catch (IllegalAccessException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * A utility method that consumes a string and returns an object of a given class.
     *
     * @param targetType class for resulting object
     * @param text       string content
     * @return resulting object
     */
    public static Object convertFromString(Class<?> targetType, String text) {
        // Yes, we are using java.beans utility classes here.
        PropertyEditor editor = PropertyEditorManager.findEditor(targetType);
        if (editor == null) {
            if (File.class.isAssignableFrom(targetType)) {
                return Paths.get(text).toFile();
            }
            if (Path.class.isAssignableFrom(targetType)) {
                return Paths.get(text);
            }
            if (URL.class.isAssignableFrom(targetType)) {
                try {
                    return new URL(text);
                } catch (MalformedURLException e) {
                    throw new RuntimeException(e);
                }
            }
            return null;
        }
        editor.setAsText(text);
        return editor.getValue();
    }

    /**
     * Given XML element, instance and its field, injects converted value into field.
     *
     * @param element  XML node containing string
     * @param instance object of that class
     * @param field    field in
     */
    public static void injectValueToField(Xpp3Dom element, Object instance, Field field) {
        field.setAccessible(true);
        Object parsed = convertFromString(field.getType(), element.getValue());
        if (parsed != null) {
            setFieldValue(instance, field, parsed);
        }
    }

    private static List<Field> getAllFields(Class<?> target) {
        List<Field> fields = new ArrayList<>();
        Class<?> clazz = target;
        while (clazz != Object.class) {
            fields.addAll(Arrays.asList(clazz.getDeclaredFields()));
            clazz = clazz.getSuperclass();
        }
        return fields;
    }

    public static void debugPrint(Object instance) {
        System.out.println("IN HERE " + instance.getClass().getCanonicalName());
        getAllFields(instance.getClass()).forEach(field -> {
            System.out.println("GET FIELD: " + field.getName());
            Parameter annotation = field.getAnnotation(Parameter.class);
            if (annotation == null) {
                System.out.println("skipped" + field.getAnnotations().length);
                Arrays.stream(field.getAnnotations()).forEach(System.out::println);
                return;
            }
            try {
                System.out.println(field.getName() + ": " + field.get(instance));
            } catch (IllegalAccessException e) {
                throw new RuntimeException(e);
            }
        });
    }

}
