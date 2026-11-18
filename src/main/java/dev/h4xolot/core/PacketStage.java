package dev.h4xolot.core;

import net.minecraft.network.protocol.Packet;

/** A module that takes part in the packet pipeline. Stages are consulted in a fixed order. */
public interface PacketStage {
    boolean isActive();

    /** May be called from the network thread (S2C), so it must be thread-safe. */
    Verdict decide(Direction dir, Packet<?> packet);

    /** Called after the pipeline applied a non-PASS verdict that this stage produced. */
    default void applied(Direction dir, Packet<?> packet, Verdict verdict, LogEntry entry) {}
}
