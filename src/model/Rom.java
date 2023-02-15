package model;


import java.io.*;
import java.util.HashMap;

public class Rom {
    private final int ROM_OFFSET_NAME = 0x134;
    private final int ROM_OFFSET_TYPE = 0x147;
    private final int ROM_OFFSET_ROM_SIZE = 0x148;
    private final int ROM_OFFSET_RAM_SIZE = 0x149;

    private String name;
    private romType type;
    private int romSize;
    private int ramSize;
    private byte[] binaryFile;
    private HashMap<Short,MemoryAddress> cart;

    private romType getStringbyByte(byte b) {
        for (romType r : romType.values())
            if(r.getRomType() == b)
                return r;

        return romType.ROM_NOT_FOUND;

    }

    public Rom(Memory memoria,String filePath) throws IOException {
        try {
            File file = new File(filePath);
            binaryFile = new byte[(int) file.length()];
            InputStream is = new FileInputStream(file);
            is.read(binaryFile);
            is.close();
        }catch(FileNotFoundException e){
            e.printStackTrace();
        }catch (IOException e){
            e.printStackTrace();
        }
        StringBuilder nameP = new StringBuilder((ROM_OFFSET_TYPE - ROM_OFFSET_NAME)+1);
        for(int i = ROM_OFFSET_NAME; i<ROM_OFFSET_NAME+16;i++){
            if(binaryFile[i]==0x80||binaryFile[i]==0xc0)
                nameP.append('\0');
            else
                nameP.append(binaryFile[i]);
        }
        name = nameP.toString();
        System.out.println(name);

        romSize = binaryFile[ROM_OFFSET_ROM_SIZE];
        if((romSize & 0xF0)==0x50)
            romSize = (int)Math.pow(2.0,(double)((0x52&0xF)+1))+64;
        else
            romSize = (int)Math.pow(2.0,(double)romSize+1);
        ramSize = (int) Math.ceil((Math.pow(4.0,(double)binaryFile[ROM_OFFSET_RAM_SIZE])/2)/8.0f);
        type = getStringbyByte(binaryFile[ROM_OFFSET_TYPE]);
        cart =  memoria.getMemoryRange((short)0x0000,(short)0x7FFF);
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public romType getType() {
        return type;
    }

    public void setType(romType type) {
        this.type = type;
    }

    public int getRomSize() {
        return romSize;
    }

    public void setRomSize(int romSize) {
        this.romSize = romSize;
    }

    public int getRamSize() {
        return ramSize;
    }

    public void setRamSize(int ramSize) {
        this.ramSize = ramSize;
    }
}

