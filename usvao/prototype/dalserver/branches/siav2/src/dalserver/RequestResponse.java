/*
 * RequestResponse.java
 * $ID*
 */

package dalserver;

import java.io.*;
import java.util.*;
import java.math.*;
import cds.savot.model.*;
import cds.savot.writer.*;
import com.inamik.utils.*;
import java.util.List;
import dalserver.ssa.*;


/**
 * The RequestResponse class is used to build a servlet request response
 * object, which is then serialized and returned to the client to respond
 * to a service request.  The request response object is a table which
 * conforms to the VOTable data model, i.e., a set of global parameters
 * and other global metadata, plus a table where each row consists of a
 * set of fields optionally collected into logical groups.  The attributes
 * of a field (or group, or param) include things such as NAME, ID,
 * DATATYPE, UTYPE, UCD, and so forth.  We separate the functions of
 * creating the RequestResponse object, from any subsequent serialization;
 * various serializations are possible, not necessarily limited to VOTable.
 * Since various serializations of the same model are possible, "streaming"
 * techniques cannot be used, and the entire model is built in memory
 * prior to output.
 *
 * <p>This implementation is a wrapper over the underlying VOTable classes;
 * in particular, elements of the VOTable data model are used to build up
 * the request response in memory prior to serialization.  The main
 * difference from the VOTable classes is that the model assumed here is
 * somewhat more restrictive (e.g., there is only one table, and a table
 * element can belong to only one group) and access to object data here
 * tends to be object oriented (keyed by object property) rather than table
 * oriented (governed by the table structure with table elements accessed
 * by a numerical index).
 *  
 * <p>This implementation uses source code created at the Centre de Donnees
 * astronomiques de Strasbourg (CDS), France, specifically, the SAVOT
 * VOTable package.  Except for a few SAVOT-specific methods however, the
 * implementation is independent of the VOTable class used, and other
 * implementations are possible.
 *
 * @version	1.0, 11-Dec-2006
 * @author	Doug Tody
 */
public class RequestResponse {
    // -------- Class Data -----------

    /** XML name space for the VOTable content. */
    private String xmlnsPrefix;
    private String xmlnsUrl;

    /** Description text for the RequestResponse object. */
    private String description;

    /** The type of response. */
    private String responseType;

    /** The Utype of the response. */
    private String responseUtype;

    /** The maximum number of output records (table rows). */
    private int maxrec = 10000;

    /** List of INFO elements. */
    private LinkedHashMap<String,TableInfo> infos;

    /** List of table groups. */
    private LinkedHashMap<String,TableGroup> groups;
    private int nGroups = 0;

    /** List of global table parameters. */
    private LinkedHashMap<String,TableParam> params;
    private int nParams = 0;

    /** List of table fields (defines the record structure). */
    private LinkedHashMap<String,TableField> fields;
    private int nFields = 0;

    /** Global list of table elements (maintains order of definition). */
    private ArrayList<Object> atoms;

    /** Table data rows. */
    private java.util.Vector<Vector<Object>> rows;

    /** Active table row pointer (for get/set operations). */
    private java.util.Vector<Object> row;


    // -------- Constructors -----------

    /**
     * Create a new, empty RequestResponse object.
     */
    public RequestResponse() {
	infos = new LinkedHashMap<String,TableInfo>();
	groups = new LinkedHashMap<String,TableGroup>();
	params = new LinkedHashMap<String,TableParam>();
	fields = new LinkedHashMap<String,TableField>();
	atoms = new ArrayList<Object>();
	rows = new java.util.Vector<Vector<Object>>();
    }

    // -------- General Methods -----------

    /** The number of rows in the request response object. */
    public int size() {
	return (rows.size());
    }

    /** Get the current value of MAXREC. */
    public int maxrec() {
	return (maxrec);
    }

    /** Set the value of MAXREC. */
    public void setMaxrec(int newval) {
	maxrec = newval;
    }


    // -------- Global Response Metadata -----------

    /** Add a brief description to the RequestResponse. */
    public void setDescription(String description) {
	this.description = description;
    }

    /** Get the RequestResponse description element. */
    public String getDescription() {
	return (description);
    }

    /**
     * Set the TYPE element of the RequestResponse, used to identify the
     * type of response table returned (e.g., "results").
     */
    public void setType(String type) {
	responseType = type;
    }

    /**
     * Get the TYPE element of the RequestResponse, used to identify the
     * type of response table returned (e.g., "results").
     */
    public String getType() {
	return (responseType);
    }

    /** Set the UTYPE element of the RequestResponse. */
    public void setUtype(String utype) {
	responseUtype = utype;
    }

    /** Get the UTYPE element of the RequestResponse. */
    public String getUtype() {
	return (responseUtype);
    }

    /**
     * Set an XML namespace to be used in XML serializations of the
     * request response.
     *
     * @param	prefix	The namespace prefix.
     * @param	url	The namespace url.
     */
    public void setXmlns(String prefix, String url) {
	this.xmlnsPrefix = prefix;
	this.xmlnsUrl = url;
    }


    // -------- Global INFOs -----------

    /**
     * Add a global Info element.  Infos are global for the entire
     * RequestResponse, and are not part of the table data.
     *
     * @param	key	String-valued key for the Info object.
     * @param	info	The Info object.
     */
    public void addInfo(String key, TableInfo info) {
	infos.put(key, info);
	atoms.add((Object) info);
    }

    /** Get a global Info element. */
    public TableInfo getInfo(String key) {
	return (infos.get(key));
    }

    /** Get an iterator to access the Infos list. */
    public Iterator infoIterator() {
        return ((Iterator) infos.entrySet().iterator());
    }


    /**
     * Utility to echo query params as Infos in a query response table.
     */
    public void echoParamInfos(ParamSet pset) {
	boolean debug = (pset.getParam("DEBUG") != null);
	String id, pname, value;

        for (Object o : pset.entrySet()) {
	    Map.Entry<String,Param> keyVal = (Map.Entry<String,Param>)o;
            Param p = keyVal.getValue();
            if (!p.isSet() || (p.getLevel() == ParamLevel.SERVICE && !debug))
		continue;

	    pname = p.getName();
	    value = p.stringValue();
	    if (debug && p.getType().contains(ParamType.RANGELIST))
		value += " (" + p.toString() + ")";

	    id = (p.getLevel() == ParamLevel.EXTENSION) ? "" : "INPUT:";
            id += pname;

            addInfo(id,
		new TableInfo(id, pname.equals("dbPassword") ? "*******" : value));
        }
    }


