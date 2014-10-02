<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<xsl:stylesheet xmlns:xsl="http://www.w3.org/1999/XSL/Transform" 
                xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance" 
                version="1.0">

   <xsl:output method="html" encoding="UTF-8" />

   <xsl:variable name="rofroai"
                 select="http://rofr.ivoa.net/cgi-bin/rofr/oai.pl"/>

   <xsl:template match="/" xml:space="preserve">

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


   </xsl:template>

</xsl:stylesheet>
