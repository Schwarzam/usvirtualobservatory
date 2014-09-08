package net.ivoa.util;

import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.ParserConfigurationException;
import org.xml.sax.SAXException;
import org.xml.sax.SAXParseException;
import org.xml.sax.InputSource;

import java.io.InputStream;
import java.io.FileInputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.List;
import java.util.LinkedList;
import java.util.ListIterator;
import java.util.StringTokenizer;
import java.net.URL;

/**
 * a container class that holds application configuration paramters read in 
 * from an XML file.  <p>
 *
 * A Configuration file is an XML file in which parameters are encoded either 
 * as attributes, simple elements containing only text values, or complex 
 * elements--an element containing one or more child elements.  Elements of the 
 * same name may be repeated anywhere in the file.  There are no 
 * requirements on the schema for the file except that there should be no
 * elements with mixed content (i.e., containing both elements and text as 
 * siblings). <p>
 * 
 * A Configuration is created by passing an XML file (either by name or as 
 * an input stream) which is parsed and the parameters loaded into memory.  
 * However, one can retrieve parses and loads into memory the parameters 
 * stored in an XML configuration file.  However, a Configuration object
 * can contain only a "block" of parameters, represented in the file as an 
 * XML element containing one or more child elements; such instances can be
 * retrieved via the getBlocks() method.  <p>
 * 
 * Because the configuration file is XML, the parameters are hierarchical.  
 * One can easily retrieve a parameter deep within the hierarchy via a 
 * hierarchical, slash-delimited path (much like a simple XPath without 
 * predicates).  See getParameters() for details.  
 */
public class Configuration {

    /**
     * the root DOM node of the configuration
     */
    protected Node root = null;

    /**
     * the name associated with this configuration
     */
    protected String name = null;

    /**
     * read the configuration in from an input stream
     * @param strm  the input stream to read configuration from
     * @param name  a name to associate with this configuration (can be null)
     * @throws IOException   if there is a problem while reading the file
     * @throws SAXException  if XML syntax errors are found
     */
    public Configuration(InputStream strm, String name) 
         throws IOException, SAXException 
    {
        load(strm, name);
    }
 
    /**
     * load a named configuration file.  This constructor will look for the 
     * file in the following locations, in order:
     * 
     * <ol>
     *   <li> If the file name is absolute, then it will load it from there.
     *        If that file does not exist, an exception is thrown.
     *   <li> It will look for the file in the class path. 
     *   <li> If the appClass argument is provided, the file will be looked 
     *          for relative to the directory of that class's package path.  
     * </ol>
     *
     * @param config   the XML-encoded config file name
     * @param appClass an optional object representing the object that will 
     *                   use this configuration.  Its class name may be used
     *                   locate the config file.  A null value will be ignored.
     * 
     * @throws FileNotFoundException  if the name configuration file can be
     *    found anywhere.
     * @throws IOException   if there is a problem while reading the file
     * @throws SAXException  if XML syntax errors are found
     */
    public Configuration(String config, Class appClass) 
         throws FileNotFoundException, IOException, SAXException
    {

        // check to see if the file is absolute
        File cfile = new File(config);
        if (cfile.isAbsolute()) { 
            load(new FileInputStream(cfile), null);
        }
        else {
            InputStream cstrm = 
                ClassLoader.getSystemResourceAsStream(config);

            if (cstrm == null && appClass != null) 
                cstrm = appClass.getResourceAsStream(config);

            if (cstrm == null) 
              throw new FileNotFoundException("Can't find configuration file: "
                                              + config);
            load(cstrm, null);
        }
    }

    /**
     * equivalent to Configuration(config, null)
     * @throws FileNotFoundException  if the name configuration file can be
     *    found anywhere.
     * @throws IOException   if there is a problem while reading the file
     * @throws SAXException  if XML syntax errors are found
     */
    public Configuration(String config) 
         throws FileNotFoundException, IOException, SAXException
    {
        this(config, null);
    }

    /**
     * create a Configuration from a DOM node.  This is used internally to 
     * create a Configuration object from a block of XML from the 
     * configuration file.
     * @param root  the DOM node that is to serve as the root of the 
     *                configuration.
     * @param name  a name to associate with this configuration; if null,
     *                a name will be generated from the name of the given
     *                node.
     */
    protected Configuration(Node root, String name) {
        this.root = root;
        this.name = name;
    }

    /**
     * create an empty configuration.  This is used by 
     * {@link #makeEmpty(String) makeEmpty()}.
     */
    protected Configuration() {  }

