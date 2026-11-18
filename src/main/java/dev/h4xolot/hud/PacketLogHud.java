package dev.h4xolot.hud;

import dev.h4xolot.H4xolotAddon;
import dev.h4xolot.core.Action;
import dev.h4xolot.core.Direction;
import dev.h4xolot.core.HeldQueue;
import dev.h4xolot.core.LogEntry;
import dev.h4xolot.core.PacketLog;
import dev.h4xolot.core.Pipeline;
import dev.h4xolot.modules.PacketMonitor;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.ColorSetting;
import meteordevelopment.meteorclient.settings.IntSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.hud.HudElement;
import meteordevelopment.meteorclient.systems.hud.HudElementInfo;
import meteordevelopment.meteorclient.systems.hud.HudRenderer;
import meteordevelopment.meteorclient.systems.modules.Modules;
import meteordevelopment.meteorclient.utils.render.color.Color;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;

import java.util.ArrayList;
import java.util.List;

/** Live packet log with packet rates and queue / loop status. */
public class PacketLogHud extends HudElement {
    public static final HudElementInfo<PacketLogHud> INFO = new HudElementInfo<>(
        H4xolotAddon.HUD_GROUP, "packet-log", "Live packet log.", PacketLogHud::new
    );

    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final SettingGroup sgColors = settings.createGroup("Colors");

    private final Setting<Integer> lines = sgGeneral.add(new IntSetting.Builder()
        .name("lines").description("How many lines to show.").defaultValue(12).min(1).sliderRange(1, 40).build());
    private final Setting<Boolean> showC2s = sgGeneral.add(new BoolSetting.Builder()
        .name("show-c2s").description("Show client-to-server packets.").defaultValue(true).build());
    private final Setting<Boolean> showS2c = sgGeneral.add(new BoolSetting.Builder()
        .name("show-s2c").description("Show server-to-client packets.").defaultValue(true).build());
    private final Setting<Boolean> showPass = sgGeneral.add(new BoolSetting.Builder()
        .name("show-pass").description("Also show packets that passed untouched.").defaultValue(true).build());
    private final Setting<Boolean> shortNames = sgGeneral.add(new BoolSetting.Builder()
        .name("short-names").description("Compact names (no Serverbound/Clientbound/Packet).").defaultValue(true).build());
    private final Setting<Boolean> details = sgGeneral.add(new BoolSetting.Builder()
        .name("details").description("Show packet fields (when the reference is kept).").defaultValue(false).build());
    private final Setting<Boolean> stats = sgGeneral.add(new BoolSetting.Builder()
        .name("stats").description("Header lines with packets per second, queues and loops.").defaultValue(true).build());
    private final Setting<Boolean> background = sgGeneral.add(new BoolSetting.Builder()
        .name("background").description("Translucent background.").defaultValue(true).build());

    private final Setting<SettingColor> bgColor = sgColors.add(new ColorSetting.Builder()
        .name("background-color").defaultValue(new SettingColor(0, 0, 0, 110)).build());
    private final Setting<SettingColor> c2sColor = sgColors.add(new ColorSetting.Builder()
        .name("c2s-color").defaultValue(new SettingColor(130, 190, 255)).build());
    private final Setting<SettingColor> s2cColor = sgColors.add(new ColorSetting.Builder()
        .name("s2c-color").defaultValue(new SettingColor(150, 230, 150)).build());
    private final Setting<SettingColor> blockedColor = sgColors.add(new ColorSetting.Builder()
        .name("blocked-color").defaultValue(new SettingColor(255, 90, 90)).build());
    private final Setting<SettingColor> delayedColor = sgColors.add(new ColorSetting.Builder()
        .name("delayed-color").defaultValue(new SettingColor(255, 210, 80)).build());
    private final Setting<SettingColor> loopColor = sgColors.add(new ColorSetting.Builder()
        .name("loop-color").defaultValue(new SettingColor(200, 130, 255)).build());
    private final Setting<SettingColor> replayColor = sgColors.add(new ColorSetting.Builder()
        .name("replay-color").defaultValue(new SettingColor(90, 230, 230)).build());

    public PacketLogHud() {
        super(INFO);
    }

    @Override
    public void render(HudRenderer renderer) {
        List<String> texts = new ArrayList<>();
        List<Color> colors = new ArrayList<>();

        if (stats.get()) {
            PacketMonitor monitor = Modules.get().get(PacketMonitor.class);
            Pipeline p = Pipeline.INSTANCE;

            if (monitor == null || !monitor.isActive()) {
                texts.add("H4xolot - Packet Monitor is off");
                colors.add(Color.GRAY);
            } else {
                texts.add("H4xolot - C2S " + PacketLog.INSTANCE.pps(Direction.C2S) + "/s"
                    + "  S2C " + PacketLog.INSTANCE.pps(Direction.S2C) + "/s");
                colors.add(Color.WHITE);
            }

            int c2sHeld = p.heldCount(Direction.C2S, HeldQueue.Tag.DELAY);
            int s2cHeld = p.heldCount(Direction.S2C, HeldQueue.Tag.DELAY);
            int clicks = p.heldCount(Direction.C2S, HeldQueue.Tag.CLICK);
            int loops = p.loops().size();

            texts.add("held C2S/S2C " + c2sHeld + "/" + s2cHeld + "  clicks " + clicks + "  loops " + loops);
            colors.add(c2sHeld + s2cHeld + clicks + loops > 0 ? Color.YELLOW : Color.GRAY);
        }

        boolean c2s = showC2s.get();
        boolean s2c = showS2c.get();
        boolean pass = showPass.get();

        List<LogEntry> entries = PacketLog.INSTANCE.last(lines.get(),
            e -> (e.dir == Direction.C2S ? c2s : s2c) && (pass || e.action != Action.PASS));

        for (LogEntry e : entries) {
            texts.add(e.line(shortNames.get(), details.get()));
            colors.add(colorFor(e));
        }

        if (texts.isEmpty()) {
            texts.add("No packets");
            colors.add(Color.GRAY);
        }

        double lineHeight = renderer.textHeight(true) + 2;
        double width = 180;
        for (String s : texts) width = Math.max(width, renderer.textWidth(s, true));

        setSize(width + 4, texts.size() * lineHeight + 4);

        if (background.get()) renderer.quad(x, y, getWidth(), getHeight(), bgColor.get());

        double yy = y + 2;
        for (int i = 0; i < texts.size(); i++) {
            renderer.text(texts.get(i), x + 2, yy, colors.get(i), true);
            yy += lineHeight;
        }
    }

    private Color colorFor(LogEntry e) {
        return switch (e.action) {
            case BLOCKED -> blockedColor.get();
            case DELAYED -> delayedColor.get();
            case LOOPED -> loopColor.get();
            case REPLAYED -> replayColor.get();
            case PASS -> e.dir == Direction.C2S ? c2sColor.get() : s2cColor.get();
        };
    }
}
