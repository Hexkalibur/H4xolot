package dev.h4xolot.modules;

import dev.h4xolot.H4xolotAddon;
import dev.h4xolot.core.ClickTools;
import dev.h4xolot.core.Direction;
import dev.h4xolot.core.DisableMode;
import dev.h4xolot.core.HeldQueue;
import dev.h4xolot.core.Msg;
import dev.h4xolot.core.Overflow;
import dev.h4xolot.core.PacketStage;
import dev.h4xolot.core.Pipeline;
import dev.h4xolot.core.Verdict;
import meteordevelopment.meteorclient.gui.GuiTheme;
import meteordevelopment.meteorclient.gui.widgets.WWidget;
import meteordevelopment.meteorclient.gui.widgets.containers.WHorizontalList;
import meteordevelopment.meteorclient.gui.widgets.containers.WVerticalList;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.EnumSetting;
import meteordevelopment.meteorclient.settings.IntSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Module;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ServerboundContainerButtonClickPacket;
import net.minecraft.network.protocol.game.ServerboundContainerClickPacket;

/**
 * While enabled, container click packets are held instead of being sent. Release them with the
 * Flush button / command, or send them right before dropping the connection with Disconnect And Send.
 */
public class ClickDelay extends Module implements PacketStage {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    private final Setting<Boolean> includeButtonClicks = sgGeneral.add(new BoolSetting.Builder()
        .name("include-button-clicks")
        .description("Also hold container button clicks (enchanting table, stonecutter, ...).")
        .defaultValue(true)
        .build()
    );

    private final Setting<Integer> queueLimit = sgGeneral.add(new IntSetting.Builder()
        .name("queue-limit")
        .description("Maximum number of held clicks.")
        .defaultValue(256)
        .min(1)
        .sliderRange(10, 2000)
        .build()
    );

    private final Setting<Overflow> overflow = sgGeneral.add(new EnumSetting.Builder<Overflow>()
        .name("on-full")
        .description("ReleaseOldest sends the oldest held click to make room; PassThrough sends the new click immediately (may reorder).")
        .defaultValue(Overflow.ReleaseOldest)
        .build()
    );

    private final Setting<DisableMode> disableMode = sgGeneral.add(new EnumSetting.Builder<DisableMode>()
        .name("on-disable")
        .description("What to do with held clicks when the module is disabled.")
        .defaultValue(DisableMode.Flush)
        .build()
    );

    public ClickDelay() {
        super(H4xolotAddon.CATEGORY, "click-delay", "Holds container click packets until you flush them or disconnect.");
    }

    @Override
    public void onDeactivate() {
        switch (disableMode.get()) {
            case Flush -> Pipeline.INSTANCE.flush(Direction.C2S, HeldQueue.Tag.CLICK);
            case Drop -> Pipeline.INSTANCE.drop(Direction.C2S, HeldQueue.Tag.CLICK);
            case Keep -> {}
        }
    }

    @Override
    public Verdict decide(Direction dir, Packet<?> packet) {
        if (dir != Direction.C2S || !isClickRelated(packet)) return Verdict.PASS;

        Pipeline p = Pipeline.INSTANCE;
        if (p.heldCount(Direction.C2S, HeldQueue.Tag.CLICK) >= queueLimit.get()) {
            if (overflow.get() == Overflow.PassThrough) return Verdict.PASS;
            p.releaseOldest(Direction.C2S, HeldQueue.Tag.CLICK);
        }
        return Verdict.delay(HeldQueue.Tag.CLICK, Long.MAX_VALUE);
    }

    private boolean isClickRelated(Packet<?> packet) {
        return packet instanceof ServerboundContainerClickPacket
            || (includeButtonClicks.get() && packet instanceof ServerboundContainerButtonClickPacket);
    }

    @Override
    public String getInfoString() {
        int n = Pipeline.INSTANCE.heldCount(Direction.C2S, HeldQueue.Tag.CLICK);
        return n == 0 ? null : "clicks " + n;
    }

    @Override
    public WWidget getWidget(GuiTheme theme) {
        WVerticalList root = theme.verticalList();

        WHorizontalList row1 = root.add(theme.horizontalList()).expandX().widget();
        row1.add(theme.button("Flush clicks")).expandX().widget().action = () ->
            info(Msg.esc("Sent " + Pipeline.INSTANCE.flush(Direction.C2S, HeldQueue.Tag.CLICK) + " held clicks."));
        row1.add(theme.button("Drop clicks")).expandX().widget().action = () ->
            info(Msg.esc("Dropped " + Pipeline.INSTANCE.drop(Direction.C2S, HeldQueue.Tag.CLICK) + " held clicks."));

        WHorizontalList row2 = root.add(theme.horizontalList()).expandX().widget();
        row2.add(theme.button("Close without packet")).expandX().widget().action = () -> {
            if (ClickTools.closeWithoutPacket()) info("Screen closed on the client; no close packet sent.");
            else warning("No screen is open.");
        };
        row2.add(theme.button("Disconnect + send")).expandX().widget().action = () ->
            info(Msg.esc("Sent " + ClickTools.disconnectAndSend(true, true) + " packets and disconnected."));

        return root;
    }
}
