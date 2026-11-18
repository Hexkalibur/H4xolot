package dev.h4xolot.modules;

import dev.h4xolot.H4xolotAddon;
import dev.h4xolot.core.Msg;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Module;

/**
 * Shows h4xolot buttons inside container screens (chests, inventory, ...). Chat and the Meteor GUI
 * cannot be opened while such a screen is up, so this is how you reach the tools mid-screen.
 * The buttons are added by AbstractContainerScreenMixin.
 */
public class ContainerButtons extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    private final Setting<Boolean> showClose = sgGeneral.add(new BoolSetting.Builder()
        .name("close-without-packet")
        .description("Show the Close without packet button.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> showDelay = sgGeneral.add(new BoolSetting.Builder()
        .name("click-delay")
        .description("Show the button that toggles the Click Delay module.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> showFlush = sgGeneral.add(new BoolSetting.Builder()
        .name("flush-clicks")
        .description("Show the Flush Clicks button.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> showDrop = sgGeneral.add(new BoolSetting.Builder()
        .name("drop-clicks")
        .description("Show the Drop Clicks button.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> showDisconnect = sgGeneral.add(new BoolSetting.Builder()
        .name("disconnect-and-send")
        .description("Show the Disconnect+Send button.")
        .defaultValue(true)
        .build()
    );

    public ContainerButtons() {
        super(H4xolotAddon.CATEGORY, "container-buttons", "Adds h4xolot buttons to container screens (close without packet, click delay, flush, disconnect+send).");
    }

    public boolean showClose() { return showClose.get(); }

    public boolean showDelay() { return showDelay.get(); }

    public boolean showFlush() { return showFlush.get(); }

    public boolean showDrop() { return showDrop.get(); }

    public boolean showDisconnect() { return showDisconnect.get(); }

    /** Chat feedback for button presses. */
    public void feedback(String text) {
        info(Msg.esc(text));
    }
}