    // -------- Table GROUPs -----------

    /**
     * Add a table Group element.  Each group should contain a unique group
     * ID string.  Each member of a group should set the GroupID attribute
     * to this same value.  Params, Fields, or other Groups may be members
     * of a group.  Group instances are indexed by both GroupID and UTYPE.
     *
     * @param	group	The Group object.
     */
    public void addGroup(TableGroup group) {
	String key;
	if ((key = group.getGroupId()) != null)
	    groups.put(key, group);
	if ((key = group.getUtype()) != null)
	    groups.put(key, group);

	atoms.add((Object) group);
	nGroups++;
    }

    /** Get a table group element. */
    public TableGroup getGroup(String key) {
	return (groups.get(key));
    }

    /** Get an iterator to access the Group list. */
    public Iterator groupIterator() {
        return ((Iterator) groups.entrySet().iterator());
    }


    // -------- Table PARAMs -----------

    /**
     * Add a global table Param element.  Table parameters have a constant
     * value for the entire table.  A Param may logically be considered
     * to be a table field which has a constant value for every row of the
     * table.  The value of a Param is stored in the Param element, and is
     * not table data as for a Field; nonetheless the setValue method below
     * may be used to set the value of a Param as well as a Field.  Params
     * are indexed by both ID and UTYPE.
     *
     * @param	param	The Param object.
     */ 
    public void addParam(TableParam param) {
	// If we are adding a new param, set the index value.
	if (!params.containsKey(param.getUtype())) {
	    param.setIndex(nParams);
	}

	// Index by both ID and UTYPE.
	String key;
	if ((key = param.getId()) != null)
	    params.put(key, param);
	if ((key = param.getUtype()) != null)
	    params.put(key, param);

	atoms.add((Object) param);
	nParams++;
    }

    /** Get a table parameter element. */
    public TableParam getParam(String key) {
	return (params.get(key));
    }

    /** Get an iterator to access the Param list. */
    public Iterator paramIterator() {
        return ((Iterator) params.entrySet().iterator());
    }


    // -------- Table FIELDs -----------

    /**
     * Add a table field element.  Table fields define the table data,
     * i.e., data which may vary in each table row.  Unlike Param, the Field
     * object does not contain a data value; table fields are defined 
     * globally for the entire table, and field data is stored separately
     * in the data rows of the table.  Fields are indexed by both ID and
     * UTYPE.
     *
     * @param	field	The Field object.
     */
    public void addField(TableField field) {
	// If we are adding a new field, set the index value.  This will be
	// required later to assign a data value to a field in a table row.

        String id = field.getId();
        String utype = field.getUtype();
	if ((id.length() > 0 && !fields.containsKey(id)) || 
            utype.length() == 0 || !fields.containsKey(id)) 
        {
	    field.setIndex(nFields);
	}

	// Index by both ID and UTYPE.
	if (id != null && id.length() > 0)
	    fields.put(id, field);
	if (utype != null && utype.length() > 0)
	    fields.put(utype, field);

	atoms.add((Object) field);
	nFields++;
    }

    /** Get a table field element. */
    public TableField getField(String key) {
	return (fields.get(key));
    }

    /** Get the index of a table field element. */
    public int getFieldIndex(String key) throws DalServerException {
	TableField field = fields.get(key);
	if (field == null)
	    throw new DalServerException("unrecognized field " + "["+key+"]");
	return (field.getIndex());
    }

    /** Get an iterator to access the Field list. */
    public Iterator fieldIterator() {
        return ((Iterator) fields.entrySet().iterator());
    }

    /**
     * Set the string value of a table Field or Param.
     *
     * @param	key	Key of field to be set in "current" row.
     * @param	value	String value of field, or null to "unset" the value.
     */
    public void setValue(String key, String value) throws DalServerException {
	// Set the value of a Param.  There is no check if a value is 
	// changed or set multiple times.

	TableParam param = params.get(key);
	if (param != null) {
	    param.setValue(value);
	    return;
	}

	// Set the value of a Field.  Here also, an existing value may be
	// overridden.

	TableField field = fields.get(key);
	if (field == null)
	    throw new DalServerException("unrecognized field " +"["+key+"]");
	if (row == null)
	    throw new DalServerException("table row not set");

	SavotTD td = new SavotTD();
	td.setContent((value == null) ? "" : value);
	row.set(field.getIndex(), td);
    }

    /**
     * Set the integer value of a table Field or Param.
     *
     * @param	key	Key of field to be set in "current" row.
     * @param	value	New value of the field.
     */
    public void setValue(String key, int value) throws DalServerException {
	this.setValue(key, new Integer(value).toString());
    }

    /**
     * Set the double value of a table Field or Param.
     *
     * @param	key	Key of field to be set in "current" row.
     * @param	value	New value of the field.
     */
    public void setValue(String key, double value) throws DalServerException {
	this.setValue(key, new Double(value).toString());
    }

    /**
     * Get the string value of a table Field or Param.  If no value has been
     * set, null will be returned.
     *
     * @param	key	String key of field to be accessed in "current" row.
     */
    public String getValue(String key) throws DalServerException {
	TableParam param = params.get(key);
	if (param != null)
	    return (param.getValue());

	TableField field = fields.get(key);
	if (field == null)
	    throw new DalServerException("unrecognized field " +"["+key+"]");
	if (row == null)
	    throw new DalServerException("table row not set");

	SavotTD td = (SavotTD)row.get(field.getIndex());
	if (td == null)
	    return (null);
	else
	    return (td.getContent());
    }

    /**
     * Set the UCD attribute of a table Field or Param.
     *
     * @param	key	String key of field or param to be accessed.
     * @param	value	New UCD value.
     */
    public void setUcd(String key, String value) throws DalServerException {
	TableParam param = params.get(key);
	if (param != null)
	    param.setUcd(value);

	TableField field = fields.get(key);
	if (field == null)
	    throw new DalServerException("unrecognized field " +"["+key+"]");
	else
	    field.setUcd(value);
    }

