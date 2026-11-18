package dev.h4xolot.core;

import com.mojang.logging.LogUtils;
import meteordevelopment.meteorclient.events.game.GameLeftEvent;
import meteordevelopment.meteorclient.events.packets.PacketEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.orbit.EventHandler;
import meteordevelopment.orbit.EventPriority;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import org.slf4j.Logger;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Central packet dispatcher. It is the only class that listens to Meteor's packet events; the
 * modules register as {@link PacketStage}s and are consulted in registration order
 * (Block, then Click Delay, then Delay, then Loop), so priorities are deterministic.
 */
public final class Pipeline {
    public static final Pipeline INSTANCE = new Pipeline();

    private static final Logger LOG = LogUtils.getLogger();

    /** Marks packets that h4xolot itself is sending so they are not processed again. */
    private static final ThreadLocal<Boolean> BYPASS = ThreadLocal.withInitial(() -> Boolean.FALSE);

    /** Liveness packets that must not be pulled into a preserve-order hold. */
    private static final NameMatcher LIVENESS = new NameMatcher(List.of("KeepAlive", "Pong"));

    private final List<PacketStage> stages = new CopyOnWriteArrayList<>();
    private final HeldQueue held = new HeldQueue();
    private final LoopScheduler loops = new LoopScheduler();
    private volatile PacketRecorder recorder;
    private volatile long tick;

    private Pipeline() {}

    // --- wiring ---

    public void addStage(PacketStage stage) {
        stages.add(stage);
    }

    public void setRecorder(PacketRecorder recorder) {
        this.recorder = recorder;
    }

    // --- state ---

    public long tick() {
        return tick;
    }

    public LoopScheduler loops() {
        return loops;
    }

    public int heldCount(Direction dir, HeldQueue.Tag tag) {
        return held.count(dir, tag);
    }

    public static boolean isLiveness(Class<?> type) {
        return LIVENESS.matches(type);
    }

    // --- events ---

    @EventHandler(priority = EventPriority.HIGHEST)
    private void onSend(PacketEvent.Send event) {
        if (BYPASS.get()) return;
        if (handle(Direction.C2S, event.packet)) event.cancel();
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    private void onReceive(PacketEvent.Receive event) {
        if (handle(Direction.S2C, event.packet)) event.cancel();
    }

    @EventHandler
    private void onTick(TickEvent.Pre event) {
        tick = tick + 1;
        if (tick % 20 == 0) PacketLog.INSTANCE.rollWindow();
    }

    @EventHandler
    private void onGameLeft(GameLeftEvent event) {
        held.clear();
        loops.stopAll();
    }

    /** Returns true when the packet must be cancelled. */
    private boolean handle(Direction dir, Packet<?> packet) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.getConnection() == null) return false;

        PacketLog.INSTANCE.count(dir);

        Verdict verdict = Verdict.PASS;
        PacketStage decider = null;
        for (PacketStage stage : stages) {
            if (!stage.isActive()) continue;
            Verdict v = stage.decide(dir, packet);
            if (v.action() != Action.PASS) {
                verdict = v;
                decider = stage;
                break;
            }
        }

        PacketRecorder rec = recorder;
        LogEntry entry = rec == null ? null : rec.record(dir, packet, verdict.action(), tick);

        switch (verdict.action()) {
            case BLOCKED -> {
                return true;
            }
            case DELAYED -> {
                held.add(dir, verdict.tag(), packet, verdict.releaseTick(), entry);
                return true;
            }
            case LOOPED -> {
                if (decider != null) decider.applied(dir, packet, verdict, entry);
                return false;
            }
            default -> {
                return false;
            }
        }
    }

    // --- releasing ---

    /** Releases the held packets of one kind whose time has come. limitPerDir &lt;= 0 means unlimited. */
    public int releaseDue(HeldQueue.Tag tag, int limitPerDir) {
        return deliver(held.takeDue(tick, tag, limitPerDir));
    }

    /** Delivers held packets in arrival order. A null dir or tag matches everything. Returns the count. */
    public int flush(Direction dir, HeldQueue.Tag tag) {
        return deliver(held.takeAll(dir, tag));
    }

    public int releaseOldest(Direction dir, HeldQueue.Tag tag) {
        HeldQueue.Held h = held.takeOldest(dir, tag);
        return h == null ? 0 : deliver(List.of(h));
    }

    /** Discards held packets. Their log entries are marked BLOCKED. Returns the count. */
    public int drop(Direction dir, HeldQueue.Tag tag) {
        List<HeldQueue.Held> list = held.takeAll(dir, tag);
        for (HeldQueue.Held h : list) {
            if (h.entry != null) h.entry.action = Action.BLOCKED;
        }
        return list.size();
    }

    private int deliver(List<HeldQueue.Held> list) {
        for (HeldQueue.Held h : list) {
            if (h.dir == Direction.C2S) sendNow(h.packet);
            else applyS2C(h.packet);
            if (h.entry != null) h.entry.markReleased(tick);
        }
        return list.size();
    }

    public void runLoops(int budgetPerTick) {
        loops.run(budgetPerTick, this::sendNow);
    }

    // --- sending ---

    /** Sends a C2S packet immediately, bypassing every stage. */
    public void sendNow(Packet<?> packet) {
        ClientPacketListener listener = Minecraft.getInstance().getConnection();
        if (listener == null) return;

        BYPASS.set(Boolean.TRUE);
        try {
            listener.send(packet);
        } finally {
            BYPASS.set(Boolean.FALSE);
        }
    }

    /** Feeds an S2C packet to the client as if it had just arrived. Always runs on the client thread. */
    @SuppressWarnings("unchecked")
    private void applyS2C(Packet<?> packet) {
        Minecraft mc = Minecraft.getInstance();
        if (!mc.isSameThread()) {
            mc.execute(() -> applyS2C(packet));
            return;
        }

        ClientPacketListener listener = mc.getConnection();
        if (listener == null) return;

        try {
            ((Packet<ClientGamePacketListener>) packet).handle(listener);
        } catch (Throwable t) {
            LOG.warn("Could not deliver held S2C packet {}", PacketNames.of(packet.getClass()), t);
        }
    }

    // --- manual log entries ---

    /** Logs a packet that h4xolot created itself (replay, fabricate) and forwards it to the output sinks. */
    public LogEntry logManual(Direction dir, Packet<?> packet, Action action, int sourceId) {
        LogEntry e = PacketLog.INSTANCE.add(dir, packet, action, tick, true);
        e.sourceId = sourceId;

        PacketRecorder rec = recorder;
        if (rec != null) rec.emit(e);
        return e;
    }
}
