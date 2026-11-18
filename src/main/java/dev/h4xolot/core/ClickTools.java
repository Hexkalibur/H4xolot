package dev.h4xolot.core;

import com.mojang.logging.LogUtils;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ServerboundContainerClickPacket;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import org.slf4j.Logger;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.Comparator;

/** Container / click utilities shared by modules, GUI buttons and commands. */
public final class ClickTools {
    private static final Logger LOG = LogUtils.getLogger();

    private ClickTools() {}

    /**
     * Closes the current screen on the client without telling the server (no close-container packet).
     * The client-side menu is left as it is, so the container id and state id stay valid for later clicks.
     */
    public static boolean closeWithoutPacket() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.gui.screen() == null) return false;
        mc.gui.setScreen(null);
        return true;
    }

    /**
     * Sends the queued packets in arrival order, then disconnects. Returns the number of packets sent.
     *
     * @param clicks  include packets held by Click Delay
     * @param delayed include client-to-server packets held by Delay Packets
     */
    public static int disconnectAndSend(boolean clicks, boolean delayed) {
        Minecraft mc = Minecraft.getInstance();
        ClientPacketListener conn = mc.getConnection();
        if (conn == null) return 0;

        int sent = 0;
        if (clicks || delayed) {
            HeldQueue.Tag tag = clicks && delayed ? null : clicks ? HeldQueue.Tag.CLICK : HeldQueue.Tag.DELAY;
            sent = Pipeline.INSTANCE.flush(Direction.C2S, tag);
        }

        conn.getConnection().disconnect(Component.literal("h4xolot: disconnect-and-send"));
        return sent;
    }

    /** Builds and sends a click packet with explicit ids. Returns false if the packet could not be built. */
    public static boolean fabricate(int containerId, int stateId, int slot, int button, ClickKind kind, ItemStack carried) {
        Packet<?> packet;
        try {
            packet = buildClickPacket(containerId, stateId, slot, button, kind, carried);
        } catch (Throwable t) {
            LOG.warn("fabricate: building the click packet failed", t);
            return false;
        }
        if (packet == null) return false;

        Pipeline.INSTANCE.sendNow(packet);
        Pipeline.INSTANCE.logManual(Direction.C2S, packet, Action.REPLAYED, -1);
        return true;
    }

    /** Like {@link #fabricate} but takes container id, state id and carried item from the menu that is currently open. */
    public static boolean fabricateFromCurrent(int slot, int button, ClickKind kind) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return false;

        AbstractContainerMenu menu = mc.player.containerMenu;
        return fabricate(menu.containerId, menu.getStateId(), slot, button, kind, menu.getCarried().copy());
    }

    /**
     * Builds a ServerboundContainerClickPacket by inspecting its public constructors, so the argument
     * types (short/byte vs int, HashedStack vs ItemStack) follow whatever the running game version uses.
     * The carried item is hashed when the packet type offers a static ItemStack factory; otherwise an
     * empty value is sent. Changed slots are always empty.
     */
    private static Packet<?> buildClickPacket(int containerId, int stateId, int slot, int button,
                                              ClickKind kind, ItemStack carried) {
        Constructor<?>[] ctors = ServerboundContainerClickPacket.class.getConstructors();
        Arrays.sort(ctors, Comparator.comparingInt((Constructor<?> c) -> c.getParameterCount()).reversed());

        for (Constructor<?> ctor : ctors) {
            Object[] args = buildArgs(ctor.getParameterTypes(), containerId, stateId, slot, button, kind, carried);
            if (args == null) continue;
            try {
                return (Packet<?>) ctor.newInstance(args);
            } catch (ReflectiveOperationException | IllegalArgumentException e) {
                LOG.debug("click packet constructor {} rejected", ctor, e);
            }
        }
        return null;
    }

    private static Object[] buildArgs(Class<?>[] types, int containerId, int stateId, int slot, int button,
                                      ClickKind kind, ItemStack carried) {
        int[] ints = {containerId, stateId, slot, button};
        int next = 0;
        Object[] args = new Object[types.length];

        for (int i = 0; i < types.length; i++) {
            Class<?> t = types[i];

            if (t == int.class || t == short.class || t == byte.class) {
                if (next >= ints.length) return null;
                int v = ints[next++];
                args[i] = t == int.class ? (Object) v : t == short.class ? (Object) (short) v : (Object) (byte) v;
            } else if (t == kind.input.getClass() || t.isInstance(kind.input)) {
                args[i] = kind.input;
            } else if (t == ItemStack.class) {
                args[i] = carried == null ? ItemStack.EMPTY : carried;
            } else if (t.isAssignableFrom(Int2ObjectOpenHashMap.class)) {
                args[i] = new Int2ObjectOpenHashMap<>();
            } else {
                Object v = hashedOrEmpty(t, carried);
                if (v == null) return null;
                args[i] = v;
            }
        }
        return args;
    }

    /** For a "hashed stack" style parameter: a static (ItemStack) -> T factory if present, else T.EMPTY. */
    private static Object hashedOrEmpty(Class<?> type, ItemStack carried) {
        if (carried != null && !carried.isEmpty()) {
            for (Method m : type.getMethods()) {
                if (!Modifier.isStatic(m.getModifiers()) || m.getParameterCount() != 1) continue;
                if (m.getParameterTypes()[0] != ItemStack.class || !type.isAssignableFrom(m.getReturnType())) continue;
                try {
                    return m.invoke(null, carried);
                } catch (ReflectiveOperationException ignored) {
                    // fall through to EMPTY
                }
            }
        }
        try {
            Field f = type.getField("EMPTY");
            if (Modifier.isStatic(f.getModifiers()) && type.isAssignableFrom(f.getType())) return f.get(null);
        } catch (ReflectiveOperationException ignored) {
            // no EMPTY constant
        }
        return null;
    }
}
