/*
 * SsapService.java
 * $ID*
 */

package dalserver.ssa;

import java.io.*;
import java.net.*;
import java.util.*;
import java.sql.*;
import java.util.zip.GZIPInputStream;

import dalserver.*;
import dalserver.ssa.*;

/**
 * This generic class implements the service operations for a DALServer SSA
 * service, independent of the transport protocol (HTTP).
 *
 * To configure a data service for a Spectrum data collection, a Spectrum
 * table (DBMS table) describing each spectrum dataset is required.  The
 * generic SSA service implementation uses this metadata to implement discovery
 * queries and to access spectrum datasets.  An additional Spectrum Data table
 * may optionally be provided containing data content for the data element(s)
 * of the spectrum.  Finally the data service instance must be configured via
 * the DALServer service configuration mechanism.
 *
 * In most cases this purely data-driven approach will be sufficient to
 * provide a SSA service for an Spectrum data collection.  In more advanced
 * cases it is possible to subclass the methods of the generic SSA service
 * to provide customized functionality.
 */
public class SsapService {
    /**
     * The service name, used to identify resources associated with this
     * service instance.
     */
    protected String serviceName = "ssap";

    /**
     * The service class assumed by the caller, e.g., if the service
     * implementation supports multiple service protocols such as SCS
     * and TAP.
     */
    protected String serviceClass = "ssap";

    /** The service or protocol version. */
    protected String serviceVersion;

    /** The minor service version (for custom services). */
    protected String minorVersion = "generic";

    /** The type of DBMS to be accessed, if any. */
    protected String dbType;

    /** The name of the database to be accessed. */
    protected String dbName;

    /** The name of the table to be accessed (for the SSAP metadata). */
    protected String tableName;

    /** The JDBC URL (endpoint) of the DBMS. */
    protected String jdbcUrl;

    /** The JDBC driver to be used for the connection. */
    protected String jdbcDriver;

    /** The user name to use to login to the DBMS. */
    private String dbUser;

    /** The user password to use to login to the DBMS. */
    private String dbPassword;

    /** Local directory where configuration files are stored. */
    protected String configDir;

    /** Local directory where data files are stored. */
    protected String dataDirURL;

    /** Local directory where dynamically generated spectrums are stored. */
    protected String stagingDir;

    /** File content (MIME) type of any returned datasets. */
    protected String contentType;

    /** File pathname of the vocutout task. */
    protected String cutoutTask;

    /** Tasking daemon name. */
    protected String tdName;

    /** Tasking daemon location. */
    protected String tdLocation;

    /** MDFILE name if accessing a virtual spectrum dataset. */
    protected String mdfile;

    /** Imagefile input stream (keep for later close()). */
    protected InputStream in1 = null;
    protected InputStream in2 = null;

    /** Task Manager */
    protected TaskManager taskman;

    /**
     * Create a new local SSAP service instance.
     *
     * @param params	Input parameter set.
     */
    public SsapService(SsapParamSet params, TaskManager taskman) {
	this.taskman = taskman;

	if (params == null) {
	    this.serviceName = "ssap";
	    this.serviceClass = "ssap";
	    this.serviceVersion = "1.1";
	    this.minorVersion = "generic";
            this.dbType = null;
            this.dbName = null;
            this.tableName = null;
	    this.tdName = "localhost";
	    this.tdLocation = null;
	    this.configDir = "/tmp";
	    this.dataDirURL = "/tmp";
	    this.contentType = "text/xml;content=x-votable";
	} else {
	    this.serviceName = params.getValue("serviceName");
	    this.serviceClass = params.getValue("serviceClass");
	    this.serviceVersion = params.getValue("serviceVersion");
	    this.minorVersion = params.getValue("minorVersion");
	    this.dbType = params.getValue("dbType");
	    this.dbName = params.getValue("dbName");
	    this.tableName = params.getValue("tableName");
	    this.jdbcUrl = params.getValue("jdbcUrl");
	    this.jdbcDriver = params.getValue("jdbcDriver");
	    this.dbUser = params.getValue("dbUser");
	    this.dbPassword = params.getValue("dbPassword");
	    this.tdName = params.getValue("tdName");
	    this.tdLocation = params.getValue("tdLocation"); ;
	    this.configDir = params.getValue("configDir");
	    this.dataDirURL = params.getValue("dataDirURL");
	    this.stagingDir = params.getValue("stagingDir");
	    this.cutoutTask = params.getValue("cutoutTask");
	    this.contentType = params.getValue("contentType");
	}
    }

