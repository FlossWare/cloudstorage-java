package org.flossware.cloud.storage;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import static org.junit.jupiter.api.Assertions.*;

class PrefixUtilsTest {

    @Test
    @DisplayName("Should return path unchanged when prefix is empty")
    void testBuildPrefixedPathEmptyPrefix() {
        assertEquals("file.txt", PrefixUtils.buildPrefixedPath("", "file.txt"));
    }

    @Test
    @DisplayName("Should prepend prefix with slash separator")
    void testBuildPrefixedPathWithPrefix() {
        assertEquals("my-prefix/file.txt", PrefixUtils.buildPrefixedPath("my-prefix", "file.txt"));
    }

    @Test
    @DisplayName("Should not double slash when prefix ends with slash")
    void testBuildPrefixedPathPrefixEndingSlash() {
        assertEquals("my-prefix/file.txt", PrefixUtils.buildPrefixedPath("my-prefix/", "file.txt"));
    }

    @Test
    @DisplayName("Should return key unchanged when prefix is empty")
    void testRemovePrefixEmptyPrefix() {
        assertEquals("file.txt", PrefixUtils.removePrefix("", "file.txt"));
    }

    @Test
    @DisplayName("Should remove prefix from key")
    void testRemovePrefixMatching() {
        assertEquals("file.txt", PrefixUtils.removePrefix("my-prefix", "my-prefix/file.txt"));
    }

    @Test
    @DisplayName("Should remove prefix ending with slash from key")
    void testRemovePrefixMatchingWithSlash() {
        assertEquals("file.txt", PrefixUtils.removePrefix("my-prefix/", "my-prefix/file.txt"));
    }

    @Test
    @DisplayName("Should return key unchanged when prefix doesn't match")
    void testRemovePrefixNoMatch() {
        assertEquals("other/file.txt", PrefixUtils.removePrefix("my-prefix", "other/file.txt"));
    }

    @Test
    @DisplayName("Constructor should not be instantiable")
    void testConstructorNotInstantiable() throws Exception {
        java.lang.reflect.Constructor<PrefixUtils> constructor =
            PrefixUtils.class.getDeclaredConstructor();
        constructor.setAccessible(true);
        assertNotNull(constructor.newInstance());
    }
}
