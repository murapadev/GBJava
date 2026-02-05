package gbc.model.memory;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class DmaControllerTest {

    @Test
    void dmaStartupDelayThenCopies160Bytes() {
        DmaController dma = new DmaController();
        byte[] oam = new byte[160];
        dma.setOam(oam);
        dma.setOamReader(address -> address & 0xFF);

        dma.startDma(0xC0);

        assertTrue(dma.isDmaActive(), "DMA should be active after start");
        assertFalse(dma.isBusLocked(0x8000), "Bus should not lock during initial startup delay");

        for (int i = 0; i < 8; i++) {
            dma.stepOamDma();
        }

        assertTrue(dma.isBusLocked(0x8000), "Bus should lock once startup delay is over");
        assertFalse(dma.isBusLocked(0xFF80), "HRAM/IO region should stay accessible");

        // 160 bytes, each transferred every 4 T-cycles.
        for (int i = 0; i < 160 * 4; i++) {
            dma.stepOamDma();
        }

        assertFalse(dma.isDmaActive(), "DMA should end after 160 bytes");
        assertEquals(0x00, oam[0] & 0xFF, "First transferred byte");
        assertEquals(0x9F, oam[159] & 0xFF, "Last transferred byte");
    }

    @Test
    void restartingActiveDmaKeepsBusLockedDuringNewStartup() {
        DmaController dma = new DmaController();
        byte[] oam = new byte[160];
        dma.setOam(oam);
        dma.setOamReader(address -> address & 0xFF);

        dma.startDma(0xC0);
        for (int i = 0; i < 8; i++) {
            dma.stepOamDma();
        }
        assertTrue(dma.isBusLocked(0xC000), "Bus should be locked during active DMA");

        dma.startDma(0xD0); // restart while active
        assertTrue(dma.isBusLocked(0xC000), "Restarted DMA should keep bus locked during startup");

        for (int i = 0; i < 8 + (160 * 4); i++) {
            dma.stepOamDma();
        }

        assertFalse(dma.isDmaActive(), "Restarted DMA should complete");
        assertEquals(0x00, oam[0] & 0xFF, "Restart should copy from new source base");
        assertEquals(0x9F, oam[159] & 0xFF, "Restart should copy full OAM block");
    }

    @Test
    void hblankDmaTransfersOnlyOnHblankSteps() {
        DmaController dma = new DmaController();
        byte[] vram = new byte[0x2000];
        dma.setHdmaReader(address -> address & 0xFF);
        dma.setVramWriter((address, value) -> vram[address - 0x8000] = value);

        dma.setHdmaSrcHigh(0xC0);
        dma.setHdmaSrcLow(0x00);
        dma.setHdmaDstHigh(0x00);
        dma.setHdmaDstLow(0x00);

        dma.handleHdmaStart(0x80, true); // 1 block, HBlank mode
        assertTrue(dma.isHdmaActive(), "HBlank HDMA should remain active after start");
        assertTrue(dma.isHdmaHblank(), "HDMA mode should be HBlank");
        assertEquals(0x00, dma.readHdma5(), "HDMA5 should report one block remaining");
        assertEquals(0x00, vram[0] & 0xFF, "No data copied before HBlank step");

        dma.stepHdmaBlock();

        assertFalse(dma.isHdmaActive(), "HBlank HDMA should complete after one block");
        assertEquals(0x00, vram[0] & 0xFF, "First byte should be copied");
        assertEquals(0x0F, vram[0x0F] & 0xFF, "Last byte of the block should be copied");
    }

    @Test
    void writingBit7ClearStopsActiveHblankDma() {
        DmaController dma = new DmaController();
        byte[] vram = new byte[0x2000];
        dma.setHdmaReader(address -> address & 0xFF);
        dma.setVramWriter((address, value) -> vram[address - 0x8000] = value);

        dma.setHdmaSrcHigh(0xC0);
        dma.setHdmaSrcLow(0x00);
        dma.setHdmaDstHigh(0x00);
        dma.setHdmaDstLow(0x00);

        dma.handleHdmaStart(0x81, true); // 2 blocks
        dma.stepHdmaBlock();             // complete first block
        assertEquals(0x00, dma.readHdma5(), "One block should remain");

        dma.handleHdmaStart(0x00, true); // stop active HBlank HDMA

        assertFalse(dma.isHdmaActive(), "HDMA should stop on write with bit7 clear");
        assertEquals(0x80, dma.readHdma5(), "HDMA5 should report stopped state with remaining count");
        assertEquals(0x00, vram[0x10] & 0xFF, "Second block should not be copied after stop");
    }

    @Test
    void writingBit7SetWhileHblankDmaActiveIsIgnored() {
        DmaController dma = new DmaController();
        byte[] vram = new byte[0x2000];
        dma.setHdmaReader(address -> (address >>> 8) & 0xFF);
        dma.setVramWriter((address, value) -> vram[address - 0x8000] = value);

        dma.setHdmaSrcHigh(0xC0);
        dma.setHdmaSrcLow(0x00);
        dma.setHdmaDstHigh(0x00);
        dma.setHdmaDstLow(0x00);

        dma.handleHdmaStart(0x81, true); // 2 blocks from 0xC000
        assertEquals(0x01, dma.readHdma5(), "Initial remaining blocks should be 2");

        // Attempt to restart with different source/destination while active.
        dma.setHdmaSrcHigh(0xD0);
        dma.setHdmaSrcLow(0x00);
        dma.setHdmaDstHigh(0x02);
        dma.setHdmaDstLow(0x00);
        dma.handleHdmaStart(0x83, true); // would be 4 blocks if not ignored

        assertEquals(0x01, dma.readHdma5(), "Active HBlank transfer should ignore bit7-set writes");

        dma.stepHdmaBlock();
        dma.stepHdmaBlock();

        assertFalse(dma.isHdmaActive(), "Original transfer should complete after two blocks, not restart to four");
        assertFalse(dma.isHdmaHblank(), "HBlank mode flag should be cleared after transfer ends");
        assertEquals(0xC0, vram[0x0000] & 0xFF, "First block should still come from original source");
        assertEquals(0xC0, vram[0x0010] & 0xFF, "Second block should still come from original source");
        assertEquals(0x00, vram[0x0200] & 0xFF, "New destination should remain untouched while active transfer ignored");
    }
}
