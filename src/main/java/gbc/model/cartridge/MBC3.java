package gbc.model.cartridge;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.logging.Level;
import java.util.logging.Logger;

public class MBC3 extends Cartridge {
	private static final Logger LOGGER = Logger.getLogger(MBC3.class.getName());
	private static final byte[] RTC_MAGIC = new byte[] { 'R', 'T', 'C', '1' };
	private static final int RTC_VERSION = 1;

	private int romBankNumber = 1; // Default ROM bank
	private int ramBankNumber = 0; // Default RAM bank
	private boolean ramEnabled = false;
	private boolean rtcLatched = false;
	private long lastRtcUpdateMs = 0;
	private int lastLatchValue = 0;

	private final byte[] rtcRegisters = new byte[5]; // 0-4: S, M, H, DL, DH
	private final byte[] latchedRtcRegisters = new byte[5];

	public MBC3(byte[] data, boolean hasBattery) {
		super(data);
		this.hasBattery = hasBattery;
		int ramSize = getRamSize(data[0x0149]);
		if (ramSize > 0) {
			this.ram = new byte[ramSize];
		}
	}

	private int getRamSize(byte ramSizeType) {
		switch (ramSizeType) {
			case 0x01:
				return 2 * 1024; // 2KB
			case 0x02:
				return 8 * 1024; // 8KB
			case 0x03:
				return 32 * 1024; // 32KB
			case 0x04:
				return 128 * 1024; // 128KB
			case 0x05:
				return 64 * 1024; // 64KB
			default:
				return 0;
		}
	}

	private void updateRtc() {
		long now = System.currentTimeMillis();
		if (lastRtcUpdateMs == 0) {
			lastRtcUpdateMs = now;
			return;
		}
		if ((rtcRegisters[4] & 0x40) != 0) { // halted
			lastRtcUpdateMs = now;
			return;
		}

		long diff = now - lastRtcUpdateMs;
		long seconds = diff / 1000;
		if (seconds <= 0) {
			return;
		}
		lastRtcUpdateMs = now;

		long totalSeconds = seconds + (rtcRegisters[0] & 0xFF);
		rtcRegisters[0] = (byte) (totalSeconds % 60);
		long minutesCarry = totalSeconds / 60;
		long totalMinutes = minutesCarry + (rtcRegisters[1] & 0xFF);
		rtcRegisters[1] = (byte) (totalMinutes % 60);
		long hoursCarry = totalMinutes / 60;
		long totalHours = hoursCarry + (rtcRegisters[2] & 0xFF);
		rtcRegisters[2] = (byte) (totalHours % 24);
		long daysCarry = totalHours / 24;

		int dayLow = rtcRegisters[3] & 0xFF;
		int dayHigh = rtcRegisters[4] & 0x01;
		long totalDays = daysCarry + dayLow + (dayHigh << 8);

		if (totalDays >= 512) {
			rtcRegisters[4] |= (byte) 0x80; // carry
			totalDays %= 512;
		}
		rtcRegisters[3] = (byte) (totalDays & 0xFF);
		rtcRegisters[4] = (byte) ((rtcRegisters[4] & 0xFE) | ((totalDays >> 8) & 0x01));
	}

	@Override
	public byte read(int address) {
		if (address >= 0x0000 && address < 0x4000) {
			// ROM bank 0
			if (address >= 0 && address < data.length) {
				return data[address];
			}
			return (byte) 0xFF;
		} else if (address >= 0x4000 && address < 0x8000) {
			// Switchable ROM bank
			int bankOffset = romBankNumber * 0x4000;
			int index = bankOffset + (address - 0x4000);
			if (index >= 0 && index < data.length) {
				return data[index];
			}
			return (byte) 0xFF;
		} else if (address >= 0xA000 && address < 0xC000 && ramEnabled) {
			if (ramBankNumber >= 0x08 && ramBankNumber <= 0x0C) {
				updateRtc();
				return rtcLatched ? latchedRtcRegisters[ramBankNumber - 0x08] : rtcRegisters[ramBankNumber - 0x08];
			} else {
				if (ram == null) {
					return (byte) 0xFF;
				}
				// Switchable RAM bank
				int bankOffset = ramBankNumber * 0x2000;
				int index = bankOffset + (address - 0xA000);
				if (index >= 0 && index < ram.length) {
					return ram[index];
				}
				return (byte) 0xFF;
			}
		}
		return (byte) 0xFF;
	}

