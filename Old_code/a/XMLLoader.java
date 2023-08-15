package a;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import javax.xml.parsers.ParserConfigurationException;
import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;

import org.xml.sax.SAXException;

public class XMLLoader {

	public static <E> E load(BaseConfigurationHandler<E> handler, String path) throws SAXException, IOException, ParserConfigurationException {
		SAXParserFactory saxParserFactory = SAXParserFactory.newInstance();
		SAXParser saxParser = saxParserFactory.newSAXParser();
		
		File file = new File(path);
		FileInputStream fis = null;

		try {
			fis = new FileInputStream(file);
		} catch (IOException e) {
			e.printStackTrace();
		} finally {
			try {
				saxParser.parse(fis, handler);
			} catch (IOException ex) {
				ex.printStackTrace();
			} finally {
				if (fis != null) {
					fis.close();
				}
			}
		}
		
		return  handler.getParsedObject();
	}
	
}