    /**
     * Set the Unit attribute of a table Field or Param.
     *
     * @param	key	String key of field or param to be accessed.
     * @param	value	New Unit value.
     */
    public void setUnit(String key, String value) throws DalServerException {
	TableParam param = params.get(key);
	if (param != null)
	    param.setUnit(value);

	TableField field = fields.get(key);
	if (field == null)
	    throw new DalServerException("unrecognized field " +"["+key+"]");
	else
	    field.setUnit(value);
    }

    /**
     * Set the CsvKeyword attribute of a table Field or Param.
     *
     * @param	key	String key of field or param to be accessed.
     * @param	value	New Unit value.
     */
    public void setCsvKeyword(String key, String value)
	throws DalServerException {

	TableParam param = params.get(key);
	if (param != null)
	    param.setCsvKeyword(value);

	TableField field = fields.get(key);
	if (field == null)
	    throw new DalServerException("unrecognized field " +"["+key+"]");
	else
	    field.setCsvKeyword(value);
    }


    // -------- Table DATA -----------
    // Vector is used here so we can pass the data directly to SAVOT.

    /** Add a new table row. */
    public int addRow() throws DalOverflowException {
	if (rows.size() >= maxrec) {
	    TableInfo info = getInfo("QUERY_STATUS");
	    info.setValue("OVERFLOW");
	    throw new DalOverflowException("maxrec=" + maxrec);
	}

	row = new java.util.Vector<Object>(nFields);
	row.setSize(nFields);
	rows.add(row);

	return (rows.size());
    }

    /** Delete the end (most recently added) row. */
    public void deleteRow() {
	int nrows = rows.size();
	if (nrows > 0) {
	    rows.remove(nrows-1);
	    row = rows.get(rows.size()-1);
	}
    }

    /** Set the table row to be used for sets and gets. */
    public void setRow(int index) {
	row = rows.get(index);
    }

    /** Get the number of table rows. */
    public int getRowCount() {
	return (rows.size());
    }

    // -------- Table Ordering -----------

    /**
     * Compute a default SCORE heuristic for the request response table.
     * The score heuristic is a measure of how well a given dataset (table
     * row) matches the query; larger values indicate a better match.
     * Since we are doing this for the entire table, we normalize the
     * result to a maximum value of 1.0.
     *
     * <p>The scoring heuristic used here is a simple generic one based
     * on standard query parameters and metadata.  Individual rows or
     * records (candidate datasets) are scored based on how well they
     * match the POS, BAND, and TIME values specified by the query
     * parameters.  In addition, records which are calibrated or have
     * a higher SNR receive a higher score.  While the SCORE values
     * returned by this routine are normalized to 1.0, the absolute
     * SCORE value is meaningless except for order comparision to
     * another value computed for the same query.
     *
     * <p>As a value-added feature, services may wish to use their own
     * custom score heuristic instead, which can know more about the specific
     * data collections being scored.
     *
     * @param	params		The query request parameter set defining the
     *				goal which we score against.
     *
     * @param	fieldName	The key of the table field to which the score
     *				value should be saved (normally "Score").
     *				This must already exist in the table being
     *				scored.  Any previous value is overwritten.
     */
    @SuppressWarnings("unchecked")
    public void score(ParamSet params, String fieldName)
	throws DalServerException {

	// Internal data.
	final double arcsec = 0.000277;

	// Max score value before normalization.
	double maxScore = 0.0;

	// Score each row (individual candidate dataset).
	for (int i = 0;  i < rows.size();  i++) {
	    this.setRow(i);
	    double score = 0.0;

	    for (Object o : params.entrySet()) {
		Map.Entry<String,Param> keyVal = (Map.Entry<String,Param>) o;
		Param p = keyVal.getValue();
		String pName = p.getName();
		if (!p.isSet())
		    continue;

		if (pName.equalsIgnoreCase("POS")) {
		    String loc = this.getValue("SpatialLocation");
		    if (loc == null)
			continue;

		    RangeList rl = p.rangeListValue();
		    double pos1 = rl.doubleValue(0);
		    double pos2 = rl.doubleValue(1);

		    StringTokenizer tok = new StringTokenizer(loc);
		    double loc1 = new Double(tok.nextToken()).doubleValue();
		    double loc2 = new Double(tok.nextToken()).doubleValue();

		    // The POS score falls to 37% at 5 arcsec.
		    score += (1.0 / Math.exp(Math.abs(pos1-loc1) / (5*arcsec)));
		    score += (1.0 / Math.exp(Math.abs(pos2-loc2) / (5*arcsec)));

		} else if (pName.equalsIgnoreCase("BAND")) {
		    // Use BAND only if numeric bandpasses are used.
		    String bandStr = this.getValue("SpectralLocation");
		    double band;
		    Range r;

		    if (bandStr == null)
			continue;
		    try {
			band = new Double(bandStr).doubleValue();
		    } catch (NumberFormatException ex) {
			continue;
		    }

		    try {
			RangeList rl = p.rangeListValue();
			r = rl.getRange(0);
		    } catch (DalServerException ex) {
			continue;
		    }
		    if (!r.numeric)
			continue;

		    double val1 = r.doubleValue1();
		    double val2 = r.doubleValue2();
		    double bandLoc = (val1 + val2) / 2.0;
		    double scale = Math.abs(val1 - val2) / bandLoc;
		    if (scale < 1.0)
			scale = 5.0;

		    // The BAND metric scales with the bandpass, but otherwise
		    // it is not clear what to use for a scale here.

		    score += (1.0 /
			Math.exp(Math.abs(band-bandLoc) / (scale*bandLoc)));


		} else if (pName.equalsIgnoreCase("TIME")) {
		    // The TIME parameter specifies an ISO UTC time.
		    // TimeLocation in the candidate dataset is in MJD.

		    // Get the dataset MJD value.
		    String timeStr = this.getValue("TimeLocation");
		    double obsTime;
		    Range r;

		    if (timeStr == null)
			continue;
		    try {
			obsTime = new Double(timeStr).doubleValue();
		    } catch (NumberFormatException ex) {
			continue;
		    }

		    // Get the query TIME param as a Java Date object.
		    try {
			RangeList rl = p.rangeListValue();
			r = rl.getRange(0);
		    } catch (DalServerException ex) {
			continue;
		    }
		    if (!r.isoDate)
			continue;

		    // Convert the Java (Unix) Date values to MJD.
		    DateParser dp = new DateParser();
		    double time1 = dp.getMJD(r.dateValue1());
		    double time2 = dp.getMJD(r.dateValue2());
		    double refTime = (time1 + time2) / 2.0;

		    // Scale score to units of years.
		    score += (1.0 /
			Math.exp(Math.abs(obsTime-refTime) / 360.0));
		}
	    }

	    // Allow a good SNR to bias the ordering.
	    try {
		String snrStr = this.getValue("DerivedSNR");
		if (snrStr != null) {
		    double snr = new Double(snrStr).doubleValue();
		    score += snr;
		}
	    } catch (NumberFormatException ex) {
		// Skip if has no value
	    } catch (DalServerException ex) {
		// Skip if SNR not defined
	    } 

	    // Favor calibrated data.
	    try {
		String fluxCalib =
		    this.getValue("Points.Flux.Accuracy.Calibration");
		if (fluxCalib != null)
		    if (fluxCalib.equalsIgnoreCase("Absolute"))
			score += 0.3;
	    } catch (DalServerException ex) {
		// Skip if Calibration not defined
	    }

	    // Set the computed SCORE value in the table row.
	    this.setValue(fieldName, new Double(score).toString());

	    // Keep track of the maximum score.
	    if (score > maxScore)
		maxScore = score;
	}

	// Normalize the score to 1.0 for the whole table.
	for (int i = 0;  i < rows.size();  i++) {
	    this.setRow(i);
	    double score = new Double(this.getValue(fieldName)).doubleValue();
	    score = (maxScore > 0) ? score / maxScore : 0.0;
	    this.setValue(fieldName, new Double(score).toString());
	}
    }

