package dev.h4xolot.modules;

import dev.h4xolot.H4xolotAddon;
import dev.h4xolot.core.ClickTools;
import dev.h4xolot.core.Msg;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Module;

/** One-shot action: sends the held packets in their original order, then drops the connection. */
public class DisconnectAndSend extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    private final Setting<Boolean> sendClicks = sgGeneral.add(new BoolSetting.Builder()
        .name("send-held-clicks")
        .description("Send the clicks held by Click Delay before disconnecting.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> sendDelayed = sgGeneral.add(new BoolSetting.Builder()
        .name("send-delayed-packets")
        .description("Send the client-to-server packets held by Delay Packets before disconnecting.")
        .defaultValue(true)
        .build()
    );

    public DisconnectAndSend() {
        super(H4xolotAddon.CATEGORY, "disconnect-and-send", "Sends the held packets in order and disconnects immediately. Runs once when enabled.");
    }

    @Override
    public void onActivate() {
        int n = ClickTools.disconnectAndSend(sendClicks.get(), sendDelayed.get());
        info(Msg.esc("Sent " + n + " packets and started the disconnect."));
        toggle();
    }
}
