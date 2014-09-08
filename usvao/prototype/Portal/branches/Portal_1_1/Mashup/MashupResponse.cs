using System;
using System.Data;
using System.IO;
using System.Threading;
using System.Text;
using System.Net;
using System.Configuration;
using System.Collections;
using System.Collections.Generic;

using Utilities;
using ExcelLibrary;
using ExcelLibrary.SpreadSheet;
using JsonFx.Json;
using log4net;

namespace Mashup
{	
	public class MashupResponse
	{	
		// Mashup.txt Logging
		public static readonly ILog log = LogManager.GetLogger(System.Reflection.MethodBase.GetCurrentMethod().DeclaringType);
		public static string tid { get {return String.Format("{0,6}", "[" + System.Threading.Thread.CurrentThread.ManagedThreadId) + "] ";}  }
		
		//
		// MashupResponseData Class: 
		// This class is a dumb pallette, used to hold each of the intermediate steps of the output processing pipeline.
		// 
		protected MashupResponseData mrd = new MashupResponseData();
		
		// Methods to access information in the MashupResponseData 
		public string status 
		{ 
			get { return ((this.mrd != null && this.mrd.status != null) ? this.mrd.status : ""); } 
			set { this.mrd.status = value;}
		}
		
		public string msg 
		{ 
			get { return ((this.mrd != null && this.mrd.msg != null) ? this.mrd.msg : ""); } 
			set { this.mrd.msg = value;}
		}
		
		public int length
		{
			get { return ((this.mrd != null && this.mrd.ob != null) ? this.mrd.ob.Length : 0); }
		}
			
		//
		// Response Thread Stuff
		//
		public Thread thread;
		
		public Boolean isActive{
		get {
			return (this.thread != null && this.thread.IsAlive);
			}	
		}
		
		public int threadID
		{
			get {return (thread != null ? thread.ManagedThreadId : -1);}
		}
		
		public void wait(int timeout)
		{
			if (isActive)
			{
				thread.Join(timeout);
			}
		}
		
		public void abort()
		{
			if (isActive)
			{
				thread.Abort();
			}
		}
				
		//
		// Constructor
		//
		public MashupResponse ()
		{	
		}
		
		public string Debug()
		{		
			StringBuilder sb = new StringBuilder();
			sb.Append("[RESPONSE] : ");
			sb.Append(mrd.getOutBuffer().Replace("\n", "")); 
			sb.Append(" ...[LENGTH:" + length + "] ");
			
			Dictionary<string, object> dict = new Dictionary<string, object>();
			dict["id"] = threadID;
			dict["active"] = isActive;
			sb.Append(" [THREAD] : ");
			new JsonFx.Json.JsonWriter(sb).Write(dict); 

			return sb.ToString();
		}
		
		//
		// Load Methods are called by the Adaptor Threads to load the initial DataSet onto the Pallette
		//
		public void load(DataSet ds)
		{
			load(ds, true);
		}
		
		public void load(DataSet ds, Boolean complete)
		{
			mrd.dsin = ds;
			mrd.status = (complete ? "COMPLETE" : "EXECUTING");
		}

		//
		// WriteMashupResponse() Method : called by the Mashup Main Request Thread
		//		
		public void writeMashupResponse(MashupRequest muRequest, System.Web.HttpResponse httpResponse)
		{
			//
			// BEGIN LOCK (mrd):
			//
			// This is to ensure that 1 Thread at a time is accessing/manipulating the mrd Container sbject
			// This object is saved/loaded into web cache and can have multiple muRequest threads accessing it simultaneously,
			// We need to ensure only one thread modifies the MashupResponseData (mrd) at a time.
			//
			lock(mrd)
			{	
				//
				// STEP 0: Create new Output Buffer (ob and wb)
				//
				mrd.ob = new StringBuilder();
				mrd.wb = null;
					
				// If Input DataSet contains a DataTable, run it through: STEPS 1 - 3
				if (mrd.dsin != null && mrd.dsin.Tables.Count > 0)
				{
					//
					// STEP 1: Filter, sort rows and columns in DataSet (dsin) ===> (dssort)
					//
					mrd.dssort = filterSortDataSet(muRequest, mrd.dsin);
				
					//
					// STEP 2: Paginate DataSet (dssort) ===> (dsout)
					//
					mrd.dsout = pageDataSet(muRequest, mrd.dssort);
				
					//
					// STEP 3: Format Output DataSet to Output Products (dsout) ===> (ob or wb)
					//
					formatDataSet(muRequest, mrd.dsout, mrd.ob, out mrd.wb);
				}
				else
				{
					mrd.ob = new StringBuilder("{}");
				}
				
				//
				// STEP 4: (Optional) Save Output Products To File (ob or wb) ===> (file)
				//
				if (muRequest.filenameIsSpecified)
				{
					// Save the file out to local disk
					string url = saveToFile(muRequest, mrd.ob, mrd.wb);
					
					// Set Output Buffer to the Saved File Url
					mrd.ob = new StringBuilder("{ \"url\": \"" + url + "\"}");
				}
				
				//
				// STEP 5: Insert the Mashup Header/Trailer in the Output Buffer (ob)
				//
				if (muRequest.formatIsSpecified)
				{
					mrd.ob.Insert(0, getMashupHeader(muRequest));
					mrd.ob.Append(getMashupTrailer(muRequest));
				}
				
				//
				// STEP 6: Write Output Buffer Response to the client (ob) ===> (client)
				//
				writeMashupResponseData(mrd, httpResponse);
				
				//
				// STEP 7: Log Outgoing Response
				//
				log.Info(tid + "<=== " + this.Debug());	
				
				
				//
				// STEP 8: Clear up Output Objects to reduce memory footprint
				//
				mrd.ob = null;
				mrd.wb = null;
				mrd.dsout = null;
				
			} // END LOCK (mrd)			
		}	
		
