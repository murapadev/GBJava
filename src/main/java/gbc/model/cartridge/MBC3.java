package gbc.model.cartridge;

public class MBC3 extends Cartridge {
	private int romBankNumber = 1; // Default ROM bank
	private int ramBankNumber = 0; // Default RAM bank
	private boolean ramEnabled = false;
	private boolean rtcLatched = false;
	private long lastLatchTime = 0;

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
			default:
				return 0;
		}
	}

	private void updateRtc() {
		long now = System.currentTimeMillis();
		if ((rtcRegisters[4] & 0x40) != 0) { // is halted?
			return;
		}

		long diff = now - lastLatchTime;
		if (lastLatchTime == 0) {
			diff = 0;
			lastLatchTime = now;
		}

		long seconds = diff / 1000;
		if (seconds == 0) {
			return;
		}
		lastLatchTime = now;

		seconds += rtcRegisters[0];
		rtcRegisters[0] = (byte) (seconds % 60);
		long minutes = seconds / 60;

		minutes += rtcRegisters[1];
		rtcRegisters[1] = (byte) (minutes % 60);
		long hours = minutes / 60;

		hours += rtcRegisters[2];
		rtcRegisters[2] = (byte) (hours % 24);
		long days = hours / 24;

		days += (rtcRegisters[3] & 0xFF) | ((rtcRegisters[4] & 0x01) << 8);

		if (days >= 512) {
			rtcRegisters[4] |= 0x80; // set carry bit
			days %= 512;
		}

		rtcRegisters[3] = (byte) (days & 0xFF);
		rtcRegisters[4] = (byte) ((rtcRegisters[4] & 0xFE) | ((days >> 8) & 0x01));
	}

	@Override
	public byte read(int address) {
		if (address >= 0x0000 && address < 0x4000) {
			// ROM bank 0
			return data[address];
		} else if (address >= 0x4000 && address < 0x8000) {
			// Switchable ROM bank
			int bankOffset = romBankNumber * 0x4000;
			int index = bankOffset + (address - 0x4000);
			return data[index];
		} else if (address >= 0xA000 && address < 0xC000 && ramEnabled) {
			if (ramBankNumber >= 0x08 && ramBankNumber <= 0x0C) {
				updateRtc();
				return rtcLatched ? latchedRtcRegisters[ramBankNumber - 0x08] : rtcRegisters[ramBankNumber - 0x08];
			} else {
				if (ram == null)
					return 0;
				// Switchable RAM bank
				int bankOffset = ramBankNumber * 0x2000;
				int index = bankOffset + (address - 0xA000);
				return ram[index];
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
			if (value == 0x01 && !rtcLatched) {
				updateRtc();
				System.arraycopy(rtcRegisters, 0, latchedRtcRegisters, 0, rtcRegisters.length);
			}
			rtcLatched = (value == 0x01);
		} else if (address >= 0xA000 && address < 0xC000 && ramEnabled) {
			if (ramBankNumber >= 0x08 && ramBankNumber <= 0x0C) {
				rtcRegisters[ramBankNumber - 0x08] = value;
			} else {
				if (ram == null)
					return;
				// Switchable RAM bank
				int bankOffset = ramBankNumber * 0x2000;
				int index = bankOffset + (address - 0xA000);
				ram[index] = value;
			}
		}
	}
}
