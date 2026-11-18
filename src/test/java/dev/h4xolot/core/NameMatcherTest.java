package dev.h4xolot.core;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NameMatcherTest {
    /** Stand-ins for packet classes; only their names matter. */
    static class ServerboundContainerClickPacket {}
    static class ServerboundContainerButtonClickPacket {}
    static class ServerboundKeepAlivePacket {}
    static class Outer { static class Pos {} }

    @Test
    void emptyMatcherMatchesNothing() {
        assertFalse(new NameMatcher().matches(ServerboundContainerClickPacket.class));
        assertFalse(new NameMatcher(List.of(" ", "")).matches(ServerboundContainerClickPacket.class));
    }

    @Test
    void containsIsCaseInsensitive() {
        NameMatcher m = new NameMatcher(List.of("containerclick"));
        assertTrue(m.matches(ServerboundContainerClickPacket.class));
        assertFalse(m.matches(ServerboundContainerButtonClickPacket.class));
    }

    @Test
    void exactMatchesFullOrLeafName() {
        NameMatcher m = new NameMatcher(List.of("=ServerboundKeepAlivePacket"));
        assertTrue(m.matches(ServerboundKeepAlivePacket.class));
        assertFalse(m.matches(ServerboundContainerClickPacket.class));

        NameMatcher leaf = new NameMatcher(List.of("=Pos"));
        assertTrue(leaf.matches(Outer.Pos.class));
    }

    @Test
    void updateReplacesPatternsAndResetsCache() {
        NameMatcher m = new NameMatcher(List.of("KeepAlive"));
        assertTrue(m.matches(ServerboundKeepAlivePacket.class));

        m.update(List.of("ContainerClick"));
        assertFalse(m.matches(ServerboundKeepAlivePacket.class));
        assertTrue(m.matches(ServerboundContainerClickPacket.class));
    }
}
