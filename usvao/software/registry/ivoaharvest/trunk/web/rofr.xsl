<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<xsl:stylesheet xmlns:xsl="http://www.w3.org/1999/XSL/Transform" 
                xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance" 
                version="1.0">

   <xsl:output method="html" encoding="UTF-8" />

   <xsl:variable name="rofroai"
                 select="http://rofr.ivoa.net/cgi-bin/oai.pl"/>

   <xsl:template match="/" xml:space="preserve">
<html> <head>
  <title>Registry Harvesting Validater</title>

  <style type="text/css">
<!--
.tiny {FONT-SIZE: 7pt;}
-->
  </style>
  <link href="ivoa_rofr.css" rel="stylesheet" type="text/css"/>
  <link href="tp.css" rel="stylesheet" type="text/css"/>
</head>
<body>

<center>
<table width="100%">
  <tr>
    <td>
      <font class="titleText"><b>I</b>nternational
      <span style="visibility: hidden">i</span>
      <b>V</b>irtual 
      <span style="visibility: hidden">i</span>
      <b>O</b>bservatory 
      <span style="visibility: hidden">i</span><b>A</b>lliance</font><br />
      <font class="titleText" style="font-size: 18pt; font-weight: 600">
      <a name="rofr" title="IVOA Registry of Registries" 
         class="titleText">IVOA Registry of Registries</a>
      </font><br /><br />

      <table cellspacing="0" cellpadding="0" border="0" width="100%">
        <tr>
          <!-- the local links -->
          <td class="rollcall"><a href="http://www.ivoa.net/Documents/latest/RegistryInterface.html">Registry Interfaces Spec.</a></td>
          <td class="rollcall"><a href="http://www.openarchives.org/OAI/openarchivesprotocol.html">OAI-PMH Spec.</a></td>
          <td class="rollcall"><a href="http://www.ivoa.net/Documents/latest/VOResource.html">VOResource Spec.</a></td>
        </tr>
      </table>
    </td>
    <td>
      <a href="/"><img src="IVOA_wb_300.jpg" width="150" 
         height="85" border="0" alt="ivoa.net" /></a>
    </td>
  </tr>
</table>
</center>

<xsl:comment> =======================================================================
  -  Page Content
  -  ======================================================================= </xsl:comment>

<h1>Welcome to the Registry of Registries</h1>

<p>
The Registry of Registries (RofR, pronounced <em>rover</em>) is an
IVOA publishing registry operated on behalf of the 
<a href="http://www.ivoa.net/">International Virtual Observatory
Alliance (IVOA)</a> and overseen by the
<a href="http://www.ivoa.net/twiki/bin/view/IVOA/IvoaResReg">IVOA
Registry Working Group</a>.  This registry publishes the existance of
all publishing registries known to the IVOA.  When a resource metadata
harvester harvests from these publishing registries, they can discover
all published VO resources around the world.  The design and recommend
uses of the RofR is documented in the 
<a href="http://www.ivoa.net/Documents/latest/RofR.html">IVOA Note,
The Registry of Registries</a>.  
</p>

<p>
If you maintain a publishing registry and you are ready to let it be
known to the outside world, you can
<a href="http://rofr.ivoa.net:8080/vovalidation/regvalidate.html">register
it here.</a>  Before you are allowed to register, you must demonstrate
that it conforms to the
<a href="http://www.ivoa.net/Documents/latest/RegistryInterface.html">IVOA 
Registry Interfaces standard.</a>  Note, that you can use the
<a href="http://rofr.ivoa.net:8080/vovalidation/regvalidate.html">registry
validater</a> to check your registry without actually registering it.
</p>

<h3>Currently Registered Publishing Registries</h3>

<dl>
   <xsl:for-each select="//ri:Resource">

  <dt> <xsl:value-of select="title"/> </dt>
  <dd> <strong>IVOA Identifier: </strong> 
       <a href="{$rofroai}?verb=GetRecord&metadataPrefix=ivo_vor&identifier={//ri:Resource/identifier}"><xsl:value-of select="identifier"/></a> <br />
       <strong>OAI service endpoint: </strong> 
       <xsl:value-of
       select="capability[@standardID='ivo://ivoa.net/std/Registry']/interface[contains(@xsi:type,':OAIHTTP') and @role='std' and (@version='1.0' or not(@version)]/accessURL"/> </dd>
      
   </xsl:for-each>
</dl>


<xsl:comment> =======================================================================
  -  End Page Content
  -  ======================================================================= </xsl:comment>

</body>
</html>
   </xsl:template>

</xsl:stylesheet>
