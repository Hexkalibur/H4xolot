package dev.h4xolot.modules;

import dev.h4xolot.H4xolotAddon;
import dev.h4xolot.core.ClickTools;
import meteordevelopment.meteorclient.systems.modules.Module;

/** One-shot action: closes the open container screen on the client only, without sending the close packet. */
public class CloseWithoutPacket extends Module {
    public CloseWithoutPacket() {
        super(H4xolotAddon.CATEGORY, "close-without-packet", "Closes the container screen locally without telling the server. Runs once when enabled.");
    }

    @Override
    public void onActivate() {
        if (ClickTools.closeWithoutPacket()) info("Screen closed on the client; no close packet sent.");
        else warning("No screen is open.");
        toggle();
    }
}
