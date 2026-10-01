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
            for (double decay : new double[]{0, 0.1, 0.35}) {
                Canvas c = new Canvas(new Random(7), decay);
                s.draw(c);
                assertEquals(Canvas.Slot.AIR, c.get(c.chestX, c.chestY, c.chestZ), s + " chest cell must be air");
                assertNotEquals(Canvas.Slot.AIR, c.get(c.chestX, c.chestY - 1, c.chestZ), s + " chest needs a block under it (decay " + decay + ")");
                assertTrue(c.count() > 20, s + " should have blocks");
            }
        }
    }

    @Test
    void shapesStayInsideTheFootprint() {
        for (Shape s : Shape.values()) {
            Canvas c = new Canvas(new Random(1), 0);
            s.draw(c);
            assertTrue(c.minX() >= -8 && c.maxX() <= 8 && c.minZ() >= -8 && c.maxZ() <= 8, s + " footprint too wide");
            assertTrue(c.height() <= 18, s + " too tall");
            assertTrue(c.count() < 1500, s + " too many blocks: " + c.count());
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
