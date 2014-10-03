<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<xsl:stylesheet xmlns:xsl="http://www.w3.org/1999/XSL/Transform" 
                xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance" 
                xmlns:ri="http://www.ivoa.net/xml/RegistryInterface/v1.0"
                xmlns:rbx="http://nvo.ncsa.uiuc.edu/xml/VORegInABox/v1.0"
                version="1.0">

   <xsl:output method="html" encoding="UTF-8" />

   <xsl:include href="Resource_Form_Site.xsl"/>

   <xsl:template match="/">
     <xsl:apply-templates select="." mode="site">
        <xsl:with-param name="title">Site Identification</xsl:with-param>
     </xsl:apply-templates>
   </xsl:template>

   <xsl:template match="/" mode="appbody">
      <script type="text/javascript" src="/vopub/Resource_Form.js" ></script>
      <xsl:apply-templates select="voreginabox" />
   </xsl:template>

   <xsl:template match="voreginabox">

<h1>Site Identification Form</h1>  

<xsl:if test="@prob != ''">

<h3>A Problem Was Encountered:</h3>
<blockquote><font color="red">
<xsl:value-of select="@prob"/>
</font></blockquote>

</xsl:if>

        <p>
        Please provide a username and password for accessing your
        repository of resource descriptions.
        </p>

        <p>
        Click on "Create Repository" to save the configuration and begin adding
resources to the repository
        </p>

        <p>
        Click on "Reset" to reset the values on this page.
        </p>

<form method="post" action="/cgi-bin/sso/vaologin.cgi/Resource_Form.cgi" enctype="multipart/form-data">
        <p>
        <a href="/confighelp.html#Username">
           Login name for Repository:</a>
        <input type="text" name="uname" value="{@uname}" size="60" />
        </p>

        <p>
        <a href="/confighelp.html#Password">
           Password for Login for Repository:</a>
        <input type="password" name="passwd" size="20" maxlength="25" />
        </p>

        <p>
        <a href="/confighelp.html#Password">
           Verify Password for Repository:</a>
        <input type="password" name="passwd2" size="20" maxlength="25" />
        </p>

        <p>
        <table border="0" width="100%" cellpadding="1" cellspacing="8">
        <tr align="center">
        <td bgcolor="bbbbbb" colspan="2">
           <xsl:if test="@tryout!=''">
                <input type="hidden" name="tryout" value="{@tryout}"/>
           </xsl:if>
           <input type="hidden" name="src" value="{@src}" />
           <input type="hidden" name="reg" value="alt" />
           <input type="submit" name="ftype" value="Create Repository" />
           <input type="reset" value="Reset Form" /></td>
        </tr>
        </table>
        </p>

        <p>
        <b><font color="#ff0000"></font></b>
        </p>
</form>

   </xsl:template>

</xsl:stylesheet>