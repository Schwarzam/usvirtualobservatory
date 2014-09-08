<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<xsl:stylesheet xmlns:xsl="http://www.w3.org/1999/XSL/Transform" 
                xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance" 
                xmlns="http://www.w3.org/1999/xhtml"
                version="1.0">

   <xsl:output method="html" encoding="UTF-8" />

   <xsl:template match="/" xml:space="preserve">
<html> <head>
  <title>ConeSearch Validation Results</title>
  <script type="text/javascript" src="MochiKit.js" ></script>

  <style type="text/css">
<!--
.tiny {FONT-SIZE: 7pt;}
-->
  </style>
  <link href="usvo_template.css" rel="stylesheet" type="text/css"/>
</head>
<body>

<table width="100%" border="0" cellpadding="0" cellspacing="0">
  <tr>
    <td width="112" height="32" align="center" valign="top">
      <a href="http://www.us-vo.org" class="nvolink"><img 
         src="http://www.us-vo.org/images/NVO_100pixels.jpg" alt="NVO HOME"
         border="0"/></a>
             <br />
      <span class="nvolink"><a
             href="http://www.us-vo.org/">National Virtual Observatory</a>
      </span><br />
    </td>

    <td valign="top">
      <table  width="100%" border="0" cellpadding="0" cellspacing="0">
        <tr>
          <td bgcolor="white" width="2"></td>
          <td bgcolor="#CFE5FC" valign="middle" align="center" height="32"
              class="nvoapptitle">

       <!-- Name of Application -->
       Cone Search Service Validater

          </td>
          <td bgcolor="white" width="2"></td>
       </tr>
       <tr>
          <td bgcolor="white" width="2"></td>
          <td height="10" valign="top"
              background="http://www.us-vo.org/app_templates/stars.jpg" >
          </td>
          <td bgcolor="white" width="2"></td>
       </tr>
       <tr>
         <td align="center" valign="top" colspan="3">
         <table cellspacing="2" cellpadding="0" border="0" width="100%"
                style="margin: 0pt;">
           <tr>
             <!-- the local links -->
             <td class="navlink"><a href="http://www.ivoa.net/Documents/latest/ConeSearch.html">Cone Search Spec.</a></td>
             <td class="navlink"><a href="http://nvo.ncsa.uiuc.edu/nvoregistration.html">Publish at the NCSA Registry</a></td>
             <td class="helplink"><a href="http://www.us-vo.org/feedback/">Contact Us</a></td>
           </tr>
         </table>
         </td>
       </tr>
    </table>
    </td>
    <td width="112" align="center" valign="top">

      <!-- local logo and link -->
      <a href="http://nvo.ncsa.uiuc.edu"><img height="54"
         src="http://gladiator.ncsa.uiuc.edu/Images/logo/UofI-NCSA-black_logo_color.md.jpg"
         alt="NVO @ NCSA" border="0"/></a>
         
      <br />
      <span class="nvolink"><a
            href="http://www.caltech.edu/">National Center for 
      Supercomputing Applications</a> </span>
    </td>
   </tr>

</table>

<xsl:comment> =======================================================================
  -  Page Content
  -  ======================================================================= </xsl:comment>

<div id="results">

<h1>Cone Search Verification Results</h1>

<p>
<strong>Base URL of Service:</strong>  <xsl:text>  </xsl:text>
<xsl:value-of select="/*/@baseURL"/>
</p>

<xsl:choose>
   <xsl:when test="count(ConeSearchValidation/testQuery)=1">
A single test query (<xsl:value-of 
   select="ConeSearchValidation/testQuery/@options"/>) was sent to
the service.  The results are described below.  Click on the links to
   see the part of the Cone Search Specification relevant to that test
   result.  <p />
   </xsl:when>
   <xsl:otherwise>
The following <xsl:value-of select="count(ConeSearchValidation/testQuery)"/> 
test queries were sent to the service:
<ul>
      <xsl:for-each select="ConeSearchValidation/testQuery">
 <li> <a href="{concat('#',@name)}"><xsl:value-of 
         select="@name"/></a>: <xsl:value-of select="@options"/> </li>
      </xsl:for-each>
</ul>
   </xsl:otherwise>
</xsl:choose>

<xsl:apply-templates select="ConeSearchValidation/testQuery"/>

</div>

<xsl:comment> =======================================================================
  -  End Page Content
  -  ======================================================================= </xsl:comment>

<hr noshade="1"/>
<table width="100%"  border="0">
  <tr>
    <td><div align="center"><a href="http://www.nsf.gov"><img src="http://www.us-vo.org/images/nsflogo_64x.gif" width="64" height="64" border="0"/></a></div></td>
    <td><div align="center"><a href="http://www.nasa.gov"><img src="http://www.us-vo.org/images/nasa_logo_sm.gif" width="72" height="60" border="0"/></a></div></td>
    <td><p class="tiny" align="center">Developed with the support of the National Science Foundation under Cooperative Agreement AST0122449 with The Johns Hopkins University.</p>
      <p class="tiny" align="center">The NVO is a member of the International Virtual Observatory Alliance.<br />
      </p></td>
    <td><div align="center"><a href="http://www.ivoa.net"><img src="http://www.us-vo.org/images/IVOAlogo.gif" width="80" height="44" border="0"/></a></div></td>

    <td><div align="center"><a href="#"><img src="http://www.us-vo.org/images/bee_hammer.gif" border="0"/><br />
          <span class="tiny">meet the<br />
    developers</span></a></div></td>
  </tr>