    /**
     * Sort a request response on the given key.
     *
     * @param key	Field to be used for sorting.
     * @param order	Sort order: positive for ascending order,
     *			negative for descending order.
     *
     */
    @SuppressWarnings("unchecked")
    public void sort(String key, int order) throws DalServerException {
	Comparator<Vector> comparator = new Compare(key, order);
	Collections.sort((Vector)rows, comparator);
    }

    /**
     * Implementation of the List Comparator interface, used to sort a
     * request response table.
     */
    public class Compare implements Comparator<Vector> {

	/** The index of the table field to be used for sorting. */
	private int fieldIndex;

	/** Sort order, positive=ascending, negative=descending. */
	private int order;

	/** Is this a lexicographic or numeric sort key? */
	private boolean isNumeric;

	// A date sort key would be useful too, but since RequestResponse
	// is based on VOTable and VOTable does not provide a date datatype,
	// it doesn't appear we can provide that.

	/** Create a new Compare instance for the named field. */
	public Compare(String key, int order) throws DalServerException {
	    TableField field = RequestResponse.this.getField(key);
	    if (field == null)
		throw new DalServerException("unknown field " + "["+key+"]");

	    this.fieldIndex = field.getIndex();
	    this.order = order;

	    String datatype = field.getDataType().toUpperCase();
	    this.isNumeric = 
		datatype.equals("DOUBLE") ||
		datatype.equals("FLOAT") ||
		datatype.equals("INT") ||
		datatype.equals("LONG") ||
		datatype.equals("SHORT");
	}

	/** Compare two table rows for their sort order. */
	@SuppressWarnings("unchecked")
	public int compare(Vector row1, Vector row2) {
	    String val1 = ((Vector<SavotTD>)row1).get(fieldIndex).getContent();
	    String val2 = ((Vector<SavotTD>)row2).get(fieldIndex).getContent();

	    if (this.isNumeric)
		return (new Double(val1).compareTo(new Double(val2)) * order);
	    else 
		return (val1.compareTo(val2) * order);
	}

	/** Compare the sort key of two table rows for equality. */
	@SuppressWarnings("unchecked")
	public boolean equals(Vector row1, Vector row2) {
	    String val1 = ((Vector<SavotTD>)row1).get(fieldIndex).getContent();
	    String val2 = ((Vector<SavotTD>)row2).get(fieldIndex).getContent();

	    if (this.isNumeric)
		return (new Double(val1).equals(new Double(val2)));
	    else 
		return (val1.equals(val2));
	}
    }


    // -------- Serializers -----------

    // Currently the only RequestResponse serializations supported are
    // Text, VOTable and CSV.  "Native" format is supported as a pass-through.
    // Support for FITS is planned. For the queryData operation, only
    // VOTable format is supported.

