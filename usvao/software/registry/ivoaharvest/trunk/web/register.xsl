<?xml version="1.0" encoding="ISO-8859-1"?>
<xsl:stylesheet version="1.0" xmlns:xsl="http://www.w3.org/1999/XSL/Transform">

   <xsl:template match="/">
      <div>
         <xsl:if test="register/@status='ok'">
<h3>Step 3: Relax</h3>
<p>
Your registry is now part of the VO Registry Framework.  You can view
the record via the RofR's OAI interface at 
<a href="{register/@view}"><xsl:value-of
   select="register/@view"/></a>.  
</p>
         </xsl:if>
         <xsl:if test="register/@status!='ok'">
<h3><font color="red">Registration Error</font></h3>
<p>
<xsl:value-of select="register"/>
</p>
<p>
Contact Ray Plante (rplante at ncsa.uiuc.edu) if you think received
      this message by mistake.
</p>
         </xsl:if>
      </div>

   </xsl:template>
</xsl:stylesheet>