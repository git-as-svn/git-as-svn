<?xml version="1.0" encoding="UTF-8"?>
<xsl:stylesheet xmlns:xsl="http://www.w3.org/1999/XSL/Transform"
				xmlns:d="http://docbook.org/ns/docbook"
				xmlns:fo="http://www.w3.org/1999/XSL/Format"
				xmlns:xslthl="http://xslthl.sf.net"
				exclude-result-prefixes="xslthl d"
				version='1.0'>

	<xsl:import href="http://docbook.sourceforge.net/release/xsl/current/fo/docbook.xsl"/>
	<xsl:import href="http://docbook.sourceforge.net/release/xsl/current/fo/highlight.xsl"/>
	<xsl:import href="common.xsl"/>

	<!-- Extensions -->
	<xsl:param name="fop1.extensions" select="1"/>
	<xsl:param name="paper.type" select="'A4'"/>

	<!-- Format Variable Lists as Blocks (prevents horizontal overflow) -->
	<xsl:param name="variablelist.as.blocks">1</xsl:param>
	<xsl:param name="body.start.indent">0pt</xsl:param>
	<xsl:param name="ulink.footnotes">1</xsl:param>

	<!-- COLORED AND HYPHENATED LINKS -->
	<xsl:template match="ulink">
		<fo:basic-link external-destination="{@url}"
				xsl:use-attribute-sets="xref.properties"
				text-decoration="underline"
				color="blue">
			<xsl:choose>
				<xsl:when test="count(child::node())=0">
					<xsl:value-of select="@url"/>
				</xsl:when>
				<xsl:otherwise>
					<xsl:apply-templates/>
				</xsl:otherwise>
			</xsl:choose>
		</fo:basic-link>
	</xsl:template>

	<xsl:template match="link">
		<fo:basic-link internal-destination="{@linkend}"
				xsl:use-attribute-sets="xref.properties"
				text-decoration="underline"
				color="blue">
			<xsl:choose>
				<xsl:when test="count(child::node())=0">
					<xsl:value-of select="@linkend"/>
				</xsl:when>
				<xsl:otherwise>
					<xsl:apply-templates/>
				</xsl:otherwise>
			</xsl:choose>
		</fo:basic-link>
	</xsl:template>

	<!-- SYNTAX HIGHLIGHT -->
	<xsl:template match='xslthl:keyword' mode="xslthl">
	  <fo:inline font-weight="bold" color="#7F0055"><xsl:apply-templates mode="xslthl"/></fo:inline>
	</xsl:template>

	<xsl:template match='xslthl:string' mode="xslthl">
	  <fo:inline font-weight="bold" font-style="italic" color="#2A00FF"><xsl:apply-templates mode="xslthl"/></fo:inline>
	</xsl:template>

	<xsl:template match='xslthl:comment' mode="xslthl">
	  <fo:inline font-style="italic" color="#3F5FBF"><xsl:apply-templates mode="xslthl"/></fo:inline>
	</xsl:template>

	<xsl:template match='xslthl:tag' mode="xslthl">
	  <fo:inline font-weight="bold" color="#3F7F7F"><xsl:apply-templates mode="xslthl"/></fo:inline>
	</xsl:template>

	<xsl:template match='xslthl:attribute' mode="xslthl">
	  <fo:inline font-weight="bold" color="#7F007F"><xsl:apply-templates mode="xslthl"/></fo:inline>
	</xsl:template>

	<xsl:template match='xslthl:value' mode="xslthl">
	  <fo:inline font-weight="bold" color="#2A00FF"><xsl:apply-templates mode="xslthl"/></fo:inline>
	</xsl:template>
</xsl:stylesheet>
