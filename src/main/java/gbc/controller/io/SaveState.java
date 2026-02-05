package gbc.controller.io;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

import gbc.model.GameBoyColor;
import gbc.model.cpu.CPU;
import gbc.model.cpu.Registers;
import gbc.model.memory.Memory;

/**
 * Save state manager for the Game Boy Color emulator.
 * Supports saving and loading complete emulator state to/from files.
 * Uses GZIP compression for smaller file sizes.
 */
public class SaveState {
    private static final Logger LOGGER = Logger.getLogger(SaveState.class.getName());
    
    // Magic number to identify save state files
    private static final int MAGIC = 0x47425353; // "GBSS" - Game Boy Save State
    private static final int VERSION = 1;
    
    private SaveState() {
        // Utility class
    }
    
    /**
     * Saves the complete emulator state to a file.
     * 
     * @param gbc The GameBoyColor instance to save
     * @param path The path to save to
     * @return true if save was successful, false otherwise
     */
    public static boolean save(GameBoyColor gbc, Path path) {
        if (gbc == null || path == null) {
            LOGGER.warning("Cannot save: null parameters");
            return false;
        }
        
        try (OutputStream fos = Files.newOutputStream(path);
             GZIPOutputStream gzos = new GZIPOutputStream(fos);
             DataOutputStream dos = new DataOutputStream(gzos)) {
            
            // Write header
            dos.writeInt(MAGIC);
            dos.writeInt(VERSION);
            
            // Write CPU state
            saveCPU(dos, gbc.getCpu());
            
            // Write Memory state
            saveMemory(dos, gbc.getMemory());
            
            dos.flush();
            LOGGER.log(Level.INFO, () -> String.format("Saved state to %s", path));
            return true;
            
        } catch (IOException e) {
            LOGGER.log(Level.SEVERE, "Failed to save state to " + path, e);
            return false;
        }
    }
    
    /**
     * Loads emulator state from a file.
     * 
     * @param gbc The GameBoyColor instance to restore state to
     * @param path The path to load from
     * @return true if load was successful, false otherwise
     */
    public static boolean load(GameBoyColor gbc, Path path) {
        if (gbc == null || path == null) {
            LOGGER.warning("Cannot load: null parameters");
            return false;
        }
        
        if (!Files.exists(path)) {
            LOGGER.warning("Save state file not found: " + path);
            return false;
        }
        
        try (InputStream fis = Files.newInputStream(path);
             GZIPInputStream gzis = new GZIPInputStream(fis);
             DataInputStream dis = new DataInputStream(gzis)) {
            
            // Read and verify header
            int magic = dis.readInt();
            if (magic != MAGIC) {
                LOGGER.warning("Invalid save state file (bad magic)");
                return false;
            }
            
            int version = dis.readInt();
            if (version > VERSION) {
                LOGGER.warning("Save state version too new: " + version);
                return false;
            }
            
            // Load CPU state
            loadCPU(dis, gbc.getCpu());
            
            // Load Memory state
            loadMemory(dis, gbc.getMemory());
            
            LOGGER.log(Level.INFO, () -> String.format("Loaded state from %s", path));
            return true;
            
        } catch (IOException e) {
            LOGGER.log(Level.SEVERE, "Failed to load state from " + path, e);
            return false;
        }
    }
    
    /**
     * Gets the default save state path for a ROM.
     * Save states are stored alongside the ROM with .ss# extension.
     */
    public static Path getSaveStatePath(String romPath, int slot) {
        if (romPath == null) {
            return null;
        }
        String romName = Path.of(romPath).getFileName().toString();
        String baseName = romName.replaceFirst("\\.[^.]+$", "");
        String saveDir = System.getProperty("emulator.saveDir");
        Path dir = (saveDir == null || saveDir.isBlank())
                ? Path.of(romPath).getParent()
                : Path.of(saveDir);
        return dir.resolve(baseName + ".ss" + slot);
    }
    
    // ============ CPU Serialization ============
    
