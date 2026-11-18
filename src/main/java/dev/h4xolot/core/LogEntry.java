package dev.h4xolot.core;

import net.minecraft.network.protocol.Packet;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

/** One log line. Mutable fields are updated when a held packet is released or looped. */
public final class LogEntry {
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm:ss.SSS").withZone(ZoneId.systemDefault());

    public final int id;
    public final long wallMs;
    public final long tick;
    public final Direction dir;
    public final Class<?> type;
    public final String name;
    /** Reference to the packet, null when it is not retained (typically S2C). */
    public final Packet<?> packet;

    public volatile Action action;
    public volatile long releasedTick = -1;
    public volatile int loopSent;
    public volatile int sourceId = -1;

    private volatile String summary;

    LogEntry(int id, long wallMs, long tick, Direction dir, Packet<?> packet, Action action, boolean keepRef) {
        this.id = id;
        this.wallMs = wallMs;
        this.tick = tick;
        this.dir = dir;
        this.type = packet.getClass();
        this.name = PacketNames.of(type);
        this.packet = keepRef ? packet : null;
        this.action = action;
    }

    public void markReleased(long nowTick) {
        this.releasedTick = nowTick;
    }

    public String tag() {
        return switch (action) {
            case PASS -> "";
            case BLOCKED -> "BLOCKED";
            case DELAYED -> releasedTick < 0 ? "HELD" : "DELAYED " + (releasedTick - tick) + "t";
            case LOOPED -> "LOOP x" + loopSent;
            case REPLAYED -> "REPLAY #" + sourceId;
        };
    }

    /** Field details, computed once and only on demand. */
    public String summary() {
        String s = summary;
        if (s == null) {
            s = packet == null ? "" : PacketDumper.summary(packet, 70);
            summary = s;
        }
        return s;
    }

    public String line(boolean shortName, boolean details) {
        StringBuilder sb = new StringBuilder();
        sb.append('#').append(id).append(' ').append(dir.tag).append(' ')
            .append(shortName ? PacketNames.shortName(name) : name);

        String tag = tag();
        if (!tag.isEmpty()) sb.append(" [").append(tag).append(']');

        if (details) {
            String d = summary();
            if (!d.isEmpty()) sb.append("  ").append(d);
        }
        return sb.toString();
    }

    /** Text format for files/export, with timestamp. */
    public String fileLine() {
        return TIME.format(Instant.ofEpochMilli(wallMs)) + " t" + tick + " " + line(false, true);
    }

    /** One JSON object per line (JSONL), with the full field list when the packet is retained. */
    public String jsonLine() {
        StringBuilder sb = new StringBuilder(192);
        sb.append("{\"id\":").append(id)
            .append(",\"time\":").append(JsonUtil.quote(Instant.ofEpochMilli(wallMs).toString()))
            .append(",\"tick\":").append(tick)
            .append(",\"dir\":").append(JsonUtil.quote(dir.tag))
            .append(",\"packet\":").append(JsonUtil.quote(name))
            .append(",\"action\":").append(JsonUtil.quote(action.name()));

        if (releasedTick >= 0) sb.append(",\"releasedTick\":").append(releasedTick);
        if (loopSent > 0) sb.append(",\"loopSent\":").append(loopSent);
        if (sourceId >= 0) sb.append(",\"sourceId\":").append(sourceId);

        if (packet != null) {
            sb.append(",\"fields\":[");
            boolean first = true;
            for (String f : PacketDumper.fields(packet, 200)) {
                if (!first) sb.append(',');
                sb.append(JsonUtil.quote(f));
                first = false;
            }
            sb.append(']');
        }
        return sb.append('}').toString();
    }
}
