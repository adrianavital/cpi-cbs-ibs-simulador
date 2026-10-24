package com.sap.it.api.mapping

/**
 * Mock local da ValueMappingApi do SAP Cloud Integration.
 * Os testes preenchem "tabela" para simular o artefato Value Mapping implantado.
 */
class ValueMappingApi {
    static Map<String, String> tabela = [:]

    String getMappedValue(String srcAgency, String srcId, String srcValue, String tgtAgency, String tgtId) {
        return tabela["${srcAgency}|${srcId}|${srcValue}|${tgtAgency}|${tgtId}".toString()]
    }
}
