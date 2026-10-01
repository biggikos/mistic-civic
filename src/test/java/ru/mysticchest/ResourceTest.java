package ru.mysticchest;

import org.junit.jupiter.api.Test;
import org.yaml.snakeyaml.Yaml;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.*;

/** Every bundled yml must parse, and the two languages must stay in sync. */
class ResourceTest {
    private static final File RES = new File("src/main/resources");

    @SuppressWarnings("unchecked")
    private static Map<String, Object> load(String name) throws Exception {
        InputStream in = new FileInputStream(new File(RES, name));
        try { return (Map<String, Object>) new Yaml().load(in); } finally { in.close(); }
    }

    @Test
    void allBundledYamlParses() throws Exception {
        assertNotNull(load("config.yml").get("language"));
        assertEquals("en", load("config.yml").get("language"));
        assertNotNull(load("tiers.yml").get("tiers"));
        File[] loot = new File(RES, "loot").listFiles();
        assertTrue(loot != null && loot.length >= 7);
        for (File f : loot) {
            Map<String, Object> m = load("loot/" + f.getName());
            assertTrue(m.get("entries") instanceof List, f.getName());
            assertFalse(((List<?>) m.get("entries")).isEmpty(), f.getName());
        }
    }

    @Test
    void languageIsTheFirstRealKey() throws Exception {
        for (String line : java.nio.file.Files.readAllLines(new File(RES, "config.yml").toPath())) {
            if (line.isEmpty() || line.startsWith("#")) continue;
            assertTrue(line.startsWith("language:"), "first key was: " + line);
            return;
        }
    }

    private static void flatten(String prefix, Object node, Map<String, Object> out) {
        if (node instanceof Map) {
            for (Map.Entry<?, ?> e : ((Map<?, ?>) node).entrySet()) flatten(prefix.isEmpty() ? String.valueOf(e.getKey()) : prefix + "." + e.getKey(), e.getValue(), out);
        } else out.put(prefix, node);
    }

    private static Set<String> placeholders(Object v) {
        Set<String> s = new TreeSet<String>();
        Matcher m = Pattern.compile("\\{([a-z0-9_\\-]+)\\}").matcher(String.valueOf(v));
        while (m.find()) s.add(m.group(1));
        return s;
    }

    @Test
    void enAndRuHaveSameKeysAndPlaceholders() throws Exception {
        Map<String, Object> en = new java.util.TreeMap<String, Object>(), ru = new java.util.TreeMap<String, Object>();
        flatten("", load("lang/en.yml"), en);
        flatten("", load("lang/ru.yml"), ru);
        assertEquals(en.keySet(), ru.keySet(), "language files differ in keys");
        for (String k : en.keySet()) {
            assertEquals(placeholders(en.get(k)), placeholders(ru.get(k)), "placeholders differ for " + k);
        }
    }

    @Test
    void configTutorialUsesDoubleHashComments() throws Exception {
        String text = new String(java.nio.file.Files.readAllBytes(new File(RES, "config.yml").toPath()), "UTF-8");
        assertTrue(text.contains("## HOW TO READ THIS FILE"));
        assertTrue(text.contains("## ═══ SPAWN") || text.contains("## ═══ AUTOMATIC SPAWNING"));
    }
}
