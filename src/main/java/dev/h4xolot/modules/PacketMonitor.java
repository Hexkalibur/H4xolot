package dev.h4xolot.modules;

import dev.h4xolot.H4xolotAddon;
import dev.h4xolot.core.Action;
import dev.h4xolot.core.Direction;
import dev.h4xolot.core.LogEntry;
import dev.h4xolot.core.Msg;
import dev.h4xolot.core.NameMatcher;
import dev.h4xolot.core.PacketLog;
import dev.h4xolot.core.PacketRecorder;
import dev.h4xolot.core.Pipeline;
import meteordevelopment.meteorclient.MeteorClient;
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

import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

/** Records the packet stream into the shared log (HUD, commands, chat, file). */
public class PacketMonitor extends Module implements PacketRecorder {
    public enum CaptureMode { Managed, Selected, All }
    public enum ChatMode { Off, Managed, Logged }

    private final NameMatcher logC2sMatcher = new NameMatcher();
    private final NameMatcher logS2cMatcher = new NameMatcher();
    private final NameMatcher ignoreMatcher = new NameMatcher();

    private final SettingGroup sgCapture = settings.createGroup("Capture");
    private final SettingGroup sgOutput = settings.createGroup("Output");

    private final Setting<CaptureMode> capture = sgCapture.add(new EnumSetting.Builder<CaptureMode>()
        .name("capture")
        .description("Managed: only packets touched by h4xolot (blocked, delayed, looped). Selected: also the packets in the log lists. All: everything except ignored names.")
        .defaultValue(CaptureMode.All)
        .build()
    );

    private final Setting<List<String>> logC2s = sgCapture.add(new StringListSetting.Builder()
        .name("log-c2s")
        .description("Client-to-server packets to log in Selected mode. Pattern: part of the class name or =ExactName.")
        .onChanged(logC2sMatcher::update)
        .visible(() -> capture.get() == CaptureMode.Selected)
        .build()
    );

    private final Setting<List<String>> logS2c = sgCapture.add(new StringListSetting.Builder()
        .name("log-s2c")
        .description("Server-to-client packets to log in Selected mode. Pattern: part of the class name or =ExactName.")
        .onChanged(logS2cMatcher::update)
        .visible(() -> capture.get() == CaptureMode.Selected)
        .build()
    );

    private final Setting<List<String>> ignoreNames = sgCapture.add(new StringListSetting.Builder()
        .name("ignore-names")
        .description("In All mode, packets whose name contains one of these strings are not logged (does not affect block/delay/loop).")
        .defaultValue(List.of(
            "MovePlayer", "MoveEntity", "KeepAlive", "SetTime", "LevelChunk", "Light", "RotateHead",
            "TeleportEntity", "EntityPositionSync", "SetEntityMotion", "SetEntityData", "UpdateAttributes",
            "BundleDelimiter", "Ping", "ChunkBatch", "ForgetLevelChunk", "BlockUpdate", "SectionBlocksUpdate"
        ))
        .onChanged(ignoreMatcher::update)
        .visible(() -> capture.get() == CaptureMode.All)
        .build()
    );

    private final Setting<Integer> logSize = sgCapture.add(new IntSetting.Builder()
        .name("log-size")
        .description("How many entries to keep in the log buffer.")
        .defaultValue(500)
        .min(10)
        .sliderRange(50, 5000)
        .onChanged(PacketLog.INSTANCE::setCapacity)
        .build()
    );

    private final Setting<Boolean> keepS2cRefs = sgCapture.add(new BoolSetting.Builder()
        .name("keep-s2c-refs")
        .description("Keep references to logged S2C packets so they can be inspected (.h4xolot show). Uses more memory.")
        .defaultValue(false)
        .build()
    );

    private final Setting<ChatMode> chatLog = sgOutput.add(new EnumSetting.Builder<ChatMode>()
        .name("chat-log")
        .description("Print the log in chat.")
        .defaultValue(ChatMode.Off)
        .build()
    );

    private final Setting<Boolean> fileLog = sgOutput.add(new BoolSetting.Builder()
        .name("file-log")
        .description("Write the log live to meteor-client/h4xolot-logs while the module is active.")
        .defaultValue(false)
        .onChanged(on -> {
            if (!isActive()) return;
            if (on) openFile();
            else closeFile();
        })
        .build()
    );

