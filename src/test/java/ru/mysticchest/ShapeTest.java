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
                assertTrue(c.minX() >= -10 && c.maxX() <= 10 && c.minZ() >= -10 && c.maxZ() <= 10, s + " footprint too wide (seed " + seed + ")");
                assertTrue(c.height() <= 22, s + " too tall");
                assertTrue(c.count() < 2200, s + " too many blocks: " + c.count());
            }
        }
    }

    @Test
    void shapesAreRandomised() {
        for (Shape s : Shape.values()) {
            java.util.Set<Integer> sizes = new java.util.HashSet<Integer>();
            for (long seed = 0; seed < 30; seed++) { Canvas c = new Canvas(new Random(seed), 0); s.draw(c); sizes.add(c.count() * 31 + c.height()); }
            assertTrue(sizes.size() > 1, s + " should vary between spawns");
        }
    }

    @Test
    void canvasKeysRoundTrip() {
        long k = Canvas.key(-7, 12, 5);
        assertEquals(-7, Canvas.kx(k));
        assertEquals(12, Canvas.ky(k));
        assertEquals(5, Canvas.kz(k));
    }
}
