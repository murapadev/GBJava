package gbc.model.memory;

/**
 * OAM DMA and CGB HDMA controller.
 *
 * <h3>OAM DMA Timing</h3>
 * <p>OAM DMA transfers 160 bytes at 1 byte per 4 T-cycles (160 M-cycles total).
 * There is an 8 T-cycle startup delay after writing to FF46. During active DMA,
 * the CPU can only access HRAM (FF80-FFFE) and IO registers (FF00-FF7F).
 *
 * <h3>Bus Conflicts</h3>
 * <p>On restart (writing FF46 while DMA is active), the previous transfer's
 * bus lock persists through the new startup delay. The old transfer is aborted
 * and the new one begins from byte 0.
 *
 * <h3>HDMA (CGB only)</h3>
 * <p>General-purpose DMA transfers 16 bytes per block. HBlank DMA transfers
 * one 16-byte block per HBlank period. Writing bit 7=0 to FF55 while HBlank
 * DMA is active cancels the transfer. LCD disable also cancels HBlank DMA.
 */
public class DmaController {

    private static final int DMA_TRANSFER_LENGTH = 160;

    // OAM DMA state
    private boolean dmaActive;
    private int dmaSourceBase;
    private int dmaBytesTransferred;
    private int dmaStartupDelay;
    private int dmaCycleCounter;
    private boolean dmaWasRestarted;

    // CGB HDMA state
    private int hdmaSrcHigh;
    private int hdmaSrcLow;
    private int hdmaDstHigh;
    private int hdmaDstLow;
    private int hdmaCurrentSource;
    private int hdmaCurrentDest;
    private boolean hdmaActive;
    private boolean hdmaHblank;
    private int hdmaBlocksRemaining;
    private boolean hdmaStopped;

    // Bus access delegates
    private InternalReader oamReader;
    private InternalReader hdmaReader;
    private byte[] oam;
    private VramWriter vramWriter;

    @FunctionalInterface
    public interface InternalReader {
        int read(int address);
    }

    @FunctionalInterface
    public interface VramWriter {
        void write(int address, byte value);
    }

    public void setOam(byte[] oam) {
        this.oam = oam;
    }

    public void setReader(InternalReader reader) {
        this.oamReader = reader;
        this.hdmaReader = reader;
    }

    public void setOamReader(InternalReader reader) {
        this.oamReader = reader;
    }

    public void setHdmaReader(InternalReader reader) {
        this.hdmaReader = reader;
    }

    public void setVramWriter(VramWriter writer) {
        this.vramWriter = writer;
    }

    // --- OAM DMA ---

    public boolean isDmaActive() {
        return dmaActive;
    }

    public void startDma(int highByte) {
        // Track if DMA was already active and past startup (bus was locked)
        dmaWasRestarted = dmaActive && dmaStartupDelay == 0;
        dmaSourceBase = (highByte & 0xFF) << 8;
        dmaBytesTransferred = 0;
        // 8 T-cycles: 4 consumed during the write M-cycle's callback,
        // leaving 4 for the next M-cycle (setup cycle before first transfer)
        dmaStartupDelay = 8;
        dmaCycleCounter = 0;
        dmaActive = true;
    }

    public int getDmaSourceHighByte() {
        return (dmaSourceBase >> 8) & 0xFF;
    }

    public void stepOamDma() {
        if (!dmaActive) {
            return;
        }
        if (dmaStartupDelay > 0) {
            dmaStartupDelay--;
            if (dmaStartupDelay == 0) {
                dmaCycleCounter = 0;
                dmaWasRestarted = false;
            }
            return;
        }
        dmaCycleCounter++;
        if (dmaCycleCounter >= 4) {
            dmaCycleCounter = 0;
            if (dmaBytesTransferred < DMA_TRANSFER_LENGTH) {
                int srcAddr = (dmaSourceBase + dmaBytesTransferred) & 0xFFFF;
                int byteValue = (oamReader != null ? oamReader.read(srcAddr) : 0xFF) & 0xFF;
                oam[dmaBytesTransferred] = (byte) byteValue;
                dmaBytesTransferred++;
            }
            if (dmaBytesTransferred >= DMA_TRANSFER_LENGTH) {
                dmaActive = false;
            }
        }
    }

    public boolean isBusLocked(int address) {
        if (!dmaActive) {
            return false;
        }
        // During startup delay, bus is only locked if DMA was restarted
        // (previous DMA's bus lock persists through new startup)
        if (dmaStartupDelay > 0 && !dmaWasRestarted) {
            return false;
        }
        // During OAM DMA, CPU can access only I/O and HRAM ($FF00-$FFFF).
        // All other regions are blocked regardless of source.
        return (address & 0xFFFF) < 0xFF00;
    }

    // --- CGB HDMA ---

    public int getHdmaSrcHigh() {
        return hdmaSrcHigh;
    }

    public int getHdmaSrcLow() {
        return hdmaSrcLow & 0xF0;
    }

