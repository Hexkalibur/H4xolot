package dev.h4xolot.commands;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import dev.h4xolot.core.Action;
import dev.h4xolot.core.ClickKind;
import dev.h4xolot.core.ClickTools;
import dev.h4xolot.core.Direction;
import dev.h4xolot.core.HeldQueue;
import dev.h4xolot.core.LogEntry;
import dev.h4xolot.core.LoopScheduler;
import dev.h4xolot.core.Msg;
import dev.h4xolot.core.PacketDumper;
import dev.h4xolot.core.PacketLog;
import dev.h4xolot.core.Pipeline;
import dev.h4xolot.modules.LoopPackets;
import dev.h4xolot.modules.PacketMonitor;
import meteordevelopment.meteorclient.commands.Command;
import meteordevelopment.meteorclient.systems.modules.Modules;
import net.minecraft.client.multiplayer.ClientSuggestionProvider;
import net.minecraft.world.item.ItemStack;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.Locale;

/** .h4xolot (alias .h4x): log inspection, replay, manual loops, queues and click tools. */
public class H4xolotCommand extends Command {
    private static final String CLICK_TYPES = "pickup, quick_move, swap, clone, throw, quick_craft, pickup_all";

    public H4xolotCommand() {
        super("h4xolot", "Inspect and control packets (h4xolot).", "h4x");
    }

