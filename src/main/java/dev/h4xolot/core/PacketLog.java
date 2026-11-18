package dev.h4xolot.core;

import net.minecraft.network.protocol.Packet;

import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Predicate;

/** Thread-safe ring buffer shared by the monitor module, the HUD and the commands. */
public final class PacketLog {
    public static final PacketLog INSTANCE = new PacketLog();

    public enum ExportFormat {
        TEXT(".log"),
        JSON(".jsonl");

        public final String extension;

        ExportFormat(String extension) {
            this.extension = extension;
        }
    }

    private final ConcurrentLinkedDeque<LogEntry> entries = new ConcurrentLinkedDeque<>();
    private final ConcurrentHashMap<Integer, LogEntry> byId = new ConcurrentHashMap<>();
    private final AtomicInteger ids = new AtomicInteger(1);
    private final AtomicInteger size = new AtomicInteger();
    private volatile int capacity = 500;

    private final AtomicInteger c2sWindow = new AtomicInteger();
    private final AtomicInteger s2cWindow = new AtomicInteger();
    private volatile int c2sPps;
    private volatile int s2cPps;

    private PacketLog() {}

    public void setCapacity(int capacity) {
        this.capacity = Math.max(10, capacity);
    }

    public LogEntry add(Direction dir, Packet<?> packet, Action action, long tick, boolean keepRef) {
        LogEntry e = new LogEntry(ids.getAndIncrement(), System.currentTimeMillis(), tick, dir, packet, action, keepRef);
        entries.addLast(e);
        byId.put(e.id, e);

        size.incrementAndGet();
        while (size.get() > capacity) {
            LogEntry old = entries.pollFirst();
            if (old == null) break;
            byId.remove(old.id);
            size.decrementAndGet();
        }
        return e;
    }

    public LogEntry get(int id) {
        return byId.get(id);
    }

    /** Last n entries (chronological order) that satisfy the filter. */
    public List<LogEntry> last(int n, Predicate<LogEntry> filter) {
        List<LogEntry> out = new ArrayList<>(Math.min(n, 64));
        Iterator<LogEntry> it = entries.descendingIterator();
        while (it.hasNext() && out.size() < n) {
            LogEntry e = it.next();
            if (filter == null || filter.test(e)) out.add(e);
        }
        Collections.reverse(out);
        return out;
    }

    public int size() {
        return Math.max(0, size.get());
    }

    public void clear() {
        entries.clear();
        byId.clear();
        size.set(0);
    }

    // --- packets per second (window of 20 ticks) ---

    public void count(Direction dir) {
        (dir == Direction.C2S ? c2sWindow : s2cWindow).incrementAndGet();
    }

    public void rollWindow() {
        c2sPps = c2sWindow.getAndSet(0);
        s2cPps = s2cWindow.getAndSet(0);
    }

    public int pps(Direction dir) {
        return dir == Direction.C2S ? c2sPps : s2cPps;
    }

    // --- export ---

    public File export(File dir, String name, ExportFormat format) throws IOException {
        Files.createDirectories(dir.toPath());
        File file = new File(dir, name.endsWith(format.extension) ? name : name + format.extension);

        try (BufferedWriter w = Files.newBufferedWriter(file.toPath(), StandardCharsets.UTF_8)) {
            for (LogEntry e : entries) {
                w.write(format == ExportFormat.JSON ? e.jsonLine() : e.fileLine());
                w.newLine();
            }
        }
        return file;
    }
}
