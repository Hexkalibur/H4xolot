package dev.h4xolot.core;

/** The decision a {@link PacketStage} takes for one packet. */
public record Verdict(Action action, HeldQueue.Tag tag, long releaseTick) {
    public static final Verdict PASS = new Verdict(Action.PASS, null, 0);
    public static final Verdict BLOCK = new Verdict(Action.BLOCKED, null, 0);
    public static final Verdict LOOP = new Verdict(Action.LOOPED, null, 0);

    /** Hold the packet; it is released when the tick counter reaches releaseTick (Long.MAX_VALUE = manual only). */
    public static Verdict delay(HeldQueue.Tag tag, long releaseTick) {
        return new Verdict(Action.DELAYED, tag, releaseTick);
    }
}