    private static void saveCPU(DataOutputStream dos, CPU cpu) throws IOException {
        Registers regs = cpu.getRegisters();
        
        // Registers (A, F, B, C, D, E, H, L)
        dos.writeByte(regs.getRegister("A"));
        dos.writeByte(regs.getRegister("F"));
        dos.writeByte(regs.getRegister("B"));
        dos.writeByte(regs.getRegister("C"));
        dos.writeByte(regs.getRegister("D"));
        dos.writeByte(regs.getRegister("E"));
        dos.writeByte(regs.getRegister("H"));
        dos.writeByte(regs.getRegister("L"));
        
        // 16-bit registers
        dos.writeShort(regs.getPC());
        dos.writeShort(regs.getSP());
        
        // CPU state flags
        dos.writeBoolean(cpu.isIme());
        dos.writeBoolean(cpu.isImePending());
        dos.writeBoolean(cpu.isHalted());
        dos.writeBoolean(cpu.isDoubleSpeedMode());
        dos.writeBoolean(cpu.isPrepareSpeedSwitch());
        
        // Cycle counter
        dos.writeInt(cpu.getCycles());
    }
    
    private static void loadCPU(DataInputStream dis, CPU cpu) throws IOException {
        Registers regs = cpu.getRegisters();
        
        // Registers
        regs.setRegister("A", dis.readByte());
        regs.setRegister("F", dis.readByte());
        regs.setRegister("B", dis.readByte());
        regs.setRegister("C", dis.readByte());
        regs.setRegister("D", dis.readByte());
        regs.setRegister("E", dis.readByte());
        regs.setRegister("H", dis.readByte());
        regs.setRegister("L", dis.readByte());
        
        // 16-bit registers
        regs.setPC(dis.readShort() & 0xFFFF);
        regs.setSP(dis.readShort() & 0xFFFF);
        
        // CPU state flags
        cpu.setIme(dis.readBoolean());
        cpu.setImePending(dis.readBoolean());
        cpu.setHalted(dis.readBoolean());
        cpu.setDoubleSpeedMode(dis.readBoolean());
        cpu.setPrepareSpeedSwitch(dis.readBoolean());
        
        // Cycle counter
        cpu.setCycles(dis.readInt());
    }
    
    // ============ Memory Serialization ============
    
    private static void saveMemory(DataOutputStream dos, Memory memory) throws IOException {
        // Save VRAM (16KB for CGB - 2 banks)
        saveByteArray(dos, memory, 0x8000, 0x2000); // Bank 0
        // Note: Bank 1 would require VRAM bank switching to read
        
        // Save WRAM (32KB for CGB - 8 banks of 4KB each)
        // Bank 0 is at 0xC000-0xCFFF
        // Banks 1-7 are switchable at 0xD000-0xDFFF
        saveByteArray(dos, memory, 0xC000, 0x2000); // Banks 0+1
        
        // Save OAM (160 bytes)
        saveByteArray(dos, memory, 0xFE00, 0xA0);
        
        // Save HRAM (127 bytes)
        saveByteArray(dos, memory, 0xFF80, 0x7F);
        
        // Save I/O registers (0xFF00-0xFF7F)
        saveByteArray(dos, memory, 0xFF00, 0x80);
        
        // Save IE register
        dos.writeByte(memory.readByte(0xFFFF));
    }
    
    private static void loadMemory(DataInputStream dis, Memory memory) throws IOException {
        // Load VRAM
        loadByteArray(dis, memory, 0x8000, 0x2000);
        
        // Load WRAM
        loadByteArray(dis, memory, 0xC000, 0x2000);
        
        // Load OAM
        loadByteArray(dis, memory, 0xFE00, 0xA0);
        
        // Load HRAM
        loadByteArray(dis, memory, 0xFF80, 0x7F);
        
        // Load I/O registers
        loadByteArray(dis, memory, 0xFF00, 0x80);
        
        // Load IE register
        memory.writeByte(0xFFFF, dis.readByte() & 0xFF);
    }
    
    private static void saveByteArray(DataOutputStream dos, Memory memory, int startAddr, int length) 
            throws IOException {
        for (int i = 0; i < length; i++) {
            dos.writeByte(memory.readByte(startAddr + i));
        }
    }
    
    private static void loadByteArray(DataInputStream dis, Memory memory, int startAddr, int length) 
            throws IOException {
        for (int i = 0; i < length; i++) {
            memory.writeByte(startAddr + i, dis.readByte() & 0xFF);
        }
    }
}
