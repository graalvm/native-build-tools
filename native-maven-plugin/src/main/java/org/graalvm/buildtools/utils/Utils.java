package org.graalvm.buildtools.utils;

public class Utils {

    public static boolean parseBoolean(String description, String value) {
        value = assertNotEmptyAndTrim(value, description + " must have a value").toLowerCase();
        switch (value) {
            case "true":
                return true;
            case "false":
                return false;
            default:
                throw new IllegalStateException(description + " must have a value of 'true' or 'false'");
        }
    }

    public static String assertNotEmptyAndTrim(String input, String message) {
        if (input == null || input.isEmpty()) {
            throw new IllegalStateException(message);
        }
        return input.trim();
    }

}