    @Override
    public void build(LiteralArgumentBuilder<ClientSuggestionProvider> builder) {
        builder.executes(ctx -> {
            status();
            return SINGLE_SUCCESS;
        });

        // .h4xolot list [n]
        builder.then(literal("list")
            .executes(ctx -> { list(15); return SINGLE_SUCCESS; })
            .then(argument("n", IntegerArgumentType.integer(1, 100))
                .executes(ctx -> { list(IntegerArgumentType.getInteger(ctx, "n")); return SINGLE_SUCCESS; })));

        // .h4xolot search <text> [n]
        builder.then(literal("search")
            .then(argument("text", StringArgumentType.word())
                .executes(ctx -> { search(StringArgumentType.getString(ctx, "text"), 15); return SINGLE_SUCCESS; })
                .then(argument("n", IntegerArgumentType.integer(1, 100))
                    .executes(ctx -> {
                        search(StringArgumentType.getString(ctx, "text"), IntegerArgumentType.getInteger(ctx, "n"));
                        return SINGLE_SUCCESS;
                    }))));

        // .h4xolot show <id>
        builder.then(literal("show")
            .then(argument("id", IntegerArgumentType.integer(1))
                .executes(ctx -> { show(IntegerArgumentType.getInteger(ctx, "id")); return SINGLE_SUCCESS; })));

        // .h4xolot replay <id> [times]
        builder.then(literal("replay")
            .then(argument("id", IntegerArgumentType.integer(1))
                .executes(ctx -> { replay(IntegerArgumentType.getInteger(ctx, "id"), 1); return SINGLE_SUCCESS; })
                .then(argument("times", IntegerArgumentType.integer(1, 100))
                    .executes(ctx -> {
                        replay(IntegerArgumentType.getInteger(ctx, "id"), IntegerArgumentType.getInteger(ctx, "times"));
                        return SINGLE_SUCCESS;
                    }))));

        // .h4xolot loop <id> <count> <interval>   (count 0 = infinite)
        builder.then(literal("loop")
            .then(argument("id", IntegerArgumentType.integer(1))
                .then(argument("count", IntegerArgumentType.integer(0, 100000))
                    .then(argument("interval", IntegerArgumentType.integer(1, 1200))
                        .executes(ctx -> {
                            loop(IntegerArgumentType.getInteger(ctx, "id"),
                                IntegerArgumentType.getInteger(ctx, "count"),
                                IntegerArgumentType.getInteger(ctx, "interval"));
                            return SINGLE_SUCCESS;
                        })))));

        builder.then(literal("loops").executes(ctx -> { loops(); return SINGLE_SUCCESS; }));

        // .h4xolot stop [id]
        builder.then(literal("stop")
            .executes(ctx -> {
                out("Stopped " + Pipeline.INSTANCE.loops().stopAll() + " loops.");
                return SINGLE_SUCCESS;
            })
            .then(argument("id", IntegerArgumentType.integer(1))
                .executes(ctx -> {
                    int id = IntegerArgumentType.getInteger(ctx, "id");
                    out(Pipeline.INSTANCE.loops().stop(id) ? "Loop " + id + " stopped." : "No loop with id " + id + ".");
                    return SINGLE_SUCCESS;
                })));

        // .h4xolot flush [c2s|s2c|click]
        builder.then(literal("flush")
            .executes(ctx -> { flush(null, null, "packets"); return SINGLE_SUCCESS; })
            .then(literal("c2s").executes(ctx -> { flush(Direction.C2S, HeldQueue.Tag.DELAY, "C2S packets"); return SINGLE_SUCCESS; }))
            .then(literal("s2c").executes(ctx -> { flush(Direction.S2C, HeldQueue.Tag.DELAY, "S2C packets"); return SINGLE_SUCCESS; }))
            .then(literal("click").executes(ctx -> { flush(Direction.C2S, HeldQueue.Tag.CLICK, "held clicks"); return SINGLE_SUCCESS; })));

        // .h4xolot drop [c2s|s2c|click]
        builder.then(literal("drop")
            .executes(ctx -> { drop(null, null, "packets"); return SINGLE_SUCCESS; })
            .then(literal("c2s").executes(ctx -> { drop(Direction.C2S, HeldQueue.Tag.DELAY, "C2S packets"); return SINGLE_SUCCESS; }))
            .then(literal("s2c").executes(ctx -> { drop(Direction.S2C, HeldQueue.Tag.DELAY, "S2C packets"); return SINGLE_SUCCESS; }))
            .then(literal("click").executes(ctx -> { drop(Direction.C2S, HeldQueue.Tag.CLICK, "held clicks"); return SINGLE_SUCCESS; })));

        builder.then(literal("clear").executes(ctx -> {
            PacketLog.INSTANCE.clear();
            out("Log cleared.");
            return SINGLE_SUCCESS;
        }));

        // .h4xolot export [text|json] [name]
        builder.then(literal("export")
            .executes(ctx -> { export(PacketLog.ExportFormat.TEXT, null); return SINGLE_SUCCESS; })
            .then(literal("text")
                .executes(ctx -> { export(PacketLog.ExportFormat.TEXT, null); return SINGLE_SUCCESS; })
                .then(argument("name", StringArgumentType.word())
                    .executes(ctx -> { export(PacketLog.ExportFormat.TEXT, StringArgumentType.getString(ctx, "name")); return SINGLE_SUCCESS; })))
            .then(literal("json")
                .executes(ctx -> { export(PacketLog.ExportFormat.JSON, null); return SINGLE_SUCCESS; })
                .then(argument("name", StringArgumentType.word())
                    .executes(ctx -> { export(PacketLog.ExportFormat.JSON, StringArgumentType.getString(ctx, "name")); return SINGLE_SUCCESS; }))));

        // ---------- click tools ----------

        // .h4xolot close
        builder.then(literal("close").executes(ctx -> {
            if (ClickTools.closeWithoutPacket()) out("Screen closed on the client; no close packet sent.");
            else error("No screen is open.");
            return SINGLE_SUCCESS;
        }));

        // .h4xolot disconnectsend  (alias: race)
        builder.then(literal("disconnectsend").executes(ctx -> { disconnectSend(); return SINGLE_SUCCESS; }));
        builder.then(literal("race").executes(ctx -> { disconnectSend(); return SINGLE_SUCCESS; }));

        // .h4xolot fabricate <slot> <button> <type> [containerId stateId]
        builder.then(literal("fabricate")
            .then(argument("slot", IntegerArgumentType.integer(-999, 999))
                .then(argument("button", IntegerArgumentType.integer(0, 64))
                    .then(argument("type", StringArgumentType.word())
                        .executes(ctx -> {
                            ClickKind kind = ClickKind.parse(StringArgumentType.getString(ctx, "type"));
                            if (kind == null) {
                                error("Invalid type. Use: " + CLICK_TYPES);
                                return SINGLE_SUCCESS;
                            }
                            boolean ok = ClickTools.fabricateFromCurrent(
                                IntegerArgumentType.getInteger(ctx, "slot"),
                                IntegerArgumentType.getInteger(ctx, "button"), kind);
                            if (!ok) error("Fabricate failed (no menu open, or no usable packet constructor).");
                            return SINGLE_SUCCESS;
                        })
                        .then(argument("containerId", IntegerArgumentType.integer(0, 255))
                            .then(argument("stateId", IntegerArgumentType.integer(0, Integer.MAX_VALUE))
                                .executes(ctx -> {
                                    ClickKind kind = ClickKind.parse(StringArgumentType.getString(ctx, "type"));
                                    if (kind == null) {
                                        error("Invalid type. Use: " + CLICK_TYPES);
                                        return SINGLE_SUCCESS;
                                    }
                                    boolean ok = ClickTools.fabricate(
                                        IntegerArgumentType.getInteger(ctx, "containerId"),
                                        IntegerArgumentType.getInteger(ctx, "stateId"),
                                        IntegerArgumentType.getInteger(ctx, "slot"),
                                        IntegerArgumentType.getInteger(ctx, "button"), kind, ItemStack.EMPTY);
                                    if (!ok) error("Fabricate failed (no usable packet constructor).");
                                    return SINGLE_SUCCESS;
                                })))))));

        // .h4xolot clickstatus
        builder.then(literal("clickstatus").executes(ctx -> {
            Pipeline p = Pipeline.INSTANCE;
            out("held clicks: " + p.heldCount(Direction.C2S, HeldQueue.Tag.CLICK)
                + " | held C2S: " + p.heldCount(Direction.C2S, HeldQueue.Tag.DELAY)
                + " | held S2C: " + p.heldCount(Direction.S2C, HeldQueue.Tag.DELAY));
            return SINGLE_SUCCESS;
        }));
    }

    // ---------------------------------------------------------------- commands

