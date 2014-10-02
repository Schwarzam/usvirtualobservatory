/*
 * ParamSet.java
 * $ID*
 */

package dalserver;

import java.util.*;
import java.io.*;

/**
 * The ParamSet class implements a parameter set mechanism oriented
 * toward service request parameters.  A parameter set is an ordered set
 * of parameters; a parameter has a name, type, value, and other metadata.

 * @version	1.0, 1-Dec-2006
 * @author	Doug Tody
 */
public class ParamSet implements Iterable {
    // -------- Class Data -----------

    /** Hash table containing the parameter objects. */
    private LinkedHashMap<String,Param> params;


    // -------- Constructors -----------

    /** Create a new, empty parameter set. */
    protected ParamSet() {
	params = new LinkedHashMap<String,Param>();
    }

    // Add additional constructors here to create a ParamSet from a
    // previously saved serialization, from an external schema, from a
    // "wired in" model (as we do here for SSAP), and so forth.

    // Subclass ParamSet and add a custom constructor to create a specific
    // type of parameter set, e.g., for an SSAP or SIAP service.


    // -------- Class Methods -----------

    /**
     * Add a parameter to a ParamSet.  The parameter set is ordered,
     * with newly added parameters added at the end of the list.
     *
     * @param	param	An object of type Param.
     */
    public void addParam(Param param) {
	params.put(param.name.toLowerCase(), param);
    }

    /**
     * Add a parameter to a ParamSet.  The parameter set is ordered,
     * with newly added parameters added at the end of the list.
     *
     * @param	name	The string name of a parameter.
     * @param	value	The string value of the parameter.
     */
    public void addParam(String name, String value)
	throws DalServerException, InvalidDateException {

	Param param = new Param(name, value);
	params.put(param.name.toLowerCase(), param);
    }

    /** Lookup a parameter by name. */
    public Param getParam(String name) {
	return (params.get(name.toLowerCase()));
    }

    /**
     * Set or update the value of a parameter (convenience method).
     * Causes the value of the <i>isSet</i> attribute to be set to
     * true if the new value is non-null, false otherwise.  For finer
     * control the methods of the Param class should be called directly.
     *
     * @param	newValue	The new parameter value, as a String.
     */
    public void setValue(String name, String newValue)
	throws DalServerException, InvalidDateException {

	Param p = params.get(name.toLowerCase());
	if (p == null)
	    throw new DalServerException("param not found: " + name);

	p.setValue(newValue);
	p.isSet = (newValue != null);
    }

    /**
     * Get the value of a parameter as a String (convenience method).
     *
     * @param	name	The parameter name.
     *
     * @return		Returns the parameter value as a String, or null
     *			if no parameter value is set.
     *
     * @throws	DALServerException	If parameter is not found.
     */
    public String getValue(String name)
	throws DalServerException {

	Param p = params.get(name.toLowerCase());
	if (p == null)
	    throw new DalServerException("param not found: " + name);

	return (p.stringValue());
    }

    /**
     * Get the value of a parameter as a String (convenience method).
     *
     * @param	name	The parameter name.
     * @param	defval	The default value to return if the parameter is not
     *                    set
     * @return		Returns the parameter value as a String, or null
     *			if no parameter value is set.
     *
     * @throws	DALServerException	If parameter is not found.
     */
    public String getValue(String name, String defval) {
	Param p = params.get(name.toLowerCase());
	if (p == null) return defval;
	return (p.stringValue());
    }

    /**
     * return true if the named parameter is set 
     */
    public boolean isDefined(String name) {
        return (params.containsKey(name.toLowerCase()));
    }

    /** Get an iterator to access a ParamSet as a list. */
    public Iterator iterator() {
	return ((Iterator) params.entrySet().iterator());
    };

    /** Get an entrySet to access a ParamSet as a Collection. */
    public Set entrySet() {
	return (params.entrySet());
    };

    /** Get the number of parameters in the list. */
    public int size() {
	return (params.entrySet().size());
    };


    // Save a parameter set to external storage.
    // ( load operation would be a constructor.)
    /** Write ParamSet to file.
     *  
     *  @param  filename            The String filename path
     *  
     *  @throws	DalServerException  Error writing file.
     */
    public void write( String filename ) throws DalServerException
    {
	final String newLine = System.getProperty("line.separator");

        File file;
	BufferedWriter bw;
        String line;
        String sval;

        try {
          file = new File( filename );
          file.getParentFile().mkdirs();

          bw = new BufferedWriter( new FileWriter( filename ) );

          line = "#"+this.getClass().getSimpleName();
          line += newLine;
          bw.write( line, 0, line.length() );

          for (Iterator ii = params.entrySet().iterator();  ii.hasNext();  ) 
          {
	    Map.Entry me = (Map.Entry) ii.next();
	    Param p = (Param) me.getValue();

            sval = p.stringValue();
            if ( sval == null )
              line = "".format("%s=null \"%s\"%s",p.getName(), p.getDescription(), newLine);
            else
              line = "".format("%s=\"%s\" \"%s\"%s",p.getName(), p.stringValue(), p.getDescription(), newLine);

            bw.write( line, 0, line.length() );
          }
          bw.close();
        }
        catch (Exception ex) {
            throw new DalServerException( "problem writing file \'"+filename+"\'" );
	}


    }

    /** Custom toString method to print out a parameter set. */
    public String toString() {
        StringBuilder result = new StringBuilder();
	final String newLine = System.getProperty("line.separator");
	int nparams = this.size();

	result.append("{");
	result.append(this.getClass().getName());
	result.append(" {");
	result.append("nparams=");
	result.append(nparams);
	result.append("}");
	result.append(newLine);

	for (Iterator i = params.entrySet().iterator();  i.hasNext();  ) {
	    Map.Entry me = (Map.Entry) i.next();
	    Param p = (Param) me.getValue();
	    result.append(p.toString());
	    if (i.hasNext())
		result.append(newLine);
	}

        result.append("}");
	result.append(newLine);

        return (result.toString());
    }
}
