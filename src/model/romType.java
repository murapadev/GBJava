package model;

public enum romType {
    ROM_PLAIN((byte)0x00,"ROM_PLAIN"),
    ROM_MBC1((byte)0x01,"ROM_MBC1"),
    ROM_MBC1_RAM((byte)0x02,"ROM_MBC1_RAM"),
    ROM_MBC1_RAM_BATT((byte)0x03,"ROM_MBC1_RAM_BATT"),
    ROM_MBC2((byte)0x05,"ROM_MBC2"),
    ROM_MBC2_BATTERY((byte)0x06,"ROM_MBC2_BATTERY"),
    ROM_RAM((byte)0x08,"ROM_RAM"),
    ROM_RAM_BATTERY((byte)0x09,"ROM_RAM_BATTERY"),
    ROM_MMM01((byte)0x0B,"ROM_MMM01"),
    ROM_MMM01_SRAM((byte)0x0C,"ROM_MMM01_SRAM"),
    ROM_MMM01_SRAM_BATT((byte)0x0D,"ROM_MMM01_SRAM_BATT"),
    ROM_MBC3_TIMER_BATT((byte)0x0F,"ROM_MBC3_TIMER_BATT"),
    ROM_MBC3_TIMER_RAM_BATT((byte)0x10,"ROM_MBC3_TIMER_RAM_BATT"),
    ROM_MBC3((byte)0x11,"ROM_MBC3"),
    ROM_MBC3_RAM((byte)0x12,"ROM_MBC3_RAM"),
    ROM_MBC3_RAM_BATT((byte)0x13,"ROM_MBC3_RAM_BATT"),
    ROM_MBC5((byte)0x19,"ROM_MBC5"),
    ROM_MBC5_RAM((byte)0x1A,"ROM_MBC5_RAM"),
    ROM_MBC5_RAM_BATT((byte)0x1B,"ROM_MBC5_RAM_BATT"),
    ROM_MBC5_RUMBLE((byte)0x1C,"ROM_MBC5_RUMBLE"),
    ROM_MBC5_RUMBLE_SRAM((byte)0x1D,"ROM_MBC5_RUMBLE_SRAM"),
    ROM_MBC5_RUMBLE_SRAM_BATT((byte)0x1E,"ROM_MBC5_RUMBLE_SRAM_BATT"),
    ROM_POCKET_CAMERA((byte)0x1F,"ROM_POCKET_CAMERA"),
    ROM_BANDAI_TAMA5((byte)0xFD,"ROM_BANDAI_TAMA5"),
    ROM_HUDSON_HUC3((byte)0xFE,"ROM_HUDSON_HUC3"),
    ROM_HUDSON_HUC1((byte)0xFF,"ROM_HUDSON_HUC1"),
    ROM_NOT_FOUND((byte)0xF0,"ROM_NOT_FOUND");



    private byte romType;
    private String romString;

    romType(byte romType, String romString) {
        this.romType = romType;
        this.romString = romString;

    }

    public byte getRomType(){
        return romType;
    }
    public String getRomString(){
        return romString;
    }


}
