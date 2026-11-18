package dev.h4xolot;

import com.mojang.logging.LogUtils;
import dev.h4xolot.commands.H4xolotCommand;
import dev.h4xolot.core.Pipeline;
import dev.h4xolot.hud.PacketLogHud;
import dev.h4xolot.modules.BlockPackets;
import dev.h4xolot.modules.ClickDelay;
import dev.h4xolot.modules.CloseWithoutPacket;
import dev.h4xolot.modules.ContainerButtons;
import dev.h4xolot.modules.DelayPackets;
import dev.h4xolot.modules.DisconnectAndSend;
import dev.h4xolot.modules.FabricateClick;
import dev.h4xolot.modules.LoopPackets;
import dev.h4xolot.modules.PacketMonitor;
import meteordevelopment.meteorclient.MeteorClient;
import meteordevelopment.meteorclient.addons.GithubRepo;
import meteordevelopment.meteorclient.addons.MeteorAddon;
import meteordevelopment.meteorclient.commands.Commands;
import meteordevelopment.meteorclient.systems.hud.Hud;
import meteordevelopment.meteorclient.systems.hud.HudGroup;
import meteordevelopment.meteorclient.systems.modules.Category;
import meteordevelopment.meteorclient.systems.modules.Modules;
import org.slf4j.Logger;

public class H4xolotAddon extends MeteorAddon {
    public static final Logger LOG = LogUtils.getLogger();
    public static final Category CATEGORY = new Category("H4xolot");
    public static final HudGroup HUD_GROUP = new HudGroup("H4xolot");

    @Override
    public void onInitialize() {
        LOG.info("Loading h4xolot");

        BlockPackets block = new BlockPackets();
        ClickDelay clickDelay = new ClickDelay();
        DelayPackets delay = new DelayPackets();
        LoopPackets loop = new LoopPackets();
        PacketMonitor monitor = new PacketMonitor();

        // Stage order = priority: Block > Click Delay > Delay > Loop.
        Pipeline pipeline = Pipeline.INSTANCE;
        pipeline.addStage(block);
        pipeline.addStage(clickDelay);
        pipeline.addStage(delay);
        pipeline.addStage(loop);
        pipeline.setRecorder(monitor);
        MeteorClient.EVENT_BUS.subscribe(pipeline);

        Modules modules = Modules.get();
        modules.add(block);
        modules.add(delay);
        modules.add(loop);
        modules.add(monitor);
        modules.add(clickDelay);
        modules.add(new ContainerButtons());
        modules.add(new CloseWithoutPacket());
        modules.add(new DisconnectAndSend());
        modules.add(new FabricateClick());

        Commands.add(new H4xolotCommand());
        Hud.get().register(PacketLogHud.INFO);
    }

    @Override
    public void onRegisterCategories() {
        Modules.registerCategory(CATEGORY);
    }

    @Override
    public String getPackage() {
        return "dev.h4xolot";
    }

    @Override
    public GithubRepo getRepo() {
        return new GithubRepo("TenebrisInLux", "h4xolot");
    }
}
