package me.emulador.gbc.model;


public class GameBoyColor {
    private CPU cpu;
    private Memory memory;

    public GameBoyColor() {
        this.memory = new Memory();
        this.cpu = new CPU(this.memory);

    }

    public void executeCycle() {
        // Lógica para ejecutar un ciclo de la CPU, leer memoria, etc.
    }
}