    /**
     * Process a data query and generate a list of candidate spectrum
     * datasets matching the query parameters.  The datasets referenced
     * in the query response may be physical datasets, or virtual datasets
     * which will be generated on demand, only if subsequently requested
     * by the client.
     *
     * @param	params	  The fully processed SSAP parameter set representing
     *			  the request to be processed.
     *
     * @param	response  A dalserver request response object to which the
     *			  query response should be written.  Note this is
     *			  not a file, but an object containing the metadata
     *			  to be returned to the client.
     */
    @SuppressWarnings("unchecked")
    public void
    queryData(SsapParamSet params, RequestResponse response)
	throws DalServerException {

	RequestResponse r = response;
	SsapKeywordFactory ssap = new SsapKeywordFactory(response,
	    "main", serviceVersion);

	TableInfo dbNameInfo = new TableInfo("dbName", dbName);
	TableInfo tableNameInfo = new TableInfo("tableName", tableName);
	String id, key;

	// The "collection" parameter may be set to the value "none"
	// to cause a built-in test / null query to be executed.  Otherwise
	// the configured data service executes normally.

	Param cp = params.getParam("Collection");
	if (cp != null && cp.isSet()) {
	    String pval = cp.stringValue().toLowerCase();
	    if (pval.equals("none")) {
		try {
		    params.setValue("minorVersion", "test");
		    dbType = null;
		} catch (InvalidDateException ex) {
		    ;  // can't happen, not a date param
		}
	    }
	}

	// Set global metadata.
	r.setDescription("DALServer SSAP Version " +
	    this.serviceVersion + " (" + this.minorVersion + ")");
	r.setType("results");

	// This indicates the query executed successfully.  If an exception
	// occurs the output we generate here will never be returned anyway,
	// so OK is always appropriate here.

	r.addInfo(key="QUERY_STATUS", new TableInfo(key, "OK"));
	r.echoParamInfos(params);

        // This implementation currently supports SSAP V1 and V2(proto).
	if (!serviceClass.substring(0,3).equalsIgnoreCase("ssa")) {
	    throw new DalServerException(
	      "Unsupported SSA service class '" + serviceClass + "'");
	}

	// If the minorVersion is null or is set to "test" we execute a query
	// against the testQuery DBMS class, otherwise we query an actual DBMS.

	String queryType;
	if ((queryType = this.minorVersion) == null || dbType == null)
	    queryType = "test";

	if (queryType.equalsIgnoreCase("test")) {
	    SsapTestQuery testQuery = null;
	    Exception error = null;

	    try {
		testQuery = new SsapTestQuery(jdbcDriver);
		testQuery.connect(jdbcUrl, dbName, dbUser, dbPassword);

		// Since we do not have an actual database, create fields in
		// the output table for all SSAV2 metadata fields defined as
		// mandatory or recommended by the data model.

		testQuery.addFields(params, response);

		// Execute the query.
		testQuery.query(params, response);

	    } catch (Exception ex) {
		error = ex;
	    } finally {
		if (testQuery != null)
		    testQuery.disconnect();
		if (error != null)
		    throw new DalServerException(error.getMessage());
	    }

	} else {
	    // Execute a generic DBMS query using SsapQuery.

	    if (dbName != null && tableName != null) {
		SsapQuery dbms = null;
		Exception error = null;

		try {
		    dbms = new SsapQuery(dbType, jdbcDriver);
		    dbms.connect(jdbcUrl, dbName, dbUser, dbPassword);
		} catch (Exception ex) {
		    throw new DalServerException("Cannot connect to database");
		}

		try {
		    // Create fields in the output table for all columns in the main
		    // SSAV2 DBMS metadata table defined for this service.
		    dbms.addFields(params, response);

		    // Execute the SSAP query.
		    dbms.query(this, params, response);

		} catch (DalOverflowException ex) {
		    // Just quit normally if overflow occurs.
		    error = null;
		} catch (Exception ex) {
		    error = ex;
		} finally {
		    if (dbms != null)
			dbms.disconnect();
		    if (error != null) {
			throw new DalServerException("DBMS query error (" +
			    error.getMessage() + ")");
		    }
		}
	    } else
		throw new DalServerException("DBMS table not specified");
	}

	// Show the number of table rows in the response header.
	r.addInfo(key="TableRows",
	    new TableInfo(key, new Integer(r.size()).toString()));

	// We are done once the information content of the query response
	// is fully specified.  The servlet code will take care of serializing
	// the query response as a VOTable, and anything else required.
    }


