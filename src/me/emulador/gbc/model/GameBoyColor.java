package me.emulador.gbc.model;

import me.emulador.gbc.model.cartridge.Cartridge;
import me.emulador.gbc.model.cpu.CPU;
import me.emulador.gbc.model.memory.Memory;

public class GameBoyColor {
    private CPU cpu;
    private Memory memory;
    private Cartridge cartridge;

    public GameBoyColor() {
        this.memory = new Memory();
        this.cpu = new CPU(this.memory);
        
    }



    public void executeCycle() {
        // Lógica para ejecutar un ciclo de la CPU, leer memoria, etc.
    }
}
