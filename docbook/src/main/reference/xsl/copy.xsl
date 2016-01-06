<?xml version="1.0"?>
<xsl:stylesheet version="1.0" xmlns:xsl="http://www.w3.org/1999/XSL/Transform" xmlns:xi="http://www.w3.org/2001/XInclude">
	<xsl:param name="lang" select="''" />
	
	<!-- Remove all xml:lang attributes -->
	<xsl:template match="@xml:lang" />
	
	<!-- Copy all attributes as is -->
	<xsl:template match="@*">
		<xsl:copy />
	</xsl:template>
	
	<!-- Copy all nodes as is -->
	<xsl:template match="node()">
		<xsl:copy>
			<xsl:apply-templates select="@*|node()" />
		</xsl:copy>
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
			<xsl:when test="(($l = $lang) or ($l = 'C')) and ($lang != '')">
			</xsl:when>
			<xsl:otherwise>
				<xsl:copy />
			</xsl:otherwise>
		</xsl:choose>
	</xsl:template>
</xsl:stylesheet>