    /**
     * Create a HTML representation of the RequestResponse object and
     * write it to the given OutputStream.
     *
     * <p>Table fields (nonscalar TableParams) which have a non-null CSV
     * value are output to create the CSV.  The CSV value has two elements
     * formatted as "index;label".  Index specifies the CSV column
     * order.  Label is either an explicit column label, or "@col"
     * where "col" is the name of the column of the data model to be
     * used for the CSV column label.  The data model specifies the
     * default set of fields to be output for the CSV format, however
     * the default fields or labels may be overriden by setting the CSV
     * attributes of the table fields in the RequestResponse.
     * See {@link dalserver.TableParam#setCsvKeyword}.
     *
     * @param	out	OutputStream to which the VOTable is to be written.
     */
    @SuppressWarnings("unchecked")
    public void writeHTML(OutputStream out) throws DalServerException {
	final int MAXCOLS = 1024;
	String[] fieldKeys = new String[MAXCOLS];
	String[] fieldLabels = new String[MAXCOLS];
	int nfields=0, maxIndex=0, totchars=0;;
	boolean useCsv = false;

	for (int i=0;  i < MAXCOLS;  i++)
	    fieldKeys[i] = fieldLabels[i] = null;

	PrintWriter output = new PrintWriter(out);
	Object obj=null, lastObj=null;
	String delimChar = ",";

	// Check whether the table has CSV tags; if so use these here too.
	for (Iterator i = fields.entrySet().iterator();  i.hasNext();  ) {
	    Map.Entry me = (Map.Entry) i.next();
	    obj = (Object) me.getValue();
	    if (obj == lastObj)
		continue;

	    TableField p = (TableField) obj;
	    String csv = p.getCsvKeyword();
	    if (csv != null) {
		useCsv = true;
		break;
	    }
	}

	// Get a list of the Table columns and their labels.
	for (Iterator i = fields.entrySet().iterator();  i.hasNext();  ) {
	    Map.Entry me = (Map.Entry) i.next();
	    obj = (Object) me.getValue();
	    if (obj == lastObj)
		continue;

	    TableField p = (TableField) obj;
	    String csv = p.getCsvKeyword();
	    if (useCsv && csv == null)
		continue;

	    if (useCsv) {
		String[] items = csv.split(";", 2);
		if (items.length < 2)
		    continue;
		int index = new Integer(items[0]).intValue();
		String label = items[1];

		if (label.equalsIgnoreCase("@Id"))
		    label = p.getId();
		else if (label.equalsIgnoreCase("@Utype"))
		    label = p.getUtype();
		else if (label.equalsIgnoreCase("@Ucd"))
		    label = p.getUcd();
		else if (label.equalsIgnoreCase("@FITS"))
		    label = p.getFitsKeyword();
		else if (label.equalsIgnoreCase("@Description"))
		    label = p.getDescription();

		if (index < MAXCOLS) {
		    fieldKeys[index] = p.getId();
		    fieldLabels[index] = label;
		    nfields++;

		    if (maxIndex < index)
			maxIndex = index;
		}
	    } else {
		fieldKeys[nfields] = p.getId();
		fieldLabels[nfields] = p.getId();
		maxIndex = nfields;
		nfields++;
	    }

	    lastObj = obj;
	}

	// Estimate the required table width.
	for (int j=0, nchars=0;  j < rows.size();  j++) {
	    this.setRow(j);
	    if (j > 4)
		break;

	    for (int i=0;  i <= maxIndex;  i++) {
		String key = fieldKeys[i];
		if (key == null)
		    continue;

		String value = getValue(key);
		nchars += value.length();
	    }

	    if (nchars > totchars)
		totchars = nchars;
	}

	// Output the HTML header.
	output.println("<!DOCTYPE html>");
	output.println("<html>");
	output.println("<head>");
	output.println("<style>");
	output.println("body { background-color:WhiteSmoke; }");
	output.println("table,th,td");
	output.println("{ border:1px solid DeepSkyBlue; border-collapse:collapse; ");
	output.println("white-space: nowrap; }");
	output.println("th,td");
	output.println("{ padding:5px; }");
	output.println("</style>");
	output.println("</head>");
	output.println("<body>");

	// Start the table.
	String tabledef = "".format("<table style=\"width:%dpx\">",
	    totchars * 2 * 125 / 100);
	output.println(tabledef);

	// Output the column description line.
	output.println("<tr BGCOLOR=\"#99CCFF\">");
	for (int i=0;  i <= maxIndex;  i++) {
	    String label = fieldLabels[i];
	    if (label != null)
		output.println("<th>" + label + "</th>");
	}
	output.println("</tr>");

	// Output the table data.
	for (int j=0;  j < rows.size();  j++) {
	    if (j % 2 == 0)
		output.println("<tr BGCOLOR=\"#FFFFFF\">");
	    else
		output.println("<tr BGCOLOR=\"#EEEEEE\">");
	    this.setRow(j);

	    for (int i=0;  i <= maxIndex;  i++) {
		String key = fieldKeys[i];
		if (key == null)
		    continue;

		output.println("<td>");
		String value = getValue(key);

		if (key.equals("obs_title")) {
		    try {
			String acref = getValue("access_url");
			String url = "<a href=\"" + acref + "\">" + value + "</a>";
			output.println(url);
		    } catch (DalServerException ex) {
			output.println(value);
		    }
		} else
		    output.println(value);

		output.println("</td>");
	    }

	    output.println("</tr>");
	}

	output.println("</table>");
	output.println("</body>");
	output.println("</html>");

	if (output != null)
	    output.close();
    }