    /**
     * create an empty configuration.  This is useful for a client that 
     * has hard-coded defaults for configuration data to use when a 
     * Configuration doesn't provide a value.  This empty configuration
     * can be used when, say, 
     * {@link #getConfiguration(String, String, String) getConfiguration()}
     * returns null.
     * @param name    a name for the configuration.  This value must conform
     *                  to the restrictions of an XML Element name.  If this
     *                  instance is meant to stand in for the null response
     *                  returned by 
     * {@link #getConfiguration(String, String, String) getConfiguration()},
     *                  then you can use the name passed into that call.  
     *                  The actual value, however, does not affect this 
     *                  instance functionally, so it doesn't matter too much.
     */
    public static Configuration makeEmpty(String name) {
        Configuration out = new Configuration();

        try {
            Document resdoc = DocumentBuilderFactory.newInstance()
                                         .newDocumentBuilder().newDocument();
            out.root = resdoc.createElement(name);
            resdoc.appendChild(out.root);

            return out;
        }
        catch (ParserConfigurationException ex) {
            throw new InternalError("Problem creating empty XML document: " +
                                    ex.getMessage());
        }
    }

    /**
     * read in the configuration in from the stream.
     * @param strm  the input stream to read configuration from
     * @param name  a name to associate with this configuration; if null,
     *                a name will be generated from the name of the root 
     *                element read from the stream.
     * @throws IOException   if there is a problem while reading the file
     * @throws SAXException  if XML syntax errors are found
     * @throws Error         if there's a problem with the XML parser 
     *                         configuration
     */
    protected void load(InputStream strm, String name) 
         throws IOException, SAXException 
    {
        try {
            DocumentBuilderFactory factory = 
                DocumentBuilderFactory.newInstance();
            DocumentBuilder builder = factory.newDocumentBuilder();

            Document doc = builder.parse(strm);
            root = doc.getDocumentElement();

            // concatonate split text values 
            root.normalize();
        }
        catch (ParserConfigurationException e) {
            throw new Error("XML Parser Configuration problem: " + 
                            e.getMessage());
        }

        // set the config name
        if (name == null) name = "/" + root.getNodeName();
        this.name = name;
    }

    /**
     * return the root node of the Configuration
     */
    protected Node getRoot() { return root; }

    /**
     * return the name of this Configuration.  This is typically the path to 
     * XML node in the input that contains the data held by this Configuration;
     * however, this can be overridden by setName();
     */
    public String getName() { return name; }

    /**
     * override the current default name of this Configuration.
     */
    public void setName(String name) { this.name = name; }

    /**
     * return all configuration blocks that match a given path name as a List
     * @param path   the path to the desired XML node.  This is a 
     *                  slash-delimited string in which each field between 
     *                  slashes is usually an element name.  The last field 
     *                  may either be the name of an element or an attribute.  
     *                  If the last field begins with an "at" character (@), 
     *                  only a matching attribute will be returned.
     * @throws IllegalArgumentException  if any field except the last one 
     *             contains an "@" character
     */
    protected List findBlocks(String path) throws IllegalArgumentException {
        String name;
        Node node;
        int i;

        MatchedBlocks matched = new MatchedBlocks(this);
        StringTokenizer tok = new StringTokenizer(path, "/");
        while (tok.hasMoreTokens()) {
            boolean attrOnly = false;
            name = tok.nextToken();
            if (name.startsWith("@")) {
                if (tok.hasMoreTokens())
                    throw new IllegalArgumentException("Illegal path: " + path);
                attrOnly = true;
                name = name.substring(1);
            }

            int len = matched.size();
            for(i=0; i < len; i++) {
                Configuration candidate = matched.pop();
                boolean findElements = true;
                if (! tok.hasMoreTokens()) {
                    // at the end of the path; check for matching attributes
                    node = 
                        ((Element) candidate.getRoot()).getAttributeNode(name);

                    if (node != null) 
                        matched.appendNode(node, candidate.getName());
                    if (attrOnly) findElements = false;
                }

                if (findElements) {
                    for(node = candidate.getRoot().getFirstChild(); 
                        node != null; 
                        node = node.getNextSibling())
                    {
                        if (node.getNodeType() == Node.ELEMENT_NODE && 
                            node.getNodeName().equals(name))
                        {
                            matched.appendNode(node, candidate.getName());
                        }
                    }
                }
            }
        }

        return matched;
    }

