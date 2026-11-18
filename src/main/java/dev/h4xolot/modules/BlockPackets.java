package dev.h4xolot.modules;

import dev.h4xolot.H4xolotAddon;
import dev.h4xolot.core.Direction;
import dev.h4xolot.core.NameMatcher;
import dev.h4xolot.core.PacketStage;
import dev.h4xolot.core.Verdict;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.settings.StringListSetting;
import meteordevelopment.meteorclient.systems.modules.Module;
import net.minecraft.network.protocol.Packet;

import java.util.List;

/** Cancels the selected packets before they are sent or handled. Highest priority in the pipeline. */
public class BlockPackets extends Module implements PacketStage {
    private final NameMatcher c2sMatcher = new NameMatcher();
    private final NameMatcher s2cMatcher = new NameMatcher();

    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    private final Setting<List<String>> c2sPackets = sgGeneral.add(new StringListSetting.Builder()
        .name("c2s-packets")
        .description("Client-to-server packets to cancel. Pattern: part of the class name (e.g. ContainerClick) or =ExactName.")
        .onChanged(c2sMatcher::update)
        .build()
    );

    private final Setting<List<String>> s2cPackets = sgGeneral.add(new StringListSetting.Builder()
        .name("s2c-packets")
        .description("Server-to-client packets to cancel. Pattern: part of the class name or =ExactName.")
        .onChanged(s2cMatcher::update)
        .build()
    );

    public BlockPackets() {
        super(H4xolotAddon.CATEGORY, "block-packets", "Cancels the selected packets (client-to-server and server-to-client).");
    }

    @Override
    public void onActivate() {
        c2sMatcher.update(c2sPackets.get());
        s2cMatcher.update(s2cPackets.get());
    }

    @Override
    public Verdict decide(Direction dir, Packet<?> packet) {
        NameMatcher m = dir == Direction.C2S ? c2sMatcher : s2cMatcher;
        return m.matches(packet.getClass()) ? Verdict.BLOCK : Verdict.PASS;
    }
}