    private final Object fileLock = new Object();
    private BufferedWriter fileWriter;

    public PacketMonitor() {
        super(H4xolotAddon.CATEGORY, "packet-monitor", "Logs the packet stream to the HUD, chat, a live file and the export commands.");
    }

    @Override
    public void onActivate() {
        logC2sMatcher.update(logC2s.get());
        logS2cMatcher.update(logS2c.get());
        ignoreMatcher.update(ignoreNames.get());
        PacketLog.INSTANCE.setCapacity(logSize.get());
        if (fileLog.get()) openFile();
    }

    @Override
    public void onDeactivate() {
        closeFile();
    }

    @EventHandler
    private void onTick(TickEvent.Pre event) {
        flushFile();
    }

    // --- PacketRecorder ---

    @Override
    public LogEntry record(Direction dir, Packet<?> packet, Action action, long tick) {
        if (!isActive()) return null;
        if (!shouldRecord(dir, packet.getClass(), action)) return null;

        LogEntry e = PacketLog.INSTANCE.add(dir, packet, action, tick, dir == Direction.C2S || keepS2cRefs.get());
        emit(e);
        return e;
    }

    private boolean shouldRecord(Direction dir, Class<?> type, Action action) {
        if (action != Action.PASS) return true;
        return switch (capture.get()) {
            case Managed -> false;
            case Selected -> (dir == Direction.C2S ? logC2sMatcher : logS2cMatcher).matches(type);
            case All -> !ignoreMatcher.matches(type);
        };
    }

    @Override
    public void emit(LogEntry entry) {
        ChatMode cm = chatLog.get();
        if (isActive() && (cm == ChatMode.Logged || (cm == ChatMode.Managed && entry.action != Action.PASS))) {
            String text = entry.line(false, false);
            mc.execute(() -> info(Msg.esc(text)));
        }
        writeFile(entry.fileLine());
    }

    // --- widget ---

    @Override
    public WWidget getWidget(GuiTheme theme) {
        WVerticalList root = theme.verticalList();
        WHorizontalList row = root.add(theme.horizontalList()).expandX().widget();

        row.add(theme.button("Clear log")).expandX().widget().action = () -> {
            PacketLog.INSTANCE.clear();
            info("Log cleared.");
        };
        row.add(theme.button("Export text")).expandX().widget().action = () -> export(PacketLog.ExportFormat.TEXT);
        row.add(theme.button("Export JSON")).expandX().widget().action = () -> export(PacketLog.ExportFormat.JSON);

        return root;
    }

    private void export(PacketLog.ExportFormat format) {
        try {
            File f = PacketLog.INSTANCE.export(logDir(), "export-" + timestamp(), format);
            info(Msg.esc("Log exported: " + f.getAbsolutePath()));
        } catch (IOException e) {
            error(Msg.esc("Export failed: " + e.getMessage()));
        }
    }

    // --- file logging ---

    public static File logDir() {
        return new File(MeteorClient.FOLDER.toString(), "h4xolot-logs");
    }

    public static String timestamp() {
        return LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"));
    }

    private void openFile() {
        synchronized (fileLock) {
            if (fileWriter != null) return;
            try {
                File dir = logDir();
                Files.createDirectories(dir.toPath());
                File f = new File(dir, "session-" + timestamp() + ".log");
                fileWriter = Files.newBufferedWriter(f.toPath(), StandardCharsets.UTF_8);
                info(Msg.esc("Logging to file: " + f.getAbsolutePath()));
            } catch (IOException e) {
                fileWriter = null;
                error(Msg.esc("Could not open the log file: " + e.getMessage()));
            }
        }
    }

    private void writeFile(String line) {
        synchronized (fileLock) {
            if (fileWriter == null) return;
            try {
                fileWriter.write(line);
                fileWriter.newLine();
            } catch (IOException e) {
                closeFile();
            }
        }
    }

    private void flushFile() {
        synchronized (fileLock) {
            if (fileWriter == null) return;
            try {
                fileWriter.flush();
            } catch (IOException e) {
                closeFile();
            }
        }
    }

    private void closeFile() {
        synchronized (fileLock) {
            if (fileWriter == null) return;
            try {
                fileWriter.close();
            } catch (IOException ignored) {
                // nothing to do
            }
            fileWriter = null;
        }
    }
}
