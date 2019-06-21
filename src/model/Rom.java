package model;


public class Rom {
    private final int ROM_OFFSET_NAME = 0x134;
    private final int ROM_OFFSET_TYPE = 0x147;
    private final int ROM_OFFSET_ROM_SIZE = 0x148;
    private final int ROM_OFFSET_RAM_SIZE = 0x149;

    private String name;
    private romType type;
    private int romSize;
    private int ramSize;

    private romType getStringbyByte(byte b) {
        for (romType r : romType.values())
            if(r.getRomType() == b)
                return r;

        return romType.ROM_NOT_FOUND;

    }


}