    /**
     * Directly access an individual spectrum dataset, returning a spectrum
     * or spectrum subset to the client.  The MODE parameter determines
     * whether an archival dataset is returned, or a virtual spectrum is
     * generated and returned.
     *
     * The dataset to be returned may be an archival spectrum dataset,
     * a precomputed virtual spectrum (e.g., the parameters of the
     * virtual spectrum were computed in a queryData response but the
     * spectrum has not yet been generated), or a dynamically computed virtual
     * spectrum dataset computed and generated according to the accessData
     * task parameters input in the client request.
     *
     * In cases where an existing archival or virtual dataset is to be
     * returned the dataset is specified by the PubDID parameter in the
     * input request.  The value of PubDID is an IVOA dataset identifier
     * as returned in an earlier call to the queryData operation.
     *
     * The interpretation of PubDID is entirely up to the service.  In
     * a simple case it provides a key which can be used to retrieve an 
     * archival dataset.  In another case, the service might generate a
     * unique PubDID on the fly for each virtual dataset described in the
     * query response (e.g., for a cutout or other virtual dataset), either
     * building sufficient information into the PubDID (and hence the URL)
     * to specify the dataset to be generated, or saving internally a 
     * persistent description of the virtual dataset, indexed by the PubDID.
     * The service can later generate the dataset on the fly if and when it
     * is subsequently requested by the client.
     *
     * @param	params	The fully processed SSAP parameter set representing
     *			the request to be processed.  Upon output the
     *			parameters "datasetContentType" and
     *			"datasetContentLength" are added to specify
     *			the content (MIME) type of the dataset to be returned,
     *			and the size of the data entity to be returned, if
     *			known.  Since data entities may be dynamically
     *			computed or may be dynamic streams, the content
     *			length is not always known in advance, in which
     *			case the value should be set to null.
     *
     * @param response  A request response object.  Not currently used.
     *
     * @return		An InputStream is returned which can be can be used
     *			read the dataset as a byte stream; it is
     *			up to the caller to close the returned stream when
     *			the data has been read.
     */
    public InputStream
    accessData(SsapParamSet params, RequestResponse response)
	throws DalServerException {

	String spectrumfile=null, spectrumURL;
	KeywordTable md;
	URL fileURL;

	// Determine whether we will be dynamically generating a cutout.
	String mode = params.getValue("mode");
	if (mode == null)
	    mode = "archival";
	boolean cutout = false;
	if (mode.equalsIgnoreCase("cutout") || mode.equalsIgnoreCase("match"))
	    cutout = true;

	// Get the publisher dataset identifier (PubDID).
	String pubDid = params.getValue("PubDID");
	if (pubDid == null)
	    throw new DalServerException("accessData requires a PubDID spectrum reference");

	/* Check if we want a preview, or the full dataset. */
	boolean preview = false;
	Param cp = params.getParam("Preview");
	if (cp != null && cp.isSet())
	    preview = cp.booleanValue();

	// Get the spectrumfile URL or virtual MDFILE name from PubDID.
	// If a non-null spectrumURL is returned the referenced file is to be
	// returned, otherwise the the virtual spectrum specified by this.mdfile
	// is to be generated and returned.  Has the side affect of writing
	// this.mdfile in the latter case.

	this.mdfile = null;
	spectrumURL = getSpectrumURL(null, pubDid, preview);

	if (spectrumURL != null) {
	    try {
		fileURL = new URL(spectrumURL);
		spectrumfile = fileURL.getPath();
	    } catch (Exception ex) {
		throw new DalServerException(ex.getMessage());
	    }
	}

	// In dynamic cutout mode, compute metadata for the new virtual spectrum.
	// Leaves the metadata for the virtual spectrum in the MDFILE in the
	// data staging area.

	if (cutout && spectrumfile != null) {
	    try {
		md = defineVirtualSpectrum(params, spectrumfile);
		this.mdfile = md.getKeyword("MDFILE");
	    } catch (Exception ex) {
		throw new DalServerException(ex.getMessage());
	    }
	}

	// In virtual spectrum mode, generate the spectrum and return its spectrumURL.
	// This applies to both dynamic cutout mode, and to an acref used to
	// retrieve a virtual spectrum described in a queryData response.

	if (this.mdfile != null) {
	    // Connect to the task manager.
	    TaskManager tm = getTaskManager();

	    // Get a new Task instance to compute the metadata.
	    Task task = tm.newTask(this.cutoutTask, this.tdName);

	    task.addParam("-x");
	    task.addParam("-l", this.mdfile);
	    task.addParam("-d", stagingDir);

	    // Execute the task.
	    try {
		task.execute();
		task.waitForCompletion();

		KeywordTable opset = task.getOutputPset();
		if (opset != null)
		    spectrumfile = opset.getKeyword("spectrum");
		else
		    spectrumfile = null;

	    } finally {
		task.close();
		task = null;
	    }

	}

	// Stream the spectrum back to the client.  In the simplest case this
	// is a static archival spectrum.  In the more complex case it is a 
	// virtual spectrum which has been generated and written to the staging
	// area.

	InputStream in;
	URLConnection conn;

	File file;
	String fileType = null;
	long fileLength = -1;
	boolean fileGZIPed = false;
	boolean gunzip = false;

	// Get the file-level metadata for the file and pass this to the
	// client, then copy the file data out.

	try {
	    if (spectrumfile == null)
		throw new DalServerException("accessData: spectrumfile null");

	    // Check for a GZIP compressed file.  The given spectrumfile name
	    // can't be trusted, and may or may not end with ".gz".  So strip
	    // the ".gz" if any, and check for an actual file with and without
	    // the extension.  For the file to be recognized as GZIPed the
	    // actual file must have a ".gz" extension.
	    //
	    // By default only uncompressed datasets are returned to the
	    // client.  The COMPRESS parameter may be used to enable return
	    // of compressed dataset files.
	    
	    String filePath = spectrumfile;
	    if (spectrumfile.toLowerCase().endsWith(".gz"))
		filePath = spectrumfile.substring(0, spectrumfile.length()-3);

	    file = new File(filePath);
	    if (!file.exists()) {
		filePath += ".gz";
		file = new File(filePath);
		if (!file.exists()) {
		    throw new DalServerException(
			"accessData: cannot access file " + spectrumfile);
		}
		fileGZIPed = true;
		gunzip = true;

		/* Check if the COMPRESS parameter has enabled compressed files. */
		Param ccp = params.getParam("COMPRESS");
		if (ccp != null && ccp.isSet())
		    gunzip = ccp.booleanValue();
	    }

	    // Try to determine the file type and size.
	    fileURL = new URL("file://localhost" + filePath);
	    conn = fileURL.openConnection();
	    fileType = conn.getContentType();
	    fileLength = conn.getContentLength();

	    if (fileType.equals("content/unknown")) {
		FileNameMap map = URLConnection.getFileNameMap();
		fileType = map.getContentTypeFor(filePath);
	    }

	    // Don't trust the system idea of contentType if FITS file.
	    // If content type cannot be determined, VOTable is the default.

	    if (fileGZIPed) {
		if (filePath.toLowerCase().endsWith(".fits.gz"))
		    fileType = "application/fits";
		else if (filePath.toLowerCase().endsWith(".fit.gz"))
		    fileType = "application/fits";
		else
		    fileType = "text/xml;content=x-votable";
	    } else {
		if (filePath.toLowerCase().endsWith(".fits"))
		    fileType = "application/fits";
		else if (filePath.toLowerCase().endsWith(".fit"))
		    fileType = "application/fits";
		else
		    fileType = "text/xml;content=x-votable";
	    }

	    // Check for a valid file length. 
	    if (gunzip)
		fileLength = -1;
	    else if (fileLength <= 0) {
		String fileName = fileURL.getFile();
		if (fileName.length() > 0) {
		    File dataset = new File(fileName);
		    fileLength = dataset.length();
		}
	    }

	} catch (MalformedURLException ex) {
	    throw new DalServerException(ex.getMessage());
	} catch (IOException ex) {
	    throw new DalServerException(ex.getMessage());
	}

	// Tell the servlet what type of data stream we are returning.
	try {
	    String contentType = this.contentType;
	    if (contentType == null || contentType.equalsIgnoreCase("DYNAMIC"))
		contentType = fileType;
	    params.addParam(new Param("datasetContentType",
		EnumSet.of(ParamType.STRING), contentType,
		ParamLevel.SERVICE, false, "Content type of dataset"));

	    params.addParam(new Param("datasetContentLength",
	    	EnumSet.of(ParamType.STRING),
	    	(fileLength < 0) ? null : new Long(fileLength).toString(),
	    	ParamLevel.SERVICE, false, "Content length of dataset"));

	    params.addParam(new Param("datasetContentDisposition",
	    	EnumSet.of(ParamType.STRING), file.getName(),
	    	ParamLevel.SERVICE, false, "Content disposition or filename"));

	} catch (InvalidDateException ex) {
	    throw new DalServerException(ex.getMessage());
	}

	// Return an InputStream to stream the dataset out.  If we are
	// accessing a GZIPed file then it is uncompressed on the fly.

	try {
	    in = conn.getInputStream();
	    if (gunzip) {
		this.in1 = in;   // save for later close
		in = new GZIPInputStream(this.in1);
	    }
	} catch (IOException ex) {
	    throw new DalServerException(ex.getMessage());
	}

	return (in);
    }

    
    /**
     * Clean up after a call to accessData.  This is used to close the
     * InputStream returned by a prior call to accessData (passed as the
     * function value), as well as any other resources used internally
     * by accessData.
     *
     * @param	in		InputStream to be closed
     */
    public void
    accessDataClose(InputStream in) {
	try {
	    // Close the primary input stream.
	    in.close();

	    // Free any other resources.
	    if (this.in1 != null) {
		this.in1.close();
		this.in1 = null;
	    } else if (this.in2 != null) {
		this.in2.close();
		this.in2 = null;
	    }
	} catch (IOException ex) {
	    ;
	}
    }