    /**
     * Create a Text representation of the RequestResponse object and
     * write it to the given OutputStream.
     *
     * <p>Table fields (nonscalar TableParams) which have a non-null CSV
     * value are output to create the CSV.  The CSV value has two elements
     * formatted as "index;label".  Index specifies the CSV column
     * order.  Label is either an explicit column label, or "@col"
     * where "col" is the name of the column of the data model to be
     * used for the CSV column label.  The data model specifies the
     * default set of fields to be output for the CSV format, however
     * the default fields or labels may be overriden by setting the CSV
     * attributes of the table fields in the RequestResponse.
     * See {@link dalserver.TableParam#setCsvKeyword}.
     *
     * @param	out	OutputStream to which the VOTable is to be written.
     */
    @SuppressWarnings("unchecked")
    public void writeText(OutputStream out) throws DalServerException {
	final int MAXCOLS = 1024;
	String[] fieldKeys = new String[MAXCOLS];
	String[] fieldLabels = new String[MAXCOLS];
	int nfields=0, maxIndex=0;
	boolean useCsv = false;

	for (int i=0;  i < MAXCOLS;  i++)
	    fieldKeys[i] = fieldLabels[i] = null;

	PrintWriter output = new PrintWriter(out);
	Object obj=null, lastObj=null;
	String delimChar = ",";

	// Check whether the table has CSV tags; if so use these here too.
	for (Iterator i = fields.entrySet().iterator();  i.hasNext();  ) {
	    Map.Entry me = (Map.Entry) i.next();
	    obj = (Object) me.getValue();
	    if (obj == lastObj)
		continue;

	    TableField p = (TableField) obj;
	    String csv = p.getCsvKeyword();
	    if (csv != null) {
		useCsv = true;
		break;
	    }
	}

	// Get a list of the Table columns and their labels.
	for (Iterator i = fields.entrySet().iterator();  i.hasNext();  ) {
	    Map.Entry me = (Map.Entry) i.next();
	    obj = (Object) me.getValue();
	    if (obj == lastObj)
		continue;

	    TableField p = (TableField) obj;
	    String csv = p.getCsvKeyword();
	    if (useCsv && csv == null)
		continue;

	    if (useCsv) {
		String[] items = csv.split(";", 2);
		int index = new Integer(items[0]).intValue();
		String label = items[1];

		if (label.equalsIgnoreCase("@Id"))
		    label = p.getId();
		else if (label.equalsIgnoreCase("@Utype"))
		    label = p.getUtype();
		else if (label.equalsIgnoreCase("@Ucd"))
		    label = p.getUcd();
		else if (label.equalsIgnoreCase("@FITS"))
		    label = p.getFitsKeyword();
		else if (label.equalsIgnoreCase("@Description"))
		    label = p.getDescription();

		if (index < MAXCOLS) {
		    fieldKeys[index] = p.getId();
		    fieldLabels[index] = label;
		    nfields++;

		    if (maxIndex < index)
			maxIndex = index;
		}
	    } else {
		fieldKeys[nfields] = p.getId();
		fieldLabels[nfields] = p.getId();
		maxIndex = nfields;
		nfields++;
	    }

	    lastObj = obj;
	}

	// Create a new simple text table formatter.
	TableFormatter tf = new SimpleTableFormatter(true, false);

	// Output the column description line.
	tf.nextRow();
	for (int i=0;  i <= maxIndex;  i++) {
	    String label = fieldLabels[i];
	    if (label == null)
		continue;

	    tf.nextCell();
	    tf.addLine(" " + label + " ");
	}

	// Format the text data.
	for (int j=0;  j < rows.size();  j++) {
	    this.setRow(j);
	    tf.nextRow();

	    for (int i=0;  i <= maxIndex;  i++) {
		String key = fieldKeys[i];
		if (key == null)
		    continue;

		tf.nextCell();
		tf.addLine(" " + this.getValue(key) + " ");
	    }
	}

	// Output the text data.
	String[] table = tf.getFormattedTable();
	for (int j=0;  j < table.length;  j++) {
	    String text = table[j];
	    output.println(text);
	}

	if (output != null)
	    output.close();
	table = null;
	tf = null;
    }

    /**
     * Create a CSV representation of the RequestResponse object and
     * write it to the given OutputStream.
     *
     * <p>Table fields (nonscalar TableParams) which have a non-null CSV
     * value are output to create the CSV.  The CSV value has two elements
     * formatted as "index;label".  Index specifies the CSV column
     * order.  Label is either an explicit column label, or "@col"
     * where "col" is the name of the column of the data model to be
     * used for the CSV column label.  The data model specifies the
     * default set of fields to be output for the CSV format, however
     * the default fields or labels may be overriden by setting the CSV
     * attributes of the table fields in the RequestResponse.
     * See {@link dalserver.TableParam#setCsvKeyword}.
     *
     * @param	out	OutputStream to which the VOTable is to be written.
     */
    @SuppressWarnings("unchecked")
    public void writeCsv(OutputStream out) throws DalServerException {
	final int MAXCOLS = 1024;
	String[] fieldKeys = new String[MAXCOLS];
	String[] fieldLabels = new String[MAXCOLS];
	int nfields=0, maxIndex=0;
	boolean useCsv = false;

	for (int i=0;  i < MAXCOLS;  i++)
	    fieldKeys[i] = fieldLabels[i] = null;

	PrintWriter output = new PrintWriter(out);
	Object obj=null, lastObj=null;
	String delimChar = ",";

	// Check whether the table has CSV field tags.
	for (Iterator i = fields.entrySet().iterator();  i.hasNext();  ) {
	    Map.Entry me = (Map.Entry) i.next();
	    obj = (Object) me.getValue();
	    if (obj == lastObj)
		continue;

	    TableField p = (TableField) obj;
	    String csv = p.getCsvKeyword();
	    if (csv != null) {
		useCsv = true;
		break;
	    }
	}

	// Get a list of the CSV columns and their labels.
	for (Iterator i = fields.entrySet().iterator();  i.hasNext();  ) {
	    Map.Entry me = (Map.Entry) i.next();
	    obj = (Object) me.getValue();
	    if (obj == lastObj)
		continue;

	    TableField p = (TableField) obj;
	    String csv = p.getCsvKeyword();
	    if (useCsv && csv == null)
		continue;

	    if (useCsv) {
		String[] items = csv.split(";", 2);
		int index = new Integer(items[0]).intValue();
		String label = items[1];

		if (label.equalsIgnoreCase("@Id"))
		    label = p.getId();
		else if (label.equalsIgnoreCase("@Utype"))
		    label = p.getUtype();
		else if (label.equalsIgnoreCase("@Ucd"))
		    label = p.getUcd();
		else if (label.equalsIgnoreCase("@FITS"))
		    label = p.getFitsKeyword();
		else if (label.equalsIgnoreCase("@Description"))
		    label = p.getDescription();

		if (index < MAXCOLS) {
		    fieldKeys[index] = p.getId();
		    fieldLabels[index] = label;
		    nfields++;

		    if (maxIndex < index)
			maxIndex = index;
		}
	    } else {
		fieldKeys[nfields] = p.getId();
		fieldLabels[nfields] = p.getId();
		maxIndex = nfields;
		nfields++;
	    }

	    lastObj = obj;
	}

	// Output the CSV header.
	StringBuilder line = new StringBuilder();
	int ncols = 0;

	for (int i=0;  i <= maxIndex;  i++) {
	    String label = fieldLabels[i];
	    if (label == null)
		continue;

	    if (ncols++ > 0)
		line.append(delimChar);
	    line.append(label);
	}
	output.println(line.toString());

	// Output the CSV data.
	for (int j=0;  j < rows.size();  j++) {
	    this.setRow(j);

	    line = new StringBuilder();
	    ncols = 0;

	    for (int i=0;  i <= maxIndex;  i++) {
		String key = fieldKeys[i];
		if (key == null)
		    continue;

		String value = this.getValue(key);
		boolean quoteit = (value.contains(delimChar));

		if (ncols++ > 0)
		    line.append(delimChar);
		if (quoteit)
		    line.append("\"");
		line.append(value);
		if (quoteit)
		    line.append("\"");
	    }

	    output.println(line.toString());
	}

	if (output != null)
	    output.close();
    }

