<?xml version="1.0"?>
<xsl:stylesheet version="2.0" xmlns:xsl="http://www.w3.org/1999/XSL/Transform"
	 xmlns:fn="http://www.w3.org/2005/xpath-functions"
	 xmlns:xi="http://www.w3.org/2001/XInclude">
	<xsl:param name="lang" select="''" />
	<xsl:param name="skip" select="''" />
	
	<!-- Remove all xml:lang attributes -->
	<xsl:template match="@xml:lang" />
	
	<!-- Copy all attributes as is -->
	<xsl:template match="@*">
		<xsl:copy />
	</xsl:template>
	
	<!-- Copy all nodes as is -->
	<xsl:template match="node()" priority="-1">
		<xsl:copy>
			<xsl:apply-templates select="@*|node()" />
		</xsl:copy>
	</xsl:template>
	
	<xsl:template match="@fileref">
		<xsl:variable name="l" select="ancestor-or-self::*[@xml:lang][1]/@xml:lang" />
		<xsl:attribute name="fileref">
			<xsl:choose>
				<xsl:when test="($l = $lang) or ($l = 'C') or ($lang = '')">
					<xsl:value-of select="." />
				</xsl:when>
				<xsl:otherwise>
					<xsl:value-of select="fn:replace(., $l, $lang)" />
				</xsl:otherwise>
			</xsl:choose>
		</xsl:attribute>
	</xsl:template>
	
	<xsl:template match="xi:include[@href][@parse='xml' or not(@parse)]">
		<xsl:apply-templates select="document(@href)/*" />
	</xsl:template>
	
	<!-- Copy text nodes only for foreign language -->
	<xsl:template match="text()">
		<xsl:variable name="l" select="ancestor-or-self::*[@xml:lang][1]/@xml:lang" />
		<xsl:choose>
			<xsl:when test="normalize-space() = ''">
				<xsl:copy />
			</xsl:when>
			<xsl:when test="(($l = $skip) or ($l = 'C')) and ($skip != '')">
			</xsl:when>
			<xsl:otherwise>
				<xsl:copy />
			</xsl:otherwise>
		</xsl:choose>
	</xsl:template>
</xsl:stylesheet>
