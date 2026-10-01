package ru.mysticchest;

import org.junit.jupiter.api.Test;
import ru.mysticchest.util.Template;
import ru.mysticchest.util.TimeFmt;
import ru.mysticchest.util.WeightedPool;
import ru.mysticchest.util.YamlBlocks;

import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Random;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class UtilTest {
    @Test
    void templateFillsKnownKeysAndKeepsUnknown() {
        Template t = Template.parse("Hello {player}, wait {time}! {unknown}");
        assertEquals("Hello Steve, wait 5s! {unknown}", t.apply("player", "Steve", "time", "5s"));
        assertEquals("no placeholders", Template.parse("no placeholders").apply());
        assertEquals("{ not a key }", Template.parse("{ not a key }").apply());
    }

    @Test
    void timeFormat() {
        assertEquals("1h 2m 5s", TimeFmt.format(3725, "d", "h", "m", "s"));
        assertEquals("1d", TimeFmt.format(86400, "d", "h", "m", "s"));
        assertEquals("1s", TimeFmt.format(0, "d", "h", "m", "s"));
        assertEquals("2 мин", TimeFmt.format(120, "д", "ч", " мин", "с"));
    }

    @Test
    void weightedPoolChancesAndDistribution() {
        WeightedPool<int[]> pool = new WeightedPool<int[]>(
                Arrays.asList(new int[]{1}, new int[]{3}, new int[]{6}),
                new WeightedPool.Weigher<int[]>() { public int weight(int[] a) { return a[0]; } });
        assertEquals(10, pool.total());
        assertEquals(10.0, pool.percent(0), 1e-9);
        assertEquals(60.0, pool.percent(2), 1e-9);
        int[] hits = new int[3];
        Random r = new Random(42);
        for (int i = 0; i < 100000; i++) { int w = pool.pick(r)[0]; hits[w == 1 ? 0 : w == 3 ? 1 : 2]++; }
        assertTrue(Math.abs(hits[2] / 100000.0 - 0.6) < 0.02);
    }

    @Test
    void emptyPoolPicksNull() {
        WeightedPool<String> p = new WeightedPool<String>(Arrays.<String>asList(),
                new WeightedPool.Weigher<String>() { public int weight(String s) { return 1; } });
        assertNull(p.pick(new Random()));
    }

    @Test
    void yamlBlocksKeepCommentsAndAppendOnlyMissing() {
        String tpl = "## head\nlanguage: en\n\n## limits help\nlimits:\n  a: 1\n\n## new section help\nnewsec:\n  ## inner\n  x: 2\n";
        String user = "language: ru\nlimits:\n  a: 5\n";
        Set<String> added = new LinkedHashSet<String>();
        String merged = YamlBlocks.appendMissing(user, tpl, added);
        assertTrue(merged.startsWith(user));                 // user text untouched
        assertTrue(merged.contains("## new section help"));  // tutorial comment travels with the block
        assertTrue(merged.contains("  ## inner"));
        assertEquals(1, added.size());
        assertTrue(added.contains("newsec"));
        assertFalse(merged.contains("language: en"));
        Map<String, String> blocks = YamlBlocks.topLevelBlocks(tpl);
        assertEquals(3, blocks.size());
        assertTrue(blocks.get("limits").startsWith("## limits help"));
    }
}
