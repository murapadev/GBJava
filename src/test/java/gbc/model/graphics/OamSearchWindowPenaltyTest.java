package gbc.model.graphics;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

import gbc.model.memory.Memory;

class OamSearchWindowPenaltyTest {

    @Test
    void wxZeroWithScxMisalignedReducesWindowPenaltyByOneDot() {
        TestMemory memory = new TestMemory();
        memory.lcdc = 0xA1; // LCD on, BG on, window on
        memory.ly = 0;
        memory.wy = 0;
        memory.scx = 3;

        OamSearch search = new OamSearch(memory);

        memory.wx = 0;
        search.searchSprites(memory.ly);
        assertEquals(181, search.getMode3Duration(),
                "WX=0 with SCX%8!=0 should use 5-dot window penalty");

        memory.wx = 7;
        search.searchSprites(memory.ly);
        assertEquals(182, search.getMode3Duration(),
                "Regular window start should keep the 6-dot penalty");
    }

    @Test
    void windowOffscreenDoesNotAddPenalty() {
        TestMemory memory = new TestMemory();
        memory.lcdc = 0xA1; // LCD on, BG on, window on
        memory.ly = 0;
        memory.wy = 0;
        memory.scx = 5;
        memory.wx = 167; // window start at x=160 -> fully off-screen

        OamSearch search = new OamSearch(memory);
        search.searchSprites(memory.ly);

        assertEquals(180, search.getMode3Duration(),
                "Off-screen window should only apply SCX fine-scroll penalty");
    }

    private static final class TestMemory extends Memory {
        int lcdc = 0x91;
        int scx;
        int ly;
        int wy;
        int wx = 7;

        @Override
        public int getLcdc() {
            return lcdc & 0xFF;
        }

        @Override
        public int getScx() {
            return scx & 0xFF;
        }

        @Override
        public int getLy() {
            return ly & 0xFF;
        }

        @Override
        public int getWy() {
            return wy & 0xFF;
        }

        @Override
        public int getWx() {
            return wx & 0xFF;
        }
    }
}
