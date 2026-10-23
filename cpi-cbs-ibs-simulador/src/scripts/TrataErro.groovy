/*
 * TrataErro.groovy
 * Usado no Exception Subprocess do iFlow CBS_IBS_Simulador.
 *
 * Converte a exceção capturada em uma resposta JSON padronizada:
 *   - IllegalArgumentException -> HTTP 400 (erro do chamador: payload inválido)
 *   - qualquer outra            -> HTTP 500 (erro de configuração/processamento)
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

    boolean erroCliente = causa instanceof IllegalArgumentException
    int status = erroCliente ? 400 : 500

    def resposta = [
        status   : status,
        erro     : erroCliente ? "REQUISICAO_INVALIDA" : "ERRO_PROCESSAMENTO",
        mensagem : causa?.message ?: "Erro não identificado",
        ocorridoEm: OffsetDateTime.now(ZoneId.of("America/Sao_Paulo")).toString()
    ]

    message.setBody(semEscape(JsonOutput.prettyPrint(JsonOutput.toJson(resposta))))
    message.setHeader("Content-Type", "application/json")
    message.setHeader("CamelHttpResponseCode", status)
    return message
}

// Mantém acentos legíveis no JSON (ver CalculaCBSIBS.groovy).
private String semEscape(String json) {
    return json.replaceAll(/(?<!\\)\\u([0-9a-fA-F]{4})/) { all, hex ->
        int c = Integer.parseInt(hex, 16)
        c >= 0x80 ? new String(Character.toChars(c)) : all
    }
}
