package ru.mysticchest.structure;

import static ru.mysticchest.structure.Canvas.Slot.*;

/** The artistic part: each shape draws itself on a Canvas and says where the chest goes. */
public enum Shape {
    /** Stepped pyramid with a golden cap, light posts in the corners, the chest on the very top. */
    PYRAMID {
        public void draw(Canvas c) {
            int r = 6;
            for (int k = 0; k <= r; k++) {
                int half = r - k;
                for (int x = -half; x <= half; x++) {
                    for (int z = -half; z <= half; z++) {
                        boolean edge = Math.abs(x) == half || Math.abs(z) == half;
                        Canvas.Slot s = edge && k % 2 == 1 ? ACCENT : BASE;
                        if (edge && k > 0 && k < r) c.worn(x, k, z, s); else c.set(x, k, z, s);
                    }
                }
            }
            c.set(0, r, 0, TRIM);
            for (int sx = -1; sx <= 1; sx += 2) {
                for (int sz = -1; sz <= 1; sz += 2) {
                    int x = sx * (r + 1), z = sz * (r + 1);
                    c.fill(x, 0, z, x, 2, z, TRIM);
                    c.set(x, 3, z, LIGHT);
                }
            }
            c.chest(0, r + 1, 0);
        }
    },
    /** Open temple: raised platform, four pillars, a cross-beam roof, an altar with the chest under a lamp. */
    TEMPLE {
        public void draw(Canvas c) {
            c.fill(-6, 0, -6, 6, 0, 6, BASE);
            for (int t = -6; t <= 6; t++) { c.set(t, 0, -6, ACCENT); c.set(t, 0, 6, ACCENT); c.set(-6, 0, t, ACCENT); c.set(6, 0, t, ACCENT); }
            c.fill(-4, 1, -4, 4, 1, 4, ACCENT);
            c.fill(-3, 1, -3, 3, 1, 3, BASE);
            for (int sx = -1; sx <= 1; sx += 2) {
                for (int sz = -1; sz <= 1; sz += 2) {
                    int x = sx * 3, z = sz * 3;
                    c.fill(x, 2, z, x, 6, z, BASE);
                    c.set(x, 7, z, TRIM);
                    c.set(x, 8, z, LIGHT);
                }
            }
            for (int t = -2; t <= 2; t++) {
                c.worn(t, 7, -3, BASE); c.worn(t, 7, 3, BASE); c.worn(-3, 7, t, BASE); c.worn(3, 7, t, BASE);
            }
            for (int t = -2; t <= 2; t++) { c.worn(t, 7, 0, ACCENT); c.worn(0, 7, t, ACCENT); }
            c.set(0, 7, 0, TRIM);
            c.set(0, 6, 0, LIGHT);
            c.set(0, 2, 0, TRIM);
            c.chest(0, 3, 0);
        }
    },
    /** Tall tapering obelisk with a glowing tip; the chest sits on a pedestal in front of it. */
    OBELISK {
        public void draw(Canvas c) {
            c.fill(-2, 0, -2, 2, 1, 2, BASE);
            for (int sx = -1; sx <= 1; sx += 2) for (int sz = -1; sz <= 1; sz += 2) c.set(sx * 2, 2, sz * 2, LIGHT);
            c.fill(-1, 2, -1, 1, 6, 1, BASE);
            for (int x = -1; x <= 1; x++) for (int z = -1; z <= 1; z++) c.set(x, 4, z, ACCENT);
            c.fill(0, 7, 0, 0, 12, 0, BASE);
            c.set(0, 9, 0, ACCENT);
            c.set(0, 13, 0, TRIM);
            c.set(0, 14, 0, LIGHT);
            c.set(0, 0, 3, TRIM);
            c.set(-1, 0, 3, ACCENT);
            c.set(1, 0, 3, ACCENT);
            c.chest(0, 1, 3);
        }
    },
    /** A ring of standing stones (some broken), a stone disc in the middle, the chest on an altar. */
    HENGE {
        public void draw(Canvas c) {
            int n = 12;
            for (int i = 0; i < n; i++) {
                double ang = i * 2 * Math.PI / n;
                int x = (int) Math.round(Math.cos(ang) * 6), z = (int) Math.round(Math.sin(ang) * 6);
                int h = 3 + c.rnd.nextInt(4);
                if (c.decay > 0 && c.rnd.nextDouble() < c.decay * 1.5) h = 1 + c.rnd.nextInt(2);
                c.fill(x, 0, z, x, h - 1, z, BASE);
                c.set(x, h, z, i % 3 == 0 ? LIGHT : ACCENT);
            }
            for (int x = -3; x <= 3; x++) for (int z = -3; z <= 3; z++) {
                double d = Math.sqrt(x * x + z * z);
                if (d <= 3.2) c.set(x, 0, z, d <= 1.6 ? TRIM : (d <= 2.4 ? ACCENT : BASE));
            }
            c.set(0, 1, 0, TRIM);
            c.chest(0, 2, 0);
        }
    },
    /** A gate arch behind a stepped dais: the chest in front, a glowing keystone above. */
    GATE {
        public void draw(Canvas c) {
            c.fill(-2, 0, -2, 2, 0, 2, BASE);
            c.fill(-1, 1, -1, 1, 1, 1, ACCENT);
            for (int sx = -1; sx <= 1; sx += 2) {
                int x = sx * 3;
                c.fill(x, 0, -3, x, 6, -3, BASE);
                c.set(x, 7, -3, ACCENT);
                c.set(x, 8, -3, LIGHT);
                c.set(sx * 2, 6, -3, ACCENT);
            }
            for (int x = -2; x <= 2; x++) c.set(x, 7, -3, x == 0 ? TRIM : BASE);
            c.set(0, 8, -3, LIGHT);
            for (int x = -1; x <= 1; x++) c.worn(x, 6, -3, ACCENT);
            c.set(-2, 0, -3, ACCENT);
            c.set(2, 0, -3, ACCENT);
            c.chest(0, 2, 0);
        }
    };

    public abstract void draw(Canvas c);

    public static Shape parse(String s) {
        if (s == null) return null;
        try { return valueOf(s.trim().toUpperCase().replace('-', '_')); } catch (IllegalArgumentException e) { return null; }
    }
}
