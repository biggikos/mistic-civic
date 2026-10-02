package ru.mysticchest.core;

import java.util.ArrayList;
import java.util.List;

/** Small ring buffer of "why did (not) something happen" notes, shown by /mystic debug. Always on, costs nothing idle. */
public final class Diag {
    public static final class Note {
        public final long at;
        public final String source, text;
        Note(String source, String text) { this.at = System.currentTimeMillis(); this.source = source; this.text = text; }
    }

    private static final int MAX = 40;
    private final Note[] ring = new Note[MAX];
    private int next, size;

    public synchronized void add(String source, String text) {
        ring[next] = new Note(source, text);
        next = (next + 1) % MAX;
        if (size < MAX) size++;
    }

    /** Newest first, at most {@code n}. */
    public synchronized List<Note> last(int n) {
        List<Note> out = new ArrayList<Note>();
        for (int i = 1; i <= size && out.size() < n; i++) out.add(ring[(next - i + MAX) % MAX]);
        return out;
    }

    public synchronized void clear() { size = 0; next = 0; }
}
