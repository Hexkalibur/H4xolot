package dev.h4xolot.core;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Matches packet classes against a list of patterns (case-insensitive).
 *
 * <ul>
 *   <li>{@code ContainerClick} - the class name contains the text</li>
 *   <li>{@code =ServerboundContainerClickPacket} - the class name equals the text</li>
 * </ul>
 *
 * Patterns are compiled once by {@link #update(Collection)} (call it from the setting's onChanged
 * callback). Lookups are lock-free and safe from any thread, including the network thread.
 */
public final class NameMatcher {
    private volatile Compiled compiled = Compiled.EMPTY;

    public NameMatcher() {}

    public NameMatcher(Collection<String> patterns) {
        update(patterns);
    }

    public void update(Collection<String> patterns) {
        compiled = Compiled.of(patterns);
    }

    public boolean matches(Class<?> type) {
        return compiled.matches(type);
    }

    public boolean isEmpty() {
        return compiled.isEmpty();
    }

    private static final class Compiled {
        static final Compiled EMPTY = new Compiled(new String[0], new String[0]);

        private final String[] contains;
        private final String[] exact;
        private final ConcurrentHashMap<Class<?>, Boolean> cache = new ConcurrentHashMap<>();

        private Compiled(String[] contains, String[] exact) {
            this.contains = contains;
            this.exact = exact;
        }

        static Compiled of(Collection<String> patterns) {
            if (patterns == null || patterns.isEmpty()) return EMPTY;

            List<String> c = new ArrayList<>();
            List<String> e = new ArrayList<>();
            for (String raw : List.copyOf(patterns)) {
                if (raw == null) continue;
                String p = raw.trim().toLowerCase(Locale.ROOT);
                if (p.isEmpty()) continue;

                if (p.startsWith("=")) {
                    if (p.length() > 1) e.add(p.substring(1));
                } else {
                    c.add(p);
                }
            }
            if (c.isEmpty() && e.isEmpty()) return EMPTY;
            return new Compiled(c.toArray(new String[0]), e.toArray(new String[0]));
        }

        boolean isEmpty() {
            return contains.length == 0 && exact.length == 0;
        }

        boolean matches(Class<?> type) {
            if (isEmpty()) return false;
            return cache.computeIfAbsent(type, this::test);
        }

        private boolean test(Class<?> type) {
            String n = PacketNames.of(type).toLowerCase(Locale.ROOT);
            int dot = n.lastIndexOf('.');
            String leaf = dot >= 0 ? n.substring(dot + 1) : n;

            for (String x : exact) {
                if (n.equals(x) || leaf.equals(x)) return true;
            }
            for (String c : contains) {
                if (n.contains(c)) return true;
            }
            return false;
        }
    }
}