    /**
     * Given an archival spectrum and a set of filter parameters (POS, SIZE,
     * BAND, TIME, POL), compute the metadata for a virtual spectrum
     * corresponding to the given cutout.  The computed metadata is
     * returned in a KeywordTable object, and is also written to the
     * virtual spectrum staging area to permit later creation and
     * retrieval of the virtual spectrum.  Only metadata values that differ
     * from the parent archival spectrum are returned.
     *
     * @param	params		A SSAPV2 parameter set, from either
     *				accessData or queryData, containing
     *				filter parameters and a PubDID referencing
     *				the parent spectrum.
     *
     * @param	spectrumfile	If not null, use the given spectrumfile
     *				pathname instead of computing the spectrum
     *				reference from PubDID.
     *
     * @return			A KeywordTable object containing the
     *				changed metadata.  A MDFILE is also written
     *				to the staging area to allow later generation
     *				of the referenced virtual spectrum.
     */
    public KeywordTable
	defineVirtualSpectrum(SsapParamSet params, String spectrumfile)
	throws DalServerException {

	boolean spatial_constraint = true;
	boolean spectral_constraint = true;
	boolean time_constraint = false;
	double ra=0.0, dec=0.0, ra_size=0.0, dec_size=0.0;
	double wavelo=0.0, wavehi=0.0;
	String section = null;
	KeywordTable md;
	Param p;

	// --- FILTER TERM ---

	// SPATIAL Coverage.
	// ------------------

        if ((p = params.getParam("POS")) != null && p.isSet()) {
	    // Get POS (e.g., "<val1>,<val2>;<property>").
            RangeList r = p.rangeListValue();
            ra = r.doubleValue(0);
            dec = r.doubleValue(1);

	    // Verify that the coordinates are in a supported frame.
	    // Frames are specified as a property of the range list.

	    for (Iterator i = r.propertiesIterator();  i.hasNext();  ) {
		Map.Entry me = (Map.Entry) i.next();
		String key = (String) me.getKey();

		// Currently only ICRS is supported.
		if (key != null && !key.equalsIgnoreCase("ICRS"))
		    throw new DalServerException("unsupported coordinate frame '" + key + "'");
	    }

	    // GET SIZE (either a single value, or separate values for RA and DEC).
            if ((p = params.getParam("SIZE")) != null && p.isSet()) {
		r = p.rangeListValue();
		ra_size = r.doubleValue(0);
		try {
		    dec_size = r.doubleValue(1);
		} catch (DalServerException ex) {
		    dec_size = ra_size;
		}
	    }
        } else
	    spatial_constraint = false;


        // SPECTRAL Coverage.
	// ------------------

        if ((p = params.getParam("BAND")) != null && p.isSet()) {
            RangeList r = p.rangeListValue();
            wavelo = r.getRange(0).doubleValue1();
            wavehi = r.getRange(0).doubleValue2();
        } else {
	    spectral_constraint = false;
	}

        // TIME Coverage.
	// ------------------
	// omitted for now.

	if (time_constraint) {
	    ;
	}

        // POLARIZATION Coverage.
	// ------------------
	// omitted for now.
	

	// --- PIXEL TERM ---
        if ((p = params.getParam("SECTION")) != null && p.isSet()) {
	    section = p.stringValue();
	}

	// Connect to the task manager.
	TaskManager tm = getTaskManager();

	// Get a new Task instance to compute the metadata.
	Task task = tm.newTask(this.cutoutTask, this.tdName);

	task.addParam("-m");
	task.addParam("-d", this.stagingDir);

	/* FILTER term. */
	if (spatial_constraint) {
	    task.addParam("--ra", new Double(ra).toString());
	    task.addParam("--dec", new Double(dec).toString());
	    task.addParam("--width", new Double(ra_size).toString());
	    task.addParam("--height", new Double(dec_size).toString());
	}

	if (spectral_constraint) {
	    task.addParam("--wavelo", new Double(wavelo).toString());
	    task.addParam("--wavehi", new Double(wavehi).toString());
	}

	/* PIXEL term. */
	if (section != null) {
	    task.addParam("--section", section);
	}

	task.addParam(spectrumfile);

	// Execute the task.
	KeywordTable opset = null;

	try {
	    task.execute();
	    task.waitForCompletion();
	    opset = task.getOutputPset();
	} finally {
	    task.close();
	    task = null;
	}

	return (opset);
    }


