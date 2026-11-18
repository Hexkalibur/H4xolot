package dev.h4xolot.modules;

import dev.h4xolot.H4xolotAddon;
import dev.h4xolot.core.Direction;
import dev.h4xolot.core.LogEntry;
import dev.h4xolot.core.Msg;
import dev.h4xolot.core.NameMatcher;
import dev.h4xolot.core.PacketStage;
import dev.h4xolot.core.Pipeline;
import dev.h4xolot.core.Verdict;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.gui.GuiTheme;
import meteordevelopment.meteorclient.gui.widgets.WWidget;
import meteordevelopment.meteorclient.gui.widgets.containers.WVerticalList;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.IntSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.settings.StringListSetting;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.network.protocol.Packet;

import java.util.List;

/** After a selected client-to-server packet is sent, keeps re-sending it every N ticks. */
public class LoopPackets extends Module implements PacketStage {
    private final NameMatcher c2sMatcher = new NameMatcher();

    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    private final Setting<List<String>> c2sPackets = sgGeneral.add(new StringListSetting.Builder()
        .name("c2s-packets")
        .description("Client-to-server packets to loop once they are sent. Pattern: part of the class name or =ExactName.")
        .onChanged(c2sMatcher::update)
        .build()
    );

    private final Setting<Integer> loopCount = sgGeneral.add(new IntSetting.Builder()
        .name("loop-count")
        .description("Extra repetitions per packet (0 = infinite until you stop the loops).")
        .defaultValue(10)
        .min(0)
        .sliderRange(0, 200)
        .build()
    );

    private final Setting<Integer> loopInterval = sgGeneral.add(new IntSetting.Builder()
        .name("loop-interval")
        .description("Ticks between repetitions.")
        .defaultValue(1)
        .min(1)
        .sliderRange(1, 100)
        .build()
    );

    private final Setting<Boolean> replace = sgGeneral.add(new BoolSetting.Builder()
        .name("replace")
        .description("A new loop of a packet type replaces the running loop of the same type.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Integer> maxPerTick = sgGeneral.add(new IntSetting.Builder()
        .name("max-per-tick")
        .description("Cap on packets sent by all loops in a single tick (protection).")
        .defaultValue(5)
        .min(1)
        .sliderRange(1, 100)
        .build()
    );

    private final Setting<Integer> maxTasks = sgGeneral.add(new IntSetting.Builder()
        .name("max-tasks")
        .description("Maximum number of loops running at the same time.")
        .defaultValue(20)
        .min(1)
        .sliderRange(1, 100)
        .build()
    );

    public LoopPackets() {
        super(H4xolotAddon.CATEGORY, "loop-packets", "Repeats selected client-to-server packets every few ticks.");
    }

    @Override
    public void onActivate() {
        c2sMatcher.update(c2sPackets.get());
    }

    @Override
    public void onDeactivate() {
        Pipeline.INSTANCE.loops().stopAll();
    }

    @Override
    public Verdict decide(Direction dir, Packet<?> packet) {
        if (dir != Direction.C2S) return Verdict.PASS;
        return c2sMatcher.matches(packet.getClass()) ? Verdict.LOOP : Verdict.PASS;
    }

    @Override
    public void applied(Direction dir, Packet<?> packet, Verdict verdict, LogEntry entry) {
        startLoop(packet, loopCount.get(), loopInterval.get(), entry);
    }

    /** Starts a loop with this module's limits. Returns false when the task limit is reached. */
    public boolean startLoop(Packet<?> packet, int count, int interval, LogEntry source) {
        return Pipeline.INSTANCE.loops().start(packet, count, interval, source, replace.get(), maxTasks.get()) >= 0;
    }

    @EventHandler
    private void onTick(TickEvent.Pre event) {
        Pipeline.INSTANCE.runLoops(maxPerTick.get());
    }

    @Override
    public String getInfoString() {
        int n = Pipeline.INSTANCE.loops().size();
        return n == 0 ? null : "loops " + n;
    }

    @Override
    public WWidget getWidget(GuiTheme theme) {
        WVerticalList root = theme.verticalList();
        root.add(theme.button("Stop all loops")).expandX().widget().action = () ->
            info(Msg.esc("Stopped " + Pipeline.INSTANCE.loops().stopAll() + " loops."));
        return root;
    }
}