    /**
     * return all configuration blocks that match a given path name.  
     * @param path   the path to the desired XML node.  This is a 
     *                  slash-delimited string in which each field between 
     *                  slashes is usually an element name.  The last field 
     *                  may either be the name of an element or an attribute.  
     *                  If the last field begins with an "at" character (@), 
     *                  only a matching attribute will be returned.
     * @throws IllegalArgumentException  if any field except the last one 
     *             contains an "@" character
     */
    public Configuration[] getBlocks(String path) 
         throws IllegalArgumentException 
    {
        List matched = findBlocks(path);
        Configuration[] out = new Configuration[matched.size()];
        ListIterator iter = matched.listIterator();
        for(int i=0; i < out.length && iter.hasNext(); i++) {
            out[i] = (Configuration) iter.next();
        }
        return out;
    }

    /**
     * return the first Configuration block the with given attached attribute 
     * value
     * @param path     the path to the desired XML node.  This is a 
     *                  slash-delimited string in which each field between 
     *                  slashes is usually an element name.  The last field 
     *                  may either be the name of an element or an attribute.  
     *                  If the last field begins with an "at" character (@), 
     *                  only a matching attribute will be returned.
     * @param attname  the name of the attribute to look for.  If null, the 
     *                  first matching patch node will be returned.
     * @param value    the attribute value the returned value should have
     * @return Configuration -- the first block having the given attribute
     *                  and value, or null if none was found.
     * @throws IllegalArgumentException  if any field except the last one 
     *             contains an "@" character
     */
    public Configuration getConfiguration(String path, String attname, 
                                          String value) 
    {
        List matched = findBlocks(path);
        ListIterator iter = matched.listIterator();
        if (attname == null) {
            return ((Configuration) ((iter.hasNext()) ? iter.next() : null));
        }
        else {
            Configuration c = null;
            String val = null;

            while(iter.hasNext()) {
                c = (Configuration) iter.next();
                val = c.getParameter("@"+attname);
                if (val != null && val.trim().equals(value))
                    return c;
            }
        }
        return null;
    }

    class MatchedBlocks extends LinkedList {
        public MatchedBlocks(Configuration first) {
            addLast(first);
        }

        public void appendNode(Node node, String base) { 
            addLast(new Configuration(node, base + "/" + node.getNodeName()));
        }

        public Configuration pop() { return (Configuration) removeFirst(); }
    }

    /**
     * return values of all parameters with a given name
     * @param path   the path to the desired parameter.  This is a 
     *                  slash-delimited string in which each field between 
     *                  slashes is usually an element name.  The last field 
     *                  may either be the name of an element or an attribute.  
     *                  If the last field begins with an "at" character (@), 
     *                  only a matching attribute will be returned.
     * @throws IllegalArgumentException  if any field except the last one 
     *             contains an "@" character
     */
    public String[] getParameters(String path) {
        List matched = findBlocks(path);

        LinkedList values = new LinkedList();
        ListIterator iter = matched.listIterator();
        while(iter.hasNext()) {
            Node node = ((Configuration) iter.next()).getRoot();
            if (node.getNodeType() == Node.ATTRIBUTE_NODE) {
                values.addLast(node.getNodeValue());
            }
            else if (node.getNodeType() == Node.ELEMENT_NODE) {
                for (node = node.getFirstChild();
                     node != null && node.getNodeType() != Node.TEXT_NODE;
                     node = node.getNextSibling()) 
                { 
                    if (node.getNodeType() == Node.ELEMENT_NODE) {
                        node = null;
                        break;
                    }
                }
                if (node != null) 
                    values.addLast(node.getNodeValue().trim());
            }
        }

        String[] out = new String[values.size()];
        iter = values.listIterator();
        for(int i=0; i < out.length; i++) {
            out[i] = (String) iter.next();
        }

        return out;
    }

    /**
     * return the first value matching the given parameter name
     * @param path   the path to the desired parameter.  This is a 
     *                  slash-delimited string in which each field between 
     *                  slashes is usually an element name.  The last field 
     *                  may either be the name of an element or an attribute.  
     *                  If the last field begins with an "at" character (@), 
     *                  only a matching attribute will be returned.
     * @throws IllegalArgumentException  if any field except the last one 
     *             contains an "@" character
     */
    public String getParameter(String path) {
        String[] out = getParameters(path);
        return ((out != null && out.length > 0) ? out[0] : null);
    }

