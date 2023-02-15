package test;

import model.Memory;
import model.Rom;
import org.junit.jupiter.api.Test;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.*;

class RomTest {

    @Test
    void getName() {
        Memory memoria = new Memory();
        Rom rom = null;
        try {
            rom = new Rom(memoria,"C:\\Pokemon.gb");
        } catch (IOException e) {
            e.printStackTrace();
        }
        System.out.println(rom.getName());
        System.out.println(rom.getType().getRomString());
        System.out.println("Internal RAM Size; "+rom.getRamSize()*8 + "KB");
        System.out.println("Internal ROM Size: "+rom.getRomSize()*16+"KB");

    }
}