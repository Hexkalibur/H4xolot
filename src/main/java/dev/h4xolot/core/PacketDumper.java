package dev.h4xolot.core;

import java.lang.reflect.Array;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;

/** Reflection-based packet field dump (best effort, never throws to the caller). */
public final class PacketDumper {
    private PacketDumper() {}

    /** One "field=value, field=value" line truncated to maxLen. */
    public static String summary(Object packet, int maxLen) {
        StringBuilder sb = new StringBuilder();
        for (String s : fields(packet, 40)) {
            if (sb.length() > 0) sb.append(", ");
            sb.append(s);
            if (sb.length() > maxLen) break;
        }
        return truncate(sb.toString(), maxLen);
    }

    /** "field=value" list, one element per field. */
    public static List<String> fields(Object packet, int maxValueLen) {
        List<String> out = new ArrayList<>();
        if (packet == null) return out;

        for (Class<?> c = packet.getClass(); c != null && c != Object.class; c = c.getSuperclass()) {
            Field[] declared;
            try {
                declared = c.getDeclaredFields();
            } catch (Throwable t) {
                continue;
            }

            for (Field f : declared) {
                if (Modifier.isStatic(f.getModifiers()) || f.isSynthetic()) continue;

                String value;
                try {
                    f.setAccessible(true);
                    value = format(f.get(packet), maxValueLen);
                } catch (Throwable t) {
                    value = "<unreadable>";
                }
                out.add(f.getName() + "=" + value);
            }
        }
        return out;
    }

    private static String format(Object v, int max) {
        if (v == null) return "null";
        if (v instanceof CharSequence) return "\"" + truncate(clean(v.toString()), max) + "\"";
        if (v instanceof Number || v instanceof Boolean || v instanceof Character || v instanceof Enum<?>) return v.toString();

        Class<?> type = v.getClass();
        if (type.isArray()) {
            if (v instanceof byte[] bytes) return "byte[" + bytes.length + "]";
            return type.getComponentType().getSimpleName() + "[" + Array.getLength(v) + "]";
        }
        if (type.getName().contains("ByteBuf")) return "<buffer>";
        if (v instanceof Collection<?> col) return type.getSimpleName() + "(" + col.size() + ")" + firstItems(col, max);
        if (v instanceof Map<?, ?> map) return type.getSimpleName() + "(" + map.size() + ")";

        try {
            return truncate(clean(String.valueOf(v)), max);
        } catch (Throwable t) {
            return "<" + type.getSimpleName() + ">";
        }
    }

    private static String firstItems(Collection<?> col, int max) {
        if (col.isEmpty()) return "";
        StringBuilder sb = new StringBuilder(" [");
        int i = 0;
        for (Object o : col) {
            if (i++ > 0) sb.append(", ");
            if (i > 3) { sb.append("..."); break; }
            sb.append(truncate(clean(String.valueOf(o)), Math.max(10, max / 3)));
        }
        return sb.append("]").toString();
    }

    private static String clean(String s) {
        return s.replace('\n', ' ').replace('\r', ' ');
    }

    private static String truncate(String s, int max) {
        return s.length() <= max ? s : s.substring(0, Math.max(0, max - 1)) + "…";
    }
}
