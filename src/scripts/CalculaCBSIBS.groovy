/*
 * CalculaCBSIBS.groovy
 * iFlow: CBS_IBS_Simulador (SAP Cloud Integration)
 *
 * Recebe um documento fiscal simplificado em JSON, valida os itens e calcula
 * CBS e IBS por item e no total, usando alíquotas parametrizadas no iFlow.
 *
 * Entrada : body JSON  (ver test/payloads/request_ok.json)
 * Parâmetros (Exchange Properties definidas no Content Modifier):
 *   aliquotaCBS  -> percentual, ex.: 0.9
 *   aliquotaIBS  -> percentual, ex.: 0.1
 *   logPayload   -> "true" anexa entrada/saída no MPL (opcional)
 * Saída   : body JSON com valores por item e totais
 *
 * AVISO: simulação para fins de estudo/portfólio. Base de cálculo simplificada
 * (quantidade x valor unitário - desconto). Não substitui o cálculo oficial.
 */
import com.sap.gateway.ip.core.customdev.util.Message
import groovy.json.JsonOutput
import groovy.json.JsonSlurper

import java.math.RoundingMode
import java.time.OffsetDateTime
import java.time.ZoneId

def Message processData(Message message) {
    String bodyIn = message.getBody(String) as String
    if (!bodyIn?.trim()) {
        throw new IllegalArgumentException("Body vazio: envie o documento em JSON.")
    }

    def entrada
    try {
        entrada = new JsonSlurper().parseText(bodyIn)
    } catch (Exception e) {
        throw new IllegalArgumentException("JSON inválido: " + e.message)
    }

    BigDecimal aliqCBS = lerAliquota(message, "aliquotaCBS")
    BigDecimal aliqIBS = lerAliquota(message, "aliquotaIBS")

    def doc = entrada.documento
    if (!doc?.numero) {
        throw new IllegalArgumentException("Campo obrigatório ausente: documento.numero")
    }
    def itens = entrada.itens
    if (!(itens instanceof List) || itens.isEmpty()) {
        throw new IllegalArgumentException("O documento precisa ter ao menos 1 item em 'itens'.")
    }

    BigDecimal totBase = 0, totCBS = 0, totIBS = 0
    def itensSaida = []

    itens.eachWithIndex { it, idx ->
        String ref = "itens[${idx}]"
        String ncm = (it.ncm ?: "").toString()
        if (!(ncm ==~ /\d{8}/)) {
            throw new IllegalArgumentException("${ref}.ncm deve ter 8 dígitos numéricos (recebido: '${ncm}').")
        }
        BigDecimal qtd = numero(it.quantidade, "${ref}.quantidade")
        BigDecimal vUnit = numero(it.valorUnitario, "${ref}.valorUnitario")
        BigDecimal desc = it.desconto != null ? numero(it.desconto, "${ref}.desconto") : 0
        if (qtd <= 0) throw new IllegalArgumentException("${ref}.quantidade deve ser maior que zero.")
        if (vUnit < 0 || desc < 0) throw new IllegalArgumentException("${ref}: valores não podem ser negativos.")

        BigDecimal base = arred(qtd * vUnit - desc)
        if (base < 0) throw new IllegalArgumentException("${ref}: desconto maior que o valor do item.")

        BigDecimal vCBS = arred(base * aliqCBS / 100)
        BigDecimal vIBS = arred(base * aliqIBS / 100)

        totBase += base; totCBS += vCBS; totIBS += vIBS
        itensSaida << [
            item       : it.item ?: (idx + 1),
            ncm        : ncm,
            descricao  : it.descricao,
            baseCalculo: base,
            valorCBS   : vCBS,
            valorIBS   : vIBS
        ]
    }

    def saida = [
        documento    : doc,
        parametros   : [aliquotaCBS: aliqCBS, aliquotaIBS: aliqIBS],
        itens        : itensSaida,
        totais       : [
            baseCalculo       : arred(totBase),
            valorCBS          : arred(totCBS),
            valorIBS          : arred(totIBS),
            valorTotalTributos: arred(totCBS + totIBS)
        ],
        processadoEm : OffsetDateTime.now(ZoneId.of("America/Sao_Paulo")).toString(),
        observacao   : "Simulação para estudo. Não substitui o cálculo fiscal oficial."
    ]

    String bodyOut = semEscape(JsonOutput.prettyPrint(JsonOutput.toJson(saida)))
    message.setBody(bodyOut)
    message.setHeader("Content-Type", "application/json")
    message.setProperty("totalTributos", saida.totais.valorTotalTributos.toPlainString())

    if (message.getProperty("logPayload")?.toString()?.equalsIgnoreCase("true")) {
        anexarLog(message, "Entrada", bodyIn)
        anexarLog(message, "Saida", bodyOut)
    }
    return message
}

private BigDecimal lerAliquota(Message message, String nome) {
    def valor = message.getProperty(nome)
    if (valor == null || valor.toString().trim().isEmpty()) {
        throw new IllegalStateException("Parâmetro '${nome}' não configurado no Content Modifier.")
    }
    try {
        BigDecimal v = new BigDecimal(valor.toString().trim().replace(",", "."))
        if (v < 0 || v > 100) throw new IllegalStateException("Parâmetro '${nome}' fora da faixa 0-100: ${v}")
        return v
    } catch (NumberFormatException e) {
        throw new IllegalStateException("Parâmetro '${nome}' não é numérico: '${valor}'")
    }
}

private BigDecimal numero(def valor, String campo) {
    if (valor == null) throw new IllegalArgumentException("Campo obrigatório ausente: ${campo}")
    try {
        return new BigDecimal(valor.toString())
    } catch (NumberFormatException e) {
        throw new IllegalArgumentException("${campo} não é numérico: '${valor}'")
    }
}

private BigDecimal arred(BigDecimal v) {
    return v.setScale(2, RoundingMode.HALF_UP)
}

// JsonOutput grava acentos como ç. Convertemos de volta para "ç" (só caracteres
// acima de 0x7F; aspas e caracteres de controle continuam escapados, então o JSON segue válido).
private String semEscape(String json) {
    return json.replaceAll(/(?<!\\)\\u([0-9a-fA-F]{4})/) { all, hex ->
        int c = Integer.parseInt(hex, 16)
        c >= 0x80 ? new String(Character.toChars(c)) : all
    }
}

private void anexarLog(Message message, String nome, String conteudo) {
    // messageLogFactory existe no runtime do CPI; em teste local ele não existe.
    if (binding.hasVariable("messageLogFactory")) {
        def log = messageLogFactory.getMessageLog(message)
        log?.addAttachmentAsString(nome, conteudo, "application/json")
    }
}