		public void writeCleanupResponse(MashupRequest muRequest, System.Web.HttpResponse httpResponse)
		{
			//
			// writeCleanup() Method is called when an Exception in thrown and the caught by the cleanUp() method of Mashup.asmx.cs
			// IMPORTANT NOTE: The muRequest could be null here
			//
			
			//
			// BEGIN LOCK (mrd):
			//
			// This is to ensure that 1 Thread at a time is accessing/manipulating the mrd Container sbject
			// This object is saved/loaded into web cache and can have multiple muRequest threads accessing it simultaneously,
			// We need to ensure only one thread modifies the MashupResponseData (mrd) at a time.
			//
			lock(mrd)
			{
				//
				// STEP 0: Create new Output Buffer
				//
				mrd.ob = new StringBuilder();
				
				//
				// STEP 1: Create the Mashup Response Message Header and Trailer
				//
				if (muRequest != null && muRequest.formatIsSpecified)
				{
					mrd.ob.Insert(0, getMashupHeader(muRequest));
					mrd.ob.Append(getMashupTrailer(muRequest));
				}
				
				//
				// STEP 2: Write Mashup Response
				//
				writeMashupResponseData(mrd, httpResponse);
				
				//
				// STEP 3: Log Outgoing Response
				//
				log.Info(tid + "<=== " + this.Debug() + " " + muRequest.Debug());
				
				//
				// STEP 4: Clear up Output Objects to reduce memory footprint
				//
				mrd.ob = null;
				
			} // END LOCK (mrd)			
		}		
		
		protected DataSet filterSortDataSet(MashupRequest muRequest, DataSet ds)
		{
			//
			// Create new DataSet that is Sorted and Filtered based on original DS
			//
			DataTable dtpage = null;
			if (ds != null && ds.Tables != null && ds.Tables.Count > 0)
			{
				string rowFilter = "";
				string colSort = "";	
				
				DataTable dt = ds.Tables[0];
				string[] columnNames = getOrderedColumnNames(muRequest, dt);
				
				//
				// TODO: Validate this next step is even necessary:
				// 	(1) columnNames != DataSet columnNames
				// 	(2) rowFilter != ""
				// 	(3) colSort != ""
				//
				DataView dv = new DataView(dt, rowFilter, colSort, DataViewRowState.CurrentRows);	
				dtpage = dv.ToTable(dt.TableName, false, columnNames);
				copyColumnProperties(dt, dtpage);
				
				// Update DataTable Properties 
				DataTableExtendedProperties props = getDataTableExtendedProperties(dtpage);
				props.rowsTotal = dt.Rows.Count;
				props.rowsFiltered = dtpage.Rows.Count;
			}
				
			// Create New DataSet
			DataSet dsnew = new DataSet(ds.DataSetName);
			dsnew.Tables.Add(dtpage);
			return dsnew;
		}
		
		protected DataTableExtendedProperties getDataTableExtendedProperties(DataTable dt)
		{
			DataTableExtendedProperties dtp = null;
			if (dt.ExtendedProperties.ContainsKey(DataTableExtendedProperties.KEY))
			{
				dtp = dt.ExtendedProperties[DataTableExtendedProperties.KEY] as DataTableExtendedProperties;
			}
			else
			{
				dtp = new DataTableExtendedProperties();
				dt.ExtendedProperties[DataTableExtendedProperties.KEY] = dtp;
			}
			return dtp;
		} 	
		
