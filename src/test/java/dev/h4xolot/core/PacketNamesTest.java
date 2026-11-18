package dev.h4xolot.core;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PacketNamesTest {
    static class Outer { static class Inner {} }

    @Test
    void nestedClassesUseDots() {
        assertEquals("PacketNamesTest.Outer.Inner", PacketNames.of(Outer.Inner.class));
    }

    @Test
    void shortNameStripsPrefixAndSuffix() {
        assertEquals("ContainerClick", PacketNames.shortName("ServerboundContainerClickPacket"));
        assertEquals("SetTime", PacketNames.shortName("ClientboundSetTimePacket"));
    }
}
