package edu.jhu.pha.vospace.process.tika;

import java.io.IOException;
import java.io.InputStream;
import java.util.Collections;
import java.util.Set;

import org.apache.poi.openxml4j.exceptions.InvalidFormatException;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.apache.tika.exception.TikaException;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.TikaCoreProperties;
import org.apache.tika.mime.MediaType;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.parser.Parser;
import org.apache.tika.sax.XHTMLContentHandler;
import org.xml.sax.ContentHandler;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.AttributesImpl;

public class ExcelParser implements Parser {
	private static final Set<MediaType> SUPPORTED_TYPES = Collections.singleton(MediaType.application("vnd.openxmlformats-officedocument.spreadsheetml.sheet"));
	public static final String FITS_MIME_TYPE = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";

	@Override
	public Set<MediaType> getSupportedTypes(ParseContext arg0) {
		return SUPPORTED_TYPES;
	}
	@Override
	public void parse(InputStream stream, ContentHandler handler, Metadata metadata,
			ParseContext context) throws IOException, SAXException, TikaException {

        XHTMLContentHandler xhtml = new XHTMLContentHandler(handler, metadata);
        xhtml.startDocument();

	    Workbook wb;
		
	    try {
			wb = WorkbookFactory.create(stream);
		} catch (InvalidFormatException e) {
			throw new TikaException("Invalid format");
		}
		
	    Sheet sheet = wb.getSheetAt(0);
	    int nRows = sheet.getLastRowNum();
	    int nCols = sheet.getRow(0).getLastCellNum();
		xhtml.startElement("section","id",String.valueOf(1));
		
		AttributesImpl attributes = new AttributesImpl();
		//attributes.addAttribute("", "id", "id", "CDATA", String.valueOf(1));
		attributes.addAttribute("", "columns", "columns", "CDATA", String.valueOf(nCols));
		xhtml.startElement("table",attributes);
		
		Row headerRow = sheet.getRow(0);
		xhtml.startElement("th", "info", "columnNames");
		for (int j=0; j<nCols; j++) {
    		Cell cell = headerRow.getCell(j);
    		String columnName = cell.getStringCellValue();
    		xhtml.element("td", columnName);
    	}
		xhtml.endElement("th");
		Row firstDataRow = sheet.getRow(1);
		xhtml.startElement("th", "info", "columnTypes");
		for (int j=0; j<nCols; j++) {
    		Cell cell = firstDataRow.getCell(j);
    		int type = cell.getCellType();
    		String columnType = null;
    		switch (type) {
    			case Cell.CELL_TYPE_NUMERIC:
    	    		columnType = "D";
    	    		break;
    			case Cell.CELL_TYPE_STRING:
    				columnType = "A";
    				break;
    		}
    		xhtml.element("td", columnType);
    	}
		xhtml.endElement("th");
		
	    for (int i=1; i<=nRows; i++) {
	    	Row row = sheet.getRow(i);
	    	xhtml.startElement("tr");
	    	for (int j=0; j<nCols; j++) {
	    		Cell cell = row.getCell(j);
	    		int type = cell.getCellType();
	    		switch (type) {
	    			case Cell.CELL_TYPE_NUMERIC:
	    				xhtml.element("td",String.valueOf(cell.getNumericCellValue()));
	    	    		break;
	    			case Cell.CELL_TYPE_STRING:
	    				xhtml.element("td",cell.getStringCellValue());
	    				break;
	    		}	
	    	}
	    	xhtml.endElement("tr");

	    }
	    xhtml.endElement("table");
	    xhtml.endElement("section");
	    xhtml.endDocument();
	    
	    metadata.add(TikaCoreProperties.TYPE, "EXCEL");
	}
}