		protected DataSet pageDataSet(MashupRequest muRequest, DataSet ds)
		{
			//
			// Validate Request Page Params
			//
			if (muRequest.pageIsSpecified && muRequest.pageAsInt <= 0) 
				throw new Exception("page parameter must be > 0");
			if (muRequest.pagesizeIsSpecified && muRequest.pagesizeAsInt <= 0) 
				throw new Exception("pagesize parameter must be > 0");
			
			// Extract first DataTable
			DataTable dt = ds.Tables[0];
			
			// Set default start, end, pagesize values.
			int start = 0;
			int end = dt.Rows.Count;
			int pagesize = end;
			
			// If 'page' is specified, determine start record AND end record using specified pagesize (or default)
			if (muRequest.pageIsSpecified)
			{
				start = (muRequest.pageAsInt-1) * muRequest.pagesizeAsInt;	
				end = start + muRequest.pagesizeAsInt;
				pagesize = muRequest.pagesizeAsInt;
			}
			else if (muRequest.pagesizeIsSpecified)
			{
				// If 'pagesize' is specified, start = 0, determine end row
				end = start + muRequest.pagesizeAsInt;
				pagesize = muRequest.pagesizeAsInt;
			}
			
			//
			// Check if we should just return Existing Table containing all rows
			//
			DataTableExtendedProperties ep;
			if (start == 0 && end >= dt.Rows.Count)
			{
				if (dt.Rows.Count > 0)
				{
					// Update DataTable Extended Properties 
					ep = getDataTableExtendedProperties(dt);
					ep.page = 1;
					ep.pageSize = pagesize;
					ep.pagesFiltered = 1;
					ep.rows = dt.Rows.Count;
				}
				return ds;
			}
			
			//
			// We need to create a new DataTable containing the requested Page
            // So we clone the Schema from the original DataTable and load records from start - end
            //
            DataTable dtpage = dt.Clone();
			copyColumnProperties(dt, dtpage);
			
            // Import the Rows for the specified page range
            for (int i = start; i < end; i++)
            {
				if (i < dt.Rows.Count)
				{
                	DataRow row = dt.Rows[i];
                	dtpage.ImportRow(row);
                	dtpage.AcceptChanges();
				}
				else
				{
					break;
				}
            }
			
			// Update DataTable-Page Extended Properties 
			ep = getDataTableExtendedProperties(dtpage);
			ep.rows = dtpage.Rows.Count;
			ep.page = muRequest.pageAsInt;
			ep.pageSize = muRequest.pagesizeAsInt; 
			ep.pagesFiltered = dt.Rows.Count/muRequest.pagesizeAsInt;
			if ((dt.Rows.Count % muRequest.pagesizeAsInt) > 0) ep.pagesFiltered++;
			
			// Create New DataSet-Page for the DataTable-Page
			DataSet dspage = new DataSet(ds.DataSetName);
			dspage.Tables.Add(dtpage);
			return dspage;
		}
		
		protected void copyColumnProperties(DataTable dt, DataTable dtpage)
		{
			//
			// NOTE: 
			// The Extended Properties of the new DataTable are turned into DataCollection Objects
			// This appears to be a bug in Microsoft or Mono.  Not sure.
			// So, we clear it the Extended Properties on the new column and copy them over from the origninal table
			//
			foreach (DataColumn colnew in dtpage.Columns)
			{
				PropertyCollection epnew = colnew.ExtendedProperties;
				PropertyCollection ep = dt.Columns[colnew.ColumnName].ExtendedProperties;
				
				epnew.Clear();
				foreach (string key in ep.Keys)
				{
					epnew.Add(key, ep[key]);
				}
			}
		}
		
		protected void resetDefaultView(ref DataTable dt)
        {
            DataView dv = dt.DefaultView;
            dv.Sort = "";
            dv.RowFilter = "";
        }
		
