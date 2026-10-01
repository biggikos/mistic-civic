package ru.mysticchest.structure;

import static ru.mysticchest.structure.Canvas.Slot.*;

/**
 * The artistic part: every shape draws itself on a Canvas and says where the chest goes. Sizes, heights,
 * counts and ornaments are rolled per structure, so no two look the same.
 */
public enum Shape {
    /** Stepped pyramid: random size, accent stripes, golden cap, light posts, sometimes a notch-entrance. */
    PYRAMID {
        public void draw(Canvas c) {
            int r = c.rnd(4, 7);
            int stripe = c.rnd(2, 3);
            boolean notch = c.chance(0.5);
            for (int k = 0; k <= r; k++) {
                int half = r - k;
                for (int x = -half; x <= half; x++) {
                    for (int z = -half; z <= half; z++) {
                        boolean edge = Math.abs(x) == half || Math.abs(z) == half;
                        Canvas.Slot s = edge && k % stripe == 1 ? ACCENT : BASE;
                        if (edge && k > 0 && k < r) c.worn(x, k, z, s); else c.set(x, k, z, s);
                    }
                }
            }
            c.set(0, r, 0, TRIM);
            if (r >= 5) { c.set(1, r - 1, 0, TRIM); c.set(-1, r - 1, 0, TRIM); c.set(0, r - 1, 1, TRIM); c.set(0, r - 1, -1, TRIM); }
            if (notch) {                      // a little ramp of steps up the front
                for (int k = 0; k < r; k++) c.set(0, k, r - k + 1, ACCENT);
            }
            int post = c.rnd(2, 4);
            for (int sx = -1; sx <= 1; sx += 2) {
                for (int sz = -1; sz <= 1; sz += 2) {
                    int x = sx * (r + 1), z = sz * (r + 1);
                    c.fill(x, 0, z, x, post - 1, z, TRIM);
                    c.set(x, post, z, LIGHT);
                }
            }
            c.chest(0, r + 1, 0);
        }
    },
    /** Open temple: platform, 4-6 pillars, cross-beam roof, an altar with the chest under a lamp. */
    TEMPLE {
        public void draw(Canvas c) {
            int w = c.rnd(5, 7), h = c.rnd(4, 6);
            c.fill(-w, 0, -w, w, 0, w, BASE);
            for (int t = -w; t <= w; t++) { c.set(t, 0, -w, ACCENT); c.set(t, 0, w, ACCENT); c.set(-w, 0, t, ACCENT); c.set(w, 0, t, ACCENT); }
            int in = w - 2;
            c.fill(-in, 1, -in, in, 1, in, ACCENT);
            c.fill(-in + 1, 1, -in + 1, in - 1, 1, in - 1, BASE);
            int pc = in - 1;
            for (int sx = -1; sx <= 1; sx += 2) {
                for (int sz = -1; sz <= 1; sz += 2) {
                    int x = sx * pc, z = sz * pc;
                    c.fill(x, 2, z, x, 1 + h, z, BASE);
                    c.set(x, 2 + h, z, TRIM);
                    c.set(x, 3 + h, z, LIGHT);
                }
            }
            if (c.chance(0.6)) { for (int sz = -1; sz <= 1; sz += 2) { c.fill(0, 2, sz * pc, 0, 1 + h, sz * pc, BASE); c.set(0, 2 + h, sz * pc, TRIM); } }
            for (int t = -pc + 1; t <= pc - 1; t++) { c.worn(t, 2 + h, -pc, BASE); c.worn(t, 2 + h, pc, BASE); c.worn(-pc, 2 + h, t, BASE); c.worn(pc, 2 + h, t, BASE); }
            for (int t = -pc + 1; t <= pc - 1; t++) { c.worn(t, 2 + h, 0, ACCENT); c.worn(0, 2 + h, t, ACCENT); }
            c.set(0, 2 + h, 0, TRIM);
            c.set(0, 1 + h, 0, LIGHT);
            c.set(0, 2, 0, TRIM);
            c.chest(0, 3, 0);
        }
    },
    /** Tall tapering obelisk with bands, a glowing tip; the chest on a pedestal in front. */
    OBELISK {
        public void draw(Canvas c) {
            int h = c.rnd(9, 14);
            c.fill(-2, 0, -2, 2, 1, 2, BASE);
            for (int sx = -1; sx <= 1; sx += 2) for (int sz = -1; sz <= 1; sz += 2) c.set(sx * 2, 2, sz * 2, LIGHT);
            int mid = c.rnd(4, 6);
            c.fill(-1, 2, -1, 1, mid, 1, BASE);
            for (int y = 3; y <= mid; y += 2) for (int x = -1; x <= 1; x++) for (int z = -1; z <= 1; z++) c.set(x, y, z, ACCENT);
            c.fill(0, mid + 1, 0, 0, h - 2, 0, BASE);
            for (int y = mid + 2; y < h - 2; y += c.rnd(2, 3)) c.set(0, y, 0, ACCENT);
            c.set(0, h - 1, 0, TRIM);
            c.set(0, h, 0, LIGHT);
            c.set(0, 0, 3, TRIM);
            c.set(-1, 0, 3, ACCENT);
            c.set(1, 0, 3, ACCENT);
            c.chest(0, 1, 3);
        }
    },
    /** A ring of standing stones (some broken), an inlaid disc, the chest on an altar. */
    HENGE {
        public void draw(Canvas c) {
            int n = c.rnd(8, 14), rad = c.rnd(5, 7);
            for (int i = 0; i < n; i++) {
                double ang = i * 2 * Math.PI / n + c.rnd.nextDouble() * 0.2;
                int x = (int) Math.round(Math.cos(ang) * rad), z = (int) Math.round(Math.sin(ang) * rad);
                int h = c.rnd(3, 6);
                if (c.decay > 0 && c.rnd.nextDouble() < c.decay * 1.5) h = c.rnd(1, 2);
                c.fill(x, 0, z, x, h - 1, z, BASE);
                c.set(x, h, z, i % 3 == 0 ? LIGHT : ACCENT);
            }
            int disc = c.rnd(3, 4);
            for (int x = -disc; x <= disc; x++) for (int z = -disc; z <= disc; z++) {
                double d = Math.sqrt(x * x + z * z);
                if (d <= disc + 0.2) c.set(x, 0, z, d <= 1.6 ? TRIM : (d <= 2.4 ? ACCENT : BASE));
            }
            c.set(0, 1, 0, TRIM);
            c.chest(0, 2, 0);
        }
    },
    /** A gate arch behind a stepped dais: the chest in front, a glowing keystone above. */
    GATE {
        public void draw(Canvas c) {
            int w = c.rnd(3, 4), h = c.rnd(6, 8);
            c.fill(-2, 0, -2, 2, 0, 2, BASE);
            c.fill(-1, 1, -1, 1, 1, 1, ACCENT);
            for (int sx = -1; sx <= 1; sx += 2) {
                int x = sx * w;
                c.fill(x, 0, -3, x, h - 1, -3, BASE);
                c.set(x, h, -3, ACCENT);
                c.set(x, h + 1, -3, LIGHT);
                c.set(sx * (w - 1), h - 1, -3, ACCENT);
            }
            for (int x = -(w - 1); x <= w - 1; x++) c.set(x, h, -3, x == 0 ? TRIM : BASE);
            c.set(0, h + 1, -3, LIGHT);
            for (int x = -1; x <= 1; x++) c.worn(x, h - 1, -3, ACCENT);
            c.set(-2, 0, -3, ACCENT);
            c.set(2, 0, -3, ACCENT);
            c.chest(0, 2, 0);
        }
    },
    /** Round tower with battlements, window slits, a spiral of steps up the outside and the chest on top. */
    TOWER {
        public void draw(Canvas c) {
            int r = c.rnd(3, 4), h = c.rnd(10, 14);
            for (int y = 0; y < h; y++) {
                for (int x = -r; x <= r; x++) for (int z = -r; z <= r; z++) {
                    double d = Math.sqrt(x * x + z * z);
                    if (d > r + 0.4 || d < r - 0.9) continue;       // a hollow wall
                    Canvas.Slot s = y % 4 == 3 ? ACCENT : BASE;
                    boolean slit = y % 4 == 1 && (x == 0 || z == 0) && d > r - 0.5 && c.chance(0.7);
                    if (!slit) c.worn(x, y, z, s);
                }
            }
            for (int x = -r; x <= r; x++) for (int z = -r; z <= r; z++) {
                double d = Math.sqrt(x * x + z * z);
                if (d <= r + 0.4) c.set(x, h, z, d > r - 0.9 ? TRIM : BASE);
                if (d > r - 0.5 && d <= r + 0.4 && (x + z) % 2 == 0) c.set(x, h + 1, z, BASE);   // battlements
            }
            for (int t = 0; t < 4; t++) { int a = t * 90; c.set((int) Math.round(Math.cos(Math.toRadians(a)) * r), h + 2, (int) Math.round(Math.sin(Math.toRadians(a)) * r), LIGHT); }
            // spiral of single steps around the outside, one block higher every step
            int steps = h;
            for (int i = 0; i < steps; i++) {
                double ang = i * 0.55;
                int x = (int) Math.round(Math.cos(ang) * (r + 1)), z = (int) Math.round(Math.sin(ang) * (r + 1));
                c.set(x, i, z, ACCENT);
                if (i > 0) c.set(x, i - 1, z, ACCENT);
            }
            c.set(0, h + 1, 0, TRIM);
            c.chest(0, h + 2, 0);
        }
    },
    /** An arena: a ring wall with four gates, columns at the gates, an inlaid floor, the chest on a central dais. */
    COLOSSEUM {
        public void draw(Canvas c) {
            int r = c.rnd(7, 8), h = c.rnd(3, 4);
            for (int x = -r; x <= r; x++) for (int z = -r; z <= r; z++) {
                double d = Math.sqrt(x * x + z * z);
                if (d > r + 0.5) continue;
                boolean gate = (Math.abs(x) <= 1 || Math.abs(z) <= 1) && d > r - 1.5;
                if (d >= r - 0.6) {
                    if (gate) { c.set(x, 0, z, ACCENT); continue; }
                    for (int y = 0; y < h; y++) c.worn(x, y, z, y == h - 1 ? ACCENT : BASE);
                    if ((x + z) % 2 == 0) c.worn(x, h, z, BASE);
                } else {
                    boolean ring = Math.abs(d - (r - 3)) < 0.6 || Math.abs(d - 2) < 0.6;
                    c.set(x, 0, z, ring ? ACCENT : BASE);
                }
            }
            for (int sx = -1; sx <= 1; sx += 2) {
                c.fill(sx * 2, 0, r, sx * 2, h + 1, r, BASE); c.set(sx * 2, h + 2, r, LIGHT);
                c.fill(sx * 2, 0, -r, sx * 2, h + 1, -r, BASE); c.set(sx * 2, h + 2, -r, LIGHT);
                c.fill(r, 0, sx * 2, r, h + 1, sx * 2, BASE); c.set(r, h + 2, sx * 2, LIGHT);
                c.fill(-r, 0, sx * 2, -r, h + 1, sx * 2, BASE); c.set(-r, h + 2, sx * 2, LIGHT);
            }
            c.fill(-1, 1, -1, 1, 1, 1, ACCENT);
            c.set(0, 1, 0, TRIM);
            c.chest(0, 2, 0);
        }
    },
    /** A cluster of glowing crystal spikes of different heights around a small pedestal. */
    CRYSTALS {
        public void draw(Canvas c) {
            int n = c.rnd(7, 12), rad = c.rnd(3, 5);
            for (int x = -rad; x <= rad; x++) for (int z = -rad; z <= rad; z++) {
                if (Math.sqrt(x * x + z * z) <= rad + 0.3 && c.chance(0.85)) c.set(x, 0, z, BASE);
            }
            for (int i = 0; i < n; i++) {
                double ang = c.rnd.nextDouble() * Math.PI * 2, d = 2.2 + c.rnd.nextDouble() * (rad - 1.2);
                int x = (int) Math.round(Math.cos(ang) * d), z = (int) Math.round(Math.sin(ang) * d);
                int h = c.rnd(3, 9);
                for (int y = 1; y <= h; y++) {
                    Canvas.Slot s = y == h ? LIGHT : (y > h / 2 ? ACCENT : TRIM);
                    c.set(x, y, z, s);
                    if (y < h / 2 && c.chance(0.5)) c.set(x + (c.chance(0.5) ? 1 : -1), y, z, TRIM);
                }
                c.set(x, 0, z, TRIM);
            }
            c.fill(-1, 0, -1, 1, 0, 1, ACCENT);
            c.set(0, 1, 0, TRIM);
            c.chest(0, 2, 0);
        }
    },
    /** A flat rune circle: concentric rings, eight spokes, small lit pillars and a raised centre. */
    RUNES {
        public void draw(Canvas c) {
            int r = c.rnd(5, 7);
            for (int x = -r; x <= r; x++) for (int z = -r; z <= r; z++) {
                double d = Math.sqrt(x * x + z * z);
                if (d > r + 0.3) continue;
                double ang = Math.atan2(z, x);
                boolean spoke = Math.abs(Math.sin(ang * 4)) < 0.22;
                boolean ring = Math.abs(d - r) < 0.7 || Math.abs(d - (r - 2.5)) < 0.5;
                c.set(x, 0, z, ring || (spoke && d > 1.5) ? ACCENT : BASE);
            }
            int pills = c.rnd(4, 8);
            for (int i = 0; i < pills; i++) {
                double ang = i * 2 * Math.PI / pills;
                int x = (int) Math.round(Math.cos(ang) * (r - 1)), z = (int) Math.round(Math.sin(ang) * (r - 1));
                int h = c.rnd(2, 4);
                c.fill(x, 1, z, x, h, z, TRIM);
                c.set(x, h + 1, z, LIGHT);
            }
            c.fill(-1, 1, -1, 1, 1, 1, TRIM);
            c.set(0, 2, 0, ACCENT);
            c.chest(0, 3, 0);
        }
    };

    public abstract void draw(Canvas c);

    public static Shape parse(String s) {
        if (s == null) return null;
        try { return valueOf(s.trim().toUpperCase().replace('-', '_')); } catch (IllegalArgumentException e) { return null; }
    }
}
