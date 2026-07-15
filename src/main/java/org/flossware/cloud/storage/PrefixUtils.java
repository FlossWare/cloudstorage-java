package org.flossware.cloud.storage;

final class PrefixUtils {
    private PrefixUtils() {
    }

    static String buildPrefixedPath(String prefix, String path) {
        if (prefix.isEmpty()) {
            return path;
        }
        return prefix + (prefix.endsWith("/") ? "" : "/") + path;
    }

    static String removePrefix(String prefix, String key) {
        if (prefix.isEmpty()) {
            return key;
        }
        String prefixWithSlash = prefix.endsWith("/") ? prefix : prefix + "/";
        if (key.startsWith(prefixWithSlash)) {
            return key.substring(prefixWithSlash.length());
        }
        return key;
    }
}