		protected string[] getOrderedColumnNames(MashupRequest muRequest, DataTable dt)
		{
			SortedDictionary<int, String> sortedDict = new SortedDictionary<int, String>();
			ArrayList otherList = new ArrayList();
			
			//
			// Insert the orderedList columns first at their specified location
			//
			foreach (DataColumn col in dt.Columns)
			{
				if (!isColumnRemoved(col))
				{
					int order = getColumnOrder(col);
					if (order >= 0)
					{
						// Try to access index.  This may throw exception because index may be larger than array.
						string name;
						if (sortedDict.TryGetValue(order, out name))
						{
							log.Warn("Duplicate Column Order [" + order + "] for Service Adaptor [" + muRequest.service + 
								     "] column names [" + name + ", " + col.ColumnName + "]. Please correct the ColumnsConfig File.");
						}
						sortedDict[order] = col.ColumnName;	
					}
					else
					{
						otherList.Add(col.ColumnName);
					}
				}		
			}
			
			//
			// Insert the remaining otherList column names into the orderedList
			//
			int i=0;
			foreach (string colname in otherList)
			{
				// Insert column name into any gaps in the orderedList
				string name;
				while (sortedDict.TryGetValue(i, out name)) i++;
				sortedDict[i] = colname;
			}
			
			string[] colNames = new string[sortedDict.Values.Count];
			sortedDict.Values.CopyTo(colNames, 0);
			return colNames;
		}
		
		protected Boolean isColumnVisible(DataColumn col)
		{
			bool isVisible = true;
			
			if (col != null && 
				col.ExtendedProperties != null &&
				col.ExtendedProperties.ContainsKey("cc.visible"))
			{
				Object o = col.ExtendedProperties["cc.visible"];
				isVisible = Convert.ToBoolean(o);
			}	
			return isVisible;
		}
		
		protected Boolean isColumnRemoved(DataColumn col)
		{
			bool isRemoved = false;
			
			if (col != null && 
				col.ExtendedProperties != null &&
				col.ExtendedProperties.ContainsKey("cc.remove"))
			{
				Object o = col.ExtendedProperties["cc.remove"];
				isRemoved = Convert.ToBoolean(o);
			}	
			return isRemoved;
		}
		
		protected int getColumnOrder(DataColumn col)
		{
			int order = -1;
			if (col != null && 
				col.ExtendedProperties != null && 
				col.ExtendedProperties.ContainsKey("cc.order"))
			{
				Object o = col.ExtendedProperties["cc.order"];
				order = Convert.ToInt32(o);	
			}
			return order;
		}

		protected void formatDataSet(MashupRequest muRequest, DataSet ds, StringBuilder sb, out Workbook wb)
		{	
			wb = null;
			
			// Transform it to appropriate Output Data Structure
			if (ds != null && ds.Tables.Count > 0)
			{
				switch (muRequest.formatType)
				{
					case "json":
					case "extjs":
						mrd.ContentType = "text/javascript";
						Utilities.Transform.DataSetToExtjs(ds, sb); 
						break;
					
					case "csv":
						mrd.ContentType = "text/csv";
						Utilities.Transform.DataSetToCsv(ds, sb); 
						break;	
					
					case "xml":
						mrd.ContentType = "text/xml";
						Utilities.Transform.DataSetToXml(ds, sb); 
						break;
					
					case "html":
						mrd.ContentType = "text/html";
						Utilities.Transform.DataSetToHtml(ds, sb); 
						break;
					
					case "votable":
					case "vot":
						mrd.ContentType = "text/xml";
						Utilities.Transform.DataSetToVoTable(ds, sb); 
						break;
										
					case "xls":
						mrd.ContentType = "application/x-excel";
						Utilities.Transform.DataSetToXls(ds, out wb); 
						break;
					
					default:
						throw new Exception("Unknown  Format Type specified : " + muRequest.formatType);
				}
			}
		}
		
		// Static Object used to control Thread Access to Save File Directory
		private static object SaveLock = new object();
		