    /**
     * return the value matching the given parameter name and attribute 
     * constraint.  This will find the element matching the given path 
     * that also has an attribute with a specified name and value; the content
     * value of that parameter is then returned.  If there more than one 
     * such elements, the value of the first one is returned.
     * @param path   the path to the desired parameter.  This is a 
     *                  slash-delimited string in which each field between 
     *                  slashes is usually an element name.  The last field 
     *                  may either be the name of an element or an attribute.  
     *                  If the last field begins with an "at" character (@), 
     *                  only a matching attribute will be returned.
     * @param attname  the name of the attribute to look for.  If null, this 
     *                  method will behave identically to getParameter().
     * @param value    the attribute value the returned value should have
     * @throws IllegalArgumentException  if any field except the last one 
     *             contains an "@" character 
     */
    public String getParameter(String path, String attname, String value) {
        if (attname == null || attname.trim().length() == 0) 
            return getParameter(path);

        List matched = findBlocks(path);
        ListIterator iter = matched.listIterator();
        String attpath = "@"+attname;
        String out = null;
        String attval = null;
        Configuration candidate = null;
        while (out == null && iter.hasNext()) {
            candidate = (Configuration) iter.next();
            attval = candidate.getParameter(attpath);
            if ((attval == null && value == null) || 
                (value != null && value.equals(attval)))
            {
                out = candidate.getParameter("");
            }
        }

        return out;
    }

    /**
     * open up a resource file.  
     * <p>
     * Among the data stored in the Configuration 
     * may be the names of files containing additional configuration data.  
     * Such a file may be available as a resource--that is, it may reside 
     * below one of the directories in the user's CLASSPATH or inside a jar
     * file listed in the CLASSPATH.  Alternatively, it may live in a directory
     * unrelated to the CLASSPATH.  This method provides a single mechanism to
     * open either type of file by name and return it as an InputStream.
     * <p>
     * In particular, this method will look for the file in the following 
     * locations, in order:
     * 
     * <ol>
     *   <li> If the file name is absolute, then it will load it from there.
     *        If that file does not exist, an exception is thrown.
     *   <li> It will look for the file in the class path. 
     *   <li> If the appClass argument is provided, the file will be looked 
     *          for relative to the directory of that class's package path.  
     * </ol>
     *
     * @param filename the name of the file to open
     * @param wrtClass an optional object representing the application that will 
     *                   use this file.  Its class name may be used locate the 
     *                   config file.  A null value will be ignored.
     * 
     * @throws FileNotFoundException  if the name configuration file can be
     *    found anywhere.
     * @throws IOException   if there is a problem while reading the file
     */
    public static InputStream openFile(String filename, Class wrtClass) 
         throws FileNotFoundException, IOException
    {
        InputStream strm = null;

        // check to see if the file is absolute
        File file = new File(filename);
        if (file.isAbsolute()) { 
            return new FileInputStream(file);
        }
        else {
            strm = ClassLoader.getSystemResourceAsStream(filename);

            if (strm == null && wrtClass != null) 
                strm = wrtClass.getResourceAsStream(filename);

            if (strm == null) 
              throw new FileNotFoundException("Can't locate file as resource: " +
                                              filename);
        }

        return strm;
    }

    /**
     * return a URL to a resource file
     * <p>
     * This applies the same logic for locating files as 
     * {@link #openFile(String,Class) openFile()}; however, instead of 
     * returning an open stream to the resource, it returns a URL pointing 
     * to it.  When loading things like XSL stylesheets, this method is 
     * preferred as it allows the transformer framework to find any included
     * files in a location that is relative to the original file's directory
     * instead of the current working directory.  
     *
     * @param filename the name of the file to open
     * @param wrtClass an optional object representing the application that will 
     *                   use this file.  Its class name may be used locate the 
     *                   config file.  A null value will be ignored.
     * 
     * @throws FileNotFoundException  if the name configuration file can be
     *    found anywhere.
     * @throws IOException   if there is a problem while reading the file
     */
    public static String findURL(String filename, Class wrtClass) 
         throws FileNotFoundException, IOException
    {
        String out = null;

        // check to see if the file is absolute
        File file = new File(filename);
        if (file.isAbsolute()) { 
            return "file:" + file;
        }
        else {
            URL url = ClassLoader.getSystemResource(filename);

            if (url == null && wrtClass != null) 
                url = wrtClass.getResource(filename);

            if (url == null) 
              throw new FileNotFoundException("Can't locate file as resource: " +
                                              filename);
            out = url.toString();
        }

        return out;
    }
}