</table>

</body> </html>
   </xsl:template>

   <xsl:template match="testQuery">
      <xsl:param name="n"/>

      <a name="{@name}">
      <h3>Test Query Name: <xsl:value-of select="@name"/></h3></a>

      <xsl:choose>
         <xsl:when test="@description">
            <strong>Description: </strong> 
            <xsl:value-of select="@description"/> <br />
         </xsl:when>
         <xsl:when test="@name='user'">
            <strong>Description: </strong> test response from the
            user-provided query (assuming position is within service's
            coverage.)  <br />
         </xsl:when>
         <xsl:when test="@name='orion'">
            <strong>Description: </strong> test response from a
            position near the Orion nebula. <br />
         </xsl:when>
         <xsl:when test="@role='sr0'">
            <strong>Description: </strong> test response to a metadata
            query where SR=0. <br />
         </xsl:when>
         <xsl:when test="@role='error'">
            <strong>Description: </strong> test handling of an erroneous
            query. <br />
         </xsl:when>
      </xsl:choose>
      <strong>Type: </strong>
      <xsl:choose>
         <xsl:when test="@role='sr0'">metadata query (SR=0)</xsl:when>
         <xsl:when test="@role='error'">
             <a href="conesearch.html#3">error handling</a>
         </xsl:when>
         <xsl:otherwise>normal query</xsl:otherwise>
      </xsl:choose>
      <br />
      <strong>Arguments: </strong><xsl:value-of select="@options"/><br />
      <strong>URL: </strong>
      <a href="{concat(/*/@baseURL,@options)}">
      <xsl:value-of select="concat(/*/@baseURL,@options)"/></a> <br />
      <strong>VOTable version returned: </strong>
      <xsl:choose>
         <xsl:when test="test[@item='comm']">
            <xsl:text>none </xsl:text> <br />
         </xsl:when>
         <xsl:when test="@votable-version">
            <xsl:value-of select="@votable-version"/> <br />
         </xsl:when>
         <xsl:otherwise><xsl:text>unknown </xsl:text> <br /></xsl:otherwise>
      </xsl:choose>
      <strong>Number of records returned: </strong>
      <xsl:choose>
         <xsl:when test="@recordCount">
            <xsl:value-of select="@recordCount"/>
         </xsl:when>
         <xsl:otherwise>n/a</xsl:otherwise>
      </xsl:choose>
      <p />

      <xsl:if test="contains(@showStatus, 'fail')">
      <h4>Compliance Errors</h4>

      <xsl:choose>
         <xsl:when test="test[@status='fail']">
            Below is a description of failed tests.  Consult the
            Cone Search standard and correct the errors to be fully
            compliant. <p />

            <ul>
            <xsl:apply-templates select="test[@status='fail']" />
            </ul>
         </xsl:when>
         <xsl:otherwise>None found.</xsl:otherwise>
      </xsl:choose>
      </xsl:if>

      <xsl:if test="contains(@showStatus, 'warn')">
      <h4>Warnings</h4>

      <xsl:choose>
         <xsl:when test="test[@status='warn']">
            Below is a list of potential sources of problems that are not 
            strictly violations of the standard.  Consult the
            Cone Search standard document for clarification. <p />

            <ul>
            <xsl:apply-templates select="test[@status='warn']" />
            </ul>
         </xsl:when>
         <xsl:otherwise>None found.</xsl:otherwise>
      </xsl:choose>
      </xsl:if>

      <xsl:if test="contains(@showStatus, 'rec')">
      <h4>Recommendations</h4>

      <xsl:choose>
         <xsl:when test="test[@status='rec']">
            Below are a list of suggested changes that should be made
            to the service response to make it more consistent with the
            standard and common usage and, thus, easier for clients to 
            use.  These changes, however, are not required for
            compliance. 

            <ul>
            <xsl:apply-templates select="test[@status='rec']" />
            </ul>
         </xsl:when>
         <xsl:otherwise>No additional recommendations.</xsl:otherwise>
      </xsl:choose>
      </xsl:if>

      <xsl:if test="contains(@showStatus, 'pass')">
      <h4>Passed Tests</h4>

      <xsl:choose>
         <xsl:when test="test[@status='pass']">
            The service has passed the following tests.

            <ul>
            <xsl:apply-templates select="test[@status='pass']" />
            </ul>
         </xsl:when>
         <xsl:otherwise>Sadly, no passing test results detected.</xsl:otherwise>
      </xsl:choose>
      </xsl:if>

      <hr />
            
   </xsl:template>

   <xsl:template match="test">
      <li> <a href="{concat('conesearch.html#',@item)}">
           <xsl:value-of select="@item"/></a>
           <xsl:text>  </xsl:text><xsl:value-of select="."/> </li>
   </xsl:template>

</xsl:stylesheet>
