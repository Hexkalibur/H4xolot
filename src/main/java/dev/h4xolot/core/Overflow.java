package dev.h4xolot.core;

/** What happens when a queue is full and another packet should be held. */
public enum Overflow {
    /** Release the oldest held packet to make room (keeps the stream ordered). */
    ReleaseOldest,
    /** Let the new packet through immediately (may reorder the stream). */
    PassThrough
}
