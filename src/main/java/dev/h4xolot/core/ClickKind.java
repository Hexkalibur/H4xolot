package dev.h4xolot.core;

import net.minecraft.world.inventory.ContainerInput;

import java.util.Locale;

/** Container click types, with friendly names for settings and commands. */
public enum ClickKind {
    Pickup(ContainerInput.PICKUP),
    QuickMove(ContainerInput.QUICK_MOVE),
    Swap(ContainerInput.SWAP),
    Clone(ContainerInput.CLONE),
    Throw(ContainerInput.THROW),
    QuickCraft(ContainerInput.QUICK_CRAFT),
    PickupAll(ContainerInput.PICKUP_ALL);

    public final ContainerInput input;

    ClickKind(ContainerInput input) {
        this.input = input;
    }

    /** Accepts names like "pickup", "quick_move", "shift", "quickcraft", "pickup_all". Returns null if unknown. */
    public static ClickKind parse(String s) {
        return switch (s.toLowerCase(Locale.ROOT).replace("-", "_")) {
            case "pickup", "pick" -> Pickup;
            case "quick_move", "quickmove", "shift" -> QuickMove;
            case "swap" -> Swap;
            case "clone" -> Clone;
            case "throw" -> Throw;
            case "quick_craft", "quickcraft" -> QuickCraft;
            case "pickup_all", "pickupall" -> PickupAll;
            default -> null;
        };
    }
}