    /**
     * Parse a Publisher Dataset Identifier (PubDID) and return a URL
     * referencing the corresponding spectrum within the local archive.
     * There are two cases here, a PubDID that references an archival
     * spectrum via a DBMS index table, and a PubDID that references a
     * virtual spectrum in the staging area via its MDFILE (metadata file).
     *
     * @param	ssap		SsapQuery instance or null
     * @param	pubDid		Publisher dataset identifier
     *
     * @param	preview		Return preview instead of full dataset
     *
     * @return			The URL of the spectrumfile, or null for a
     *				virtual spectrum reference, with the MDFILE
     *				name returned in this.mdfile.
     */
    protected String
    getSpectrumURL (SsapQuery ssap, String pubDid, boolean preview)
	throws DalServerException {

	String tableName, archiveId=null, id;
	String fileURL = null;
	boolean virtual = false;
	int taboff;

	// We have an archival spectrum if #frag contains a ":id" field.
	// The frag is "#<mdfile>" for a virtual spectrum reference, or
	// "#<tableName>:<id>" for a reference to an spectrumfile.

	taboff = pubDid.lastIndexOf("#") + 1;
	String spectrumref = pubDid.substring(taboff);
	virtual = (spectrumref.lastIndexOf(':') < 0);

	// Get dataset or its preview?
	String archive_key = "archive_id";
	if (preview)
	    archive_key = "preview_id";

	if (virtual) {
	    this.mdfile = spectrumref;
	    return (null);

	} else {
	    boolean onetime = (ssap == null);
	    Exception error = null;
	    int idoff;

	    // Get the tableName and dataset ID.
	    idoff = pubDid.lastIndexOf(":") + 1;
	    tableName = pubDid.substring(taboff, idoff - 1);
	    id = pubDid.substring(idoff);

	    // Query the DBMS for the ArchiveID of the dataset.
	    try {
		if (onetime) {
		    ssap = new SsapQuery(dbType, jdbcDriver);
		    ssap.connect(jdbcUrl, dbName, dbUser, dbPassword);
		}

		archiveId = ssap.queryDataset(tableName, id, archive_key);
		if (archiveId == null)
		    throw new DalServerException("missing ArchiveID value");

	    } catch (Exception ex) {
		error = ex;
	    } finally {
		if (onetime && ssap != null)
		    ssap.disconnect();
		if (error != null)
		    throw new DalServerException(error.getMessage());
	    }

	}

	// Construct the internal archive URL used to access the dataset.
	if (archiveId.startsWith("/"))
	    fileURL = "file://" + archiveId;
	else {
	    fileURL = dataDirURL;
	    if (!fileURL.endsWith("/"))
		fileURL += "/";
	    fileURL += archiveId;
	}

	return (fileURL);
    }

