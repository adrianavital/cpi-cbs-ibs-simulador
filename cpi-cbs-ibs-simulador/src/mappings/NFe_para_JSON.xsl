<?xml version="1.0" encoding="UTF-8"?>
<!--
  NFe_para_JSON.xsl
  iFlow: CBS_IBS_Simulador_NFe (SAP Cloud Integration)

  Converte o XML da NF-e (layout 4.00, com ou sem nfeProc) no JSON
  de entrada do script CalculaCBSIBS.groovy.

  Origem (XPath)                          -> Destino (JSON)
  infNFe/@Id (sem o prefixo "NFe")        -> documento.chaveAcesso
  ide/nNF                                 -> documento.numero
  ide/serie                               -> documento.serie
  ide/dhEmi (só a data)                   -> documento.dataEmissao
  emit/enderEmit/UF                       -> documento.ufOrigem
  dest/enderDest/UF                       -> documento.ufDestino
  det/@nItem                              -> itens[].item
  det/prod/NCM                            -> itens[].ncm
  det/prod/xProd                          -> itens[].descricao
  det/prod/qCom                           -> itens[].quantidade
  det/prod/vUnCom                         -> itens[].valorUnitario
  det/prod/vDesc (opcional)               -> itens[].desconto

  Se o XML não tiver infNFe, o JSON sai sem documento.numero e o script
  Groovy devolve HTTP 400 com a mensagem do campo ausente.
-->
<xsl:stylesheet version="2.0"
    xmlns:xsl="http://www.w3.org/1999/XSL/Transform"
    xmlns:nfe="http://www.portalfiscal.inf.br/nfe"
    xmlns:f="urn:local:funcoes"
    exclude-result-prefixes="nfe f">

  <xsl:output method="text" encoding="UTF-8"/>

  <!-- Texto JSON: escapa barra invertida e aspas -->
  <xsl:function name="f:txt" as="xs:string" xmlns:xs="http://www.w3.org/2001/XMLSchema">
    <xsl:param name="v"/>
    <xsl:sequence select="concat('&quot;',
        replace(replace(normalize-space(string($v)), '\\', '\\\\'), '&quot;', '\\&quot;'),
        '&quot;')"/>
  </xsl:function>

  <!-- Número JSON: vazio vira null -->
  <xsl:function name="f:num" as="xs:string" xmlns:xs="http://www.w3.org/2001/XMLSchema">
    <xsl:param name="v"/>
    <xsl:variable name="s" select="normalize-space(string($v))"/>
    <xsl:sequence select="if ($s castable as xs:decimal) then $s else 'null'"/>
  </xsl:function>

  <xsl:template match="/">
    <xsl:variable name="inf" select="(//nfe:infNFe)[1]"/>
    <xsl:text>{&#10;  "documento": {</xsl:text>
    <xsl:if test="$inf">
      <xsl:text>&#10;    "chaveAcesso": </xsl:text><xsl:value-of select="f:txt(substring-after($inf/@Id, 'NFe'))"/>
      <xsl:text>,&#10;    "numero": </xsl:text><xsl:value-of select="f:txt($inf/nfe:ide/nfe:nNF)"/>
      <xsl:text>,&#10;    "serie": </xsl:text><xsl:value-of select="f:txt($inf/nfe:ide/nfe:serie)"/>
      <xsl:text>,&#10;    "dataEmissao": </xsl:text><xsl:value-of select="f:txt(substring($inf/nfe:ide/nfe:dhEmi, 1, 10))"/>
      <xsl:text>,&#10;    "ufOrigem": </xsl:text><xsl:value-of select="f:txt($inf/nfe:emit/nfe:enderEmit/nfe:UF)"/>
      <xsl:text>,&#10;    "ufDestino": </xsl:text><xsl:value-of select="f:txt($inf/nfe:dest/nfe:enderDest/nfe:UF)"/>
    </xsl:if>
    <xsl:text>&#10;  },&#10;  "itens": [</xsl:text>
    <xsl:for-each select="$inf/nfe:det">
      <xsl:variable name="p" select="nfe:prod"/>
      <xsl:if test="position() > 1"><xsl:text>,</xsl:text></xsl:if>
      <xsl:text>&#10;    { "item": </xsl:text><xsl:value-of select="f:num(@nItem)"/>
      <xsl:text>, "ncm": </xsl:text><xsl:value-of select="f:txt($p/nfe:NCM)"/>
      <xsl:text>, "descricao": </xsl:text><xsl:value-of select="f:txt($p/nfe:xProd)"/>
      <xsl:text>, "quantidade": </xsl:text><xsl:value-of select="f:num($p/nfe:qCom)"/>
      <xsl:text>, "valorUnitario": </xsl:text><xsl:value-of select="f:num($p/nfe:vUnCom)"/>
      <xsl:if test="$p/nfe:vDesc">
        <xsl:text>, "desconto": </xsl:text><xsl:value-of select="f:num($p/nfe:vDesc)"/>
      </xsl:if>
      <xsl:text> }</xsl:text>
    </xsl:for-each>
    <xsl:text>&#10;  ]&#10;}&#10;</xsl:text>
  </xsl:template>

</xsl:stylesheet>
