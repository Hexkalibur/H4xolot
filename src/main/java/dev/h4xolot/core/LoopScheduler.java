package dev.h4xolot.core;

import net.minecraft.network.protocol.Packet;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

/** Re-sends C2S packets every N ticks. Runs on the client thread. */
public final class LoopScheduler {
    public static final class Task {
        public final int id;
        public final Packet<?> packet;
        public final String name;
        public final int interval;
        /** Remaining sends, -1 = infinite. */
        public int remaining;
        public int sent;
        int cooldown;
        final LogEntry source;

        Task(int id, Packet<?> packet, int count, int interval, LogEntry source) {
            this.id = id;
            this.packet = packet;
            this.name = PacketNames.of(packet.getClass());
            this.remaining = count <= 0 ? -1 : count;
            this.interval = Math.max(1, interval);
            this.cooldown = this.interval;
            this.source = source;
        }
    }

    private final CopyOnWriteArrayList<Task> tasks = new CopyOnWriteArrayList<>();
    private final AtomicInteger ids = new AtomicInteger(1);

    /**
     * Starts a loop. count &lt;= 0 means infinite. With replace, an existing loop of the same packet
     * class is stopped first. Returns the new task id, or -1 when the task limit is reached.
     */
    public int start(Packet<?> packet, int count, int interval, LogEntry source, boolean replace, int maxTasks) {
        if (replace) tasks.removeIf(t -> t.packet.getClass() == packet.getClass());
        if (tasks.size() >= maxTasks) return -1;

        Task t = new Task(ids.getAndIncrement(), packet, count, interval, source);
        tasks.add(t);
        return t.id;
    }

    /** Sends the due packets, at most budget per call. */
    public void run(int budget, Consumer<Packet<?>> sender) {
        if (tasks.isEmpty()) return;

        for (Task t : tasks) {
            if (--t.cooldown > 0) continue;
            if (budget <= 0) {
                t.cooldown = 1;
                continue;
            }

            sender.accept(t.packet);
            budget--;
            t.sent++;
            t.cooldown = t.interval;
            if (t.source != null) t.source.loopSent = t.sent;

            if (t.remaining > 0 && --t.remaining == 0) tasks.remove(t);
        }
    }

    public boolean stop(int id) {
        return tasks.removeIf(t -> t.id == id);
    }

    public int stopAll() {
        int n = tasks.size();
        tasks.clear();
        return n;
    }

    public int size() {
        return tasks.size();
    }

    public List<Task> tasks() {
        return tasks;
    }
}