    /**
     * Create a VOTable representation of the RequestResponse object and
     * write it to the given OutputStream.
     *
     * @param	out	OutputStream to which the VOTable is to be written.
     */
    public void writeVOTable(OutputStream out) throws DalServerException {
	SavotWriter writer = new SavotWriter();
	writer.generateDocument(this.createSavotVOTable(), out);
    }

    /**
     * Create a SAVOT VOTable representation of the RequestResponse object.
     * A new VOTable object is created containing a single RESOURCE containing
     * the RequestResponse table.  This method is specific to SAVOT, to
     * allow use of the SAVOT classes to make further modifications to the
     * generated VOTable.  The parent RequestResponse object is not
     * modified (except for filling in some structural metadata if missing),
     * however data is shared with the VOTable object, and the client should
     * not modify any shared data elements such as Infos, Groups, Params,
     * Fields, or table data.
     */
    public SavotVOTable createSavotVOTable() throws DalServerException {
	SavotVOTable vot = new SavotVOTable();

	// Set global VOTable metadata (namespaces, version, etc.)
	//if (this.xmlnsUrl != null)
	//   if (this.xmlnsPrefix == null)
	//	vot.setXmlns(this.xmlnsUrl);
	//   else
	//	vot.setXmlns(this.xmlnsPrefix + "," + this.xmlnsUrl);

	// Add the RequestResponse as a RESOURCE containing a single TABLE.
	this.addResource(vot);

	return (vot);
    }

    /**
     * Add a RESOURCE representation of the RequestResponse object to an
     * existing VOTable.  This method is specific to SAVOT, to allow use 
     * of the SAVOT classes to make further modifications to the generated
     * VOTable.  The parent RequestResponse object is not modified (except
     * for filling in some structural metadata if missing), however data is
     * shared with the VOTable object, and the client should not modify any
     * shared data elements such as Infos, Groups, Params, Fields, or table
     * data.
     *
     * @param	vot	The SAVOT VOTable object to which the resource
     *			will be added.
     */
    public void addResource(SavotVOTable vot) throws DalServerException {
	// Create the RequestResponse RESOURCE element.
	SavotResource res = new SavotResource();
	vot.getResources().addItem((Object) res);

	// Initialize the new RESOURCE element.
	res.init();
	if (responseType != null)
	    res.setType(responseType);
	if (responseUtype != null)
	    res.setUtype(xmlnsPrefix + ":" + responseUtype);
	if (description != null)
	    res.setDescription(description);

	// Add any INFOs.
	if (infos.size() > 0) {
	    InfoSet infoSet = new InfoSet();

	    for (Iterator i = infos.entrySet().iterator();  i.hasNext();  ) {
		Map.Entry me = (Map.Entry) i.next();
		TableInfo info = (TableInfo) me.getValue();
		infoSet.addItem((Object) info);
	    }

	    res.setInfos(infoSet);
	}

	// Quit now if we have no table data.
	if (nParams == 0 && nFields == 0)
	    return;

	// Create the RequestResponse TABLE element.
	SavotTable table = new SavotTable();
	if (responseUtype != null)
	    table.setUtype(xmlnsPrefix + ":" + responseUtype);

	TableSet tableSet = new TableSet();
	tableSet.addItem((Object) table);
	res.setTables(tableSet);

	// Initialize forward refs to the empty set for all Group elements.
	for (Iterator i = groups.entrySet().iterator();  i.hasNext();  ) {
	    Map.Entry me = (Map.Entry) i.next();
	    TableGroup group = (TableGroup) me.getValue();

	    group.setFieldsRef(new cds.savot.model.FieldRefSet());
	    group.setGroups(new cds.savot.model.GroupSet());
	    group.setParams(new cds.savot.model.ParamSet());
	    group.setParamsRef(new cds.savot.model.ParamRefSet());
	}

	// Set up the forward refs for any GROUP elements.
	for (Iterator i = atoms.iterator();  i.hasNext();  ) {
	    Object atom = i.next();
	    TableGroup group = null;
	    String groupId = null;

	    if (atom instanceof TableGroup) {
		TableGroup element = (TableGroup) atom;
		if ((groupId = element.getGroupId()) == null)
		    continue;
		if ((group = this.getGroup(groupId)) == null)
		    throw new DalServerException("bad groupId=" + groupId);

		if (!group.getGroupId().equals(groupId))
		    group.getGroups().addItem(atom);

	    } else if (atom instanceof TableParam) {
		TableParam element = (TableParam) atom;
		if ((groupId = element.getGroupId()) == null)
		    continue;
		if ((group = this.getGroup(groupId)) == null)
		    throw new DalServerException("bad groupId=" + groupId);

		SavotParam p = element.newSavotParam(xmlnsPrefix);
		group.getParams().addItem(p);

	    } else if (atom instanceof TableField) {
		TableField element = (TableField) atom;
		if ((groupId = element.getGroupId()) == null)
		    continue;
		if ((group = this.getGroup(groupId)) == null)
		    throw new DalServerException("bad groupId=" + groupId);

		// For a Field, ensure we have a valid FieldRef.
		String fieldId = element.getId();
		if (fieldId == null) {
		    fieldId = new String("Field" + element.getIndex());
		    element.setId(fieldId);
		}
		SavotFieldRef fieldRef = new SavotFieldRef();
		fieldRef.setRef(fieldId);

		group.getFieldsRef().addItem(fieldRef);
	    }
	}

	// Add any GROUP elements to the table.
	Object obj = null, lastObj = null;
	cds.savot.model.GroupSet groupSet = new cds.savot.model.GroupSet();
	for (Iterator i = groups.entrySet().iterator();  i.hasNext();  ) {
	    Map.Entry me = (Map.Entry) i.next();
	    obj = (Object) me.getValue();
	    // If have duplicate entries for the same object, add only one.
	    if (obj != lastObj) {
		SavotGroup g = ((TableGroup)obj).newSavotGroup(xmlnsPrefix);
		groupSet.addItem(g);
		lastObj = obj;
	    }
	}
	table.setGroups(groupSet);

	// Add any PARAM elements (not already in a Group) to the table.
	cds.savot.model.ParamSet paramSet = new cds.savot.model.ParamSet();
	for (Iterator i = params.entrySet().iterator();  i.hasNext();  ) {
	    Map.Entry me = (Map.Entry) i.next();
	    obj = (Object) me.getValue();
	    TableParam param = (TableParam) obj;
	    if (param.getGroupId() == null && obj != lastObj) {
		SavotParam p = param.newSavotParam(xmlnsPrefix);
		paramSet.addItem(p);
		lastObj = obj;
	    }
	}
	table.setParams(paramSet);

	// Add any FIELD elements to the table.
	cds.savot.model.FieldSet fieldSet = new cds.savot.model.FieldSet();
	for (Iterator i = fields.entrySet().iterator();  i.hasNext();  ) {
	    Map.Entry me = (Map.Entry) i.next();
	    obj = (Object) me.getValue();
	    if (obj != lastObj) {
		SavotField f = ((TableField)obj).newSavotField(xmlnsPrefix);
		fieldSet.addItem(f);
		lastObj = obj;
	    }
	}
	table.setFields(fieldSet);


	// Add any table data (Field data).
	cds.savot.model.TRSet trSet = new cds.savot.model.TRSet();
	for (int i=0;  i < rows.size();  i++) {
	    Vector<Object> v = rows.get(i);

	    // Ensure that we don't have any null elements in the row as
	    // this will cause problems with the VOTable serialization.
	    // If the client neglects to set a field value, this ensures
	    // that an empty table data element is output.

	    for (int j=0;  j < v.size();  j++)
		if (v.get(j) == null) {
		    SavotTD td = new SavotTD();
		    td.setContent("");
		    v.set(j, td);
		}

	    // Store the row vector into a TDSet.
	    cds.savot.model.TDSet tdSet = new cds.savot.model.TDSet();
	    tdSet.setItems(rows.get(i));

	    SavotTR savotTR = new SavotTR();
	    savotTR.setTDSet(tdSet);
	    trSet.addItem((Object) savotTR);
	}

	SavotTableData tableData = new SavotTableData();
	tableData.setTRs(trSet);

	SavotData data = new SavotData();
	data.setTableData(tableData);

	table.setData(data);
    }

