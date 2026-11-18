package dev.h4xolot.core;

import net.minecraft.network.protocol.Packet;

/** Receives every packet the pipeline sees and decides whether it is written to the log. */
public interface PacketRecorder {
    /** Returns the created log entry, or null when this packet is not recorded. */
    LogEntry record(Direction dir, Packet<?> packet, Action action, long tick);

    /** Output hook (chat / file) for entries created outside the normal flow (replay, fabricate). */
    void emit(LogEntry entry);
}
