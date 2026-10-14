package com.sap.gateway.ip.core.customdev.util

/**
 * Mock mínimo da interface Message do SAP Cloud Integration,
 * apenas para rodar os scripts localmente. Não vai para o tenant.
 *
 * Atenção: como a interface real tem getProperty/setProperty (Exchange
 * Properties), use sempre os métodos explícitos (setBody, getHeaders...)
 * e não a sintaxe de propriedade do Groovy (msg.body = ...).
 */
class Message {
    private Object corpo
    private Map<String, Object> cabecalhos = [:]
    private Map<String, Object> propriedades = [:]

    def <T> T getBody(Class<T> type) { return (T) (corpo == null ? null : corpo.toString()) }
    Object getBody() { corpo }
    void setBody(Object b) { corpo = b }

    Map<String, Object> getHeaders() { cabecalhos }
    void setHeader(String k, Object v) { cabecalhos[k] = v }

    Map<String, Object> getProperties() { propriedades }
    Object getProperty(String k) { propriedades[k] }
    void setProperty(String k, Object v) { propriedades[k] = v }
}
