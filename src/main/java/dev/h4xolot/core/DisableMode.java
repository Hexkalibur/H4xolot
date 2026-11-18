package dev.h4xolot.core;

/** What a module does with its queued packets when it is disabled. */
public enum DisableMode {
    /** Deliver everything that is still queued. */
    Flush,
    /** Discard everything that is still queued. */
    Drop,
    /** Leave the queue untouched (release it manually with the flush command or button). */
    Keep
}
