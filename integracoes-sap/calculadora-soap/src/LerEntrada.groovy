/*
 * LerEntrada.groovy
 * iFlow: Calculadora_SOAP (SAP Cloud Integration)
 *
 * Fica ANTES do Content Modifier "MontarEnvelope". Lê o JSON recebido pelo
 * endpoint HTTPS ({"a": 5, "b": 3}) e grava os valores como Exchange
 * Properties, para o Content Modifier poder usá-los no XML do envelope SOAP
 * (${property.numeroA} e ${property.numeroB}).
 */
import com.sap.gateway.ip.core.customdev.util.Message
import groovy.json.JsonSlurper

def Message processData(Message message) {
    def body = message.getBody(String) as String
    def json = new JsonSlurper().parseText(body)

    if (json?.a == null || json?.b == null) {
        throw new IllegalArgumentException("Body precisa ter os campos 'a' e 'b' (números).")
    }

    message.setProperty("numeroA", json.a.toString())
    message.setProperty("numeroB", json.b.toString())
    return message
}