    public int getHdmaDstHigh() {
        return hdmaDstHigh & 0x1F;
    }

    public int getHdmaDstLow() {
        return hdmaDstLow & 0xF0;
    }

    public void setHdmaSrcHigh(int v) {
        if (hdmaActive) {
            return;
        }
        hdmaSrcHigh = v & 0xFF;
    }

    public void setHdmaSrcLow(int v) {
        if (hdmaActive) {
            return;
        }
        hdmaSrcLow = v & 0xF0;
    }

    public void setHdmaDstHigh(int v) {
        if (hdmaActive) {
            return;
        }
        hdmaDstHigh = v & 0x1F;
    }

    public void setHdmaDstLow(int v) {
        if (hdmaActive) {
            return;
        }
        hdmaDstLow = v & 0xF0;
    }

    public boolean isHdmaActive() {
        return hdmaActive;
    }

    public boolean isHdmaHblank() {
        return hdmaHblank;
    }

    public void onLcdDisabled() {
        if (hdmaActive && hdmaHblank) {
            hdmaActive = false;
            hdmaHblank = false;
            hdmaStopped = true;
        }
    }

    public int readHdma5() {
        if (hdmaActive && hdmaHblank) {
            return (hdmaBlocksRemaining - 1) & 0x7F; // bit7=0 when active
        }
        if (hdmaStopped && hdmaBlocksRemaining > 0) {
            return 0x80 | ((hdmaBlocksRemaining - 1) & 0x7F);
        }
        return 0xFF;
    }

    public void handleHdmaStart(int value, boolean lcdEnabled) {
        int length = value & 0x7F;
        boolean requestHblank = (value & 0x80) != 0;

        if (hdmaActive && hdmaHblank && !requestHblank) {
            hdmaActive = false;
            hdmaHblank = false;
            hdmaStopped = true;
            return;
        }
        if (hdmaActive && hdmaHblank && requestHblank) {
            // Writes with bit7=1 while HBlank HDMA is active are ignored.
            return;
        }

        int totalBlocks = length + 1;
        hdmaBlocksRemaining = totalBlocks;
        hdmaActive = true;
        hdmaHblank = requestHblank;
        hdmaStopped = false;
        hdmaCurrentSource = ((hdmaSrcHigh & 0xFF) << 8) | (hdmaSrcLow & 0xF0);
        hdmaCurrentDest = 0x8000 | ((hdmaDstHigh & 0x1F) << 8) | (hdmaDstLow & 0xF0);

        if (requestHblank && !lcdEnabled) {
            requestHblank = false;
            hdmaHblank = false;
        }

        if (!requestHblank) {
            for (int i = 0; i < totalBlocks; i++) {
                stepHdmaBlock();
            }
            hdmaActive = false;
            hdmaHblank = false;
            hdmaBlocksRemaining = 0;
            hdmaStopped = false;
        }
    }

    public void stepHdmaBlock() {
        if (!hdmaActive || hdmaBlocksRemaining <= 0) {
            return;
        }
        int source = hdmaCurrentSource & 0xFFFF;
        int dest = hdmaCurrentDest & 0xFFFF;

        for (int i = 0; i < 0x10; i++) {
            int value = (hdmaReader != null ? hdmaReader.read((source + i) & 0xFFFF) : 0xFF) & 0xFF;
            int addr = (dest + i) & 0xFFFF;
            if (addr >= 0x8000 && addr <= 0x9FFF) {
                vramWriter.write(addr, (byte) value);
            }
        }

        hdmaCurrentSource = (source + 0x10) & 0xFFFF;
        int destOffset = (((dest & 0x1FF0) + 0x10) & 0x1FF0);
        hdmaCurrentDest = 0x8000 | destOffset;
        hdmaSrcHigh = (hdmaCurrentSource >>> 8) & 0xFF;
        hdmaSrcLow = hdmaCurrentSource & 0xF0;
        hdmaDstHigh = (destOffset >>> 8) & 0x1F;
        hdmaDstLow = destOffset & 0xF0;

        hdmaBlocksRemaining--;
        if (hdmaBlocksRemaining <= 0) {
            hdmaActive = false;
            hdmaHblank = false;
            hdmaStopped = false;
        }
    }

    // --- Reset ---

    public void reset() {
        dmaActive = false;
        // On power-up, FF46 reads back 0xFF on DMG-family hardware.
        dmaSourceBase = 0xFF00;
        dmaBytesTransferred = 0;
        dmaStartupDelay = 0;
        dmaCycleCounter = 0;
        dmaWasRestarted = false;

        hdmaSrcHigh = 0;
        hdmaSrcLow = 0;
        hdmaDstHigh = 0;
        hdmaDstLow = 0;
        hdmaCurrentSource = 0;
        hdmaCurrentDest = 0x8000;
        hdmaActive = false;
        hdmaHblank = false;
        hdmaBlocksRemaining = 0;
        hdmaStopped = false;
    }
}
