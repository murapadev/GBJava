
package me.emulador.gbc.model.cpu;

import me.emulador.gbc.model.memory.Memory;

public class Instructions {

    private Registers r;
    private Memory m;

    Instructions(Registers r, Memory m) {
        // Constructor
        this.r = r;
        this.m = m;

    }

    public void setRegisters(Registers r) {
        this.r = r;
    }

    public void setMemory(Memory m) {
        this.m = m;
    }

    public void execute(byte opcode) {
        //TODO: Implementar la lógica para ejecutar las instrucciones
    }
}