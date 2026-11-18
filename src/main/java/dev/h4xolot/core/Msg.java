package dev.h4xolot.core;

public final class Msg {
    private Msg() {}

    /** Meteor chat helpers treat % as a format character; escape it. */
    public static String esc(String s) {
        return s.replace("%", "%%");
    }
}