    /**
     * Open or retrieve the primary control connection to the taskManager.
     * The connection is opened the first time this is called, after which
     * the connection remains open for the lifetime of the servlet.
     */
    private TaskManager
    getTaskManager()
        throws DalServerException {

	// Initialize a new TaskManager connection.
	if (taskman == null)
	    taskman = new TaskManager();

	// Connect to the daemon if not already connected, otherwise check
	// that we still have an active connection and reconnect if 
	// necessary.

	if (taskman.getConnection(tdName) == null)
	    taskman.connect(tdName, tdLocation);
	else
	    taskman.checkConnection(tdName);

	return (taskman);
    }

    /**
     * Process a service metadata query, returning a description of the
     * service to the client as a structured XML document.  In the generic
     * form this operation returns an InputStream to an actual file in
     * the local file system on the server.  The file name is formed by
     * concatenating the serviceName with "Capabilities.xml".  The file
     * is assumed to be available in the directory specified when the
     * SsapService object was created, e.g., "path/myServiceCapabilities.xml".
     * More generally, the service capabilities do not have to be maintained
     * in a static file and could be dynamically generated (e.g., from a
     * database version), so long as a valid InputStream is returned.
     *
     * @param	params	The fully processed SSAP parameter set representing
     *			the request to be processed.
     *
     * @return		An InputStream which can be used to read the 
     *			formatted getCapabilities metadata document.
     *			It is up to the caller to close the returned stream
     *			when the data has been read.
     */
    public InputStream
    getCapabilities (SsapParamSet params)
	throws FileNotFoundException {

	File capabilities = new File (this.configDir,
	    this.serviceName + "Capabilities.xml");
	return ((InputStream) new FileInputStream(capabilities));
    }


    // -------- Testing -----------

    /**
     * Unit test to do a simple query.
     */
    public static void main(String[] args)
	throws DalServerException, InvalidDateException,
	IOException, FileNotFoundException {

	if (args.length == 0 || args[0].equals("query")) {
	    // Exercise the SsapService class.
	    SsapService service = new SsapService(null, null);

	    // Simulate a typical query.
	    SsapParamSet params = new SsapParamSet();
	    params.setValue("VERSION", "1.1");
	    params.setValue("REQUEST", "queryData");
	    params.setValue("FORMAT", "all");
	    params.setValue("POS", "180.0,1.0");
	    params.setValue("SIZE", "0.3333");	// 20 arcmin

	    // Create an initial, empty request response object.
	    RequestResponse r = new RequestResponse();

	    // Set the request response context for SSAP.
	    SsapKeywordFactory ssap = new SsapKeywordFactory(r, "main", null);

	    // Perform the query. 
	    service.queryData(params, r);

	    // Write out the VOTable to a file.
	    OutputStream out = new FileOutputStream("_output.vot");
	    r.writeVOTable(out);
	    out.close();
	}
    }
}
