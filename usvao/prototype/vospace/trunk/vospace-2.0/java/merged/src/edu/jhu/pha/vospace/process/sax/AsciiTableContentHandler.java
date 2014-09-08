package edu.jhu.pha.vospace.process.sax;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Hashtable;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.xml.sax.Attributes;
import org.xml.sax.ContentHandler;
import org.xml.sax.Locator;
import org.xml.sax.SAXException;

import edu.jhu.pha.vospace.process.database.DatabaseFormat;
import edu.jhu.pha.vospace.process.tika.FITSColumnTypes;

public class AsciiTableContentHandler implements ContentHandler {

	static final int TABLE_ROW = 1;
	static final int COLUMN_NAMES = 2;
	static final int COLUMN_TYPES = 3;
	
	private Set<Integer> flags = new HashSet<Integer>();
	private List<AsciiTable> tables;
	public List<AsciiTable> getTables() {
		return tables;
	}

	private AsciiTable currentTable;
	private String[] currentRow;
	private int currentColumn;
	private int sectionId;
	
	public AsciiTableContentHandler() {
		this.tables = new ArrayList<AsciiTable>();
	}
	
	@Override
	public void characters(char[] ch, int start, int length)
			throws SAXException {
		if (flags.contains(TABLE_ROW)) {
			currentRow[currentColumn] = String.valueOf(ch,start,length);
		}
		else if (flags.contains(COLUMN_TYPES)) {
			currentTable.getColumnTypes()[currentColumn] = String.valueOf(ch,start,length);
		}
		else if (flags.contains(COLUMN_NAMES)) {
			currentTable.getColumnNames()[currentColumn] = String.valueOf(ch,start,length);
		}
 	}

	@Override
	public void endDocument() throws SAXException {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void endElement(String uri, String localName, String qName)
			throws SAXException {

		if ("tr".equals(localName)) {
			flags.remove(TABLE_ROW);
			currentTable.getRows().add(currentRow);
		}
		
		else if ("th".equals(localName)) {
			flags.remove(COLUMN_TYPES);
			flags.remove(COLUMN_NAMES);
		}
		
		else if ("td".equals(localName)) {
			currentColumn ++;
		}
	}

	@Override
	public void endPrefixMapping(String prefix) throws SAXException {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void ignorableWhitespace(char[] ch, int start, int length)
			throws SAXException {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void processingInstruction(String target, String data)
			throws SAXException {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void setDocumentLocator(Locator locator) {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void skippedEntity(String name) throws SAXException {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void startDocument() throws SAXException {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void startElement(String uri, String localName, String qName,
			Attributes atts) throws SAXException {
		if ("section".equals(localName)) {
			sectionId = Integer.parseInt(atts.getValue("id"));
		}
		if ("table".equals(localName)) {
			//int tableId = Integer.parseInt(atts.getValue("id"));
			int tableId = sectionId;
			int tableColumns = Integer.parseInt(atts.getValue("columns"));
			currentTable = new AsciiTable(tableId, tableColumns);
			tables.add(currentTable);
		}
		else if ("th".equals(localName)) {
			currentColumn = 0;
			String info = atts.getValue("info");
			if ("columnTypes".equals(info)) {
				flags.add(COLUMN_TYPES);
			}
			else if ("columnNames".equals(info)) {
				flags.add(COLUMN_NAMES);
			}
		}
		else if ("tr".equals(localName)) {
			currentColumn = 0;
			currentRow = new String[currentTable.getColumns()];
			flags.add(TABLE_ROW);
		}
	}

	@Override
	public void startPrefixMapping(String prefix, String uri)
			throws SAXException {
		// TODO Auto-generated method stub
		
	}

}
