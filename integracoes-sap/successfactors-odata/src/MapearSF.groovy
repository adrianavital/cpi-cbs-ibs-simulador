/*
 * MapearSF.groovy
 * iFlow: SF_EmployeeCentral_OData (SAP Cloud Integration)
 *
 * A API SuccessFactors "Employee Profile / Background_Community" é OData V2
 * autenticada por um header customizado "APIKey" (não é Basic Auth real, apesar
 * do rótulo no SAP Business Accelerator Hub). Por isso o receiver é um HTTP
 * Adapter genérico, não o OData Adapter: o OData Adapter do CPI não tem um tipo
 * de credencial para "header customizado".
 *
 * O payload OData V2 sempre vem embrulhado em { "d": { "results": [ ... ] } }.
 * Este script extrai essa lista e devolve um JSON limpo, no mesmo padrão dos
 * outros endpoints do portfólio (status, erro/observação, campos previsíveis).
 */
import com.sap.gateway.ip.core.customdev.util.Message
import groovy.json.JsonOutput
import groovy.json.JsonSlurper

def Message processData(Message message) {
    def body = message.getBody(String) as String
    def json = new JsonSlurper().parseText(body)

    // Blindagem: se a API mudar o formato ou devolver vazio, não deixa o
    // Groovy estourar NullPointerException sem explicação.
    def results = json?.d?.results
    if (results == null) {
        throw new IllegalArgumentException("Resposta da API SuccessFactors sem 'd.results' (formato inesperado).")
    }

    def itens = results.collect { r ->
        [
            backgroundElementId: r.backgroundElementId,
            userId             : r.userId,
            nome               : r.name,
            cargo              : r.role,
            dataInicio         : r.startDate,
            dataFim            : r.endDate,
            ultimaAtualizacao  : r.lastModifiedDate
        ]
    }

    def resposta = [
        origem      : "SuccessFactors Employee Central (sandbox API Business Accelerator Hub)",
        entidade    : "Background_Community",
        quantidade  : itens.size(),
        funcionarios: itens,
        observacao  : "Simulação para estudo com dados de sandbox da SAP. Não representa dados reais de funcionários."
    ]

    message.setBody(JsonOutput.prettyPrint(JsonOutput.toJson(resposta)))
    message.setHeader("Content-Type", "application/json")
    message.setHeader("CamelHttpResponseCode", 200)
    return message
}
