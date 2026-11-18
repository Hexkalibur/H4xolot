package dev.h4xolot.core;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class JsonUtilTest {
    @Test
    void quotesAndEscapes() {
        assertEquals("\"a\\\"b\\\\c\\n\"", JsonUtil.quote("a\"b\\c\n"));
        assertEquals("\"\\u0001\"", JsonUtil.quote("\u0001"));
        assertEquals("\"\"", JsonUtil.quote(""));
    }
}