		protected string saveToFile(MashupRequest muRequest, StringBuilder ob, Workbook wb)
		{
			//
			// Get the Temp File Directory from the Web.Config	
			// and convert TempDir From Local Relative Directory To Full Local Path 
			//
	        string tempDir = ConfigurationManager.AppSettings["TempDir"];
			
			if (tempDir == null)
			{
				status = "ERROR";
				msg = "TempDir not Specified in the Web.Config.";
				throw new Exception("Unable to save Response To File: 'TempDir' not Specified in the Web.Config.");
			}
			
			if (!tempDir.EndsWith("/")) tempDir += "/";
			string tempDirPath = System.Web.HttpContext.Current.Server.MapPath(tempDir);
			if (!Directory.Exists(tempDirPath))
			{
				throw new Exception("Unable to save Response To File: 'TempDir' Directory Does not Exist: " + tempDirPath);
			}
	
	        //
	        // Ensure that only one thread at a time is determining a unique filename
	        // First, remove any stupid characters (like '+') from the filename, which can causes headache(s) down the road
			//
	        string filename = muRequest.filename.Replace('+', '-');
	        filename = Path.GetFileName(filename);
			string filenamePath = tempDirPath + filename;
									
			string filenameWrite = filenamePath; 	// unique filename determined below
	
	        lock (SaveLock)
	        {
				// Find a Unique Filename for Exporting the Results
	            int i = 0;
	            while (File.Exists(filenameWrite))
	            {
	                filenameWrite = Path.GetDirectoryName(filenamePath) + '/' + 
						   			Path.GetFileNameWithoutExtension(filenamePath) + 
						   			"_" + (i++) + 
						   			Path.GetExtension(filenamePath);
	            }
	        }  // lock(SaveLock)
	
	        //
			// Save Response Data Output products to the Unique file name
			//
			if (wb != null)
			{
				wb.Save(filenameWrite);
			}
			else if (ob != null)
			{	
				StreamWriter sw = new StreamWriter(filenameWrite);
	        	sw.Write(ob.ToString());
				sw.Close();	
			}
				
			//
			// Create the URL to download the output file 
			//
			string url = getDownloadFileUrl(muRequest, tempDir, filenameWrite);
		
			return url;
		}
		
		protected String getDownloadFileUrl(MashupRequest muRequest, string tempDir, string file) 
		{
			//
			// Determine the URL that points to the Saved File 
			//
			// IMPORTANT NOTE: 
			//
			// We backup ONE '/' from the right side of the oringinal URL in order to determine the root URL.
			// The BIG ASSUMPTION is the the initial request came in on a URL similar to this:
			//
			// http://127.0.0.1:8080/Mashup.asmx/invoke?request={}
			//
			// so backing up ONE '/' from the right gives us a root url of:
			//
		    // http://127.0.0.1:8080/Mashup.asmx
			//
			// Next Append the 'download' [Method] Invocation and arguments:
			//
			// http://127.0.0.1:8080/Mashup.asmx/download?file=&filename=
			//
			string reqUrl = System.Web.HttpContext.Current.Request.Url.GetLeftPart(UriPartial.Path);
			int length = reqUrl.LastIndexOf("/");
			string mashupUrl = (length > 0 ? reqUrl.Substring(0, length) : reqUrl);
			if (!mashupUrl.EndsWith("/")) mashupUrl += "/";
			
			//
			// Build new Mashup Download Request and return as embedded URL for the Client
			//
			MashupRequest request = new MashupRequest();
			request.service = "Mashup.File.Download";
			request.paramss["file"] = tempDir + Path.GetFileName(file);
			request.paramss["filename"] = muRequest.filename;
			request.paramss["attachment"] = muRequest.attachmentAsBoolean().ToString();
							
			string url = mashupUrl + "invoke?request=" + Uri.EscapeDataString(request.ToJson());
			
			return url;
		}
		
		protected String getMashupHeader(MashupRequest muRequest)
		{
			StringBuilder sb = new StringBuilder("");

			switch (muRequest.format)
			{
				case "extjs":
					JsonWriter jw = new JsonFx.Json.JsonWriter(sb); 
					sb.Append("{\n");
					sb.Append("  \"status\" : "); jw.Write(status); sb.Append(",\n"); 
					sb.Append("  \"msg\" : "); jw.Write(msg); sb.Append(",\n"); 
					sb.Append("  \"data\" : ");
					break;
			}
			return sb.ToString();
		}
		
		protected String getMashupTrailer(MashupRequest muRequest)
		{
			string trailer="";
			switch (muRequest.format)
			{
				case "extjs":
					trailer = "}";
					break;
			}
			return trailer;
		}	
		
		protected void writeMashupResponseData(MashupResponseData mrd, System.Web.HttpResponse httpResponse)
		{
			//////////////////////////////////////////////////////////////
			// Write the mrd Container sbject back out to the client
			//////////////////////////////////////////////////////////////
			if (httpResponse != null)
			{		
				// Write back Data mrd if it exits
				if (mrd.ob != null && mrd.ob.Length > 0)	    
				{
					httpResponse.ContentType = mrd.ContentType;
					httpResponse.Write(mrd.ob.ToString());	
				}
				else if (mrd.status == "ERROR")
				{
					httpResponse.TrySkipIisCustomErrors = true;
					httpResponse.StatusCode = (int)HttpStatusCode.InternalServerError;
					httpResponse.ContentType = "text/plain";
					httpResponse.Write(mrd.msg);
				}
				
	            httpResponse.Flush();
			}
		}	
	}
}