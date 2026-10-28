/*
 * TrataErro.groovy
 * Usado no Exception Subprocess do iFlow CBS_IBS_Simulador.
 *
 * Converte a exceção capturada em uma resposta JSON padronizada:
 *   - XML reprovado no XML Validator (XSD) -> HTTP 400 XML_INVALIDO
 *   - IllegalArgumentException             -> HTTP 400 REQUISICAO_INVALIDA
 *   - qualquer outra                       -> HTTP 500 ERRO_PROCESSAMENTO
 */
import com.sap.gateway.ip.core.customdev.util.Message
import groovy.json.JsonOutput

import java.time.OffsetDateTime
import java.time.ZoneId

def Message processData(Message message) {
    def ex = message.getProperty("CamelExceptionCaught")

    // O CPI às vezes encapsula a exceção original; buscamos a causa raiz.
    def causa = ex
    while (causa?.cause != null && causa.cause != causa) {
        causa = causa.cause
    }

    boolean erroXsd = erroDeValidacaoXsd(ex)
    boolean erroCliente = erroXsd || causa instanceof IllegalArgumentException
    int status = erroCliente ? 400 : 500

    def resposta = [
        status   : status,
        erro     : erroXsd ? "XML_INVALIDO" : (erroCliente ? "REQUISICAO_INVALIDA" : "ERRO_PROCESSAMENTO"),
        mensagem : causa?.message ?: "Erro não identificado",
        ocorridoEm: OffsetDateTime.now(ZoneId.of("America/Sao_Paulo")).toString()
    ]

    message.setBody(semEscape(JsonOutput.prettyPrint(JsonOutput.toJson(resposta))))
    message.setHeader("Content-Type", "application/json")
    message.setHeader("CamelHttpResponseCode", status)
    return message
}

// XML Validator do CPI: a exceção (ou uma causa) é de validação e/ou traz
// a mensagem do parser de schema, que começa com "cvc-".
private boolean erroDeValidacaoXsd(def ex) {
    def e = ex
    int guarda = 0
    while (e != null && guarda++ < 20) {
        if (e.getClass().name.toLowerCase().contains("validat") || (e.message ?: "").contains("cvc-")) {
            return true
        }
        e = (e.cause != e) ? e.cause : null
    }
    return false
}

// Mantém acentos legíveis no JSON (ver CalculaCBSIBS.groovy).
private String semEscape(String json) {
    return json.replaceAll(/(?<!\\)\\u([0-9a-fA-F]{4})/) { all, hex ->
        int c = Integer.parseInt(hex, 16)
        c >= 0x80 ? new String(Character.toChars(c)) : all
    }
}