	@Override
	public void write(int address, byte value) {
		if (address >= 0x0000 && address < 0x2000) {
			// RAM enable/disable
			ramEnabled = ((value & 0x0F) == 0x0A);
		} else if (address >= 0x2000 && address < 0x4000) {
			// ROM bank number
			romBankNumber = (value & 0x7F);
			if (romBankNumber == 0)
				romBankNumber = 1;
		} else if (address >= 0x4000 && address < 0x6000) {
			// RAM bank number or RTC register select
			ramBankNumber = (value & 0x0F);
		} else if (address >= 0x6000 && address < 0x8000) {
			// Latch clock data
			if (value == 0x00) {
				rtcLatched = false;
				lastLatchValue = 0;
				return;
			}
			if (value == 0x01 && lastLatchValue == 0) {
				updateRtc();
				System.arraycopy(rtcRegisters, 0, latchedRtcRegisters, 0, rtcRegisters.length);
				rtcLatched = true;
			}
			lastLatchValue = value & 0x01;
		} else if (address >= 0xA000 && address < 0xC000 && ramEnabled) {
			if (ramBankNumber >= 0x08 && ramBankNumber <= 0x0C) {
				int registerIndex = ramBankNumber - 0x08;
				if (registerIndex == 4) {
					boolean wasHalted = (rtcRegisters[4] & 0x40) != 0;
					boolean nowHalted = (value & 0x40) != 0;
					if (wasHalted != nowHalted) {
						lastRtcUpdateMs = System.currentTimeMillis();
					}
				}
				rtcRegisters[registerIndex] = value;
			} else {
				if (ram == null)
					return;
				// Switchable RAM bank
				int bankOffset = ramBankNumber * 0x2000;
				int index = bankOffset + (address - 0xA000);
				if (index >= 0 && index < ram.length) {
					ram[index] = value;
				}
			}
		}
	}

	@Override
	public void saveSRAM() {
		super.saveSRAM();
		saveRtcState();
	}

	@Override
	public void loadSRAM() {
		super.loadSRAM();
		loadRtcState();
	}

	private void saveRtcState() {
		if (!hasBattery) {
			return;
		}
		Path rtcPath = getSaveFilePathWithExtension(".rtc");
		if (rtcPath == null) {
			return;
		}
		try {
			Files.createDirectories(rtcPath.getParent());
			try (DataOutputStream out = new DataOutputStream(Files.newOutputStream(rtcPath))) {
				out.write(RTC_MAGIC);
				out.writeInt(RTC_VERSION);
				out.writeLong(lastRtcUpdateMs);
				out.writeBoolean(rtcLatched);
				out.writeByte(lastLatchValue);
				out.write(rtcRegisters);
				out.write(latchedRtcRegisters);
			}
		} catch (IOException e) {
			LOGGER.log(Level.WARNING, "Failed to save RTC state to " + rtcPath, e);
		}
	}

	private void loadRtcState() {
		if (!hasBattery) {
			return;
		}
		Path rtcPath = getSaveFilePathWithExtension(".rtc");
		if (rtcPath == null || !Files.exists(rtcPath)) {
			return;
		}
		try (DataInputStream in = new DataInputStream(Files.newInputStream(rtcPath))) {
			byte[] magic = new byte[RTC_MAGIC.length];
			if (in.read(magic) != RTC_MAGIC.length) {
				return;
			}
			for (int i = 0; i < RTC_MAGIC.length; i++) {
				if (magic[i] != RTC_MAGIC[i]) {
					return;
				}
			}
			int version = in.readInt();
			if (version != RTC_VERSION) {
				return;
			}
			lastRtcUpdateMs = in.readLong();
			rtcLatched = in.readBoolean();
			lastLatchValue = in.readByte() & 0x01;
			in.readFully(rtcRegisters);
			in.readFully(latchedRtcRegisters);
			updateRtc();
		} catch (IOException e) {
			LOGGER.log(Level.WARNING, "Failed to load RTC state from " + rtcPath, e);
		}
	}
}
