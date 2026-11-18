package dev.h4xolot.core;

import java.util.concurrent.ConcurrentHashMap;

/** Packet class naming helpers (pure Java, no Minecraft dependency). */
public final class PacketNames {
    private static final ConcurrentHashMap<Class<?>, String> NAMES = new ConcurrentHashMap<>();

    private PacketNames() {}

    /** Class name without package; nested classes become Outer.Inner. */
    public static String of(Class<?> type) {
        return NAMES.computeIfAbsent(type, t -> {
            String n = t.getName();
            int i = n.lastIndexOf('.');
            if (i >= 0) n = n.substring(i + 1);
            return n.replace('$', '.');
        });
    }

    /** Compact name for the HUD: strips the Serverbound/Clientbound prefix and "Packet". */
    public static String shortName(String name) {
        return name.replaceFirst("^(Serverbound|Clientbound)", "").replace("Packet", "");
    }
}
