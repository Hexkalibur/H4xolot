package dev.h4xolot.modules;

import dev.h4xolot.H4xolotAddon;
import dev.h4xolot.core.Direction;
import dev.h4xolot.core.DisableMode;
import dev.h4xolot.core.HeldQueue;
import dev.h4xolot.core.Msg;
import dev.h4xolot.core.NameMatcher;
import dev.h4xolot.core.Overflow;
import dev.h4xolot.core.PacketStage;
import dev.h4xolot.core.Pipeline;
import dev.h4xolot.core.Verdict;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.gui.GuiTheme;
import meteordevelopment.meteorclient.gui.widgets.WWidget;
import meteordevelopment.meteorclient.gui.widgets.containers.WHorizontalList;
import meteordevelopment.meteorclient.gui.widgets.containers.WVerticalList;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.EnumSetting;
import meteordevelopment.meteorclient.settings.IntSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.settings.StringListSetting;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.network.protocol.Packet;

import java.util.List;

/** Holds the selected packets for a number of ticks (or until released by hand), then delivers them in order. */
public class DelayPackets extends Module implements PacketStage {
    private final NameMatcher c2sMatcher = new NameMatcher();
    private final NameMatcher s2cMatcher = new NameMatcher();

    private final SettingGroup sgPackets = settings.createGroup("Packets");
    private final SettingGroup sgTiming = settings.createGroup("Timing");
    private final SettingGroup sgQueue = settings.createGroup("Queue");

    private final Setting<List<String>> c2sPackets = sgPackets.add(new StringListSetting.Builder()
        .name("c2s-packets")
        .description("Client-to-server packets to hold. Pattern: part of the class name or =ExactName.")
        .onChanged(c2sMatcher::update)
        .build()
    );

    private final Setting<List<String>> s2cPackets = sgPackets.add(new StringListSetting.Builder()
        .name("s2c-packets")
        .description("Server-to-client packets to hold and apply later. Can desync the client. Pattern: part of the class name or =ExactName.")
        .onChanged(s2cMatcher::update)
        .build()
    );

    private final Setting<Integer> delayTicks = sgTiming.add(new IntSetting.Builder()
        .name("delay-ticks")
        .description("Ticks to wait before releasing (20 ticks = 1 second).")
        .defaultValue(20)
        .min(0)
        .sliderRange(0, 200)
        .build()
    );

    private final Setting<Boolean> hold = sgTiming.add(new BoolSetting.Builder()
        .name("hold")
        .description("Hold packets until you release them by hand (Flush button or .h4xolot flush).")
        .defaultValue(false)
        .build()
    );

    private final Setting<Integer> maxHoldTicks = sgTiming.add(new IntSetting.Builder()
        .name("max-hold-ticks")
        .description("Safety timeout for hold mode: release automatically after this many ticks (0 = never).")
        .defaultValue(0)
        .min(0)
        .sliderRange(0, 1200)
        .visible(hold::get)
        .build()
    );

    private final Setting<Boolean> preserveOrder = sgTiming.add(new BoolSetting.Builder()
        .name("preserve-order")
        .description("While anything is queued, also hold unselected packets of the same direction so the stream is not reordered.")
        .defaultValue(false)
        .build()
    );

    private final Setting<Boolean> exemptKeepAlive = sgTiming.add(new BoolSetting.Builder()
        .name("exempt-keep-alive")
        .description("With preserve-order, never hold keep-alive / pong packets. Holding them for too long gets you disconnected.")
        .defaultValue(true)
        .visible(preserveOrder::get)
        .build()
    );

    private final Setting<Integer> releasePerTick = sgQueue.add(new IntSetting.Builder()
        .name("release-per-tick")
        .description("Maximum packets released per tick and direction (0 = everything that is due).")
        .defaultValue(0)
        .min(0)
        .sliderRange(0, 50)
        .build()
    );

    private final Setting<Integer> queueLimit = sgQueue.add(new IntSetting.Builder()
        .name("queue-limit")
        .description("Maximum queue size per direction.")
        .defaultValue(1000)
        .min(1)
        .sliderRange(10, 5000)
        .build()
    );

