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
    },
    /**
     * A broken nether portal: obsidian frame with missing pieces and crying obsidian, a netherrack and magma patch
     * with fires and gold, two lava pools in obsidian rims, crumbled blackstone walls. The chest sits in the frame.
     */
    RUINED_PORTAL {
        public void draw(Canvas c) {
            c.debris = new Canvas.Slot[]{ROCK, NETHERRACK, CRYING, MAGMA};
            c.foundation = NETHERRACK;
            boolean sw = c.chance(0.5);                       // frame along x or along z
            int r = c.rnd(5, 6);
            for (int x = -r - 1; x <= r + 1; x++) for (int z = -r - 1; z <= r + 1; z++) {
                double d = Math.sqrt(x * x + z * z), lim = r + (c.rnd.nextDouble() - 0.5) * 1.6;
                if (d > lim) continue;
                double p = c.rnd.nextDouble();
                c.set(x, 0, z, p < 0.66 ? NETHERRACK : (p < 0.80 ? MAGMA : (p < 0.92 ? ROCK : (p < 0.97 ? LIGHT : SOUL))));
            }
            for (int y = 1; y <= 5; y++) for (int x = -2; x <= 1; x++) {
                if (!(y == 1 || y == 5 || x == -2 || x == 1)) continue;
                boolean keep = y == 1 && (x == 0 || x == -1);                 // the chest stands on these
                double miss = y == 5 ? 0.5 : (x == 1 ? 0.35 : 0.15);
                if (!keep && c.chance(miss)) continue;
                put(c, sw, x, y, 0, c.chance(0.28) ? CRYING : OBSIDIAN);
            }
            // lava pools in obsidian rims, left and right of the frame
            for (int side = -1; side <= 1; side += 2) {
                if (side == 1 && c.chance(0.4)) continue;
                int px = c.rnd(-2, 0), pz = side * c.rnd(3, 4);
                for (int x = px - 1; x <= px + 3; x++) for (int z = pz - 2; z <= pz + 2; z++) {
                    boolean inside = x >= px && x <= px + 2 && z >= pz - 1 && z <= pz + 1;
                    put(c, sw, x, 0, z, inside ? LAVA : OBSIDIAN);
                }
            }
            int stubs = c.rnd(5, 8);
            for (int i = 0; i < stubs; i++) {
                double ang = c.rnd.nextDouble() * Math.PI * 2, d = 4 + c.rnd.nextDouble() * 2;
                int x = (int) Math.round(Math.cos(ang) * d), z = (int) Math.round(Math.sin(ang) * d);
                if (c.get(x, 0, z) == Canvas.Slot.AIR || c.get(x, 0, z) == LAVA || c.get(x, 0, z) == OBSIDIAN) continue;
                int h = c.rnd(1, 3);
                c.fill(x, 1, z, x, h, z, ROCK);
                if (c.chance(0.25)) c.set(x, h + 1, z, c.chance(0.5) ? GOLD : CRYING);
            }
            for (int x = -r; x <= r; x++) for (int z = -r; z <= r; z++) {
                if (c.get(x, 0, z) == NETHERRACK && c.get(x, 1, z) == Canvas.Slot.AIR && c.chance(0.07) && Math.hypot(x, z) > 2.5) c.set(x, 1, z, FIRE);
            }
            c.chest(0, 2, 0);
        }
    },
    /**
     * A real volcano: a hollow cone of blackstone, ash, magma and obsidian streaks, a crater with a lava pool,
     * a lava river running down one flank between rock lips, and a stepped trail up the other side to a ledge with the chest.
     */
    VOLCANO {
        public void draw(Canvas c) {
            c.debris = new Canvas.Slot[]{ROCK, ASH, OBSIDIAN, MAGMA};
            c.foundation = ROCK;
            int R = c.rnd(11, 13), H = c.rnd(10, 13), cr = c.rnd(3, 4);
            c.crater = new int[]{0, H, 0};
            double rimD = cr + 1.2;
            int n = 2 * R + 3, o = R + 1;
            int[][] top = new int[n][n];
            for (int x = -R; x <= R; x++) for (int z = -R; z <= R; z++) {
                double d = Math.sqrt(x * x + z * z);
                int t = -1;
                if (d <= R) {
                    if (d <= rimD) t = H;
                    else {
                        t = (int) Math.round(H * (R - d) / (R - rimD));
                        if (c.chance(0.2)) t--;
                        t = Math.max(0, Math.min(H - 1, t));
                    }
                }
                top[x + o][z + o] = t;
            }
            for (int x = -R; x <= R; x++) for (int z = -R; z <= R; z++) {
                int t = top[x + o][z + o];
                if (t < 0) continue;
                double d = Math.sqrt(x * x + z * z);
                if (d <= cr - 0.6) {                                    // the lava pool
                    c.fill(x, 0, z, x, H - 4, z, ROCK);
                    c.set(x, H - 3, z, MAGMA);
                    c.set(x, H - 2, z, LAVA);
                    c.set(x, H - 1, z, LAVA);
                    continue;
                }
                int from = d <= rimD ? 0 : Math.max(0, t - 3);
                if (from > 0) c.fill(x, 0, z, x, 1, z, ROCK);
                for (int y = from; y <= t; y++) c.set(x, y, z, rock(c, d, R, y == t));
            }
            // lava river down one flank
            double a0 = c.rnd.nextDouble() * Math.PI * 2;
            java.util.List<int[]> path = line(a0, cr, R - 1);
            int prevTop = H;
            for (int i = 0; i < path.size(); i++) {
                int x = path.get(i)[0], z = path.get(i)[1];
                int t = top[x + o][z + o];
                boolean notch = Math.sqrt(x * x + z * z) <= rimD;
                int ly = notch ? H - 1 : t;
                int lipTop = Math.max(prevTop, ly);
                if (notch) c.set(x, H, z, Canvas.Slot.AIR);
                c.set(x, ly, z, LAVA);
                for (int[] dd : new int[][]{{1, 0}, {-1, 0}, {0, 1}, {0, -1}}) {
                    int nx = x + dd[0], nz = z + dd[1];
                    if (onPath(path, nx, nz)) continue;
                    int nt = Math.abs(nx) > R || Math.abs(nz) > R ? -1 : top[nx + o][nz + o];
                    c.fill(nx, Math.max(0, nt + 1), nz, nx, lipTop, nz, ROCK);
                }
                prevTop = Math.min(prevTop, ly);
                if (!notch) prevTop = t;
            }
            // trail and ledge on the opposite side
            double a1 = a0 + Math.PI + (c.rnd.nextDouble() - 0.5) * 0.7;
            double dl = R * 0.5;
            int lx = (int) Math.round(Math.cos(a1) * dl), lz = (int) Math.round(Math.sin(a1) * dl);
            int tl = top[lx + o][lz + o];
            for (int[] p : line(a1, dl, R)) {
                double dd = Math.sqrt(p[0] * p[0] + p[1] * p[1]);
                int y = Math.max(0, Math.min(tl, (int) Math.round(tl * (R - dd) / (R - dl))));   // one block up per step
                int t = top[p[0] + o][p[1] + o];
                for (int yy = y + 1; yy <= t; yy++) c.set(p[0], yy, p[1], Canvas.Slot.AIR);        // a trench where the flank is steeper
                c.fill(p[0], Math.max(0, y - 3), p[1], p[0], y, p[1], ROCK);
                c.set(p[0], y, p[1], ASH);
            }
            for (int x = lx - 1; x <= lx + 1; x++) for (int z = lz - 1; z <= lz + 1; z++) {
                int t = top[x + o][z + o];
                for (int y = tl + 1; y <= Math.max(t, tl + 1); y++) c.set(x, y, z, Canvas.Slot.AIR);
                c.fill(x, Math.max(0, tl - 3), z, x, tl, z, ROCK);
                c.set(x, tl, z, (x + z) % 2 == 0 ? OBSIDIAN : ROCK);
            }
            int ux = (int) Math.round(Math.cos(a1) * (dl - 2)), uz = (int) Math.round(Math.sin(a1) * (dl - 2));
            c.set(ux, tl + 1, uz, LIGHT);
            c.set(lx + (ux == lx ? 1 : 0), tl + 1, lz + (uz == lz ? 1 : 0), LIGHT);
            c.chest(lx, tl + 1, lz);
        }
    },
    /** A stranded ship: dark-oak hull with ribs, a broken mast with tattered cobweb sails, a stern cabin with the chest. */
    SHIPWRECK {
        public void draw(Canvas c) {
            c.debris = new Canvas.Slot[]{DARKWOOD, LOG, WOOD, DARKWOOD};
            c.foundation = DIRT;
            int half = c.rnd(7, 8);                                   // the hull runs from x = -half to +half
            boolean broken = c.chance(0.6);
            for (int x = -half; x <= half; x++) {
                int w = x > half - 4 ? Math.max(1, 3 - (x - (half - 4))) : (x < -half + 1 ? 2 : 3);   // pointed bow, square stern
                for (int z = -w; z <= w; z++) {
                    c.set(x, 0, z, WOOD);                              // the deck / floor
                    boolean wall = Math.abs(z) == w;
                    if (!wall) continue;
                    int h = 3 - (x > half - 4 ? 0 : 0);
                    for (int y = 1; y <= h; y++) {
                        Canvas.Slot s = x % 3 == 0 ? LOG : DARKWOOD;
                        if (y == h && c.chance(0.3)) continue;           // a gap in the gunwale
                        if (broken && x > 1 && x < 5 && y > 1 && c.chance(0.7)) continue;   // a hole where it hit the rocks
                        if (c.chance(c.decay * 1.5)) continue;
                        c.set(x, y, z, s);
                    }
                }
                if (x > half - 4) { int w2 = Math.max(1, 3 - (x - (half - 4))); c.set(x, 1, w2 == 1 ? 0 : 0, DARKWOOD); }
            }
            for (int z = -2; z <= 2; z++) c.set(half, 0, z, Canvas.Slot.AIR);
            for (int z = -1; z <= 1; z++) if (c.chance(0.8)) c.set(half, 1, z, DARKWOOD);
            for (int i = 1; i <= 3; i++) c.set(half + i, 1 + i / 2, 0, LOG);          // the bowsprit
            // mast with a yard and tattered sails
            int mx = c.rnd(0, 2), mh = c.rnd(8, 10);
            if (c.chance(0.6)) mh = c.rnd(5, 7);
            c.fill(mx, 1, 0, mx, mh, 0, LOG);
            if (mh >= 7) {
                for (int z = -3; z <= 3; z++) if (c.chance(0.85)) c.set(mx, mh - 1, z, LOG);
                for (int z = -2; z <= 2; z++) for (int y = mh - 5; y <= mh - 2; y++) if (c.chance(0.55)) c.set(mx + 1, y, z, WEB);
            }
            // stern cabin
            int sx = -half + 1;
            for (int x = sx; x <= sx + 3; x++) for (int z = -2; z <= 2; z++) {
                boolean edge = x == sx || x == sx + 3 || Math.abs(z) == 2;
                if (edge) for (int y = 1; y <= 3; y++) {
                    boolean door = x == sx + 3 && z == 0 && y <= 2, window = y == 2 && (z == 0 && x == sx || (x == sx + 1 || x == sx + 2) && Math.abs(z) == 2 && x == sx + 1);
                    if (door || window) continue;
                    if (c.chance(c.decay * 2)) continue;
                    c.set(x, y, z, y == 3 ? LOG : DARKWOOD);
                }
                if (c.chance(0.85)) c.set(x, 4, z, WOOD);
            }
            c.set(sx + 1, 3, 0, Canvas.Slot.AIR);
            c.set(sx + 1, 1, -1, LIGHT);
            c.chest(sx + 2, 1, 0);
            // dunes piled against the hull
            for (int x = -half - 1; x <= half; x++) for (int z = -5; z <= 5; z++) {
                if (Math.abs(z) < 4 || c.get(x, 0, z) != Canvas.Slot.AIR || !c.chance(0.45)) continue;
                c.set(x, 0, z, DIRT);
                if (c.chance(0.25) && Math.abs(z) == 4) c.set(x, 1, z, DIRT);
            }
        }
    },
    /** The skeleton of a dragon: arched ribs along a spine, a skull with glowing eyes, a tail, the chest inside the ribcage. */
    DRAGON_BONES {
        public void draw(Canvas c) {
            c.debris = new Canvas.Slot[]{BONE, ASH, BONE, BONE};
            c.foundation = ASH;
            int ribs = c.rnd(6, 8);
            int len = ribs * 2;                                         // ribs at every second x
            int start = -len / 2;
            for (int x = start - 4; x <= start + len + 6; x++) for (int z = -5; z <= 5; z++) {
                double d = Math.sqrt((x - (start + len / 2.0)) * (x - (start + len / 2.0)) / 4.0 + z * z);
                if (d <= 5.2 && c.chance(0.8)) c.set(x, 0, z, c.chance(0.15) ? SOUL : ASH);
            }
            int prevTop = 3;
            for (int i = 0; i <= len; i++) {
                int x = start + i;
                double f = Math.sin(Math.PI * (i + 1) / (len + 2));
                int ry = 3 + (int) Math.round(f * 3), rz = 2 + (int) Math.round(f * 2);
                int topY = ry + 1;
                c.set(x, topY, 0, BONE);                                // the spine
                if (c.chance(0.15)) c.set(x, topY + 1, 0, BONE);
                if (i % 2 == 0) {
                    for (int a = 0; a <= 12; a++) {                     // one rib on each side
                        double th = a * Math.PI / 12;
                        int zz = (int) Math.round(Math.cos(th) * rz), yy = 1 + (int) Math.round(Math.sin(th) * (ry - 1));
                        if (zz == 0) continue;
                        if (a > 8 && c.chance(0.4)) continue;           // the rib tips are broken off
                        c.set(x, yy, zz, BONE);
                        c.set(x, yy, -zz, BONE);
                    }
                    if (c.chance(0.4)) c.set(x, topY + 1, 0, LIGHT);
                }
                prevTop = topY;
            }
            // tail
            int tx = start - 1, ty = 4;
            for (int i = 0; i < 6; i++) { c.set(tx - i, Math.max(1, ty - i / 2), 0, BONE); if (i < 3) c.set(tx - i, Math.max(1, ty - i / 2) - 1, 0, BONE); }
            // skull
            int hx = start + len + 2;
            c.fill(hx, 2, -2, hx + 3, 4, 2, BONE);
            c.fill(hx + 4, 2, -1, hx + 5, 3, 1, BONE);                  // snout
            c.fill(hx + 1, 1, -1, hx + 4, 1, 1, BONE);                  // lower jaw
            c.set(hx + 5, 1, -1, BONE); c.set(hx + 5, 1, 1, BONE);
            c.fill(hx + 3, 2, -1, hx + 4, 2, 1, Canvas.Slot.AIR);       // the open mouth
            c.set(hx + 3, 4, -2, LIGHT); c.set(hx + 3, 4, 2, LIGHT);    // eyes
            c.set(hx, 5, -2, BONE); c.set(hx, 6, -2, BONE); c.set(hx, 5, 2, BONE); c.set(hx, 6, 2, BONE);   // horns
            c.set(hx + 1, 7, -2, BONE); c.set(hx + 1, 7, 2, BONE);
            // cobwebs between the ribs
            for (int i = 0; i < 6; i++) c.set(start + 2 * c.rnd(1, ribs - 1) + 1, c.rnd(2, 4), c.rnd(-2, 2), WEB);
            int cx = start + len / 2 + (len / 2 % 2 == 0 ? 1 : 0);
            c.set(cx, 1, 0, Canvas.Slot.AIR);
            c.set(cx, 0, 0, TRIM);
            c.chest(cx, 1, 0);
        }
    },
    /** A ruined castle: broken curtain wall with merlons, four corner towers, a gate arch, an inlaid courtyard and a throne dais with the chest. */
    CASTLE_RUIN {
        public void draw(Canvas c) {
            int w = c.rnd(6, 7);
            double ruin = Math.max(0.25, c.decay * 2);
            c.fill(-w, 0, -w, w, 0, w, BASE);
            for (int x = -w + 1; x <= w - 1; x++) for (int z = -w + 1; z <= w - 1; z++) if ((x + z) % 2 == 0 && Math.abs(x) + Math.abs(z) > 5) c.set(x, 0, z, ACCENT);
            for (int t = -w; t <= w; t++) {
                for (int side = 0; side < 4; side++) {
                    int x = side < 2 ? t : (side == 2 ? -w : w), z = side < 2 ? (side == 0 ? -w : w) : t;
                    boolean gate = side == 1 && Math.abs(t) <= 1;
                    if (gate) { c.set(x, 1, z, Canvas.Slot.AIR); continue; }
                    int h = Math.max(1, 5 - (int) Math.round(Math.abs(t) * 0.0) - (c.chance(ruin) ? c.rnd(1, 3) : 0));
                    for (int y = 1; y <= h; y++) c.set(x, y, z, y % 4 == 0 ? ACCENT : BASE);
                    if (h == 5 && (t + w) % 2 == 0) c.set(x, 6, z, BASE);        // merlons
                    if (h >= 3 && (t + w) % 4 == 2) c.set(x, 3, z, Canvas.Slot.AIR);   // arrow slit
                }
            }
            for (int sx = -1; sx <= 1; sx += 2) for (int sz = -1; sz <= 1; sz += 2) {
                int cx = sx * w, cz = sz * w, th = c.rnd(8, 10) - (c.chance(ruin) ? c.rnd(1, 4) : 0);
                for (int y = 1; y <= th; y++) for (int x = cx - 1; x <= cx + 1; x++) for (int z = cz - 1; z <= cz + 1; z++) {
                    boolean core = x == cx && z == cz && y < th - 1;
                    if (!core && !c.chance(c.decay * 1.5)) c.set(x, y, z, y % 4 == 0 ? TRIM : BASE);
                }
                c.set(cx, th + 1, cz, LIGHT);
            }
            c.fill(-1, 0, -w - 1, 1, 0, -w - 1, ACCENT);                // gate threshold (the gate is the +z wall)
            // throne dais against the back wall
            c.fill(-2, 1, -w + 1, 2, 1, -w + 3, ACCENT);
            c.fill(-1, 2, -w + 1, 1, 2, -w + 2, BASE);
            c.set(0, 3, -w + 1, TRIM);
            c.set(-2, 2, -w + 1, LIGHT); c.set(2, 2, -w + 1, LIGHT);
            for (int i = 0; i < 10; i++) {                                // fallen stones in the courtyard
                int x = c.rnd(-w + 2, w - 2), z = c.rnd(-w + 4, w - 2);
                if (c.get(x, 1, z) == Canvas.Slot.AIR) c.set(x, 1, z, c.chance(0.5) ? BASE : ACCENT);
            }
            c.chest(0, 3, -w + 2);
        }
    },
    /** A witch hut on stilts: log legs, a plank deck with steps, dark-oak walls with windows, a pitched roof, cauldron, cobwebs; the chest inside. */
    WITCH_HUT {
        public void draw(Canvas c) {
            c.debris = new Canvas.Slot[]{DARKWOOD, LOG, WOOD, DARKWOOD};
            c.foundation = DIRT;
            for (int x = -5; x <= 5; x++) for (int z = -5; z <= 5; z++) {
                double d = Math.sqrt(x * x + z * z);
                if (d <= 5.3 && c.chance(0.55)) c.set(x, 0, z, c.chance(0.3) ? SOUL : DIRT);
            }
            int deck = c.rnd(3, 4);
            for (int sx = -1; sx <= 1; sx += 2) for (int sz = -1; sz <= 1; sz += 2) c.fill(sx * 2, 1, sz * 2, sx * 2, deck, sz * 2, LOG);
            c.fill(-3, deck + 1, -3, 3, deck + 1, 3, WOOD);
            for (int x = -2; x <= 2; x++) for (int z = -2; z <= 2; z++) {
                boolean edge = Math.abs(x) == 2 || Math.abs(z) == 2;
                if (!edge) continue;
                for (int y = deck + 2; y <= deck + 4; y++) {
                    boolean door = z == 2 && x == 0 && y <= deck + 3, window = y == deck + 3 && (x == 0 && z == -2 || z == 0 && Math.abs(x) == 2);
                    if (door || window) continue;
                    if (c.chance(c.decay)) continue;
                    c.set(x, y, z, Math.abs(x) == 2 && Math.abs(z) == 2 ? LOG : DARKWOOD);
                }
            }
            for (int k = 0; k < 3; k++) {                                // pitched roof
                int half = 3 - k;
                for (int x = -half; x <= half; x++) for (int z = -3; z <= 3; z++) {
                    if (Math.abs(x) == half && !(c.chance(c.decay))) c.set(x, deck + 5 + k, z, k == 2 ? LOG : WOOD);
                    else if (k == 2) c.set(x, deck + 5 + k, z, LOG);
                }
            }
            c.fill(0, deck + 8, -3, 0, deck + 8, 3, Canvas.Slot.AIR);
            // steps up to the door
            for (int i = 0; i <= deck; i++) c.set(0, i + 1, 3 + deck - i + (deck > 3 ? 0 : 0), WOOD);
            c.set(-2, deck + 2, 1, CAULDRON);
            c.set(2, deck + 2, -1, WEB); c.set(-2, deck + 4, -2, WEB); c.set(2, deck + 4, 2, WEB);
            c.set(2, deck + 2, 1, LIGHT);
            c.set(-3, deck + 2, 3, FENCE); c.set(3, deck + 2, 3, FENCE);
            c.chest(0, deck + 2, -1);
        }
    },
    /** A graveyard: an iron-railed plot, rows of headstones and mounds, a dead tree, a gate with lamps and a crypt with the chest. */
    GRAVEYARD {
        public void draw(Canvas c) {
            c.debris = new Canvas.Slot[]{ROCK, ACCENT, ROCK, ROCK};
            c.foundation = DIRT;
            int w = 7;
            for (int x = -w; x <= w; x++) for (int z = -w; z <= w; z++) c.set(x, 0, z, c.chance(0.2) ? SOUL : (c.chance(0.5) ? DIRT : ASH));
            for (int t = -w; t <= w; t++) {                              // the fence, with a gate gap on the +z side
                for (int side = 0; side < 4; side++) {
                    int x = side < 2 ? t : (side == 2 ? -w : w), z = side < 2 ? (side == 0 ? -w : w) : t;
                    if (side == 1 && Math.abs(t) <= 1) continue;
                    if (t % 2 == 0 && !c.chance(c.decay * 2)) { c.set(x, 1, z, FENCE); c.set(x, 2, z, FENCE); }
                    else if (c.chance(0.5)) c.set(x, 1, z, FENCE);
                }
            }
            for (int sx = -1; sx <= 1; sx += 2) { c.fill(sx * 2, 1, w, sx * 2, 3, w, ROCK); c.set(sx * 2, 4, w, LIGHT); }
            for (int row = 0; row < 2; row++) for (int col = -2; col <= 2; col += 2) {
                int x = col * 2 + (c.chance(0.5) ? 1 : 0), z = 1 + row * 3 + c.rnd(0, 1);
                if (Math.abs(x) > w - 2) continue;
                int h = c.chance(0.3) ? 1 : 2;
                c.set(x, 1, z - 1, DIRT);                                // the mound
                c.set(x, 1, z - 2, DIRT);
                c.fill(x, 1, z, x, h, z, ROCK);
                if (h == 2) c.set(x, 2, z, c.chance(0.5) ? ACCENT : ROCK);
            }
            // dead tree
            int tx = c.chance(0.5) ? -5 : 5, tz = 3, th = c.rnd(5, 7);
            c.fill(tx, 1, tz, tx, th, tz, LOG);
            for (int i = 0; i < 6; i++) {
                int dx = c.rnd(-2, 2), dz = c.rnd(-2, 2), y = c.rnd(th - 3, th);
                if (dx == 0 && dz == 0) continue;
                c.set(tx + (dx > 0 ? 1 : (dx < 0 ? -1 : 0)), y, tz + (dz > 0 ? 1 : (dz < 0 ? -1 : 0)), LOG);
                if (Math.abs(dx) > 1 || Math.abs(dz) > 1) c.set(tx + dx, y + 1, tz + dz, LOG);
            }
            c.set(tx + 1, th, tz, WEB); c.set(tx, th - 1, tz - 1, WEB);
            // the crypt against the back wall
            for (int x = -2; x <= 2; x++) for (int z = -w + 1; z <= -w + 4; z++) {
                boolean edge = Math.abs(x) == 2 || z == -w + 1 || z == -w + 4;
                c.set(x, 1, z, edge ? ROCK : ACCENT);
                if (edge) for (int y = 2; y <= 3; y++) {
                    boolean door = z == -w + 4 && x == 0 && y <= 3;
                    if (!door && !c.chance(c.decay)) c.set(x, y, z, y == 3 ? ACCENT : ROCK);
                }
                c.set(x, 4, z, ROCK);
            }
            c.set(0, 4, -w + 4, TRIM); c.set(0, 5, -w + 2, LIGHT);
            c.set(-1, 2, -w + 2, WEB);
            c.set(0, 1, -w + 2, TRIM);
            c.chest(0, 2, -w + 2);
        }
    },
    /** A nether outpost: a nether-brick bridge on pillars across a contained lava stream, rails, a watch tower in the middle with the chest. */
    NETHER_OUTPOST {
        public void draw(Canvas c) {
            c.debris = new Canvas.Slot[]{NBRICK, NRED, NBRICK, NRED};
            c.foundation = NBRICK;
            int half = c.rnd(8, 9), deck = 4;
            for (int x = -half - 1; x <= half + 1; x++) for (int z = -4; z <= 4; z++) c.set(x, 0, z, Math.abs(z) == 4 || Math.abs(x) >= half ? NBRICK : (c.chance(0.15) ? MAGMA : NETHERRACK));
            for (int x = -half + 1; x <= half - 1; x++) for (int z = -2; z <= 2; z++) c.set(x, 0, z, Math.abs(z) == 2 ? NBRICK : LAVA);    // the lava stream, rimmed
            for (int x = -half + 1; x <= half - 1; x++) { c.set(x, 0, -3, NBRICK); c.set(x, 0, 3, NBRICK); }
            for (int x = -half - 1; x <= half + 1; x++) for (int z = -1; z <= 1; z++) {
                if (c.chance(c.decay * 3) && Math.abs(x) > 2) continue;
                c.set(x, deck, z, Math.abs(z) == 1 && x % 2 == 0 ? NRED : NBRICK);
            }
            for (int x = -half; x <= half; x += 4) for (int sz = -1; sz <= 1; sz += 2) {
                c.fill(x, 1, sz * 2, x, deck - 1, sz * 2, NBRICK);
                c.set(x, deck - 1, sz * 2, NRED);
            }
            for (int x = -half; x <= half; x++) for (int sz = -1; sz <= 1; sz += 2) if (Math.abs(x) > 3 && !c.chance(0.2)) c.set(x, deck + 1, sz, NFENCE);
            // the tower in the middle
            for (int x = -3; x <= 3; x++) for (int z = -3; z <= 3; z++) {
                boolean edge = Math.abs(x) == 3 || Math.abs(z) == 3;
                c.set(x, deck, z, NBRICK);
                if (!edge) continue;
                for (int y = deck + 1; y <= deck + 4; y++) {
                    boolean door = Math.abs(x) == 3 && z == 0 && y <= deck + 2, slit = y == deck + 3 && (x + z) % 2 == 0 && !(Math.abs(x) == 3 && Math.abs(z) == 3);
                    if (door || slit || c.chance(c.decay)) continue;
                    c.set(x, y, z, y == deck + 4 ? NRED : NBRICK);
                }
            }
            for (int x = -3; x <= 3; x++) for (int z = -3; z <= 3; z++) if (Math.abs(x) <= 2 && Math.abs(z) <= 2 && c.chance(0.85)) c.set(x, deck + 5, z, NBRICK);
            c.set(0, deck + 5, 0, LIGHT);
            c.set(-2, deck + 1, -2, LIGHT); c.set(2, deck + 1, 2, LIGHT);
            c.set(0, deck + 1, 0, NRED);
            c.chest(0, deck + 2, 0);
        }
    },
    /** The beacon-killer platform: four chests round an iron pyramid with a beacon. Used by the beacon event only. */
    BEACON {
        public void draw(Canvas c) {
            int r = 6;
            c.fill(-r, 0, -r, r, 0, r, BASE);
            for (int t = -r; t <= r; t++) { c.set(t, 0, -r, ACCENT); c.set(t, 0, r, ACCENT); c.set(-r, 0, t, ACCENT); c.set(r, 0, t, ACCENT); }
            c.fill(-2, 0, -2, 2, 0, 2, TRIM);
            c.fill(-1, 1, -1, 1, 1, 1, IRON);          // the beacon needs this 3x3 of iron below it
            c.set(0, 2, 0, CORE);
            for (int sx = -1; sx <= 1; sx += 2) for (int sz = -1; sz <= 1; sz += 2) {
                c.fill(sx * r, 1, sz * r, sx * r, 3, sz * r, TRIM);
                c.set(sx * r, 4, sz * r, LIGHT);
                c.set(sx * 4, 0, sz * 4, TRIM);          // pedestals of the chests
            }
            c.chest(4, 1, 4);
            c.extraChest(-4, 1, 4);
            c.extraChest(4, 1, -4);
            c.extraChest(-4, 1, -4);
        }
    };

    /** Frame blocks of the ruined portal go along x, or along z when {@code sw}. */
    private static void put(Canvas c, boolean sw, int x, int y, int z, Canvas.Slot s) {
        if (sw) c.set(z, y, x, s); else c.set(x, y, z, s);
    }

    /** Rock of a volcano flank: ash towards the foot, now and then magma and obsidian. */
    private static Canvas.Slot rock(Canvas c, double d, int radius, boolean surface) {
        double p = c.rnd.nextDouble();
        if (surface) {
            if (d > radius * 0.7 && p < 0.45) return ASH;
            if (p < 0.08) return MAGMA;
            if (p < 0.16) return OBSIDIAN;
            if (p < 0.18) return CRYING;
            return ROCK;
        }
        return p < 0.05 ? MAGMA : ROCK;
    }

    /** A 4-connected line of cells in direction {@code angle} from distance {@code from} to {@code to} (no diagonal steps, so no gaps). */
    private static java.util.List<int[]> line(double angle, double from, double to) {
        java.util.List<int[]> out = new java.util.ArrayList<int[]>();
        int lx = Integer.MIN_VALUE, lz = Integer.MIN_VALUE;
        boolean inc = to >= from;
        for (double d = from; inc ? d <= to : d >= to; d += inc ? 0.5 : -0.5) {
            int x = (int) Math.round(Math.cos(angle) * d), z = (int) Math.round(Math.sin(angle) * d);
            if (x == lx && z == lz) continue;
            if (lx != Integer.MIN_VALUE && x != lx && z != lz) out.add(new int[]{x, lz});
            out.add(new int[]{x, z});
            lx = x; lz = z;
        }
        return out;
    }

    private static boolean onPath(java.util.List<int[]> path, int x, int z) {
        for (int[] p : path) if (p[0] == x && p[1] == z) return true;
        return false;
    }

    /** Default tags (filter with shapes: [tag:nether] in a profile or a tier). */
    public String[] tags() {
        switch (this) {
            case PYRAMID: return new String[]{"desert", "ancient"};
            case TEMPLE: return new String[]{"ancient"};
            case OBELISK: return new String[]{"ancient", "magic"};
            case HENGE: return new String[]{"ancient", "nature"};
            case GATE: return new String[]{"ancient"};
            case TOWER: return new String[]{"castle"};
            case COLOSSEUM: return new String[]{"arena", "pvp"};
            case CRYSTALS: return new String[]{"magic", "nature"};
            case RUNES: return new String[]{"magic"};
            case RUINED_PORTAL: return new String[]{"nether", "ruins"};
            case VOLCANO: return new String[]{"nature", "nether", "fire"};
            case SHIPWRECK: return new String[]{"ruins", "sea"};
            case DRAGON_BONES: return new String[]{"ancient", "monster"};
            case CASTLE_RUIN: return new String[]{"castle", "ruins"};
            case WITCH_HUT: return new String[]{"swamp", "magic"};
            case GRAVEYARD: return new String[]{"undead", "ruins"};
            case NETHER_OUTPOST: return new String[]{"nether", "castle"};
            default: return new String[0];
        }
    }

    /** Not part of the random rotation. */
    public boolean special() { return this == BEACON; }

    public abstract void draw(Canvas c);

    public static Shape parse(String s) {
        if (s == null) return null;
        try { return valueOf(s.trim().toUpperCase().replace('-', '_')); } catch (IllegalArgumentException e) { return null; }
    }
}
