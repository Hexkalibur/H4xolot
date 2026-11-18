package dev.h4xolot.modules;

import dev.h4xolot.H4xolotAddon;
import dev.h4xolot.core.ClickKind;
import dev.h4xolot.core.ClickTools;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.EnumSetting;
import meteordevelopment.meteorclient.settings.IntSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Module;
import net.minecraft.world.item.ItemStack;

/** One-shot action: builds a container click packet from the settings and sends it. */
public class FabricateClick extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final SettingGroup sgIds = settings.createGroup("Container");

    private final Setting<Integer> slot = sgGeneral.add(new IntSetting.Builder()
        .name("slot")
        .description("Slot number to click (-999 = outside the window).")
        .defaultValue(0)
        .range(-999, 999)
        .sliderRange(-1, 100)
        .build()
    );

    private final Setting<Integer> button = sgGeneral.add(new IntSetting.Builder()
        .name("button")
        .description("Button number (0 = left, 1 = right, ...).")
        .defaultValue(0)
        .range(0, 64)
        .sliderRange(0, 8)
        .build()
    );

    private final Setting<ClickKind> kind = sgGeneral.add(new EnumSetting.Builder<ClickKind>()
        .name("type")
        .description("Click type.")
        .defaultValue(ClickKind.Pickup)
        .build()
    );

    private final Setting<Integer> times = sgGeneral.add(new IntSetting.Builder()
        .name("times")
        .description("How many copies of the packet to send.")
        .defaultValue(1)
        .range(1, 100)
        .sliderRange(1, 20)
        .build()
    );

    private final Setting<Boolean> useCurrentMenu = sgIds.add(new BoolSetting.Builder()
        .name("use-current-menu")
        .description("Take container id, state id and carried item from the menu that is currently open.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Integer> containerId = sgIds.add(new IntSetting.Builder()
        .name("container-id")
        .description("Container id to put in the packet (0 = player inventory).")
        .defaultValue(0)
        .range(0, 255)
        .sliderRange(0, 20)
        .visible(() -> !useCurrentMenu.get())
        .build()
    );

    private final Setting<Integer> stateId = sgIds.add(new IntSetting.Builder()
        .name("state-id")
        .description("State id (menu revision) to put in the packet.")
        .defaultValue(0)
        .min(0)
        .sliderRange(0, 100)
        .visible(() -> !useCurrentMenu.get())
        .build()
    );

    public FabricateClick() {
        super(H4xolotAddon.CATEGORY, "fabricate-click", "Builds and sends a custom container click packet. Runs once when enabled.");
    }

    @Override
    public void onActivate() {
        boolean ok = true;
        for (int i = 0; i < times.get() && ok; i++) {
            ok = useCurrentMenu.get()
                ? ClickTools.fabricateFromCurrent(slot.get(), button.get(), kind.get())
                : ClickTools.fabricate(containerId.get(), stateId.get(), slot.get(), button.get(), kind.get(), ItemStack.EMPTY);
        }

        if (ok) info("Sent " + times.get() + "x " + kind.get() + " click on slot " + slot.get() + ".");
        else error("Could not build or send the click packet (no menu open, or no usable constructor).");
        toggle();
    }
}