    private void status() {
        Pipeline p = Pipeline.INSTANCE;
        PacketMonitor monitor = Modules.get().get(PacketMonitor.class);

        out("Monitor: " + (monitor != null && monitor.isActive() ? "on" : "off")
            + " | held C2S/S2C: " + p.heldCount(Direction.C2S, HeldQueue.Tag.DELAY) + "/" + p.heldCount(Direction.S2C, HeldQueue.Tag.DELAY)
            + " | clicks: " + p.heldCount(Direction.C2S, HeldQueue.Tag.CLICK)
            + " | loops: " + p.loops().size()
            + " | log: " + PacketLog.INSTANCE.size() + " entries"
            + " | " + PacketLog.INSTANCE.pps(Direction.C2S) + " C2S/s, " + PacketLog.INSTANCE.pps(Direction.S2C) + " S2C/s");
        out("Subcommands: list, search, show, replay, loop, loops, stop, flush, drop, clear, export");
        out("Click tools: close, disconnectsend (race), fabricate, clickstatus, flush click, drop click");
    }

    private void list(int n) {
        List<LogEntry> entries = PacketLog.INSTANCE.last(n, null);
        if (entries.isEmpty()) {
            out("The log is empty. Enable the Packet Monitor module to record packets.");
            return;
        }
        for (LogEntry e : entries) out(e.line(true, false));
    }

    private void search(String text, int n) {
        String q = text.toLowerCase(Locale.ROOT);
        List<LogEntry> entries = PacketLog.INSTANCE.last(n, e -> e.name.toLowerCase(Locale.ROOT).contains(q));
        if (entries.isEmpty()) {
            out("No log entry matches \"" + text + "\".");
            return;
        }
        for (LogEntry e : entries) out(e.line(true, false));
    }

    private void show(int id) {
        LogEntry e = PacketLog.INSTANCE.get(id);
        if (e == null) {
            error("No entry #" + id + " in the log (it may have been dropped from the buffer).");
            return;
        }

        out(e.line(false, false));
        if (e.packet == null) {
            out("Reference not kept: enable keep-s2c-refs in the Packet Monitor module to inspect S2C packets.");
            return;
        }
        for (String f : PacketDumper.fields(e.packet, 200)) out("  " + f);
    }

    private void replay(int id, int times) {
        LogEntry src = clientEntry(id);
        if (src == null) return;

        Pipeline p = Pipeline.INSTANCE;
        for (int i = 0; i < times; i++) p.sendNow(src.packet);
        p.logManual(Direction.C2S, src.packet, Action.REPLAYED, id);
        out("Re-sent #" + id + " x" + times + ".");
    }

    private void loop(int id, int count, int interval) {
        LogEntry src = clientEntry(id);
        if (src == null) return;

        LoopPackets module = Modules.get().get(LoopPackets.class);
        if (module == null || !module.isActive()) {
            error("Enable the Loop Packets module first: loops only run while it is active.");
            return;
        }

        if (module.startLoop(src.packet, count, interval, src)) {
            src.action = Action.LOOPED;
            out("Loop on #" + id + ": " + (count == 0 ? "infinite" : count + " repetitions") + ", every " + interval + " ticks.");
        } else {
            error("Active loop limit reached (max-tasks in Loop Packets).");
        }
    }

    private void loops() {
        List<LoopScheduler.Task> tasks = Pipeline.INSTANCE.loops().tasks();
        if (tasks.isEmpty()) {
            out("No active loops.");
            return;
        }
        for (LoopScheduler.Task t : tasks) {
            out("loop " + t.id + ": " + t.name + " | every " + t.interval + "t | sent " + t.sent
                + " | remaining " + (t.remaining < 0 ? "infinite" : String.valueOf(t.remaining)));
        }
    }

    private void flush(Direction dir, HeldQueue.Tag tag, String what) {
        out("Released " + Pipeline.INSTANCE.flush(dir, tag) + " " + what + ".");
    }

    private void drop(Direction dir, HeldQueue.Tag tag, String what) {
        out("Dropped " + Pipeline.INSTANCE.drop(dir, tag) + " " + what + " (not sent).");
    }

    private void disconnectSend() {
        int n = ClickTools.disconnectAndSend(true, true);
        out("Sent " + n + " packets and started the disconnect.");
    }

    private void export(PacketLog.ExportFormat format, String name) {
        String file = name != null ? name : "export-" + PacketMonitor.timestamp();
        try {
            File f = PacketLog.INSTANCE.export(PacketMonitor.logDir(), file, format);
            out("Log exported to " + f.getAbsolutePath());
        } catch (IOException ex) {
            error("Export failed: " + ex.getMessage());
        }
    }

    // ---------------------------------------------------------------- helpers

    /** A C2S log entry with a kept packet reference, otherwise prints an error and returns null. */
    private LogEntry clientEntry(int id) {
        LogEntry e = PacketLog.INSTANCE.get(id);
        if (e == null) {
            error("No entry #" + id + " in the log.");
            return null;
        }
        if (e.dir != Direction.C2S || e.packet == null) {
            error("Only C2S packets with a kept reference can be re-sent or looped.");
            return null;
        }
        return e;
    }

    private void out(String s) {
        info(Msg.esc(s));
    }
}
