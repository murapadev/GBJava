# 🕹️ GBJava

[![License: MIT](https://img.shields.io/badge/License-MIT-blue.svg)](https://opensource.org/licenses/MIT)
![Java](https://img.shields.io/badge/Java-21+-blue)
![Status](https://img.shields.io/badge/status-WIP-orange)

> A WIP Game Boy emulator made from scratch in Java.

---

## 📋 Tabla de contenidos

- [Características](#-características)
- [Estado y precisión](#-estado-y-precisión)
- [Instalación](#-instalación)
- [Uso](#-uso)
- [Tests](#-tests)
- [Contribución](#-contribución)
- [Licencia](#-licencia)

## ✨ Características

- 🎮 **Emulador Game Boy**: Emulación completa desde cero
- 🖥️ **Interfaz Swing**: UI de escritorio para desarrollo y debugging
- 🛠️ **Herramientas Debug**: VRAM viewer, step-by-step execution
- 💾 **Guardado**: save states y RAM con batería en `.sav` junto a la ROM (o en `-Demulator.saveDir`)
- 🧩 **Cartuchos**: ROM, MBC1 (incl. MBC1M), MBC2, MBC3 (con RTC) y MBC5
- 📊 **Estadísticas**: FPS, estado runtime, escala de color

## 📈 Estado y precisión

La CPU, los temporizadores, los mappers y el renderizado DMG/CGB pasan las ROMs de test
de referencia. Lo que queda pendiente son casos límite de temporización de la PPU, el
canal de onda del APU y el bug de corrupción de OAM.

Resultados en modo DMG (salvo donde se indica), medidos con las ROMs públicas de
[blargg](https://github.com/retrio/gb-test-roms),
[mooneye-test-suite](https://github.com/Gekkio/mooneye-test-suite) y
[dmg-acid2](https://github.com/mattcurrie/dmg-acid2)/[cgb-acid2](https://github.com/mattcurrie/cgb-acid2):

| Suite | Antes (v1.5.0) | Ahora |
|-------|---------------:|------:|
| blargg `cpu_instrs` | 11/11 | 11/11 |
| blargg `instr_timing` | 1/1 | 1/1 |
| blargg `mem_timing` | 3/3 | 3/3 |
| blargg `mem_timing-2` | 3/3 | 3/3 |
| blargg `halt_bug` | 0/1 | 1/1 |
| blargg `interrupt_time` (CGB) | 0/1 | 1/1 |
| blargg `dmg_sound` | 2/12 | 9/12 |
| blargg `cgb_sound` (CGB) | 2/12 | 10/12 |
| blargg `oam_bug` | 2/8 | 2/8 |
| dmg-acid2 (comparado con la imagen de referencia) | ✗ (38 px) | ✓ |
| cgb-acid2 (comparado con la imagen de referencia) | ✗ (19 px) | ✓ |
| mooneye `emulator-only` MBC1/MBC2/MBC5 | 28/28 | 28/28 |
| mooneye arranque (`boot_regs`, `boot_div`, `boot_hwio`) | 7/7 | 7/7 |
| mooneye `acceptance` (ROMs compatibles con DMG) | 47/66 | 56/66 |

Pendiente:
- `dmg_sound` 09, 10, 12 y `cgb_sound` 09, 12: lectura/escritura de wave RAM con el canal 3 activo (precisión de 2 ciclos).
- `oam_bug`: no se emula la corrupción de OAM.
- mooneye `acceptance`: `oam_dma_start`, `oam_dma/sources-GS`, `serial/boot_sclk_align`,
  y temporización de la PPU (`intr_1_2_timing`, `intr_2_mode0_timing_sprites`, `lcdon_timing`,
  `lcdon_write_timing`, `stat_irq_blocking`, `stat_lyc_onoff`, `vblank_stat_intr`).

## 🛠️ Instalación

```bash
# Clonar
git clone https://github.com/murapadev/GBJava.git
cd GBJava

# Construir con Maven
mvn clean package
```

## 🚀 Uso

```bash
# Ejecutar
java -jar target/GBJava-*.jar rom.gb

# Con Maven
mvn exec:java -Dexec.mainClass="gbc.Main"
```

## 🧪 Tests

```bash
mvn -Pci verify
```

Los tests unitarios no necesitan ROMs. Las suites de precisión (`BlarggTest`, `Acid2Test`,
`Mooneye*Test`) se saltan si no encuentran las ROMs; por defecto las buscan en `samples/roms`
y se puede apuntar a otro directorio con `-Dgbc.testRoms`:

```
<dir>/blargg/gb-test-roms-master/...   # blargg
<dir>/mooneye-test-suite/...           # mooneye (release mts-*)
<dir>/dmg-acid2.gb, dmg-acid2-reference.png
<dir>/cgb-acid2.gbc, cgb-acid2-reference.png
```

```bash
mvn -Pci test -Dgbc.testRoms=/ruta/a/roms
```

Suites opcionales que aún no pasan enteras: `-Dgbc.blargg.extended=true` (`dmg_sound`,
`cgb_sound`, `oam_bug`) y `-Dgbc.mooneye.acceptance=true` (suite `acceptance` completa).

Las ROMs no se versionan. El job `test-roms` de CI las descarga en cada ejecución.

## 📝 Contribución

Las contribuciones son bienvenidas. Abre un issue o pull request.

## 📄 Licencia

Este proyecto está licenciado bajo los términos de la licencia MIT.

---

*Hecho con ❤️ por [murapadev](https://github.com/murapadev)*