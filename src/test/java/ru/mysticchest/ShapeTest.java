package ru.mysticchest;

import org.junit.jupiter.api.Test;
import ru.mysticchest.structure.Canvas;
import ru.mysticchest.structure.Shape;

import java.util.Random;

import static org.junit.jupiter.api.Assertions.*;

class ShapeTest {
    @Test
    void everyShapeHasAFreeChestCellOnSolidGround() {
        for (Shape s : Shape.values()) {
            for (long seed = 0; seed < 40; seed++) {
              for (double decay : new double[]{0, 0.1, 0.35}) {
                Canvas c = new Canvas(new Random(seed), decay);
                s.draw(c);
                assertEquals(Canvas.Slot.AIR, c.get(c.chestX, c.chestY, c.chestZ), s + " chest cell must be air");
                assertNotEquals(Canvas.Slot.AIR, c.get(c.chestX, c.chestY - 1, c.chestZ), s + " chest needs a block under it (decay " + decay + ")");
                assertTrue(c.count() > 20, s + " should have blocks");
              }
            }
        }
    }

    @Test
    void shapesStayInsideTheFootprint() {
        for (Shape s : Shape.values()) {
            for (long seed = 0; seed < 60; seed++) {
                Canvas c = new Canvas(new Random(seed), 0);
                s.draw(c);
                assertTrue(c.minX() >= -18 && c.maxX() <= 18 && c.minZ() >= -14 && c.maxZ() <= 14, s + " footprint too wide (seed " + seed + ")");
                assertTrue(c.height() <= 24, s + " too tall");
                assertTrue(c.count() < 4500, s + " too many blocks: " + c.count());
            }
        }
    }

    @Test
    void shapesAreRandomised() {
        for (Shape s : Shape.values()) {
            if (s.special()) continue;      // the beacon platform is fixed by design
            java.util.Set<Integer> sizes = new java.util.HashSet<Integer>();
            for (long seed = 0; seed < 30; seed++) { Canvas c = new Canvas(new Random(seed), 0); s.draw(c); sizes.add(c.count() * 31 + c.height()); }
            assertTrue(sizes.size() > 1, s + " should vary between spawns");
        }
    }

    /** Lava must stay where the shape put it: flood it the way the game does (3 blocks sideways, any distance down) and see if it reaches the ground. */
    @Test
    void lavaStaysContained() {
        for (Shape s : Shape.values()) {
            for (long seed = 0; seed < 80; seed++) {
                for (double decay : new double[]{0, 0.1, 0.35}) {
                    Canvas c = new Canvas(new Random(seed), decay);
                    s.draw(c);
                    if (!c.hasLiquid()) continue;
                    String leak = flood(c);
                    assertNull(leak, s + " seed " + seed + " decay " + decay + ": lava escapes at " + leak);
                }
            }
        }
    }

    private static String flood(Canvas c) {
        java.util.Set<String> seen = new java.util.HashSet<String>();
        java.util.ArrayDeque<int[]> q = new java.util.ArrayDeque<int[]>();
        for (java.util.Map.Entry<Long, Canvas.Slot> e : c.cells().entrySet()) {
            if (e.getValue() != Canvas.Slot.LAVA) continue;
            q.add(new int[]{Canvas.kx(e.getKey()), Canvas.ky(e.getKey()), Canvas.kz(e.getKey()), 3});
        }
        int[][] dirs = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
        while (!q.isEmpty()) {
            int[] p = q.poll();
            int x = p[0], y = p[1], z = p[2], sp = p[3];
            if (!seen.add(x + "," + y + "," + z + "," + sp)) continue;
            Canvas.Slot below = c.get(x, y - 1, z);
            if (y > 0 && below == Canvas.Slot.AIR) {          // y = 0 stands on the foundation / the ground
                if (y - 1 == 0) return x + "," + (y - 1) + "," + z + " (falls to the ground)";
                q.add(new int[]{x, y - 1, z, 3});
                continue;
            }
            if (below == Canvas.Slot.LAVA && sp < 3) continue;     // flowing lava that lands on a lava source just merges into it
            if (sp <= 0) continue;
            for (int[] d : dirs) {
                int nx = x + d[0], nz = z + d[1];
                Canvas.Slot t = c.get(nx, y, nz);
                if (t != Canvas.Slot.AIR) continue;
                if (nx < c.minX() || nx > c.maxX() || nz < c.minZ() || nz > c.maxZ()) return nx + "," + y + "," + nz + " (leaves the footprint)";
                q.add(new int[]{nx, y, nz, sp - 1});
            }
        }
        return null;
    }

    @Test
    void canvasKeysRoundTrip() {
        long k = Canvas.key(-7, 12, 5);
        assertEquals(-7, Canvas.kx(k));
        assertEquals(12, Canvas.ky(k));
        assertEquals(5, Canvas.kz(k));
    }
}
