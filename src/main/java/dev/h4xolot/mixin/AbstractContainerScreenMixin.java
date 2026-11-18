package dev.h4xolot.mixin;

import dev.h4xolot.core.ClickTools;
import dev.h4xolot.core.Direction;
import dev.h4xolot.core.HeldQueue;
import dev.h4xolot.core.Pipeline;
import dev.h4xolot.modules.ClickDelay;
import dev.h4xolot.modules.ContainerButtons;
import meteordevelopment.meteorclient.systems.modules.Modules;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Adds the h4xolot buttons to every container screen (chest, hopper, inventory, ...) while the
 * Container Buttons module is enabled. Extends Screen so that addRenderableWidget (protected) can be called.
 */
@Mixin(AbstractContainerScreen.class)
public abstract class AbstractContainerScreenMixin extends Screen {
    @Unique private static final int H4X_WIDTH = 110;
    @Unique private static final int H4X_HEIGHT = 16;
    @Unique private static final int H4X_GAP = 2;

    protected AbstractContainerScreenMixin(Component title) {
        super(title);
    }

    @Inject(method = "init", at = @At("TAIL"))
    private void h4xolot$addButtons(CallbackInfo ci) {
        Modules modules = Modules.get();
        if (modules == null) return;

        ContainerButtons cb = modules.get(ContainerButtons.class);
        if (cb == null || !cb.isActive()) return;

        int index = 0;

        if (cb.showClose()) {
            h4xolot$add(index++, "Close w/o packet", b -> {
                if (ClickTools.closeWithoutPacket()) cb.feedback("Screen closed on the client; no close packet sent.");
            });
        }

        if (cb.showDelay()) {
            ClickDelay delay = modules.get(ClickDelay.class);
            if (delay != null) {
                h4xolot$add(index++, h4xolot$delayLabel(delay), b -> {
                    delay.toggle();
                    b.setMessage(Component.literal(h4xolot$delayLabel(delay)));
                });
            }
        }

        if (cb.showFlush()) {
            h4xolot$add(index++, "Flush Clicks", b ->
                cb.feedback("Sent " + Pipeline.INSTANCE.flush(Direction.C2S, HeldQueue.Tag.CLICK) + " held clicks."));
        }

        if (cb.showDrop()) {
            h4xolot$add(index++, "Drop Clicks", b ->
                cb.feedback("Dropped " + Pipeline.INSTANCE.drop(Direction.C2S, HeldQueue.Tag.CLICK) + " held clicks."));
        }

        if (cb.showDisconnect()) {
            h4xolot$add(index, "Disconnect+Send", b ->
                cb.feedback("Sent " + ClickTools.disconnectAndSend(true, true) + " packets and disconnected."));
        }
    }

    @Unique
    private static String h4xolot$delayLabel(ClickDelay delay) {
        return delay.isActive() ? "Click Delay: ON" : "Click Delay: OFF";
    }

    /** Places buttons in a two-column grid in the top-left corner of the screen. */
    @Unique
    private void h4xolot$add(int index, String label, Button.OnPress action) {
        int col = index % 2;
        int row = index / 2;
        int x = 2 + col * (H4X_WIDTH + H4X_GAP);
        int y = 2 + row * (H4X_HEIGHT + H4X_GAP);

        this.addRenderableWidget(Button.builder(Component.literal(label), action)
            .bounds(x, y, H4X_WIDTH, H4X_HEIGHT)
            .build());
    }
}
