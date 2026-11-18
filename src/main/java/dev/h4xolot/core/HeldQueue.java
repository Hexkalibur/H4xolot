package dev.h4xolot.core;

import net.minecraft.network.protocol.Packet;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Single arrival-ordered queue for every held packet (both directions, both kinds).
 * Keeping one queue preserves the original order when everything is flushed together.
 */
public final class HeldQueue {
    public enum Tag {
        /** Held by the Delay Packets module (timed or manual). */
        DELAY,
        /** Held by the Click Delay module (manual release only). */
        CLICK
    }

    public static final class Held {
        public final Direction dir;
        public final Tag tag;
        public final Packet<?> packet;
        public final long releaseTick;
        public final LogEntry entry;

        Held(Direction dir, Tag tag, Packet<?> packet, long releaseTick, LogEntry entry) {
            this.dir = dir;
            this.tag = tag;
            this.packet = packet;
            this.releaseTick = releaseTick;
            this.entry = entry;
        }
    }

    private final ConcurrentLinkedQueue<Held> items = new ConcurrentLinkedQueue<>();
    private final AtomicInteger[][] counts = new AtomicInteger[Direction.values().length][Tag.values().length];

    public HeldQueue() {
        for (AtomicInteger[] row : counts) {
            for (int i = 0; i < row.length; i++) row[i] = new AtomicInteger();
        }
    }

    public void add(Direction dir, Tag tag, Packet<?> packet, long releaseTick, LogEntry entry) {
        counts[dir.ordinal()][tag.ordinal()].incrementAndGet();
        items.add(new Held(dir, tag, packet, releaseTick, entry));
    }

    /** Number of held packets; a null dir or tag matches everything. */
    public int count(Direction dir, Tag tag) {
        int n = 0;
        for (Direction d : Direction.values()) {
            if (dir != null && d != dir) continue;
            for (Tag t : Tag.values()) {
                if (tag != null && t != tag) continue;
                n += counts[d.ordinal()][t.ordinal()].get();
            }
        }
        return n;
    }

    /**
     * Removes and returns the packets of the given tag whose release tick has been reached, in order.
     * Each direction is released FIFO: a packet that is not due yet blocks the ones behind it.
     * limitPerDir &lt;= 0 means unlimited.
     */
    public List<Held> takeDue(long tick, Tag tag, int limitPerDir) {
        List<Held> out = new ArrayList<>();
        int[] taken = new int[Direction.values().length];
        boolean[] blocked = new boolean[Direction.values().length];

        Iterator<Held> it = items.iterator();
        while (it.hasNext()) {
            Held h = it.next();
            if (h.tag != tag) continue;

            int d = h.dir.ordinal();
            if (blocked[d]) continue;
            if (h.releaseTick > tick || (limitPerDir > 0 && taken[d] >= limitPerDir)) {
                blocked[d] = true;
                continue;
            }

            it.remove();
            dec(h);
            taken[d]++;
            out.add(h);
        }
        return out;
    }

    /** Removes and returns everything that matches, in arrival order. A null dir or tag matches everything. */
    public List<Held> takeAll(Direction dir, Tag tag) {
        List<Held> out = new ArrayList<>();
        Iterator<Held> it = items.iterator();
        while (it.hasNext()) {
            Held h = it.next();
            if (!matches(h, dir, tag)) continue;
            it.remove();
            dec(h);
            out.add(h);
        }
        return out;
    }

    /** Removes and returns the oldest matching packet, or null. */
    public Held takeOldest(Direction dir, Tag tag) {
        Iterator<Held> it = items.iterator();
        while (it.hasNext()) {
            Held h = it.next();
            if (!matches(h, dir, tag)) continue;
            it.remove();
            dec(h);
            return h;
        }
        return null;
    }

    public void clear() {
        Held h;
        while ((h = items.poll()) != null) dec(h);
    }

    private static boolean matches(Held h, Direction dir, Tag tag) {
        return (dir == null || h.dir == dir) && (tag == null || h.tag == tag);
    }

    private void dec(Held h) {
        counts[h.dir.ordinal()][h.tag.ordinal()].decrementAndGet();
    }
}
