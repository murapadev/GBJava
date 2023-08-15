package a;

import org.xml.sax.Attributes;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.DefaultHandler;

public abstract class BaseConfigurationHandler<E> extends DefaultHandler {
	
	private boolean parseEnd;
	protected E returnObject;
	
	public BaseConfigurationHandler(E entity) {
		this.returnObject = entity;
	}

	@Override
	public void startDocument() throws SAXException {
		super.startDocument();
		parseEnd = false;
	}

	@Override
	public void startElement(String uri, String localName, String qName, Attributes attributes) throws SAXException {
		super.startElement(uri, localName, qName, attributes);
	}

	@Override
	public void endElement(String uri, String localName, String qName) throws SAXException {
		super.endElement(uri, localName, qName);
	}

	@Override
	public void endDocument() throws SAXException {
		super.endDocument();
		parseEnd = true;
	}
	
	public boolean finishedParsing() {
		return this.parseEnd;
	}
	
	public E getParsedObject() {
		return this.returnObject;
	}
	
}