    // -------- Testing -----------

    /**  Exercise the RequestResponse class. */
    public static void main(String[] args) {
	try {
	    SsapParamSet params = new SsapParamSet();
	    RequestResponse r = new RequestResponse();
	    SsapKeywordFactory ssap = new SsapKeywordFactory(r, "main", "1.1");
	    String id, key;

	    // Set global metadata.
	    r.setDescription("Builtin test for the RequestResponse class");
	    r.setType("testResults");

	    // Set some sample INFOs.
	    r.addInfo(key="QUERY_STATUS", new TableInfo(key, "OK"));
	    r.addInfo(key="POS", new TableInfo(key, "12.0,0.0"));

	    // Query component data model.
	    r.addGroup(ssap.newGroup("Query"));
	    r.addField(ssap.newField("Score"));

	    // TARGET component data model.
	    r.addGroup(ssap.newGroup("Target"));
	    r.addField(ssap.newField("TargetName"));
	    r.addField(ssap.newField("TargetClass"));
	    r.addField(ssap.newField("Redshift"));

	    // DERIVED component data model.
	    r.addGroup(ssap.newGroup("Derived"));
	    r.addField(ssap.newField("DerivedSNR"));

	    // DATAID component data model.
	    r.addGroup(ssap.newGroup("DataID"));
	    r.addField(ssap.newField("Title"));
	    r.addParam(ssap.newParam("Creator", "Sloan Sky Survey"));
	    r.addParam(ssap.newParam("Collection", "SDSS-DR5"));
	    r.addField(ssap.newField("CreatorDID"));

	    // Add some user-defined global PARAMs and FIELDs.
	    r.addParam(new TableParam("UserParam1", "UserParam1 value",
		"UserParam1", null, "char", "*", null, "user:Foo.Bar",
		"pos.eq", "User defined parameter 1"));

	    r.addParam(new TableParam("UserParam2", "UserParam2 value",
		"UserParam2", null, "char", "*", null, "user:Alpha.Beta",
		"phot.flux", "User defined parameter 2"));

	    r.addField(new TableField("UserField1",
		"UserField1", null, "char", "*", null, "user:Aperture.Diam",
		"instr.fov", "User defined field 1"));

	    // Set the table data.
	    r.addRow();
	    r.setValue("Score", "0.0");
	    r.setValue("TargetName", "target1");
	    r.setValue("TargetClass", "target1-class");
	    r.setValue("Redshift", "2.3");
	    r.setValue("DerivedSNR", "3.4");
	    r.setValue("Title", "Sample title 1");
	    r.setValue("CreatorDID", "ivo://myvo.org/target1");
	    r.setValue("UserField1", "User Field 1 value 1");

	    r.addRow();
	    r.setValue("Score", "0.0");
	    r.setValue("TargetName", "target2");
	    r.setValue("TargetClass", "target2-class");
	    r.setValue("Redshift", "1.5");
	    r.setValue("DerivedSNR", "2.7");
	    r.setValue("Title", "Sample title 2");
	    r.setValue("CreatorDID", "ivo://myvo.org/target2");
	    r.setValue("UserField1", "User Field 1 value 2");

	    // Compute the score.
	    r.score(params, "Score");

	    // Sort on the Score value, in descending order.
	    r.sort("Score", -1);

	    // Write the RequestResponse as a VOTable.
	    OutputStream out = new FileOutputStream("_output.vot");
	    r.writeVOTable(out);
	    out.close();

	} catch (DalServerException ex) {
	    System.out.println ("DalServerException: " + ex.getMessage());
	} catch (DalOverflowException ex) {
	    System.out.println ("DalOverflowException: " + ex.getMessage());
	} catch (InvalidDateException ex) {
	    System.out.println ("InvalidDateException: " + ex.getMessage());
	} catch (FileNotFoundException ex) {
	    System.out.println ("Cannot open file");
	} catch (IOException ex) {
	    System.out.println ("File i/o exception");
	}
    }
}