    private final Setting<Overflow> overflow = sgQueue.add(new EnumSetting.Builder<Overflow>()
        .name("on-full")
        .description("ReleaseOldest keeps the stream ordered; PassThrough lets new packets through (may reorder).")
        .defaultValue(Overflow.ReleaseOldest)
        .build()
    );

    private final Setting<DisableMode> disableMode = sgQueue.add(new EnumSetting.Builder<DisableMode>()
        .name("on-disable")
        .description("What to do with queued packets when the module is disabled.")
        .defaultValue(DisableMode.Flush)
        .build()
    );

    public DelayPackets() {
        super(H4xolotAddon.CATEGORY, "delay-packets", "Holds selected packets for a while, then delivers them in their original order.");
    }

    @Override
    public void onActivate() {
        c2sMatcher.update(c2sPackets.get());
        s2cMatcher.update(s2cPackets.get());
    }

    @Override
    public void onDeactivate() {
        Pipeline p = Pipeline.INSTANCE;
        switch (disableMode.get()) {
            case Flush -> p.flush(null, HeldQueue.Tag.DELAY);
            case Drop -> p.drop(null, HeldQueue.Tag.DELAY);
            case Keep -> {}
        }
    }

    @Override
    public Verdict decide(Direction dir, Packet<?> packet) {
        Pipeline p = Pipeline.INSTANCE;
        Class<?> type = packet.getClass();

        boolean selected = (dir == Direction.C2S ? c2sMatcher : s2cMatcher).matches(type);
        boolean pulledIn = preserveOrder.get()
            && p.heldCount(dir, HeldQueue.Tag.DELAY) > 0
            && !(exemptKeepAlive.get() && Pipeline.isLiveness(type));

        if (!selected && !pulledIn) return Verdict.PASS;

        if (p.heldCount(dir, HeldQueue.Tag.DELAY) >= queueLimit.get()) {
            if (overflow.get() == Overflow.PassThrough) return Verdict.PASS;
            p.releaseOldest(dir, HeldQueue.Tag.DELAY);
        }

        long release;
        if (hold.get()) {
            release = maxHoldTicks.get() > 0 ? p.tick() + maxHoldTicks.get() : Long.MAX_VALUE;
        } else {
            release = p.tick() + delayTicks.get();
        }
        return Verdict.delay(HeldQueue.Tag.DELAY, release);
    }

    @EventHandler
    private void onTick(TickEvent.Pre event) {
        Pipeline.INSTANCE.releaseDue(HeldQueue.Tag.DELAY, releasePerTick.get());
    }

    @Override
    public String getInfoString() {
        int n = Pipeline.INSTANCE.heldCount(null, HeldQueue.Tag.DELAY);
        return n == 0 ? null : "held " + n;
    }

    @Override
    public WWidget getWidget(GuiTheme theme) {
        WVerticalList root = theme.verticalList();
        WHorizontalList row = root.add(theme.horizontalList()).expandX().widget();

        row.add(theme.button("Flush all")).expandX().widget().action = () ->
            info(Msg.esc("Released " + Pipeline.INSTANCE.flush(null, HeldQueue.Tag.DELAY) + " packets."));
        row.add(theme.button("Flush C2S")).expandX().widget().action = () ->
            info(Msg.esc("Released " + Pipeline.INSTANCE.flush(Direction.C2S, HeldQueue.Tag.DELAY) + " C2S packets."));
        row.add(theme.button("Flush S2C")).expandX().widget().action = () ->
            info(Msg.esc("Released " + Pipeline.INSTANCE.flush(Direction.S2C, HeldQueue.Tag.DELAY) + " S2C packets."));
        row.add(theme.button("Drop all")).expandX().widget().action = () ->
            info(Msg.esc("Dropped " + Pipeline.INSTANCE.drop(null, HeldQueue.Tag.DELAY) + " packets."));

        return root;
    }
}
