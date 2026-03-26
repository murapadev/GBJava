# 🕹️ GBJava

[![License: MIT](https://img.shields.io/badge/License-MIT-blue.svg)](https://opensource.org/licenses/MIT)
![Java](https://img.shields.io/badge/Java-17+-blue)
![Status](https://img.shields.io/badge/status-WIP-orange)

> A WIP Game Boy emulator made from scratch in Java.

---

## 📋 Tabla de contenidos

- [Características](#-características)
- [Instalación](#-instalación)
- [Uso](#-uso)
- [Contribución](#-contribución)
- [Licencia](#-licencia)

## ✨ Características

- 🎮 **Emulador Game Boy**: Emulación completa desde cero
- 🖥️ **Interfaz Swing**: UI de escritorio para desarrollo y debugging
- 🛠️ **Herramientas Debug**: VRAM viewer, step-by-step execution
- 💾 **Save States**: Sistema de guardados
- 📊 **Estadísticas**: FPS, estado runtime, escala de color

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
java -jar target/gbc-1.0.jar rom.gb

# Con Maven
mvn exec:java -Dexec.mainClass="gbc.Main"
```

## 📝 Contribución

Las contribuciones son bienvenidas. Abre un issue o pull request.

## 📄 Licencia

Este proyecto está licenciado bajo los términos de la licencia MIT.

---

*Hecho con ❤️ por [murapadev](https://github.com/murapadev)*