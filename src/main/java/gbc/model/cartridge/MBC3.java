package gbc.model.cartridge;

//Imports for Real Time Clock
import java.util.Calendar;
import java.util.Date;
import java.util.GregorianCalendar;


public class MBC3 extends Cartridge {
	private int romBankNumber = 1; // Default ROM bank
	private int ramBankNumber = 0; // Default RAM bank
	private boolean ramEnabled = false;

	public MBC3(byte[] data) {
		super(data);
	}

	public byte[] rtc() {
		//Logic of Real Time Clock
		Calendar calendar = new GregorianCalendar();
		Date trialTime = new Date();
		calendar.setTime(trialTime);
		int hour = calendar.get(Calendar.HOUR);
		int minute = calendar.get(Calendar.MINUTE);
		int second = calendar.get(Calendar.SECOND);
		int day = calendar.get(Calendar.DAY_OF_MONTH);
		int month = calendar.get(Calendar.MONTH);
		int year = calendar.get(Calendar.YEAR);
		byte[] rtc = new byte[7];
		rtc[0] = (byte) second;
		rtc[1] = (byte) minute;
		rtc[2] = (byte) hour;
		rtc[3] = (byte) day;
		rtc[4] = (byte) month;
		rtc[5] = (byte) year;
		rtc[6] = (byte) 0;
		return rtc;

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
			// Switchable RAM bank
			int bankOffset = ramBankNumber * 0x2000;
			int index = bankOffset + (address - 0xA000);
			return data[index];
		}
		return 0;
	}

	@Override
	public void write(int address, byte value) {
		if (address >= 0x0000 && address < 0x2000) {
			// RAM enable/disable
			ramEnabled = ((value & 0x0F) == 0x0A);
		} else if (address >= 0x2000 && address < 0x4000) {
			// ROM bank number
			romBankNumber = (value & 0x7F);
			if (romBankNumber == 0) romBankNumber = 1;
		} else if (address >= 0x4000 && address < 0x6000) {
			// RAM bank number
			ramBankNumber = (value & 0x03);
		}



	}
}
